package com.minis.muyu;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.RemoteViews;

import java.util.Random;

/** 木鱼：剪影 + 今日功德 + 极简经卷行 */
public class FishWidget extends AppWidgetProvider {
    static final String ACTION_KNOCK = "com.minis.muyu.KNOCK";

    private static final Random RND = new Random();
    private static SoundPool SP;
    private static final int[] KNOCK_IDS = new int[3];
    private static int BOWL_ID;
    private static String TONE_SIG = "";
    private static long LAST_KNOCK_AT = 0L;
    private static Typeface SERIF;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, id, 0, false));
    }

    @Override
    public void onReceive(Context c, Intent it) {
        super.onReceive(c, it);
        if (!ACTION_KNOCK.equals(it.getAction())) return;

        long now = System.currentTimeMillis();
        if (now - LAST_KNOCK_AT < 70 && !isFirst()) return;
        LAST_KNOCK_AT = now;

        if (MuyuState.vibOn(c)) vibrate(c);
        if (MuyuState.soundOn(c)) playKnock(c);
        boolean done = MuyuState.knock(c);

        int len = SutraText.length(c);
        MuyuState.advanceSutra(c, len);

        renderAll(c, 1, true);                      // 压形 + 数字浮现
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { renderAll(c, 2, true); }
        }, 90);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { renderAll(c, 0, true); }
        }, 240);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { renderAll(c, 0, false); }   // 数字隐去
        }, 2400);

        if (done && MuyuState.soundOn(c)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { playBowl(c); }
            }, 150);
        }
    }

    private static boolean isFirst() { return false; }

    /** press: 0 静息 / 1 压形 / 2 回弹 */
    static RemoteViews build(Context c, int widgetId, int press, boolean hot) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        boolean jade = MuyuState.muyuJade(c);
        if (jade) {
            rv.setImageViewBitmap(R.id.fish, JadeRender.render(c, press == 1 ? 1 : (press == 2 ? 2 : 0)));
        } else {
            int img = press == 1 ? R.drawable.fish_tight_press
                    : (press == 2 ? R.drawable.fish_tight_press2 : R.drawable.fish_tight);
            rv.setImageViewResource(R.id.fish, img);
        }
        if (MuyuState.sutraOn(c)) {
            rv.setViewVisibility(R.id.row, android.view.View.VISIBLE);
            rv.setImageViewBitmap(R.id.row, rowBitmap(c, true, hot));
        } else {
            rv.setViewVisibility(R.id.row, android.view.View.GONE);
        }
        rv.setOnClickPendingIntent(R.id.card, knockPi(c, widgetId));
        return rv;
    }

    private static Bitmap PAPER;
    private static Bitmap paperTexture(Context c) {
        if (PAPER == null) {
            PAPER = BitmapFactory.decodeResource(c.getResources(), R.drawable.paper);
        }
        return PAPER;
    }

    static PendingIntent knockPi(Context c, int id) {
        Intent it = new Intent(ACTION_KNOCK);
        it.setComponent(new ComponentName(c, FishWidget.class));
        it.putExtra("wid", id);
        return PendingIntent.getBroadcast(c, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void renderAll(Context c, int press, boolean hot) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, FishWidget.class));
        for (int id : ids) m.updateAppWidget(id, build(c, id, press, hot));
    }

    /* ---------------- 经卷行绘制 ---------------- */

    static Bitmap rowBitmap(Context c, boolean showNumber, boolean hot) {
        if (SERIF == null) SERIF = Typeface.create("serif", Typeface.NORMAL);
        float dp = c.getResources().getDisplayMetrics().density;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(SERIF);

        String numStr = MuyuState.fmt(MuyuState.today(c));
        boolean sutraOn = MuyuState.sutraOn(c);

        // 无经文：只画数字（居中）
        if (!sutraOn) {
            Paint big = new Paint(p);
            big.setTextSize(22f * dp);
            big.setColor(0xFFF2ECE0);
            float w = big.measureText(numStr);
            int pad = (int) (dp * 6);
            int bw = (int) Math.ceil(w) + pad * 2;
            int bh = (int) (big.getTextSize() * 1.35f);
            Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            Paint halo = new Paint(big);
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeWidth(dp * 2.4f);
            halo.setColor(0x55000000);
            cv.drawText(numStr, pad, bh * 0.76f, halo);
            cv.drawText(numStr, pad, bh * 0.76f, big);
            return bmp;
        }

        // 两行 × 9 字
        // 纯色文字 + 可选底衬（不用灰，避免发脏）
        boolean ink = MuyuState.textInk(c) == 1;
        int colRead  = ink ? 0xFF1A1611 : 0xFFF2ECE0;
        int colUnread= colRead;
        int colCur   = ink ? 0xFF000000 : 0xFFFFFFFF;
        int colBox   = colRead;
        int haloCol  = 0x00000000;                      // 光晕由底衬取代
        int fadedCol = ink ? 0x991A1611 : 0xB3F2ECE0;

        String sutra = SutraText.get(c);
        int len = sutra.length();
        int idx = MuyuState.sutraIndex(c, len);
        int pos = idx % 18;                       // 本批内的位置
        int batchStart = idx - pos;

        float fs = 52f;
        Paint cp = new Paint(p);
        cp.setTextSize(fs);
        float charW = cp.measureText("國");
        float gap = fs * 0.34f;
        float lineW = 9 * charW + 8 * gap;
        float lineH = fs * 1.62f;

        int pad = 6;
        float numFs = fs * 0.52f;                 // 数字约为字符一半高
        int bw = (int) Math.ceil(lineW) + pad * 2;
        int bh = (int) (lineH * 2 + pad * 2 + numFs * 1.15f);

        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        float x0 = (bw - lineW) / 2f;
        float y0 = pad + numFs * 1.15f + fs;
        // 紙質底紋：深色宣紙小條（僅深壁紙/紙白字模式；淺壁紙墨字模式不畫）
        if (!ink && MuyuState.paperOn(c)) {
            android.graphics.Bitmap paper = paperTexture(c);
            if (paper != null) {
                float padX = fs * 0.55f, padY = fs * 0.45f;
                float l = x0 - padX, t = y0 - fs - padY + 10;
                float r = x0 + lineW + padX, b = y0 + lineH + fs * 0.3f + padY;
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(new android.graphics.RectF(l, t, r, b), fs * 0.45f, fs * 0.45f,
                        android.graphics.Path.Direction.CW);
                cv.save();
                cv.clipPath(clip);
                Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                cv.drawBitmap(paper, null, new android.graphics.RectF(l, t, r, b), pp);
                cv.restore();
                Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(2f);
                edge.setColor(0x14F2ECE0);
                cv.drawRoundRect(new android.graphics.RectF(l, t, r, b), fs * 0.45f, fs * 0.45f, edge);
            }
        }

        // 底衬：两行字背后的圆角纸底
        int scrim = MuyuState.textScrim(c);
        if (scrim > 0) {
            int a = (int) (scrim / 100f * 255);
            Paint sc = new Paint(Paint.ANTI_ALIAS_FLAG);
            sc.setColor(ink ? (a << 24 | 0x00FFFFFF) : (a << 24));
            float padX = fs * 0.55f, padY = fs * 0.45f;
            cv.drawRoundRect(x0 - padX, y0 - fs - padY + 10,
                    x0 + lineW + padX, y0 + lineH + fs * 0.3f + padY,
                    fs * 0.45f, fs * 0.45f, sc);
        }


        for (int i = 0; i < 18; i++) {
            int ci = batchStart + i;
            String ch = (ci >= 0 && ci < len) ? String.valueOf(sutra.charAt(ci)) : " ";
            int row = i / 9, col = i % 9;
            float x = x0 + col * (charW + gap);
            float y = y0 + row * lineH;
            boolean cur = (i == pos);

            Paint t = new Paint(cp);
            int mode = MuyuState.sutraContrast(c);
            int readCol, unreadCol;
            int base = colRead;
            int faded = fadedCol;
            if (mode == 0) { readCol = base; unreadCol = base; }
            else if (mode == 1) { readCol = base; unreadCol = faded; }
            else { readCol = faded; unreadCol = base; }
            t.setColor(cur ? colCur : (i < pos ? readCol : unreadCol));
            cv.drawText(ch, x, y, t);

            if (cur) {
                Paint box = new Paint();
                box.setStyle(Paint.Style.STROKE);
                box.setStrokeWidth(3f);
                box.setColor(colBox);
                cv.drawRect(x - fs * 0.12f, y - fs * 0.96f,
                        x + charW + fs * 0.12f, y + fs * 0.22f, box);
            }
        }

        int npPos = MuyuState.numPos(c);
        if (npPos == 0) return bmp;
        Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
        np.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        np.setTextSize(numFs);
        np.setColor(colRead);
        np.setAlpha(hot && MuyuState.numHotOn(c) ? 0xA6 : 0x26);   // 敲击 60% / 闲 15%
        float nw = np.measureText(numStr);
        if (npPos == 1) {
            cv.drawText(numStr, (bw - nw) / 2f, pad + numFs * 0.85f, np);
        } else {
            cv.drawText(numStr, bw - pad - nw, bh - pad * 0.4f, np);
        }

        return bmp;
    }

    /** 今日功德：细黑体（sans-serif-light），宽字距 */
    static Bitmap numberBitmap(Context c) {
        String s = MuyuState.fmt(MuyuState.today(c));
        float dp = c.getResources().getDisplayMetrics().density;
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(light);
        p.setTextSize(17f * dp);
        p.setColor(0xFFF2ECE0);
        float tracking = 2.2f * dp;
        float w = 0;
        for (char ch : s.toCharArray()) w += p.measureText(String.valueOf(ch)) + tracking;
        w -= tracking;
        int pad = (int) (dp * 4);
        int bw = (int) Math.ceil(w) + pad * 2;
        int bh = (int) (p.getTextSize() * 1.5f);
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint halo = new Paint(p);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(2.6f * dp);
        halo.setColor(0x4D000000);
        float x = pad;
        float y = bh * 0.72f;
        for (char ch : s.toCharArray()) {
            String t = String.valueOf(ch);
            cv.drawText(t, x, y, halo);
            x += p.measureText(t) + tracking;
        }
        x = pad;
        for (char ch : s.toCharArray()) {
            String t = String.valueOf(ch);
            cv.drawText(t, x, y, p);
            x += p.measureText(t) + tracking;
        }
        return bmp;
    }

    /* ---------------- 声音 / 振动 ---------------- */

    static void ensureSounds(Context c) {
        float hz = MuyuState.toneHz(c), br = MuyuState.toneBright(c), dc = MuyuState.toneDecay(c);
        String sig = hz + "/" + br + "/" + dc;
        if (SP != null && sig.equals(TONE_SIG)) return;
        if (SP != null) { SP.release(); SP = null; }
        TONE_SIG = sig;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        SP = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        // 三种微差（基频 ±4%）避免连击听起来像同一个采样
        for (int i = 0; i < 3; i++) {
            float jitter = 1f + (i - 1) * 0.04f;
            String path = ToneSynth.writeWav(c, "k" + i + ".wav", hz * jitter, br, dc, 11 + i * 7L);
            KNOCK_IDS[i] = SP.load(path, 1);
        }
        BOWL_ID = SP.load(c, R.raw.bowl, 1);
    }

    static void playKnock(Context c) {
        try {
            ensureSounds(c);
            SP.play(KNOCK_IDS[RND.nextInt(3)], 1f, 1f, 1, 0, 1f);
        } catch (Exception ignored) {}
    }

    static void playBowl(Context c) {
        try {
            ensureSounds(c);
            SP.play(BOWL_ID, 0.9f, 0.9f, 1, 0, 1f);
        } catch (Exception ignored) {}
    }

    static void vibrate(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 33) {
                VibrationEffect fx = VibrationEffect.createOneShot(24, 255);
                VibrationAttributes attrs = new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build();
                v.vibrate(fx, attrs);
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(24, 255));
            } else {
                v.vibrate(24);
            }
        } catch (Exception ignored) {}
    }
}
