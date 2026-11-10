package com.gsztv.launcher;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Categorized entry point; delegates settings changes to the existing settings controller. */
final class SettingsMenu extends LinearLayout {
    private static final String[] TITLES = {"Tela e reprodução", "Atualização", "Rede", "Apps", "Preferências", "Sobre"};
    private static final String[] DETAILS = {"Tela inicial, fotos e protetor de tela", "Verificar, baixar e instalar novas versões", "Conexão Wi-Fi e configurações de rede", "Aplicativos, recomendações e atalhos", "Idioma, tema, widgets e controle remoto", "Versão do launcher e informações do projeto"};
    private static final String[] ICONS = {"tv_settings", "bell", "wifi", "apps", "settings", "settings"};
    private final SettingsActivity activity;
    private final LinearLayout rows;
    private final TextView title;
    private final ScrollView scroll;
    private final ArrayList<TextView> values = new ArrayList<>();
    private final ArrayList<Integer> ids = new ArrayList<>();
    private int group = -1;
    private View lastFocus;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (isShown()) refreshValues();
            if (isAttachedToWindow()) postDelayed(this, 3000);
        }
    };

    SettingsMenu(SettingsActivity a) {
        super(a); activity = a;
        setOrientation(VERTICAL); setBackgroundColor(0xff030913);
        setPadding(dp(28), dp(18), dp(28), dp(18));
        title = text("Configurações", 28, true);
        title.setPadding(dp(8), 0, 0, dp(16));
        addView(title, new LayoutParams(-1, -2));
        scroll = new ScrollView(a); scroll.setFillViewport(false);
        rows = new LinearLayout(a); rows.setOrientation(VERTICAL);
        scroll.addView(rows); addView(scroll, new LayoutParams(-1, 0, 1));
        show(-1);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(refresh); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(refresh); super.onDetachedFromWindow(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(activity); t.setText(s); t.setTextSize(size); t.setTextColor(0xfff6f8ff);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private GradientDrawable background(boolean focus) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                focus ? new int[]{0xff073d84, 0xff031b40} : new int[]{0xff111c2c, 0xff0a1321});
        d.setCornerRadius(dp(16)); d.setStroke(dp(focus ? 2 : 1), focus ? 0xff008aff : 0xff243244);
        return d;
    }
    private TextView card(String label, String detail, String icon, Runnable click) {
        LinearLayout card = new LinearLayout(activity); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(10)); card.setMinimumHeight(dp(68));
        card.setFocusable(true); card.setClickable(true); card.setBackground(background(false));
        View symbol = new View(activity) {
            @Override protected void onDraw(Canvas c) {
                Symbols.draw(activity, c, icon, getWidth()/2f, getHeight()/2f, dp(28), 0xfff6f8ff);
            }
        };
        card.addView(symbol, new LayoutParams(dp(46), dp(42)));
        LinearLayout words = new LinearLayout(activity); words.setOrientation(VERTICAL);
        words.setPadding(dp(16), 0, dp(12), 0);
        words.addView(text(label, 19, true));
        TextView value = text(detail, 14, false); value.setTextColor(0xffc9d6e7); words.addView(value);
        card.addView(words, new LayoutParams(0, -2, 1));
        card.addView(text("›", 30, false));
        LayoutParams lp = new LayoutParams(-1, -2); lp.bottomMargin = dp(10); rows.addView(card, lp);
        card.setOnFocusChangeListener((v, focused) -> { card.setBackground(background(focused)); if (focused) lastFocus = card; });
        card.setOnClickListener(v -> { lastFocus = card; click.run(); });
        return value;
    }
    private void main(String label, String detail, String icon, int action) {
        card(label, detail, icon, () -> activity.menuAction(action, true));
    }
    private void show(int next) {
        group = next; lastFocus = null; rows.removeAllViews(); values.clear(); ids.clear();
        title.setText(next < 0 ? "Configurações" : TITLES[next]);
        if (next < 0) {
            for (int i=0; i<TITLES.length; i++) { final int n=i; card(TITLES[i], DETAILS[i], ICONS[i], () -> show(n)); }
        } else {
            card("Voltar", "Todas as categorias", "chevron_right", () -> show(-1));
            if (next == 0) {
                main("Tela inicial", "Escolher GSZ TV ou launcher original", "home", 0);
                main("Protetor de tela", "Visualizar o protetor de tela", "tv_settings", 1);
                main("Temporizador", "Definir quando desligar a tela", "bedtime", 6);
            }
            if (next == 2) card("Configurações de rede", "Abrir as opções de conexão do aparelho", "wifi", () -> {
                try { activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
                catch (RuntimeException e) { HomeApps.open(activity, HomeApps.tvSettings(), null); }
            });
            if (next == 3) {
                main("Apps favoritos", "Organizar os aplicativos na tela inicial", "apps", 3);
                main("Atalhos superiores", "Escolher os botões do topo", "apps", 4);
            }
            if (next == 4) {
                main("Controle remoto", "Configurar os botões do controle", "settings", 5);
                main("Configurações do aparelho", "Abrir o menu de configurações do Android", "tv_settings", 7);
                main("Central de desempenho", "Memória e opções de desempenho", "settings", 8);
            }
            String[] labels=activity.menuLabels(), current=activity.menuValues();
            for (int i=0; i<labels.length; i++) if (activity.menuGroup(i)==next) {
                final int row=i;
                TextView value = card(labels[i], current[i], ICONS[next], () -> activity.menuAction(row, false));
                View rowView = (View) value.getParent().getParent();
                rowView.setOnKeyListener((v, code, event) -> {
                    if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        activity.menuAdjust(row, code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    }
                    return true;
                });
                values.add(value);
                ids.add(i);
            }
            if (next == 5) {
                TextView info=text("GSZ TV " + Updates.installedVersion(activity) + "\n\nCriado por guisz-code com auxílio de inteligência artificial.\n\ngithub.com/guisz-code/GSZ-TV", 18, false);
                info.setPadding(dp(20), dp(20), dp(20), dp(20)); rows.addView(info);
            }
        }
        scroll.scrollTo(0,0); restoreFocus();
    }
    void refreshValues() {
        if (values.isEmpty()) return;
        String[] current=activity.menuValues();
        for (int i=0; i<values.size(); i++) {
            int row=ids.get(i); if (row<current.length && !current[row].contentEquals(values.get(i).getText())) values.get(i).setText(current[row]);
        }
    }
    void restoreFocus() { if (lastFocus != null) lastFocus.requestFocus(); else if (rows.getChildCount()>0) rows.getChildAt(0).requestFocus(); }
    boolean back(KeyEvent event) {
        if (group<0) return false;
        if (event.getAction()==KeyEvent.ACTION_UP) show(-1);
        return true;
    }
}
