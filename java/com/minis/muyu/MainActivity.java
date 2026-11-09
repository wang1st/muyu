package com.minis.muyu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

/** 木魚：功德簿与设置（敲击在小部件上完成） */
public class MainActivity extends Activity {

    private TextView total, rounds;
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
        ((RadioButton) findViewById(MuyuState.muyuJade(this) ? R.id.style_jade : R.id.style_flat)).setChecked(true);
        styleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                MuyuState.setMuyuJade(MainActivity.this, checkedId == R.id.style_jade);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
            }
        });

        View.OnClickListener jadePreset = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int id = v.getId();
                int[][] P = {
                        {55, 62, 90, 65, 65, 38, 60, 20},   // 白玉
                        {58, 20, 86, 70, 62, 34, 55, 20},   // 青玉
                        {60, 88, 74, 55, 68, 42, 62, 24},   // 蜜蠟
                        {34, 45, 82, 40, 60, 55, 66, 18},   // 墨玉
                        {70, 40, 96, 92, 68, 28, 52, 26},   // 冰種
                        {48, 78, 84, 58, 72, 62, 74, 14},   // 老玉
                        {86, 45, 100, 70, 58, 18, 44, 28},  // 素瓷
                        {24, 30, 88, 78, 64, 70, 70, 10},   // 夜玉
                };
                int idx = id == R.id.jade_celadon ? 1 : id == R.id.jade_honey ? 2
                        : id == R.id.jade_ink ? 3 : id == R.id.jade_ice ? 4
                        : id == R.id.jade_old ? 5 : id == R.id.jade_porcelain ? 6
                        : id == R.id.jade_night ? 7 : 0;
                int[] p = P[idx];
                MuyuState.setJade(MainActivity.this, p[0], p[1], p[2], p[3], p[4], 50, p[5], p[6], p[7]);
                MuyuState.setMuyuJade(MainActivity.this, true);
                ((RadioButton) findViewById(R.id.style_jade)).setChecked(true);
                FishWidget.renderAll(MainActivity.this, 0, false);
                updatePreview();
                updatePreview();
            }
        };
        int[] presetBtns = {R.id.jade_white, R.id.jade_celadon, R.id.jade_honey, R.id.jade_ink,
                R.id.jade_ice, R.id.jade_old, R.id.jade_porcelain, R.id.jade_night};
        for (int id : presetBtns) findViewById(id).setOnClickListener(jadePreset);

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
        scrimLabel.setText("文字底襯　" + scrimBar.getProgress() + "%");
        scrimBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                scrimLabel.setText("文字底襯　" + p + "%");
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

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    /** 外觀頁實時預覽：用 widget 同款渲染管線合成「魚 + 經文/數字行」 */
    private void updatePreview() {
        android.widget.ImageView pv = findViewById(R.id.preview_widget);
        if (pv == null) return;
        float dp = getResources().getDisplayMetrics().density;
        int W = getResources().getDisplayMetrics().widthPixels - (int) (64 * dp);
        if (W <= 0) return;

        android.graphics.Bitmap row = FishWidget.rowBitmap(this, true, false);
        android.graphics.Bitmap fish = MuyuState.muyuJade(this)
                ? JadeRender.render(this, 0)
                : android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.fish_tight);

        int fishH = (int) (W * (MuyuState.muyuJade(this) ? 0.52f : 0.42f));
        int gap = (int) (10 * dp);
        int H = fishH + gap + row.getHeight();
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        int fw = (int) ((float) fish.getWidth() / fish.getHeight() * fishH);
        cv.drawBitmap(fish, null, new android.graphics.Rect((W - fw) / 2, 0, (W + fw) / 2, fishH), null);
        cv.drawBitmap(row, (W - row.getWidth()) / 2f, fishH + gap, null);
        pv.setImageBitmap(out);
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

    @Override
    protected void onResume() {
        super.onResume();
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
