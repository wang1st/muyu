package com.minis.muyu;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 经文（繁体、无标点）加载 */
public final class SutraText {
    private static String text;

    private SutraText() {}

    public static synchronized String get(Context c) {
        int sel = MuyuState.sutraSel(c);
        if (sel == 1) {   // 金剛經 · 玄奘譯
            if (xuan == null) xuan = load(c, "jingang_xuanzang.txt", "如是我聞一時薄伽梵");
            return xuan;
        }
        if (sel == 2) {   // 心經
            if (xin == null) xin = load(c, "xinjing.txt", "觀自在菩薩行深般若波羅蜜多時");
            return xin;
        }
        if (text == null) {
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        c.getAssets().open("sutra.txt"), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
            } catch (Exception e) {
                sb.append("如是我聞");
            }
            text = sb.toString();
        }
        return text;
    }

    private static String xin;
    private static String xuan;

    private static String load(Context c, String name, String fallback) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getAssets().open(name), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
        } catch (Exception e) {
            sb.append(fallback);
        }
        return sb.toString();
    }

    public static int length(Context c) {
        return get(c).length();
    }
}
