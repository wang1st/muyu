package com.minis.muyu;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 功德状态：今日（每日重置）、累计、108 轮、回向记录、设置 */
public final class MuyuState {
    public static final int RING_HITS = 9;
    public static final int RING_TICKS = 12;
    public static final int ROUND_TOTAL = RING_HITS * RING_TICKS;

    private static final String P = "muyu";
    private static final String K_DAY = "day";
    private static final String K_TODAY = "today";
    private static final String K_TOTAL = "total";
    private static final String K_CYCLE = "cycle";
    private static final String K_ROUNDS = "rounds";
    private static final String K_DONUM = "donum";
    private static final String K_HIST = "hist";
    private static final String K_SEAL_ARM = "sealArm";
    private static final String K_SOUND = "soundOn";
    private static final String K_VIB = "vibOn";
    private static final String K_SUTRA_IDX = "sutraIdx";
    private static final String K_SUTRA_ON = "sutraOn";
    private static final String K_SUTRA_SEL = "sutraSel";
    private static final String K_SUTRA_SEL_V2 = "sutraSelV2";
    private static final String K_NUM_POS = "numPos";
    private static final String K_NUM_HOT = "numHot";
    private static final String K_TONE_HZ = "toneHz";
    private static final String K_TONE_BRIGHT = "toneBright";
    private static final String K_TONE_DECAY = "toneDecay";
    private static final String K_SUTRA_CONTRAST = "sutraContrast";

    private MuyuState() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    /** 跨日则清零当日数据 */
    private static void rollDay(Context c) {
        String key = todayKey();
        if (!key.equals(sp(c).getString(K_DAY, ""))) {
            sp(c).edit()
                    .putString(K_DAY, key)
                    .putInt(K_TODAY, 0)
                    .apply();
        }
    }

    public static int today(Context c) { rollDay(c); return sp(c).getInt(K_TODAY, 0); }
    public static int cycle(Context c) { rollDay(c); return sp(c).getInt(K_CYCLE, 0); }
    public static int total(Context c) { return sp(c).getInt(K_TOTAL, 0); }
    public static int rounds(Context c) { return sp(c).getInt(K_ROUNDS, 0); }
    public static int donum(Context c) { return sp(c).getInt(K_DONUM, 0); }

    /** 经文显示开关（默认开） */
    public static boolean sutraOn(Context c) { return sp(c).getBoolean(K_SUTRA_ON, true); }
    public static void setSutraOn(Context c, boolean v) { sp(c).edit().putBoolean(K_SUTRA_ON, v).apply(); }

    /** 音色参数：基频 / 脆度 / 余韵 */
    public static float toneHz(Context c) { return sp(c).getFloat(K_TONE_HZ, 400f); }
    public static float toneBright(Context c) { return sp(c).getFloat(K_TONE_BRIGHT, 0.60f); }
    public static float toneDecay(Context c) { return sp(c).getFloat(K_TONE_DECAY, 1.2f); }
    public static void setTone(Context c, float hz, float bright, float decay) {
        sp(c).edit().putFloat(K_TONE_HZ, hz).putFloat(K_TONE_BRIGHT, bright)
                .putFloat(K_TONE_DECAY, decay).apply();
    }

    /** 敲击时数字是否加亮 */
    public static boolean numHotOn(Context c) { return sp(c).getBoolean(K_NUM_HOT, true); }
    public static void setNumHotOn(Context c, boolean v) { sp(c).edit().putBoolean(K_NUM_HOT, v).apply(); }

    /** 经文对比：0 同色（只有方框）/ 1 已讀亮 / 2 未讀亮 */
    public static int sutraContrast(Context c) { return sp(c).getInt(K_SUTRA_CONTRAST, 0); }
    public static void setSutraContrast(Context c, int v) { sp(c).edit().putInt(K_SUTRA_CONTRAST, v).apply(); }

    /** 数字位置：0 不顯示 / 1 木魚下方 / 2 經文右下 */
    public static int numPos(Context c) { return sp(c).getInt(K_NUM_POS, 1); }
    public static void setNumPos(Context c, int v) { sp(c).edit().putInt(K_NUM_POS, v).apply(); }

