package com.minis.muyu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.SeekBar;

/** 木魚：功德簿与设置（敲击在小部件上完成） */
public class MainActivity extends Activity {

    private TextView total, rounds;
    private JadeView mini3d;
    /** 着色器/材质贴图变更时递增，触发一次重烘焙 */
    private static final int BAKE_VER = 13;
    private volatile boolean snapBusy;
    private CheckBox swSound, swVib, swSutra;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        total = findViewById(R.id.total);
        rounds = findViewById(R.id.rounds);
        swSound = findViewById(R.id.sw_sound);
        swVib = findViewById(R.id.sw_vib);
        // 音色三滑杆
        final SeekBar sp = findViewById(R.id.seek_pitch);
        final SeekBar sb = findViewById(R.id.seek_bright);
        final SeekBar sd = findViewById(R.id.seek_decay);
        final TextView lp = findViewById(R.id.tonepitch_label);
        final TextView lb = findViewById(R.id.tonebright_label);
        final TextView ld = findViewById(R.id.tonedecay_label);
        sp.setProgress(Math.round((MuyuState.toneHz(this) - 300f) / 200f * 100f));
        sb.setProgress(Math.round(MuyuState.toneBright(this) * 100f));
        sd.setProgress(Math.round((MuyuState.toneDecay(this) - 0.5f) / 1.1f * 100f));
        SeekBar.OnSeekBarChangeListener tl = new SeekBar.OnSeekBarChangeListener() {
            void apply() {
                float hz = 300f + sp.getProgress() / 100f * 200f;
                float br = sb.getProgress() / 100f;
                float dc = 0.5f + sd.getProgress() / 100f * 1.1f;
                lp.setText("基頻  " + Math.round(hz) + " Hz");
                lb.setText("脆度  " + sb.getProgress() + "%");
                ld.setText("餘韻  " + Math.round(dc * 100) + "%");
                MuyuState.setTone(MainActivity.this, hz, br, dc);
            }
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { apply(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { apply(); }
        };
        sp.setOnSeekBarChangeListener(tl);
        sb.setOnSeekBarChangeListener(tl);
        sd.setOnSeekBarChangeListener(tl);
        tl.onProgressChanged(sp, sp.getProgress(), false);
        // 预设：一键把三个滑杆推到对应值
        View.OnClickListener preset = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int id = v.getId();
                float hz, br, dc;
                if (id == R.id.preset_small) { hz = 465f; br = 0.72f; dc = 0.85f; }
                else if (id == R.id.preset_big) { hz = 340f; br = 0.42f; dc = 1.05f; }
                else if (id == R.id.preset_temple) { hz = 325f; br = 0.32f; dc = 1.55f; }
                else { hz = 485f; br = 0.85f; dc = 0.80f; }
                sp.setProgress(Math.round((hz - 300f) / 200f * 100f));
                sb.setProgress(Math.round(br * 100f));
                sd.setProgress(Math.round((dc - 0.5f) / 1.1f * 100f));
                FishWidget.ensureSounds(MainActivity.this);
                FishWidget.playKnock(MainActivity.this);
            }
        };
        findViewById(R.id.preset_small).setOnClickListener(preset);
        findViewById(R.id.preset_big).setOnClickListener(preset);
        findViewById(R.id.preset_temple).setOnClickListener(preset);
        findViewById(R.id.preset_crisp).setOnClickListener(preset);

