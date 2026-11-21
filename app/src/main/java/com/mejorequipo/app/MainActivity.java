package com.mejorequipo.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mejorequipo.engine.Dex;
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
import java.util.Map;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_FOLDER = 42;
    private static final String OUT_NAME = "equipos_generados.txt";

    private static final int BG = 0xFF121317, CARD = 0xFF1E2028, TXT = 0xFFECEFF4, MUTED = 0xFF9AA3B2,
            ACCENT = 0xFFFF4D4D, GOOD = 0xFF6EE7A0, BAD = 0xFFFFB454, CHIP = 0xFF2A2D38;

    private SharedPreferences prefs;
    private static Dex dex;

    private TextView status;
    private LinearLayout results;
    private ProgressBar progress;
    private Button btnSingles, btnDoubles, btnRefresh;
    private TextView txtN;
    private Switch swVariety;
    private volatile boolean running = false;

    private boolean doubles;
    private int nTeams;
    private boolean variety;

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        doubles = prefs.getBoolean("doubles", false);
        nTeams = prefs.getInt("nTeams", 5);
        variety = prefs.getBoolean("variety", true);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Cada vez que se abre la app se vuelve a leer el main de la carpeta.
        refresh();
    }

    // ------------------------------------------------------------------ interfaz
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
        bt.setBackground(round(bg, 10));
        bt.setPadding(dp(14), 0, dp(14), 0);
        return bt;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(36), dp(16), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        root.addView(text("Mejor Equipo", 26, TXT, true));
        TextView sub = text("Lee el main de tu carpeta y arma los mejores equipos con tus Pokémon.", 13, MUTED, false);
        sub.setPadding(0, dp(2), 0, dp(12));
        root.addView(sub);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFolder = button("📁  Elegir carpeta", ACCENT);
        btnRefresh = button("⟳  Actualizar", CHIP);
        row1.addView(btnFolder, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.leftMargin = dp(8);
        row1.addView(btnRefresh, lp);
        root.addView(row1);
        btnFolder.setOnClickListener(v -> pickFolder());
        btnRefresh.setOnClickListener(v -> refresh());

        status = text("Elige la carpeta donde tienes el archivo \"main\" (formato Showdown).", 13, MUTED, false);
        status.setPadding(0, dp(10), 0, dp(6));
        root.addView(status);

        // formato
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        btnSingles = button("Singles", CHIP);
        btnDoubles = button("Dobles (VGC)", CHIP);
        row2.addView(btnSingles, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp2.leftMargin = dp(8);
        row2.addView(btnDoubles, lp2);
        root.addView(row2);
        btnSingles.setOnClickListener(v -> { doubles = false; saveAndRefresh(); });
        btnDoubles.setOnClickListener(v -> { doubles = true; saveAndRefresh(); });

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        row3.setPadding(0, dp(8), 0, 0);
        row3.addView(text("Equipos:", 14, TXT, false));
        Button minus = button("−", CHIP), plus = button("+", CHIP);
        txtN = text(String.valueOf(nTeams), 16, TXT, true);
        txtN.setGravity(Gravity.CENTER);
        row3.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        row3.addView(txtN, new LinearLayout.LayoutParams(dp(36), -2));
        row3.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(40)));
        swVariety = new Switch(this);
        swVariety.setText("Variar equipos");
        swVariety.setTextColor(TXT);
        swVariety.setChecked(variety);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp3.leftMargin = dp(12);
        row3.addView(swVariety, lp3);
        root.addView(row3);
        minus.setOnClickListener(v -> { if (nTeams > 1) { nTeams--; saveAndRefresh(); } });
        plus.setOnClickListener(v -> { if (nTeams < 15) { nTeams++; saveAndRefresh(); } });
        swVariety.setOnCheckedChangeListener((v, c) -> { variety = c; saveAndRefresh(); });
        updateModeButtons();

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(36), dp(36));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.topMargin = dp(16);
        root.addView(progress, pl);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(12), 0, 0);
        root.addView(results);
        setContentView(sv);
    }

    private void updateModeButtons() {
        btnSingles.setBackground(round(doubles ? CHIP : ACCENT, 10));
        btnDoubles.setBackground(round(doubles ? ACCENT : CHIP, 10));
        txtN.setText(String.valueOf(nTeams));
    }

    private void saveAndRefresh() {
        prefs.edit().putBoolean("doubles", doubles).putInt("nTeams", nTeams).putBoolean("variety", variety).apply();
        updateModeButtons();
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

    private static Gen6Save.Names names;

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

    // ------------------------------------------------------------------ cálculo
    private synchronized Dex loadDex() throws Exception {
        if (dex == null) {
            String p = new String(readStream(getAssets().open("pokedex.tsv")), StandardCharsets.UTF_8);
            String m = new String(readStream(getAssets().open("moves.tsv")), StandardCharsets.UTF_8);
            dex = Dex.load(p, m);
        }
        return dex;
    }

    private static byte[] readStream(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private void refresh() {
        if (running || status == null) return;
        String treeStr = prefs.getString("tree", null);
        if (treeStr == null) {
            status.setText("Elige la carpeta donde tienes el archivo \"main\" (formato Showdown).");
            return;
        }
        running = true;
        progress.setVisibility(View.VISIBLE);
        results.removeAllViews();
        status.setText("Leyendo main y calculando equipos…");
        final boolean dbl = doubles;
        final int n = nTeams;
        final boolean var = variety;
        new Thread(() -> {
            String msg;
            List<TeamBuilder.TeamResult> teams = null;
            List<String> warnings = new ArrayList<>();
            int count = 0;
            try {
                Uri tree = Uri.parse(treeStr);
                List<String> seen = new ArrayList<>();
                FoundFile f = findMain(tree, seen);
                if (f == null) {
                    msg = "No encuentro el archivo \"main\" en la carpeta" + (seen.isEmpty() ? " (está vacía)."
                            : ". Archivos: " + seen) + "\nDebe llamarse main.txt (o main).";
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
                        kind = " (guardado de Rubí Omega/Zafiro Alfa: " + gr.party + " en equipo, " + gr.boxed + " en cajas)";
                    } else {
                        text = new String(raw, StandardCharsets.UTF_8);
                    }
                    Dex d = loadDex();
                    List<PokeSet> box = ShowdownParser.parse(d, text, warnings);
                    count = box.size();
                    if (box.isEmpty()) {
                        boolean binary = false;
                        for (int q = 0; q < Math.min(raw.length, 2000); q++) if (raw[q] == 0) { binary = true; break; }
                        msg = binary ? f.name + " es un guardado binario de " + raw.length + " bytes que no reconozco. "
                                + "Solo leo guardados de Rubí Omega/Zafiro Alfa (483328 bytes) o texto en formato Showdown."
                                : f.name + " no contiene Pokémon reconocibles (formato Showdown).";
                        warnings.clear();
                    } else {
                        TeamBuilder.Options o = new TeamBuilder.Options();
                        o.doubles = dbl;
                        o.nTeams = n;
                        o.minDiff = var ? 2 : 1;
                        int bagCount = 0;
                        if (saveBag != null) {
                            o.bag = saveBag;
                        } else if (bagFile != null) {
                            o.bag = ItemAdvisor.parseBag(readAll(bagFile.uri));
                        }
                        for (int q : o.bag.values()) bagCount += q;
                        teams = TeamBuilder.build(d, box, o);
                        String when = f.modified > 0 ? " · modificado " + DateUtils.getRelativeTimeSpanString(
                                f.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) : "";
                        msg = "✔ " + f.name + kind + " · " + count + " Pokémon" + when + "\n"
                                + (saveBag != null ? "🎒 Mochila leída del guardado · " + bagCount + " objetos"
                                : bagFile != null ? "🎒 " + bagFile.name + " · " + bagCount + " objetos"
                                : "🎒 Sin mochila.txt: solo uso los objetos que llevan tus Pokémon");
                        writeResults(tree, formatAll(teams));
                    }
                }
            } catch (SecurityException se) {
                msg = "Sin permiso para la carpeta. Vuelve a elegirla.";
            } catch (Throwable t) {
                msg = "Error: " + t;
            }
            final String fmsg = msg;
            final List<TeamBuilder.TeamResult> fteams = teams;
            final List<String> fwarn = warnings;
            runOnUiThread(() -> {
                running = false;
                progress.setVisibility(View.GONE);
                status.setText(fmsg);
                showTeams(fteams, fwarn);
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
        for (TeamBuilder.TeamResult t : teams) {
            b.append("=== Equipo #").append(i++).append(" · ").append(String.format("%.1f", t.score))
                    .append(" (").append(rating(t.score)).append(") ===\n\n").append(t.showdownText()).append("\n");
        }
        return b.toString();
    }

    private void showTeams(List<TeamBuilder.TeamResult> teams, List<String> warnings) {
        results.removeAllViews();
        if (!warnings.isEmpty()) {
            StringBuilder w = new StringBuilder("Avisos al leer el main:");
            for (int i = 0; i < Math.min(8, warnings.size()); i++) w.append("\n• ").append(warnings.get(i));
            if (warnings.size() > 8) w.append("\n• … y ").append(warnings.size() - 8).append(" más");
            TextView tv = text(w.toString(), 12, BAD, false);
            tv.setPadding(0, 0, 0, dp(10));
            results.addView(tv);
        }
        if (teams == null) return;
        int rank = 1;
        for (TeamBuilder.TeamResult t : teams) results.addView(teamCard(rank++, t));
    }

    private View teamCard(int rank, TeamBuilder.TeamResult t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 16));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(12);
        card.setLayoutParams(cp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView r = text("#" + rank, 22, rank == 1 ? ACCENT : TXT, true);
        head.addView(r);
        TextView sc = text("  " + String.format("%.1f", t.score) + " · " + rating(t.score), 15, TXT, true);
        head.addView(sc, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text(rank == 1 ? "▲" : "▼", 14, MUTED, false);
        head.addView(arrow);
        card.addView(head);

        for (TeamBuilder.MemberInfo m : t.info) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            PokeSet p = m.poke;
            StringBuilder types = new StringBuilder();
            for (int ty : p.species.types) types.append(types.length() > 0 ? "/" : "").append(Dex.TYPES_ES[ty]);
            row.addView(text(p.species.name + "  ·  " + types, 15, TXT, true));
            StringBuilder sub = new StringBuilder();
            if (!p.item.isEmpty()) sub.append(p.item).append(" · ");
            sub.append(p.ability).append(" · ").append(p.nature);
            row.addView(text(sub.toString(), 12, MUTED, false));
            StringBuilder mv = new StringBuilder();
            for (String s : p.moveNames) mv.append(mv.length() > 0 ? " / " : "").append(s);
            row.addView(text(mv.toString(), 12, MUTED, false));
            row.addView(text(m.role, 12, GOOD, false));
            if (!m.recItem.isEmpty()) {
                TextView it = text("🎒 " + m.recItem + (m.recChanged ? "  ← cambio" : ""), 13, TXT, true);
                it.setPadding(0, dp(4), 0, 0);
                row.addView(it);
                row.addView(text("Por qué: " + m.recWhy, 12, MUTED, false));
                row.addView(text("Origen: " + m.recSource, 12, m.recChanged ? BAD : MUTED, false));
            } else {
                TextView it = text("🎒 Sin objeto: " + m.recWhy, 12, MUTED, false);
                it.setPadding(0, dp(4), 0, 0);
                row.addView(it);
            }
            card.addView(row);
        }

        final LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(0, dp(12), 0, 0);
        fillDetails(details, t);
        details.setVisibility(rank == 1 ? View.VISIBLE : View.GONE);
        card.addView(details);

        head.setOnClickListener(v -> {
            boolean show = details.getVisibility() != View.VISIBLE;
            details.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "▲" : "▼");
        });
        return card;
    }

    private void fillDetails(LinearLayout d, TeamBuilder.TeamResult t) {
        d.addView(text(String.format("Miembros %.0f · Defensa %.0f · Cobertura %.0f · Roles %.0f · Sinergia %.0f",
                t.sMembers, t.sDef, t.sCov, t.sRoles, t.sSyn), 12, MUTED, false));

        // tabla de tipos
        TextView h = text("Tipos de ataque que recibes (▲ débiles · ▼ resisten)", 12, MUTED, false);
        h.setPadding(0, dp(10), 0, dp(4));
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
                cell.setBackground(round(bg, 6));
                cell.setPadding(0, dp(3), 0, dp(3));
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

        Button copy = button("Copiar equipo (Showdown)", CHIP);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("equipo", t.showdownText()));
            Toast.makeText(this, "Equipo copiado", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(10);
        d.addView(copy, lp);
    }

    private TextView line(String s, int color) {
        TextView t = text(s, 13, color, false);
        t.setPadding(0, dp(5), 0, 0);
        return t;
    }
}
