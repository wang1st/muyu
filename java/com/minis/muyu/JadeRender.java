package com.minis.muyu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

/** 和田玉木鱼：运行时按参数合成（颜色矩阵 + 接触阴影） */
public final class JadeRender {
    private static Bitmap BASE;
    private static String CACHE_SIG = "";
    private static Bitmap CACHE;

    private JadeRender() {}

    public static synchronized void invalidate() {
        CACHE_SIG = "";
        CACHE = null;
    }

    /** 按当前参数渲染；press: 0 静息 / 1 压形 / 2 回弹 */
    public static synchronized Bitmap render(Context c, int press) {
        int br = MuyuState.jadeBright(c), wa = MuyuState.jadeWarm(c), ma = MuyuState.jadeMatte(c);
        int tr = MuyuState.jadeTranslucent(c), vo = MuyuState.jadeVolume(c);
        int sh = MuyuState.jadeShadow(c), sp = MuyuState.jadeShadowSpread(c), so = MuyuState.jadeShadowOffset(c);
        String sig = br + "/" + wa + "/" + ma + "/" + tr + "/" + vo + "/" + sh + "/" + sp + "/" + so + "/" + press;
        if (sig.equals(CACHE_SIG) && CACHE != null) return CACHE;

        if (BASE == null) {
            BASE = BitmapFactory.decodeResource(c.getResources(), R.drawable.fish_jade);
        }
        int bw = BASE.getWidth(), bh = BASE.getHeight();
        float volume = 1f + (vo - 50) / 100f * 0.16f;          // 体量 0.92~1.08
        float squash = press == 1 ? 0.984f : (press == 2 ? 0.994f : 1f);
        int outW = (int) (bw * volume), outH = (int) (bh * volume);

        // 画布预留阴影空间
        int padX = outW / 6, padY = outH / 6;
        Bitmap out = Bitmap.createBitmap(outW + padX * 2, outH + padY * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // —— 颜色矩阵：亮度 / 暖度 / 哑光 / 通透 ——
        float bright = 0.7f + br / 100f * 0.6f;               // 0.7~1.3
        float warmR = 1f + (wa - 50) / 100f * 0.14f;
        float warmB = 1f - (wa - 50) / 100f * 0.18f;
        float sat = 1f - ma / 100f * 0.55f;                    // 哑光=降饱和
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(sat);
        ColorMatrix scale = new ColorMatrix(new float[]{
                bright * warmR, 0, 0, 0, 0,
                0, bright, 0, 0, 0,
                0, 0, bright * warmB, 0, 0,
                0, 0, 0, 1f, 0
        });
        cm.postConcat(scale);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        p.setAlpha((int) (255 * (tr / 100f * 0.55f + 0.45f)));  // 通透度 → 透明度 45%~100%

        // —— 接触阴影 ——
        if (sh > 0) {
            float spread = 0.45f + sp / 100f * 0.55f;          // 收窄：0.45~1.0
            float sw = Math.min(outW * spread, out.getWidth() - 8);
            float shh = Math.min(outH * 0.16f * spread, outH * 0.28f);
            float cx = out.getWidth() / 2f + (so - 50) / 100f * outW * 0.08f;
            float cy = padY + outH * 0.985f;
            Paint sp2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp2.setColor(0xFF000000);
            sp2.setAlpha((int) (sh / 100f * 150));
            sp2.setMaskFilter(new BlurMaskFilter(Math.max(4, outH * 0.045f), BlurMaskFilter.Blur.NORMAL));
            float top = Math.max(2, cy - shh / 2);
            float bot = Math.min(out.getHeight() - 2, cy + shh / 2);
            cv.drawOval(new RectF(Math.max(2, cx - sw / 2), top,
                    Math.min(out.getWidth() - 2, cx + sw / 2), bot), sp2);
        }

        // —— 玉体（中心锚点压缩）——
        int dw = outW, dh = (int) (outH * squash);
        RectF dst = new RectF(padX, padY + (outH - dh), padX + dw, padY + outH);
        cv.drawBitmap(BASE, null, dst, p);

        CACHE_SIG = sig;
        CACHE = out;
        return out;
    }
}