        findViewById(R.id.btn_tone_preview).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                FishWidget.ensureSounds(MainActivity.this);
                FishWidget.playKnock(MainActivity.this);
            }
        });

        RadioGroup numGroup = findViewById(R.id.num_group);
        int np = MuyuState.numPos(this);
        ((RadioButton) findViewById(np == 0 ? R.id.num_off : (np == 2 ? R.id.num_corner : R.id.num_under))).setChecked(true);
        numGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int v = checkedId == R.id.num_off ? 0 : (checkedId == R.id.num_corner ? 2 : 1);
                MuyuState.setNumPos(MainActivity.this, v);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });
        swSutra = findViewById(R.id.sw_sutra);
        swSutra.setChecked(MuyuState.sutraOn(this));
        swSutra.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setSutraOn(MainActivity.this, checked);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });
        Button dedicate = findViewById(R.id.btn_dedicate);

        swSound.setChecked(MuyuState.soundOn(this));
        swVib.setChecked(MuyuState.vibOn(this));
        CheckBox swRecite = findViewById(R.id.sw_recite);
        swRecite.setChecked(MuyuState.reciteOn(this));
        setupVoiceSpinner();
        View note = findViewById(R.id.note_hint);
        if (note != null) note.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NotesActivity.class));
            }
        });
        swRecite.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setRecite(MainActivity.this, checked);
                if (checked) {
                    // 立刻讀當前字，讓用戶確認已生效
                    String s = SutraText.get(MainActivity.this);
                    int i = MuyuState.sutraIndex(MainActivity.this, s.length());
                    TtsSpeaker.speak(MainActivity.this, SutraSpeech.at(s, i));
                }
            }
        });
        swSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setSound(MainActivity.this, checked);
            }
        });
        swVib.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setVib(MainActivity.this, checked);
            }
        });

        RadioGroup group = findViewById(R.id.sutra_group);
        int selNow = MuyuState.sutraSel(this);
        ((RadioButton) findViewById(selNow == 2 ? R.id.sutra_xj : (selNow == 1 ? R.id.sutra_jg_xz : R.id.sutra_jj))).setChecked(true);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int v = checkedId == R.id.sutra_xj ? 2 : (checkedId == R.id.sutra_jg_xz ? 1 : 0);
                MuyuState.setSutraSel(MainActivity.this, v);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });
        CheckBox swPaper = findViewById(R.id.sw_paper);
        swPaper.setChecked(MuyuState.paperOn(this));
        swPaper.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setPaper(MainActivity.this, checked);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });
        CheckBox swNumHot = findViewById(R.id.sw_num_hot);
        swNumHot.setChecked(MuyuState.numHotOn(this));
        swNumHot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setNumHotOn(MainActivity.this, checked);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        RadioGroup styleGroup = findViewById(R.id.style_group);
        int modeNow = MuyuState.previewMode(this);
        ((RadioButton) findViewById(modeNow == MuyuState.PREVIEW_3D ? R.id.style_jade
                : (modeNow == MuyuState.PREVIEW_RELIEF ? R.id.style_relief : R.id.style_flat))).setChecked(true);
        styleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int mode = checkedId == R.id.style_jade ? MuyuState.PREVIEW_3D
                        : (checkedId == R.id.style_relief ? MuyuState.PREVIEW_RELIEF : MuyuState.PREVIEW_FLAT);
                MuyuState.setPreviewMode(MainActivity.this, mode);
                setMaterialVisible(mode == MuyuState.PREVIEW_3D);
                setToneVisible(mode != MuyuState.PREVIEW_3D);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        RadioGroup toneGroup = findViewById(R.id.tone_group);
        ((RadioButton) findViewById(MuyuState.whiteTone(this) == 1 ? R.id.tone_ivory
                : (MuyuState.whiteTone(this) == 2 ? R.id.tone_moon : R.id.tone_paper))).setChecked(true);
        toneGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                MuyuState.setWhiteTone(MainActivity.this, checkedId == R.id.tone_ivory ? 1
                        : (checkedId == R.id.tone_moon ? 2 : 0));
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        RadioGroup matGroup = findViewById(R.id.mat_group);
        int matNow = MuyuState.material(this);
        ((RadioButton) findViewById(matNow == 1 ? R.id.mat_wood
                : (matNow == 2 ? R.id.mat_amber : R.id.mat_jade))).setChecked(true);
        matGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int mat = checkedId == R.id.mat_wood ? 1 : (checkedId == R.id.mat_amber ? 2 : 0);
                MuyuState.setMaterial(MainActivity.this, mat);
                MuyuState.setPreviewMode(MainActivity.this, MuyuState.PREVIEW_3D);
                ((RadioButton) findViewById(R.id.style_jade)).setChecked(true);
                setMaterialVisible(true);
                setToneVisible(false);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
                // 材质影响基底图（木有年轮、陶要烘焙色），重渲一张
                snapshotWidgetBase(MuyuState.viewYaw(MainActivity.this),
                        MuyuState.viewPitch(MainActivity.this), 1f);
            }
        });

        RadioGroup inkGroup = findViewById(R.id.ink_group);
        ((RadioButton) findViewById(MuyuState.textInk(this) == 1 ? R.id.ink_dark : R.id.ink_light)).setChecked(true);
        inkGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                MuyuState.setTextInk(MainActivity.this, checkedId == R.id.ink_dark ? 1 : 0);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        final SeekBar scrimBar = findViewById(R.id.seek_scrim);
        final TextView scrimLabel = findViewById(R.id.scrim_label);
        scrimBar.setProgress(MuyuState.textScrim(this));
        scrimLabel.setText("底襯　" + scrimBar.getProgress() + "%");
        scrimBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                scrimLabel.setText("底襯　" + p + "%");
                MuyuState.setTextScrim(MainActivity.this, p);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        RadioGroup cg = findViewById(R.id.contrast_group);
        int cm = MuyuState.sutraContrast(this);
        ((RadioButton) findViewById(cm == 1 ? R.id.ct_read : (cm == 2 ? R.id.ct_unread : R.id.ct_same))).setChecked(true);
        cg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int v = checkedId == R.id.ct_read ? 1 : (checkedId == R.id.ct_unread ? 2 : 0);
                MuyuState.setSutraContrast(MainActivity.this, v);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        findViewById(R.id.btn_reset_sutra).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MuyuState.resetSutra(MainActivity.this);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        dedicate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("回向")
                        .setMessage("願以此修習善根，回向一切眾生。\n\n本次敲擊 " + MuyuState.today(MainActivity.this)
                                + " 次\n累計記錄保留，另起一輪。")
                        .setPositiveButton("完成回向", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                MuyuState.dedicate(MainActivity.this);
                                refresh();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        findViewById(R.id.link_weibo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl("https://weibo.com/n/%E7%8E%8B%E8%8D%AF%E5%B8%88");
            }
        });
        findViewById(R.id.link_twitter).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl("https://twitter.com/qq831261");
            }
        });
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.version)).setText("木魚 v" + vn + "　·　Minis 出品");
        } catch (Exception ignored) {}

        setMaterialVisible(MuyuState.previewMode(this) == MuyuState.PREVIEW_3D);
        setToneVisible(MuyuState.previewMode(this) != MuyuState.PREVIEW_3D);
        // 标签页
        final int[] pages = {R.id.tap_page, R.id.sutra_page, R.id.look_page, R.id.sound_page};
        final int[] btns = {R.id.tab_tap, R.id.tab_sutra, R.id.tab_look, R.id.tab_sound};
        View.OnClickListener tabClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < btns.length; i++) if (btns[i] == v.getId()) idx = i;
                MuyuState.setLastTab(MainActivity.this, idx);
                if (idx == 2) updatePreview();
                for (int i = 0; i < pages.length; i++) {
                    View pg = findViewById(pages[i]);
                    if (pg == null) continue;
                    pg.animate().cancel();
                    if (i == idx) {
                        pg.setVisibility(View.VISIBLE);
                        pg.setAlpha(0f);
                        pg.animate().alpha(1f).setDuration(160).start();
                    } else {
                        pg.setVisibility(View.GONE);
                    }
                    Button b = findViewById(btns[i]);
                    if (b != null) b.setTextColor(i == idx ? 0xFF141210 : 0xFFC8A050);
                    if (b != null) b.setBackgroundResource(i == idx ? R.drawable.bg_seg_selected_sel : R.drawable.bg_seg_idle_sel);
                }
            }
        };
        for (int id : btns) findViewById(id).setOnClickListener(tabClick);
        int lastTab = MuyuState.lastTab(this);
        ((View) findViewById(btns[Math.max(0, Math.min(btns.length - 1, lastTab))])).performClick();

        refresh();
    }

    /** 音色下拉：列出系統所有中文音色，選中即保存並試讀 */
    private void setupVoiceSpinner() {
        final Spinner sp = findViewById(R.id.voice_spinner);
        if (sp == null) return;
        TtsSpeaker.init(this, new Runnable() {
            @Override public void run() {
                final java.util.List<android.speech.tts.Voice> vs = TtsSpeaker.voices();
                final java.util.List<String> labels = TtsSpeaker.labels(vs);
                if (labels.isEmpty()) {
                    sp.setVisibility(View.GONE);
                    return;
                }
                ArrayAdapter<String> ad = new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_spinner_item, labels) {
                    @Override public View getView(int pos, View cv, ViewGroup parent) {
                        TextView tv = (TextView) super.getView(pos, cv, parent);
                        tv.setTextColor(0xFFE8E2D6);
                        tv.setTextSize(13);
                        return tv;
                    }
                    @Override public View getDropDownView(int pos, View cv, ViewGroup parent) {
                        TextView tv = (TextView) super.getDropDownView(pos, cv, parent);
                        tv.setTextColor(0xFFE8E2D6);
                        tv.setTextSize(13);
                        tv.setBackgroundColor(0xFF201A14);
                        tv.setPadding(28, 28, 28, 28);
                        return tv;
                    }
                };
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(ad);
                String saved = MuyuState.voiceName(MainActivity.this);
                int sel = -1;
                for (int i = 0; i < vs.size(); i++) if (vs.get(i).getName().equals(saved)) sel = i;
                if (sel < 0) {                       // 默認：第一個本地中文（中國）音色
                    for (int i = 0; i < vs.size(); i++) {
                        android.speech.tts.Voice v = vs.get(i);
                        if ("CN".equals(v.getLocale().getCountry()) && !v.isNetworkConnectionRequired()) { sel = i; break; }
                    }
                }
                if (sel < 0) sel = 0;
                sp.setSelection(sel, false);
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                        MuyuState.setVoiceName(MainActivity.this, vs.get(pos).getName());
                        String s = SutraText.get(MainActivity.this);
                        int i = MuyuState.sutraIndex(MainActivity.this, s.length());
                        TtsSpeaker.speak(MainActivity.this, SutraSpeech.at(s, i));
                    }
                    @Override public void onNothingSelected(AdapterView<?> a) {}
                });
            }
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    /** 外觀頁预览：小窗为可拖动的实时 3D；经文行用与桌面相同的位图 */
    private void updatePreview() {
        int mode = MuyuState.previewMode(this);
        boolean jade = mode == MuyuState.PREVIEW_3D;
        android.widget.ImageView row = findViewById(R.id.preview_row);
        if (row != null) row.setImageBitmap(FishWidget.rowBitmap(this, true, false));

        android.widget.ImageView flat = findViewById(R.id.preview_fish);
        if (flat != null) {
            flat.setVisibility(jade ? android.view.View.GONE : android.view.View.VISIBLE);
            if (!jade) flat.setImageBitmap(FlatRender.render(this, mode, MuyuState.whiteTone(this), 0));
        }
        android.view.View host = findViewById(R.id.mini3d_host);
        if (host != null) host.setVisibility(jade ? android.view.View.VISIBLE : android.view.View.GONE);

        android.widget.TextView hint = findViewById(R.id.preview_hint);
        if (hint != null) hint.setText(jade ? "拖動旋轉 · 雙擊全屏 3D"
                : (mode == MuyuState.PREVIEW_RELIEF ? "微立體：光影勾畫的 2.5D 形態" : "剪影為平面設計，無 3D"));

        setMaterialVisible(jade);
        setToneVisible(!jade);
        ensureMini3D();
        if (mini3d != null) {
            if (jade) { mini3d.onResume(); mini3d.requestRender(); }
            else mini3d.onPause();
        }
    }

    /** 小窗 3D（可拖动；双击全屏；停手自动定格为小部件基底） */
    private void ensureMini3D() {
        if (mini3d != null) return;
        android.widget.FrameLayout host = findViewById(R.id.mini3d_host);
        if (host == null) return;
        mini3d = new JadeView(this, false);
        host.addView(mini3d, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        mini3d.setListener(new JadeView.Listener() {
            @Override public void onDoubleTap() {
                startActivity(new android.content.Intent(MainActivity.this, Model3DActivity.class));
            }
            @Override public void onSettled(float yaw, float pitch, float zoom) {
                MuyuState.setView(MainActivity.this, yaw, pitch, zoom);
                snapshotWidgetBase(yaw, pitch, zoom);
                updatePreview();
            }
        });
    }

    /** 小窗静止后预渲染小部件基底图（同一后台线程串行：写盘 → 失效 → 刷新小部件） */
    private void snapshotWidgetBase(final float yaw, final float pitch, final float zoom) {
        if (snapBusy) return;
        snapBusy = true;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (Off3D.renderToFile(app, yaw, pitch, zoom)) {
                        JadeRender.reloadBase();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                FishWidget.renderAll(MainActivity.this, 0, false);
                                updatePreview();   // 新基底落盘后再刷一次预览，避免滞后一拍
                            }
                        });
                    }
                } finally {
                    snapBusy = false;
                }
            }
        }, "off3d-mini").start();
    }

    private static class HistAdapter extends android.widget.ArrayAdapter<String> {
        HistAdapter(android.content.Context c, String[] items) { super(c, R.layout.item_history, items); }

        @Override public android.view.View getView(int pos, android.view.View cv, android.view.ViewGroup parent) {
            if (cv == null) {
                cv = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_history, parent, false);
            }
            String s = getItem(pos) == null ? "" : getItem(pos);
            String time = s, cnt = "";
            int k = s.indexOf("　回向 ");
            if (k >= 0) {
                time = s.substring(0, k);
                // 纯数字+次（原文已含"次"）：去前缀、去千分位逗号，保证等宽数字严格按位列对齐
                cnt = s.substring(k + "　回向 ".length()).replace(",", "");
            }
            ((TextView) cv.findViewById(R.id.hist_time)).setText(time);
            ((TextView) cv.findViewById(R.id.hist_count)).setText(cnt);
            android.view.View bar = cv.findViewById(R.id.hist_bar);
            bar.setVisibility(android.view.View.VISIBLE);
            bar.setBackgroundColor(pos == 0 ? 0xFFC8A050 : 0x00000000);
            return cv;
        }
    }

    private void refreshHistory() {
        String[] h = MuyuState.history(this);
        android.widget.ListView lv = findViewById(R.id.hist_list);
        TextView empty = findViewById(R.id.hist_empty);
        if (h.length == 0) {
            lv.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            lv.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
            lv.setAdapter(new HistAdapter(this, h));
        }
    }

    private void refresh() {
        total.setText(MuyuState.fmt(MuyuState.today(this)));
        ((TextView) findViewById(R.id.total_caption)).setText("今日功德");
        int roundsDone = MuyuState.rounds(this);
        rounds.setText("累计 " + MuyuState.fmt(MuyuState.total(this)) + " · 第 " + (roundsDone + 1)
                + " 輪 · 本輪 " + MuyuState.cycle(this) + "/108");
        refreshHistory();
    }

    /** 剪影时材质选择不可用（材质只对 3D 有意义） */
    private void setToneVisible(boolean visible) {
        int v = visible ? android.view.View.VISIBLE : android.view.View.GONE;
        android.view.View h = findViewById(R.id.tone_header);
        android.view.View g = findViewById(R.id.tone_group);
        if (h != null) h.setVisibility(v);
        if (g != null) g.setVisibility(v);
    }

    private void setMaterialVisible(boolean visible) {
        int v = visible ? android.view.View.VISIBLE : android.view.View.GONE;
        android.view.View h = findViewById(R.id.mat_header);
        android.view.View g = findViewById(R.id.mat_group);
        if (h != null) h.setVisibility(v);
        if (g != null) g.setVisibility(v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台把小部件重绘一遍，设置改完打开 App 即刻同步
        FishWidget.renderAll(this, 0, false);
        ensureMini3D();
        if (MuyuState.bakeVer(this) != BAKE_VER) {
            MuyuState.setBakeVer(this, BAKE_VER);
            snapshotWidgetBase(MuyuState.viewYaw(this), MuyuState.viewPitch(this), 1f);
        }
        // 3D 定格图可能刚在后台写入，稍后补一次预览刷新
        findViewById(android.R.id.content).postDelayed(new Runnable() {
            @Override public void run() { updatePreview(); }
        }, 1800);
        setMaterialVisible(MuyuState.previewMode(this) == MuyuState.PREVIEW_3D);
        setToneVisible(MuyuState.previewMode(this) != MuyuState.PREVIEW_3D);
        // 标签页
        final int[] pages = {R.id.tap_page, R.id.sutra_page, R.id.look_page, R.id.sound_page};
        final int[] btns = {R.id.tab_tap, R.id.tab_sutra, R.id.tab_look, R.id.tab_sound};
        View.OnClickListener tabClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < btns.length; i++) if (btns[i] == v.getId()) idx = i;
                MuyuState.setLastTab(MainActivity.this, idx);
                if (idx == 2) updatePreview();
                for (int i = 0; i < pages.length; i++) {
                    View pg = findViewById(pages[i]);
                    if (pg == null) continue;
                    pg.animate().cancel();
                    if (i == idx) {
                        pg.setVisibility(View.VISIBLE);
                        pg.setAlpha(0f);
                        pg.animate().alpha(1f).setDuration(160).start();
                    } else {
                        pg.setVisibility(View.GONE);
                    }
                    Button b = findViewById(btns[i]);
                    if (b != null) b.setTextColor(i == idx ? 0xFF141210 : 0xFFC8A050);
                    if (b != null) b.setBackgroundResource(i == idx ? R.drawable.bg_seg_selected_sel : R.drawable.bg_seg_idle_sel);
                }
            }
        };
        for (int id : btns) findViewById(id).setOnClickListener(tabClick);
        int lastTab = MuyuState.lastTab(this);
        ((View) findViewById(btns[Math.max(0, Math.min(btns.length - 1, lastTab))])).performClick();

        refresh();
    }
}
