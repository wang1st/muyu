package com.minis.muyu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 誦讀發音：單例 TTS；音色由用戶在下拉菜單裡選 */
public final class TtsSpeaker {
    private static final String TAG = "MuyuTTS";
    private static TextToSpeech tts;
    private static boolean ready;
    private static boolean failed;
    private static String pending;
    private static Context app;
    private static Runnable onReady;
    private static AudioManager am;
    private static AudioFocusRequest focusReq;

    private TtsSpeaker() {}

    /** 初始化（異步）；就緒後回調，用於填充音色列表 */
    public static void init(Context c, Runnable cb) {
        app = c.getApplicationContext();
        if (ready) {
            if (cb != null) cb.run();
            return;
        }
        onReady = cb;
        ensure();
    }

    private static void ensure() {
        if (tts != null || failed || app == null) return;
        tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    failed = true;
                    Log.w(TAG, "TTS init failed: " + status);
                    return;
                }
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                ready = true;
                applyVoice();
                if (pending != null) {
                    tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "recite");
                    pending = null;
                }
                Runnable cb = onReady;
                onReady = null;
                if (cb != null) cb.run();
            }
        });
    }

    /** 中文音色列表（本地優先、質量高的在前） */
    public static List<Voice> voices() {
        List<Voice> out = new ArrayList<Voice>();
        if (tts == null || !ready) return out;
        try {
            for (Voice v : tts.getVoices()) {
                Locale l = v.getLocale();
                if (l == null) continue;
                String lang = l.getLanguage().toLowerCase(Locale.ROOT);
                if (!(lang.equals("zh") || lang.equals("cmn"))) continue;
                out.add(v);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getVoices failed: " + t);
        }
        Collections.sort(out, new Comparator<Voice>() {
            @Override public int compare(Voice a, Voice b) {
                if (a.isNetworkConnectionRequired() != b.isNetworkConnectionRequired())
                    return a.isNetworkConnectionRequired() ? 1 : -1;
                if (a.getQuality() != b.getQuality()) return b.getQuality() - a.getQuality();
                int lc = a.getLocale().toString().compareTo(b.getLocale().toString());
                if (lc != 0) return lc;
                return a.getName().compareTo(b.getName());   // 名稱排序，保證 A/B/C 每次一致
            }
        });
        return out;
    }

    public static void dumpVoices() {
        if (tts == null || !ready) return;
        try {
            for (Voice v : tts.getVoices()) {
                Log.d(TAG, "voice " + v.getName() + " loc=" + v.getLocale()
                        + " q=" + v.getQuality() + " net=" + v.isNetworkConnectionRequired());
            }
        } catch (Throwable ignored) {}
    }

    /** 音色顯示名：按語言分組編號（A/B/C…），標明本地/在線 */
    public static String label(Voice v, int idxInLang) {
        String loc = v.getLocale().getCountry();
        String cn;
        if ("CN".equals(loc)) cn = "中文（中國）";
        else if ("TW".equals(loc)) cn = "中文（台灣）";
        else if ("HK".equals(loc)) cn = "中文（香港）";
        else cn = "中文（" + loc + "）";
        char letter = (char) ('A' + Math.min(25, idxInLang));
        return cn + " · " + letter + (v.isNetworkConnectionRequired() ? " · 在線" : " · 本地");
    }

    /** 給一組音色生成顯示名，同語言內按順序編號 */
    public static List<String> labels(List<Voice> vs) {
        List<String> out = new ArrayList<String>();
        java.util.Map<String, Integer> count = new java.util.HashMap<String, Integer>();
        for (Voice v : vs) {
            String key = v.getLocale().getCountry();
            int n = count.containsKey(key) ? count.get(key) : 0;
            count.put(key, n + 1);
            out.add(label(v, n));
        }
        return out;
    }

    /** 應用用戶選中的音色（空 = 系統默認） */
    public static void applyVoice() {
        if (tts == null || !ready || app == null) return;
        String name = MuyuState.voiceName(app);
        if (name != null && name.length() > 0) {
            try {
                for (Voice v : tts.getVoices()) {
                    if (name.equals(v.getName())) { tts.setVoice(v); break; }
                }
            } catch (Throwable ignored) {}
        }
        tts.setPitch(1.0f);
        tts.setSpeechRate(0.98f);
    }

    /** 拿一次短暫音頻焦點，避免 MIUI 把後台播放靜音 */
    private static void grabFocus(Context c) {
        if (am == null) am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (focusReq == null) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .build();
        }
        am.requestAudioFocus(focusReq);
    }

    public static void speak(Context c, String text) {
        if (text == null || text.length() == 0) return;
        if (failed) return;
        if (app == null) app = c.getApplicationContext();
        grabFocus(c);
        ensure();
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "recite");
        } else {
            pending = text;
        }
    }

    public static void stop() {
        pending = null;
        if (tts != null && ready) tts.stop();
        if (am != null && focusReq != null) am.abandonAudioFocusRequest(focusReq);
    }

    public static void release() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        ready = false;
    }
}
