package com.gsztv.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.StatFs;
import android.view.KeyEvent;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;

/** GSZ TV 1.4 performance center, intentionally simple for the d-pad. */
public class PerformanceActivity extends Activity {
    private PerformanceView view;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        view = new PerformanceView();
        setContentView(view);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP && view.onKey(e.getKeyCode())) return true;
        return super.dispatchKeyEvent(e);
    }

    private final class PerformanceView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int row;
        private String status = "";
        PerformanceView() { super(PerformanceActivity.this); p.setTypeface(Ui.medium); setFocusable(true); }
        boolean onKey(int code) {
            if (code == KeyEvent.KEYCODE_BACK) { finish(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_UP) { row = Math.max(0, row - 1); invalidate(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { row = Math.min(2, row + 1); invalidate(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (row == 0) {
                    int[] values = {0, 5, 10, 15, 30, 60};
                    int current = PerformanceManager.timeout(PerformanceActivity.this);
                    int at = 0; for (int i = 0; i < values.length; i++) if (values[i] == current) at = i;
                    at += code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                    at = Math.max(0, Math.min(values.length - 1, at));
                    PerformanceManager.setTimeout(PerformanceActivity.this, values[at]);
                }
                invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (row == 1) {
                    long before = availableRam();
                    int stopped = PerformanceManager.cleanup(PerformanceActivity.this);
                    long after = availableRam();
                    status = stopped == 0 ? "Nenhum app de usuário estava em segundo plano. RAM livre: " + format(after)
                            : "Limpeza concluída: " + stopped + " processo(s) · RAM " + format(before) + " → " + format(after);
                }
                if (row == 2) { PerformanceManager.setTimeout(PerformanceActivity.this, 0); status = "Limpeza automática desativada."; }
                invalidate(); return true;
            }
            return false;
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float k = getWidth() / 1920f;
            c.drawColor(0xFF111216);
            p.setColor(0xFFF3F4F7); p.setTextSize(58 * k); c.drawText("Central de Desempenho", 96 * k, 120 * k, p);
            p.setTextSize(32 * k); p.setColor(0xFFBFC3CD);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE); if (am != null) am.getMemoryInfo(mi);
            StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            c.drawText("RAM livre: " + format(mi.availMem) + " / " + format(mi.totalMem), 110 * k, 220 * k, p);
            c.drawText("Armazenamento livre: " + format(free) + " / " + format(total), 110 * k, 270 * k, p);
            String[] labels = {"Encerrar apps sem uso", "Liberar RAM agora", "Desativar limpeza automática"};
            String timeout = PerformanceManager.timeout(PerformanceActivity.this) == 0 ? "Desligado" : PerformanceManager.timeout(PerformanceActivity.this) + " min";
            String[] values = {timeout, "OK executa", "OK confirma"};
            for (int i = 0; i < labels.length; i++) {
                float y = (390 + i * 90) * k;
                p.setColor(i == row ? 0xFF2A72E8 : 0xFF202228); c.drawRoundRect(90 * k, y - 48 * k, 1830 * k, y + 28 * k, 18 * k, 18 * k, p);
                p.setColor(0xFFF3F4F7); c.drawText(labels[i], 125 * k, y, p);
                p.setColor(0xFFBFC3CD); p.setTextAlign(Paint.Align.RIGHT); c.drawText(values[i], 1785 * k, y, p); p.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(0xFF858A96); p.setTextSize(25 * k); c.drawText("← → altera o tempo · OK executa · 0 min desliga", 110 * k, 760 * k, p);
            if (!status.isEmpty()) {
                p.setColor(0xFF63D391); p.setTextSize(30 * k);
                c.drawText(status, 110 * k, 830 * k, p);
            }
        }
        private String format(long n) { long gb = n / (1024 * 1024 * 1024); return gb > 0 ? gb + " GB" : (n / (1024 * 1024)) + " MB"; }
        private long availableRam() {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) am.getMemoryInfo(mi);
            return mi.availMem;
        }
    }
}
