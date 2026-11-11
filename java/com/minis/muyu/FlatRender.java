package com.minis.muyu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/**
 * 平面剪影 / 微立体 2.5D：一张灰度形体图 + 运行时染色。
 * 剪影用 constant 矩阵把 RGB 直接刷成白色调；2.5D 用乘法矩阵，让明暗梯度带出体积。
 */
public final class FlatRender {
    /** 剪影三色：紙白 / 象牙白 / 月白 */
    private static final int[][] WHITE = {
            {0xF3, 0xEE, 0xE2},
            {0xF8, 0xE7, 0xC9},
            {0xE6, 0xEC, 0xF0},
    };

    /** 微立体三色：和田玉料 —— 羊脂白 / 青白玉 / 糖紅（糖玉偏红一档） */
    private static final int[][] JADE = {
            {0xF7, 0xF2, 0xE4},
            {0xC6, 0xDC, 0xC9},
            {0xB3, 0x5E, 0x45},
    };

    private static final String[] WHITE_LABELS = {"紙白", "象牙白", "月白"};
    private static final String[] JADE_LABELS = {"羊脂白", "青白玉", "糖紅"};

    /** 该形态下的三种配色名 */
    public static String[] toneLabels(int mode) {
        return mode == MuyuState.PREVIEW_RELIEF ? JADE_LABELS : WHITE_LABELS;
    }

    private static int[][] tones(int mode) {
        return mode == MuyuState.PREVIEW_RELIEF ? JADE : WHITE;
    }

    private static Bitmap FLAT, RELIEF;
    private static String SIG = "";
    private static Bitmap CACHE;

    private FlatRender() {}

    public static void invalidate() { SIG = ""; CACHE = null; }

    private static Bitmap flat(Context c) {
        if (FLAT == null) FLAT = BitmapFactory.decodeResource(c.getResources(), R.drawable.fish_flat);
        return FLAT;
    }

    private static Bitmap relief(Context c) {
        if (RELIEF == null) RELIEF = BitmapFactory.decodeResource(c.getResources(), R.drawable.fish_relief);
        return RELIEF;
    }

    /** 按形态 + 白调 + 按压态出图（512×512，与旧剪影素材同几何） */
    public static synchronized Bitmap render(Context c, int mode, int tone, int press) {
        String sig = mode + "/" + tone + "/" + press;
        if (sig.equals(SIG) && CACHE != null) return CACHE;

        int[][] pal = tones(mode);
        int[] rgb = pal[Math.max(0, Math.min(pal.length - 1, tone))];
        float r = rgb[0] / 255f, g = rgb[1] / 255f, b = rgb[2] / 255f;

        ColorMatrix cm = new ColorMatrix();
        if (mode == MuyuState.PREVIEW_RELIEF) {
            // 乘法：输出 = 白调 × 灰度明暗
            cm.set(new float[]{
                    r / 3f, r / 3f, r / 3f, 0, 0,
                    g / 3f, g / 3f, g / 3f, 0, 0,
                    b / 3f, b / 3f, b / 3f, 0, 0,
                    0, 0, 0, 1, 0});
        } else {
            // 常量：输出 RGB = 白调，alpha 保留
            cm.set(new float[]{
                    0, 0, 0, 0, r * 255f,
                    0, 0, 0, 0, g * 255f,
                    0, 0, 0, 0, b * 255f,
                    0, 0, 0, 1, 0});
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));

        float s = press == 1 ? 0.978f : (press == 2 ? 0.992f : 1f);
        float dy = press == 1 ? 4f : (press == 2 ? 1.5f : 0f);
        Bitmap src = mode == MuyuState.PREVIEW_RELIEF ? relief(c) : flat(c);
        Bitmap out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        float w = 512f * s, h = 512f * s;
        float l = (512f - w) / 2f, t = 512f - h + dy;
        cv.drawBitmap(src, null, new android.graphics.RectF(l, t, l + w, t + h), p);

        SIG = sig;
        CACHE = out;
        return out;
    }
}
