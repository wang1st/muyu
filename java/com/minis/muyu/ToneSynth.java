package com.minis.muyu;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 运行时合成木鱼音色：基频 / 脆度 / 余韵 三个参数 */
public final class ToneSynth {
    private static final int SR = 44100;

    private ToneSynth() {}

    /**
     * @param f0     基频（Hz），小木鱼 400~500，大木鱼 300~380
     * @param bright 脆度 0..1，越大中高分音越强
     * @param decay  余韵 0.5~1.6，越大尾巴越长
     * @param seed   随机种子（同一参数下生成不同微差的三份）
     */
    public static short[] render(float f0, float bright, float decay, long seed) {
        int n = (int) (SR * 0.26f);
        short[] pcm = new short[n];
        java.util.Random rnd = new java.util.Random(seed);

        double[] f = {f0, f0 * 2.55, f0 * 4.10};
        double[] amp = {1.0, 0.30 + 0.45 * bright, 0.06 + 0.34 * bright};
        double[] dec = {30.0 / decay, 46.0 / decay, 62.0 / decay};
        double[] phase = {rnd.nextDouble() * 6.28, rnd.nextDouble() * 6.28, rnd.nextDouble() * 6.28};

        // 敲击瞬态：差分白噪 + 低通 + 快速衰减
        int nc = (int) (SR * 0.006);
        double[] click = new double[nc];
        double prev = 0;
        for (int i = 0; i < nc; i++) {
            double w = rnd.nextGaussian();
            click[i] = (w - prev) * Math.exp(-i / (SR * 0.0011)) * 0.38;
            prev = w;
        }
        // 简易 3 点低通
        for (int i = 1; i < nc - 1; i++) click[i] = (click[i - 1] + click[i] + click[i + 1]) / 3.0;

        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double s = 0;
            for (int k = 0; k < 3; k++) {
                s += amp[k] * Math.sin(2 * Math.PI * f[k] * t + phase[k]) * Math.exp(-t * dec[k]);
            }
            s += 0.28 * Math.sin(2 * Math.PI * f0 * 0.5 * t) * Math.exp(-t * 22.0 / decay);
            if (i < nc) s += click[i];
            s = Math.tanh(s * 0.75);
            s *= Math.min(1.0, t / 0.001);            // 起音 1ms
            s *= Math.exp(-t * 9);
            pcm[i] = (short) (Math.max(-1, Math.min(1, s)) * 0.92 * 32767);
        }
        return pcm;
    }

    /** 把当前音色写成 WAV 到缓存目录，返回路径（variant 用于三份微差） */
    public static String writeWav(Context c, String name, float f0, float bright, float decay, long seed) {
        File dir = new File(c.getCacheDir(), "tone");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, name);
        short[] pcm = render(f0, bright, decay, seed);
        try {
            FileOutputStream fos = new FileOutputStream(out);
            byte[] header = wavHeader(pcm.length * 2);
            fos.write(header);
            byte[] body = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                body[i * 2] = (byte) (pcm[i] & 0xFF);
                body[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
            }
            fos.write(body);
            fos.close();
        } catch (IOException ignored) {}
        return out.getAbsolutePath();
    }

    private static byte[] wavHeader(int dataLen) {
        int fileLen = dataLen + 36;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, fileLen);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        le16(h, 20, 1);          // PCM
        le16(h, 22, 1);          // mono
        le32(h, 24, SR);
        le32(h, 28, SR * 2);
        le16(h, 32, 2);
        le16(h, 34, 16);
        put(h, 36, "data");
        le32(h, 40, dataLen);
        return h;
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void le32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }
}
