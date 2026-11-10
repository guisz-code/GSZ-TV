package com.gsztv.launcher;

import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Small native overlay; no artwork, animation, network or download work on the UI thread. */
final class UpdateBanner extends LinearLayout {
    private final HomeView home;
    private final TextView detail, action;
    private final SharedPreferences session;
    private final Handler handler = new Handler();
    private final String installed;
    private boolean resumed;
    private long requested;
    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                session.edit().remove("dismissedBoot").apply();
                pause();
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                if (getWindowVisibility() == VISIBLE) resume();
            }
        }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            JSONObject state = Updates.state(getContext());
            render(state);
            handler.postDelayed(this, Updates.busy(state) || SystemClock.elapsedRealtime() - requested < 15000 ? 2000 : 15000);
        }
    };

    UpdateBanner(Context c, HomeView home) {
        super(c);
        this.home = home;
        installed = Updates.installedVersion(c);
        session = c.getSharedPreferences("update-banner-session", Context.MODE_PRIVATE);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(18), dp(10), dp(14), dp(10));
        setBackground(box(0xff071426, 0xff008aff));
        setVisibility(GONE);
        TextView logo = text("GSZ TV", 18);
        logo.setTypeface(null, Typeface.BOLD);
        addView(logo, new LayoutParams(dp(95), -2));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        TextView title = text("Atualização disponível", 20);
        title.setTypeface(null, Typeface.BOLD);
        words.addView(title);
        detail = text("", 14);
        detail.setMaxLines(2);
        words.addView(detail);
        addView(words, new LayoutParams(0, -2, 1));
        action = button("Baixar atualização");
        LayoutParams ap = new LayoutParams(dp(190), dp(46));
        ap.setMargins(dp(12), 0, dp(10), 0);
        addView(action, ap);
        TextView close = button("×");
        close.setTextSize(28);
        close.setContentDescription("Fechar aviso até ligar a tela novamente");
        addView(close, new LayoutParams(dp(44), dp(46)));
        action.setOnClickListener(v -> {
            JSONObject state = Updates.state(c);
            if (Updates.busy(state) || SystemClock.elapsedRealtime() - requested < 3000) return;
            requested = SystemClock.elapsedRealtime();
            ModeJob.schedule(c, Updates.ready(state, installed) ? ModeJob.INSTALL : ModeJob.UPDATE, "", 0);
            action.setText(Updates.ready(state, installed) ? "Abrindo instalador…" : "Iniciando download…");
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 2000);
        });
        close.setOnClickListener(v -> {
            session.edit().putInt("dismissedBoot", boot()).apply();
            hide();
        });
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        c.registerReceiver(power, filter);
    }

    private int boot() {
        return Settings.Global.getInt(getContext().getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable box(int color, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(16)); d.setStroke(dp(2), border);
        return d;
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(getContext());
        t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(size);
        return t;
    }
    private TextView button(String value) {
        TextView t = text(value, 16);
        t.setGravity(Gravity.CENTER); t.setTypeface(null, Typeface.BOLD);
        t.setFocusable(true); t.setClickable(true);
        t.setBackground(box(0xff006bef, 0xff008aff));
        t.setOnFocusChangeListener((v, focus) -> t.setBackground(box(focus ? 0xff168bff : 0xff006bef, focus ? Color.WHITE : 0xff008aff)));
        return t;
    }
    void resume() {
        resumed = true;
        Updates.schedule(getContext());
        JSONObject state = Updates.state(getContext());
        long age = System.currentTimeMillis() - state.optLong("at");
        long interval = Updates.FAILED.equals(state.optString("state")) ? 30 * 60000L : 24 * 3600000L;
        if ((Prefs.updateBanner() || Prefs.autoUpdates()) && Updates.takesReleases(getContext())
                && !Updates.busy(state) && (age >= interval || age < 0)) {
            ModeJob.schedule(getContext(), ModeJob.DISCOVER, "", 0);
        }
        handler.removeCallbacks(poll);
        poll.run();
    }
    void pause() { resumed = false; handler.removeCallbacks(poll); }
    void destroy() { pause(); getContext().unregisterReceiver(power); }
    private void hide() {
        if (hasFocus()) home.requestFocus();
        setVisibility(GONE);
    }
    private void render(JSONObject state) {
        String version = state.optString("version");
        if (!Prefs.updateBanner() || session.getInt("dismissedBoot", -2) == boot()
                || !Updates.takesReleases(getContext()) || Updates.compare(version, installed) <= 0) {
            hide(); return;
        }
        setVisibility(VISIBLE);
        boolean ready = Updates.ready(state, installed), busy = Updates.busy(state);
        detail.setText("Nova versão v" + version + (Updates.FAILED.equals(state.optString("state"))
                ? " • Não foi possível atualizar. Tente novamente." : " • Correções e melhorias para o GSZ TV."));
        if (SystemClock.elapsedRealtime() - requested >= 3000)
            action.setText(busy ? "Baixando…" : ready ? "Instalar atualização" : "Baixar atualização");
    }
    boolean route(KeyEvent e) {
        if (getVisibility() != VISIBLE) return false;
        int key = e.getKeyCode();
        if (hasFocus() && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_BACK)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) home.requestFocus();
            return true;
        }
        if (!hasFocus() && home.atUpdateBannerEdge() && key == KeyEvent.KEYCODE_DPAD_UP) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) action.requestFocus();
            return true;
        }
        return false;
    }
}
