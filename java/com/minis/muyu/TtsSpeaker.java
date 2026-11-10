package com.minis.muyu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.Locale;
import java.util.Set;

/** 誦讀發音：單例 TTS，敲一下讀一字 */
public final class TtsSpeaker {
    private static final String TAG = "MuyuTTS";
    private static TextToSpeech tts;
    private static AudioManager am;
    private static AudioFocusRequest focusReq;
    private static boolean ready;
    private static boolean failed;
    private static String pending;
    private static boolean male;

    private TtsSpeaker() {}

    /** 拿一次短暂音频焦点，避免 MIUI 把后台播放静音 */
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

    public static void speak(final Context c, final String text) {
        if (text == null || text.length() == 0) return;
        if (failed) return;
        grabFocus(c);
        if (tts == null) {
            tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            ready = false;
                        } else {
                            ready = true;
                            applyVoice(c);
                        }
                        if (ready && pending != null) {
                            tts.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "recite");
                            pending = null;
                        }
                    } else {
                        failed = true;
                        Log.w(TAG, "TTS init failed: " + status);
                    }
                }
            });
        }
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "recite");
        } else if (!failed) {
            pending = text;
        }
    }

    /** 音色：女聲清亮 / 男聲低沉蒼老（系統 TTS 無性別參數，用音高＋語速＋可用音色近似） */
    public static void applyVoice(Context c) {
        male = MuyuState.voiceMale(c);
        if (tts == null || !ready) return;
        tts.setPitch(male ? 0.72f : 1.05f);
        tts.setSpeechRate(male ? 0.86f : 0.98f);
        Voice v = pickVoice(male);
        if (v != null) tts.setVoice(v);
        if (male) Log.d(TAG, "voice=male pitch=0.72 rate=0.86 pick=" + (v == null ? "default" : v.getName()));
    }

    /** 在系統音色裡挑最接近目標性別的（名字帶 male/female 時才生效，否則返回 null 用默認） */
    private static Voice pickVoice(boolean wantMale) {
        try {
            Set<Voice> vs = tts.getVoices();
            if (vs == null) return null;
            Voice best = null;
            for (Voice v : vs) {
                if (v.getLocale() == null) continue;
                if (!v.getLocale().getLanguage().equals("zh")) continue;
                if (v.isNetworkConnectionRequired()) continue;
                String n = v.getName().toLowerCase(Locale.ROOT);
                boolean nM = n.contains("male") && !n.contains("female");
                boolean nF = n.contains("female");
                if (wantMale && nM) { best = v; break; }
                if (!wantMale && nF) { best = v; break; }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 關閉誦讀時打斷當前發音 */
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
