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

    /** 基座图换了（离屏 3D 重渲）后调用：丢弃旧基座与缓存 */
    public static synchronized void reloadBase() {
        BASE_MTIME = -1L;
        BASE = null;
        CACHE_SIG = "";
        CACHE = null;
    }

    /** 按当前参数渲染；press: 0 静息 / 1 压形 / 2 回弹 */
    private static long BASE_MTIME = -1L;

    public static synchronized Bitmap render(Context c, int press) {
        java.io.File bf = new java.io.File(c.getFilesDir(), "fish3d.png");
        long mt = bf.exists() ? bf.lastModified() : 0L;
        if (mt != BASE_MTIME) {
            BASE_MTIME = mt;
            BASE = null;
            CACHE_SIG = "";
            CACHE = null;
        }
        int br = MuyuState.jadeBright(c), wa = MuyuState.jadeWarm(c), ma = MuyuState.jadeMatte(c);
        int tr = MuyuState.jadeTranslucent(c), vo = MuyuState.jadeVolume(c);
        int sh = MuyuState.jadeShadow(c), sp = MuyuState.jadeShadowSpread(c), so = MuyuState.jadeShadowOffset(c);
        String sig = br + "/" + wa + "/" + ma + "/" + tr + "/" + vo + "/" + sh + "/" + sp + "/" + so + "/" + press;
        if (sig.equals(CACHE_SIG) && CACHE != null) return CACHE;

        if (BASE == null) {
            java.io.File f = new java.io.File(c.getFilesDir(), "fish3d.png");
            if (f.exists()) BASE = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (BASE == null) BASE = BitmapFactory.decodeResource(c.getResources(), R.drawable.fish_jade);
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
        // 先乘玉基色（与 3D 实时渲染同一套色），再做亮度/暖度/哑光
        boolean jadeMat = false;   // 颜色已在离屏烘焙时确定（与全屏实时同源），静态侧不再二次调色
        cm.set(new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0});
        if (jadeMat) {
            ColorMatrix satM = new ColorMatrix();
            satM.setSaturation(sat);
            cm.postConcat(satM);
            ColorMatrix scale = new ColorMatrix(new float[]{
                    bright * warmR, 0, 0, 0, 0,
                    0, bright, 0, 0, 0,
                    0, 0, bright * warmB, 0, 0,
                    0, 0, 0, 1f, 0
            });
            cm.postConcat(scale);
        }
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        p.setAlpha(255);   // 通透由烘焙图的 alpha 承担

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
