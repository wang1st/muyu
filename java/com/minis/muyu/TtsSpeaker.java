package com.minis.muyu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/** 誦讀發音：單例 TTS，敲一下讀一字 */
public final class TtsSpeaker {
    private static final String TAG = "MuyuTTS";
    private static TextToSpeech tts;
    private static AudioManager am;
    private static AudioFocusRequest focusReq;
    private static boolean ready;
    private static boolean failed;
    private static String pending;

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
                            tts.setSpeechRate(0.95f);
                            ready = true;
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
