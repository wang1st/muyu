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

    private TextView total, rounds, history;
    private CheckBox swSound, swVib, swSutra;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        total = findViewById(R.id.total);
        rounds = findViewById(R.id.rounds);
        history = findViewById(R.id.history);
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
            }
        });
        swSutra = findViewById(R.id.sw_sutra);
        swSutra.setChecked(MuyuState.sutraOn(this));
        swSutra.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setSutraOn(MainActivity.this, checked);
                FishWidget.renderAll(MainActivity.this, 0, false);
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
            }
        });
        CheckBox swNumHot = findViewById(R.id.sw_num_hot);
        swNumHot.setChecked(MuyuState.numHotOn(this));
        swNumHot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                MuyuState.setNumHotOn(MainActivity.this, checked);
                FishWidget.renderAll(MainActivity.this, 0, false);
            }
        });

        RadioGroup cg = findViewById(R.id.contrast_group);
        int cm = MuyuState.sutraContrast(this);
        ((RadioButton) findViewById(cm == 1 ? R.id.ct_read : (cm == 2 ? R.id.ct_unread : R.id.ct_same))).setChecked(true);
        cg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int v = checkedId == R.id.ct_read ? 1 : (checkedId == R.id.ct_unread ? 2 : 0);
                MuyuState.setSutraContrast(MainActivity.this, v);
                FishWidget.renderAll(MainActivity.this, 0, false);
            }
        });

        findViewById(R.id.btn_reset_sutra).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MuyuState.resetSutra(MainActivity.this);
                FishWidget.renderAll(MainActivity.this, 0, false);
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

        refresh();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    private void refresh() {
        total.setText(MuyuState.fmt(MuyuState.today(this)));
        ((TextView) findViewById(R.id.total_caption)).setText("今日功德");
        int roundsDone = MuyuState.rounds(this);
        rounds.setText("累计 " + MuyuState.fmt(MuyuState.total(this)) + " · 第 " + (roundsDone + 1)
                + " 輪 · 本輪 " + MuyuState.cycle(this) + "/108");
        String[] h = MuyuState.history(this);
        if (h.length == 0) {
            history.setText("暫無回向記錄");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(h.length, 20); i++) {
                if (i > 0) sb.append('\n');
                sb.append(h[i]);
            }
            history.setText(sb.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
