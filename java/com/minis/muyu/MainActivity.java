package com.minis.muyu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
        ((RadioButton) findViewById(MuyuState.sutraSel(this) == 1 ? R.id.sutra_xj : R.id.sutra_jj)).setChecked(true);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                MuyuState.setSutraSel(MainActivity.this, checkedId == R.id.sutra_xj ? 1 : 0);
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

        refresh();
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
