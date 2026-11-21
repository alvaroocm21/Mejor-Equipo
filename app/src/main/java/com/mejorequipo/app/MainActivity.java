package com.mejorequipo.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mejorequipo.engine.Dex;
import com.mejorequipo.engine.Es;
import com.mejorequipo.engine.Gen6Save;
import com.mejorequipo.engine.ItemAdvisor;
import com.mejorequipo.engine.PokeSet;
import com.mejorequipo.engine.ShowdownParser;
import com.mejorequipo.engine.TeamBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_FOLDER = 42;
    private static final String OUT_NAME = "equipos_generados.txt";

    private static final int BG = 0xFF0F1015, CARD = 0xFF1A1C25, CARD2 = 0xFF232632, TXT = 0xFFECEFF4,
            MUTED = 0xFF9AA3B2, ACCENT = 0xFFFF4D4D, GOOD = 0xFF6EE7A0, BAD = 0xFFFFB454, CHIP = 0xFF2A2D3A;

    private SharedPreferences prefs;
    private static Dex dex;
    private static Gen6Save.Names names;
    private Ui.Sprites sprites;

    private TextView status, tabInfo, txtN;
    private LinearLayout results, settings;
    private ProgressBar progress;
    private Button btnSingles, btnDoubles, tabStrong, tabAdv;
    private Switch swVariety;
    private volatile boolean running = false;

    private boolean doubles, variety;
    private int nTeams, tab; // tab 0 = combate fuerte, 1 = aventura
    private List<TeamBuilder.TeamResult> strongTeams, advTeams;
    private List<String> lastWarnings = new ArrayList<>();

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        doubles = prefs.getBoolean("doubles", false);
        nTeams = prefs.getInt("nTeams", 5);
        variety = prefs.getBoolean("variety", true);
        tab = prefs.getInt("tab", 0);
        sprites = new Ui.Sprites(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh(); // cada vez que se abre la app se vuelve a leer el main
    }

    // ------------------------------------------------------------------ helpers de UI
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int bg) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextColor(TXT);
        bt.setBackground(round(bg, 12));
        bt.setPadding(dp(12), 0, dp(12), 0);
        bt.setStateListAnimator(null);
        return bt;
    }

    private TextView chip(String s, int bg, int fg, int sp) {
        TextView t = text(s, sp, fg, true);
        t.setBackground(round(bg, 20));
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(color, 18));
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    private LinearLayout.LayoutParams lpMatch(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        lp.bottomMargin = dp(bottom);
        return lp;
    }

    // ------------------------------------------------------------------ construcción de pantalla
    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(30), dp(14), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        // cabecera
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFB3262E, 0xFF5B1A3D, 0xFF1A1C25});
        hg.setCornerRadius(dp(20));
        head.setBackground(hg);
        head.setPadding(dp(16), dp(14), dp(16), dp(14));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        ht.addView(text("Mejor Equipo", 24, 0xFFFFFFFF, true));
        ht.addView(text("Tu partida, tus Pokémon, el mejor equipo posible", 12, 0xCCFFFFFF, false));
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(head, lpMatch(0, 10));

        // tarjeta de estado + botones
        LinearLayout stat = card(CARD);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFolder = button("📁  Carpeta", ACCENT);
        Button btnRefresh = button("⟳  Actualizar", CHIP);
        Button btnSet = button("⚙", CHIP);
        row1.addView(btnFolder, new LinearLayout.LayoutParams(0, dp(44), 1.2f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(44), 1.2f);
        l2.leftMargin = dp(8);
        row1.addView(btnRefresh, l2);
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(0, dp(44), 0.5f);
        l3.leftMargin = dp(8);
        row1.addView(btnSet, l3);
        stat.addView(row1);
        status = text("Elige la carpeta donde está tu archivo \"main\".", 13, MUTED, false);
        status.setPadding(0, dp(10), 0, 0);
        stat.addView(status);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(30), dp(30));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.topMargin = dp(8);
        stat.addView(progress, pl);
        settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setVisibility(View.GONE);
        settings.setPadding(0, dp(10), 0, 0);
        buildSettings(settings);
        stat.addView(settings);
        root.addView(stat, lpMatch(0, 12));
        btnFolder.setOnClickListener(v -> pickFolder());
        btnRefresh.setOnClickListener(v -> refresh());
        btnSet.setOnClickListener(v -> settings.setVisibility(settings.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        // pestañas
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackground(round(CARD, 16));
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabStrong = button("⚔️  Combate fuerte", CARD);
        tabAdv = button("🌿  Aventura", CARD);
        tabs.addView(tabStrong, new LinearLayout.LayoutParams(0, dp(46), 1f));
        tabs.addView(tabAdv, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(tabs, lpMatch(0, 6));
        tabStrong.setOnClickListener(v -> setTab(0));
        tabAdv.setOnClickListener(v -> setTab(1));
        tabInfo = text("", 12, MUTED, false);
        tabInfo.setPadding(dp(4), 0, dp(4), dp(8));
        root.addView(tabInfo);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        setContentView(sv);
        updateControls();
    }

    private void buildSettings(LinearLayout s) {
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        btnSingles = button("Singles", CHIP);
        btnDoubles = button("Dobles", CHIP);
        r2.addView(btnSingles, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.leftMargin = dp(8);
        r2.addView(btnDoubles, lp);
        s.addView(r2);
        btnSingles.setOnClickListener(v -> { doubles = false; saveAndRefresh(); });
        btnDoubles.setOnClickListener(v -> { doubles = true; saveAndRefresh(); });

        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.setGravity(Gravity.CENTER_VERTICAL);
        r3.setPadding(0, dp(8), 0, 0);
        r3.addView(text("Equipos:", 14, TXT, false));
        Button minus = button("−", CHIP), plus = button("+", CHIP);
        txtN = text(String.valueOf(nTeams), 16, TXT, true);
        txtN.setGravity(Gravity.CENTER);
        r3.addView(minus, new LinearLayout.LayoutParams(dp(42), dp(38)));
        r3.addView(txtN, new LinearLayout.LayoutParams(dp(34), -2));
        r3.addView(plus, new LinearLayout.LayoutParams(dp(42), dp(38)));
        swVariety = new Switch(this);
        swVariety.setText("Variar equipos");
        swVariety.setTextColor(TXT);
        swVariety.setChecked(variety);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1f);
        l.leftMargin = dp(12);
        r3.addView(swVariety, l);
        s.addView(r3);
        minus.setOnClickListener(v -> { if (nTeams > 1) { nTeams--; saveAndRefresh(); } });
        plus.setOnClickListener(v -> { if (nTeams < 15) { nTeams++; saveAndRefresh(); } });
        swVariety.setOnCheckedChangeListener((v, c) -> { variety = c; saveAndRefresh(); });
    }

    private void updateControls() {
        btnSingles.setBackground(round(doubles ? CHIP : ACCENT, 12));
        btnDoubles.setBackground(round(doubles ? ACCENT : CHIP, 12));
        txtN.setText(String.valueOf(nTeams));
        tabStrong.setBackground(round(tab == 0 ? ACCENT : CARD, 12));
        tabAdv.setBackground(round(tab == 1 ? 0xFF2F9E6B : CARD, 12));
        tabInfo.setText(tab == 0
                ? "Para jefes, líderes, Alto Mando y rivales clave. Aquí SÍ se usan bayas y objetos de un solo uso."
                : "Para el recorrido normal. Solo objetos que no se gastan: las bayas y consumibles se reservan para los combates fuertes.");
    }

    private void setTab(int t) {
        tab = t;
        prefs.edit().putInt("tab", tab).apply();
        updateControls();
        showTeams();
    }

    private void saveAndRefresh() {
        prefs.edit().putBoolean("doubles", doubles).putInt("nTeams", nTeams).putBoolean("variety", variety).apply();
        updateControls();
        refresh();
    }

    // ------------------------------------------------------------------ carpeta
    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FOLDER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
            prefs.edit().putString("tree", uri.toString()).apply();
            refresh();
        }
    }

    private static final class FoundFile {
        Uri uri;
        String name;
        long modified;
    }

    private FoundFile bagFile;

    private static boolean isBagName(String l) {
        return l.equals("mochila.txt") || l.equals("mochila") || l.equals("bag.txt") || l.equals("bag")
                || l.equals("objetos.txt") || l.equals("items.txt");
    }

    private FoundFile findMain(Uri tree, List<String> seen) {
        bagFile = null;
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        FoundFile exact = null, prefix = null, anyTxt = null;
        try (Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                seen.add(name);
                FoundFile f = new FoundFile();
                f.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                f.name = name;
                f.modified = c.isNull(3) ? 0 : c.getLong(3);
                String l = name.toLowerCase();
                if (isBagName(l)) { bagFile = f; continue; }
                if (l.equals("main.txt") || l.equals("main")) exact = exact == null ? f : exact;
                else if (l.startsWith("main.") || l.startsWith("main ")) prefix = prefix == null ? f : prefix;
                else if (l.endsWith(".txt") && !l.equals(OUT_NAME)) anyTxt = anyTxt == null ? f : anyTxt;
            }
        }
        return exact != null ? exact : (prefix != null ? prefix : anyTxt);
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String readAll(Uri uri) throws Exception {
        return new String(readBytes(uri), StandardCharsets.UTF_8);
    }

    private void writeResults(Uri tree, String content) {
        try {
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            Uri target = null;
            try (Cursor c = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                while (c != null && c.moveToNext())
                    if (OUT_NAME.equals(c.getString(1)))
                        target = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
            }
            if (target == null) target = DocumentsContract.createDocument(getContentResolver(), parent, "text/plain", OUT_NAME);
            if (target == null) return;
            try (OutputStream o = getContentResolver().openOutputStream(target, "wt")) {
                if (o != null) o.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // La carpeta puede ser de solo lectura; no es crítico.
        }
    }

    // ------------------------------------------------------------------ datos
    private static byte[] readStream(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private synchronized Dex loadDex() throws Exception {
        if (dex == null) {
            String p = new String(readStream(getAssets().open("pokedex.tsv")), StandardCharsets.UTF_8);
            String m = new String(readStream(getAssets().open("moves.tsv")), StandardCharsets.UTF_8);
            dex = Dex.load(p, m);
            Es.load(new String(readStream(getAssets().open("es_names.tsv")), StandardCharsets.UTF_8));
        }
        return dex;
    }

    private synchronized Gen6Save.Names loadNames() throws Exception {
        if (names == null) {
            String[] f = {"species", "moves", "abilities", "items", "growth"};
            String[] t = new String[5];
            for (int i = 0; i < 5; i++)
                t[i] = new String(readStream(getAssets().open("gen6_" + f[i] + ".txt")), StandardCharsets.UTF_8);
            names = Gen6Save.Names.load(t[0], t[1], t[2], t[3], t[4]);
        }
        return names;
    }

    // ------------------------------------------------------------------ cálculo
    private void refresh() {
        if (running || status == null) return;
        String treeStr = prefs.getString("tree", null);
        if (treeStr == null) {
            status.setText("Elige la carpeta donde está tu archivo \"main\".");
            return;
        }
        running = true;
        progress.setVisibility(View.VISIBLE);
        status.setText("Leyendo main y calculando equipos…");
        final boolean dbl = doubles;
        final int n = nTeams;
        final boolean var = variety;
        new Thread(() -> {
            String msg;
            List<TeamBuilder.TeamResult> strong = null, adv = null;
            List<String> warnings = new ArrayList<>();
            try {
                Uri tree = Uri.parse(treeStr);
                List<String> seen = new ArrayList<>();
                FoundFile f = findMain(tree, seen);
                if (f == null) {
                    msg = "No encuentro el archivo \"main\" en la carpeta" + (seen.isEmpty() ? " (está vacía)."
                            : ". Archivos: " + seen) + "\nDebe llamarse main (o main.txt).";
                } else {
                    byte[] raw = readBytes(f.uri);
                    String text;
                    String kind = "";
                    Map<String, Integer> saveBag = null;
                    if (Gen6Save.isSave(raw)) {
                        Gen6Save.Result gr = Gen6Save.read(raw, loadNames());
                        text = gr.text();
                        saveBag = gr.bag;
                        warnings.addAll(gr.warnings);
                        kind = " · Rubí Omega/Zafiro Alfa (" + gr.party + " equipo + " + gr.boxed + " cajas)";
                    } else {
                        text = new String(raw, StandardCharsets.UTF_8);
                    }
                    Dex d = loadDex();
                    List<PokeSet> box = ShowdownParser.parse(d, text, warnings);
                    if (box.isEmpty()) {
                        boolean binary = false;
                        for (int q = 0; q < Math.min(raw.length, 2000); q++) if (raw[q] == 0) { binary = true; break; }
                        msg = binary ? f.name + " es un guardado binario de " + raw.length + " bytes que no reconozco. "
                                + "Solo leo guardados de Rubí Omega/Zafiro Alfa (483328 bytes) o texto en formato Showdown."
                                : f.name + " no contiene Pokémon reconocibles (formato Showdown).";
                        warnings.clear();
                    } else {
                        Map<String, Integer> bag;
                        if (saveBag != null) bag = saveBag;
                        else if (bagFile != null) bag = ItemAdvisor.parseBag(readAll(bagFile.uri));
                        else bag = new java.util.HashMap<>();
                        int bagCount = 0;
                        for (int q : bag.values()) bagCount += q;
                        TeamBuilder.Options o = new TeamBuilder.Options();
                        o.doubles = dbl;
                        o.nTeams = n;
                        o.minDiff = var ? 2 : 1;
                        o.bag = bag;
                        o.mode = 0;
                        strong = TeamBuilder.build(d, box, o);
                        TeamBuilder.Options o2 = new TeamBuilder.Options();
                        o2.doubles = dbl;
                        o2.nTeams = n;
                        o2.minDiff = var ? 2 : 1;
                        o2.bag = bag;
                        o2.mode = 1;
                        adv = TeamBuilder.build(d, box, o2);
                        String when = f.modified > 0 ? " · modificado " + DateUtils.getRelativeTimeSpanString(
                                f.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) : "";
                        msg = "✔ " + f.name + kind + " · " + box.size() + " Pokémon" + when + "\n"
                                + (saveBag != null ? "🎒 Mochila leída del guardado · " + bagCount + " objetos"
                                : bagFile != null ? "🎒 " + bagFile.name + " · " + bagCount + " objetos"
                                : "🎒 Sin mochila.txt: solo uso los objetos que llevan tus Pokémon");
                        writeResults(tree, "######## COMBATE FUERTE ########\n\n" + formatAll(strong)
                                + "\n######## AVENTURA ########\n\n" + formatAll(adv));
                    }
                }
            } catch (SecurityException se) {
                msg = "Sin permiso para la carpeta. Vuelve a elegirla.";
            } catch (Throwable t) {
                msg = "Error: " + t;
            }
            final String fmsg = msg;
            final List<TeamBuilder.TeamResult> fs = strong, fa = adv;
            final List<String> fw = warnings;
            runOnUiThread(() -> {
                running = false;
                progress.setVisibility(View.GONE);
                status.setText(fmsg);
                strongTeams = fs;
                advTeams = fa;
                lastWarnings = fw;
                showTeams();
            });
        }).start();
    }

    // ------------------------------------------------------------------ resultados
    private static String rating(double s) {
        if (s >= 85) return "Excelente";
        if (s >= 75) return "Muy bueno";
        if (s >= 65) return "Bueno";
        if (s >= 55) return "Aceptable";
        return "Flojo";
    }

    private String formatAll(List<TeamBuilder.TeamResult> teams) {
        StringBuilder b = new StringBuilder();
        int i = 1;
        for (TeamBuilder.TeamResult t : teams)
            b.append("=== Equipo #").append(i++).append(" · ").append(String.format("%.1f", t.score))
                    .append(" (").append(rating(t.score)).append(") ===\n\n").append(t.showdownText()).append("\n");
        return b.toString();
    }

    private void showTeams() {
        results.removeAllViews();
        if (!lastWarnings.isEmpty()) {
            StringBuilder w = new StringBuilder("Avisos al leer el main:");
            for (int i = 0; i < Math.min(6, lastWarnings.size()); i++) w.append("\n• ").append(lastWarnings.get(i));
            if (lastWarnings.size() > 6) w.append("\n• … y ").append(lastWarnings.size() - 6).append(" más");
            TextView tv = text(w.toString(), 12, BAD, false);
            tv.setPadding(dp(4), 0, 0, dp(10));
            results.addView(tv);
        }
        List<TeamBuilder.TeamResult> teams = tab == 0 ? strongTeams : advTeams;
        if (teams == null) return;
        int rank = 1;
        for (TeamBuilder.TeamResult t : teams) results.addView(teamCard(rank++, t));
    }

    private ImageView spriteView(PokeSet p, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int c = Ui.TYPE_COLORS[p.species.types[0]];
        iv.setBackground(round((c & 0x00FFFFFF) | 0x55000000, sizeDp));
        Bitmap b = sprites.pokemon(p.species);
        if (b != null) iv.setImageBitmap(b);
        iv.setPadding(dp(2), dp(2), dp(2), dp(2));
        return iv;
    }

    private View teamCard(int rank, TeamBuilder.TeamResult t) {
        LinearLayout card = card(CARD);
        card.setLayoutParams(lpMatch(0, 12));

        // cabecera: anillo + título
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new Ui.ScoreRing(this, t.score), new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        String medal = rank == 1 ? "🥇 " : rank == 2 ? "🥈 " : rank == 3 ? "🥉 " : "";
        ht.addView(text(medal + "Equipo #" + rank, 17, TXT, true));
        ht.addView(text(rating(t.score), 13, Ui.scoreColor(t.score), true));
        ht.addView(text(String.format("Def %.0f · Cob %.0f · Roles %.0f · Sin %.0f", t.sDef, t.sCov, t.sRoles, t.sSyn),
                11, MUTED, false));
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView arrow = text(rank == 1 ? "▲" : "▼", 14, MUTED, false);
        head.addView(arrow);
        card.addView(head);

        // tira de sprites
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, dp(12), 0, 0);
        for (TeamBuilder.MemberInfo m : t.info) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(spriteView(m.poke, 30), new LinearLayout.LayoutParams(dp(50), dp(50)));
            TextView nm = text(m.poke.species.name.replace("-Mega", " M."), 9, MUTED, false);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            nm.setGravity(Gravity.CENTER);
            cell.addView(nm, new LinearLayout.LayoutParams(-1, -2));
            strip.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(strip);

        final LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(0, dp(10), 0, 0);
        for (TeamBuilder.MemberInfo m : t.info) details.addView(memberRow(m));
        fillAnalysis(details, t);
        details.setVisibility(rank == 1 ? View.VISIBLE : View.GONE);
        card.addView(details);

        View.OnClickListener toggle = v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "▲" : "▼");
        };
        head.setOnClickListener(toggle);
        strip.setOnClickListener(toggle);
        return card;
    }

    private View memberRow(TeamBuilder.MemberInfo m) {
        PokeSet p = m.poke;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(round(CARD2, 14));
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setLayoutParams(lpMatch(0, 8));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(spriteView(p, 34), new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView lv = text("Nv. " + p.level, 11, MUTED, true);
        lv.setGravity(Gravity.CENTER);
        left.addView(lv);
        box.addView(left, new LinearLayout.LayoutParams(dp(68), -2));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(8), 0, 0, 0);
        right.addView(text(p.species.name, 16, TXT, true));

        LinearLayout types = new LinearLayout(this);
        types.setOrientation(LinearLayout.HORIZONTAL);
        types.setPadding(0, dp(3), 0, dp(3));
        for (int ty : p.species.types) {
            TextView c = chip(Dex.TYPES_ES[ty], Ui.TYPE_COLORS[ty], 0xFF101218, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(4);
            types.addView(c, lp);
        }
        right.addView(types);
        right.addView(text(m.role + " · " + Es.ability(p.ability) + " · " + Es.nature(p.nature), 11, MUTED, false));
        int bst = 0;
        for (int b : p.species.base) bst += b;
        TextView bt = text("Estadísticas base · total " + bst, 10, MUTED, true);
        bt.setPadding(0, dp(6), 0, 0);
        right.addView(bt);
        right.addView(new Ui.StatBars(this, p.species.base), new LinearLayout.LayoutParams(-1, -2));

        // objeto recomendado
        LinearLayout itemRow = new LinearLayout(this);
        itemRow.setOrientation(LinearLayout.HORIZONTAL);
        itemRow.setGravity(Gravity.CENTER_VERTICAL);
        itemRow.setPadding(0, dp(6), 0, 0);
        if (!m.recItem.isEmpty()) {
            ImageView ii = new ImageView(this);
            Bitmap ib = sprites.item(m.recEn);
            if (ib != null) ii.setImageBitmap(ib);
            itemRow.addView(ii, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView in = text(m.recItem + (m.recChanged ? "  ← cambio" : ""), 13, TXT, true);
            in.setPadding(dp(6), 0, 0, 0);
            itemRow.addView(in, new LinearLayout.LayoutParams(0, -2, 1f));
            right.addView(itemRow);
            right.addView(text("Por qué: " + m.recWhy, 11, MUTED, false));
            right.addView(text("Origen: " + m.recSource, 11, m.recChanged ? BAD : MUTED, false));
        } else {
            right.addView(text("🎒 Sin objeto: " + m.recWhy, 11, MUTED, false));
        }

        // movimientos
        Ui.Flow flow = new Ui.Flow(this, dp(4));
        for (int i = 0; i < p.moveIds.size(); i++)
            flow.addView(chip(Es.move(p.moveIds.get(i), p.moveNames.get(i), p.hiddenPowerType), CHIP, TXT, 10));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = dp(6);
        right.addView(flow, fl);
        box.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        return box;
    }

    private void fillAnalysis(LinearLayout d, TeamBuilder.TeamResult t) {
        TextView h = text("Ataques que recibes   ▲ débiles · ▼ resisten", 12, MUTED, true);
        h.setPadding(0, dp(6), 0, dp(4));
        d.addView(h);
        for (int rowI = 0; rowI < 3; rowI++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 6; c++) {
                int ty = rowI * 6 + c;
                int net = t.weak[ty] - t.resist[ty];
                int bg = net >= 2 ? 0xFF7A2E2E : (net == 1 ? 0xFF5A4A24 : (net <= -1 ? 0xFF24523A : CHIP));
                TextView cell = text(Dex.TYPES_ES[ty] + "\n▲" + t.weak[ty] + " ▼" + t.resist[ty], 10, TXT, false);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(round(bg, 8));
                cell.setPadding(0, dp(4), 0, dp(4));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(cell, lp);
            }
            d.addView(row);
        }
        for (String s : t.good) d.addView(line("✔ " + s, GOOD));
        for (String s : t.bad) d.addView(line("⚠ " + s, BAD));
        for (TeamBuilder.MemberInfo m : t.info)
            for (String n : m.notes) d.addView(line("✎ " + m.poke.species.name + ": " + n, MUTED));

        Button copy = button("📋  Copiar equipo (Showdown)", CHIP);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("equipo", t.showdownText()));
            Toast.makeText(this, "Equipo copiado", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(12);
        d.addView(copy, lp);
    }

    private TextView line(String s, int color) {
        TextView t = text(s, 13, color, false);
        t.setPadding(0, dp(5), 0, 0);
        return t;
    }
}