    /** 0=金剛經(鳩摩羅什) 1=金剛經(玄奘) 2=心經 */
    public static int sutraSel(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean(K_SUTRA_SEL_V2, false)) {
            int old = p.getInt(K_SUTRA_SEL, 0);
            int v = (old == 1) ? 2 : 0;   // 旧版 1 表示心經
            p.edit().putInt(K_SUTRA_SEL, v).putBoolean(K_SUTRA_SEL_V2, true).apply();
            return v;
        }
        return p.getInt(K_SUTRA_SEL, 0);
    }
    public static void setSutraSel(Context c, int v) {
        sp(c).edit().putInt(K_SUTRA_SEL, v).putBoolean(K_SUTRA_SEL_V2, true).apply();
    }

    /** 经文归零（App 内手动） */
    public static void resetSutra(Context c) { sp(c).edit().putInt(K_SUTRA_IDX, 0).apply(); }

    /** 经文进度：独立于每日功德，读完循环 */
    public static int sutraIndex(Context c, int len) {
        if (len <= 0) return 0;
        int v = sp(c).getInt(K_SUTRA_IDX, 0);
        return ((v % len) + len) % len;
    }

    public static int advanceSutra(Context c, int len) {
        if (len <= 0) return 0;
        int v = (sutraIndex(c, len) + 1) % len;
        sp(c).edit().putInt(K_SUTRA_IDX, v).apply();
        return v;
    }

    public static boolean soundOn(Context c) { return sp(c).getBoolean(K_SOUND, true); }
    public static boolean vibOn(Context c) { return sp(c).getBoolean(K_VIB, true); }
    public static void setSound(Context c, boolean v) { sp(c).edit().putBoolean(K_SOUND, v).apply(); }
    public static void setVib(Context c, boolean v) { sp(c).edit().putBoolean(K_VIB, v).apply(); }

    /** 敲一下；返回 true 表示这一击刚好满 108 */
    public static boolean knock(Context c) {
        rollDay(c);
        int today = sp(c).getInt(K_TODAY, 0) + 1;
        int cycle = sp(c).getInt(K_CYCLE, 0) + 1;
        boolean completed = cycle >= ROUND_TOTAL;
        SharedPreferences.Editor e = sp(c).edit();
        e.putInt(K_TODAY, today).putInt(K_TOTAL, total(c) + 1);
        if (completed) {
            e.putInt(K_CYCLE, cycle - ROUND_TOTAL).putInt(K_ROUNDS, rounds(c) + 1);
        } else {
            e.putInt(K_CYCLE, cycle);
        }
        e.apply();
        return completed;
    }

    public static long sealArmedUntil(Context c) { return sp(c).getLong(K_SEAL_ARM, 0L); }
    public static void armSeal(Context c, long until) { sp(c).edit().putLong(K_SEAL_ARM, until).apply(); }
    public static boolean sealArmed(Context c) { return System.currentTimeMillis() < sealArmedUntil(c); }
    public static void disarmSeal(Context c) { sp(c).edit().putLong(K_SEAL_ARM, 0L).apply(); }

    /** 回向：把今日功德清零并记一笔 */
    public static void dedicate(Context c) {
        rollDay(c);
        int amount = sp(c).getInt(K_TODAY, 0);
        if (amount <= 0) return;
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
        String line = ts + "|" + amount;
        String old = sp(c).getString(K_HIST, "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        int keep = 0;
        for (int i = lines.length - 1; i >= 0 && keep < 29; i--) {
            sb.append('\n').append(lines[i]);
            keep++;
        }
        sp(c).edit()
                .putInt(K_TODAY, 0)
                .putInt(K_CYCLE, 0)
                .putInt(K_DONUM, donum(c) + 1)
                .putString(K_HIST, sb.toString())
                .putLong(K_SEAL_ARM, 0L)
                .apply();
    }

    public static String[] history(Context c) {
        String raw = sp(c).getString(K_HIST, "");
        if (raw.isEmpty()) return new String[0];
        String[] lines = raw.split("\n");
        String[] out = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String[] p = lines[i].split("\\|");
            out[i] = p.length == 2 ? (p[0] + "　回向 " + fmt(Integer.parseInt(p[1])) + " 次") : lines[i];
        }
        return out;
    }

    public static String fmt(int n) {
        StringBuilder sb = new StringBuilder();
        String s = String.valueOf(n);
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) sb.append(',');
        }
        return sb.reverse().toString();
    }
}
