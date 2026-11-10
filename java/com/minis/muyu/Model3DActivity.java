package com.minis.muyu;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** 全屏 3D 预览：JadeView（连续渲染 + 静置慢转）+ 退出时定格为小部件基底图。 */
public class Model3DActivity extends Activity {

    private JadeView jade;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            getWindow().setStatusBarColor(0xFF141210);
            getWindow().setNavigationBarColor(0xFF141210);
        } catch (Throwable ignored) {}

        setContentView(R.layout.activity_model3d);

        jade = new JadeView(this, true);
        ((FrameLayout) findViewById(R.id.gl_host)).addView(jade,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        jade.setListener(new JadeView.Listener() {
            @Override public void onDoubleTap() { }
            @Override public void onSettled(float yaw, float pitch, float zoom) {
                MuyuState.setView(Model3DActivity.this, yaw, pitch, zoom);
            }
        });

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        final TextView hint = findViewById(R.id.gl_hint);
        hint.postDelayed(new Runnable() {
            @Override public void run() { hint.animate().alpha(0f).setDuration(600).start(); }
        }, 4200);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (jade == null) return;
        final float yaw = jade.getYaw(), pitch = jade.getPitch(), zoom = jade.getZoom();
        MuyuState.setView(this, yaw, pitch, zoom);
        jade.onPause();
        // 兜底定格：后台离屏渲染 → 写盘 → 刷新小部件（同一线程串行，不存在读写竞态）
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                if (!Off3D.renderToFile(app, yaw, pitch, zoom)) return;
                JadeRender.reloadBase();
                runOnUiThread(new Runnable() {
                    @Override public void run() { FishWidget.renderAll(Model3DActivity.this, 0, false); }
                });
            }
        }, "off3d-out").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (jade != null) jade.onResume();
    }
}
