package com.minis.muyu;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 讀音說明：把誦讀筆記排成可滾動的閱讀頁 */
public class NotesActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_notes);

        findViewById(R.id.close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        TextView body = findViewById(R.id.notes_body);
        body.setText(render());
    }

    private CharSequence render() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    getAssets().open("sutra_notes.md"), "UTF-8"));
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                String raw = line;
                line = line.trim();
                if (line.length() == 0) {
                    if (!first) sb.append("\n");
                    first = false;
                    continue;
                }
                // 表格分隔行（|---|---|）直接丢
                if (line.matches("^\\|?[-: ]+\\|[ -:|]*$")) continue;   // 表格分隔行
                if (line.matches("^-{3,}$") || line.matches("^\\*{3,}$")) {   // 分隔線
                    sb.append('\n');
                    continue;
                }

                int start = sb.length();
                boolean heading = line.startsWith("#");
                String text = line.replaceAll("^#+\\s*", "")
                        .replaceAll("^>\\s*", "")
                        .replace("**", "")
                        .replace("`", "");
                if (text.contains("|")) {
                    // 表格行：竖线换成两个空格，读起来像列
                    text = text.replaceAll("^\\|", "").replaceAll("\\|$", "")
                            .replace("|", "   ");
                }
                sb.append(text);
                int end = sb.length();
                if (heading) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(0xFFE8E2D6), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (raw.startsWith(">")) {
                    sb.setSpan(new ForegroundColorSpan(0xFF6B5D49), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                sb.append('\n');
                first = false;
            }
            r.close();
        } catch (Exception e) {
            return "讀取筆記失敗：" + e;
        }
        return sb;
    }
}
