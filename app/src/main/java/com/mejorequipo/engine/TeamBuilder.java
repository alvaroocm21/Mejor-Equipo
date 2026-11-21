package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.mejorequipo.engine.Profile.*;

/** Busca y puntúa los mejores equipos de 6 a partir de los Pokémon del main. */
public final class TeamBuilder {

    public static final class Options {
        public boolean doubles = false;
        public int nTeams = 5;
        /** Mínimo de Pokémon distintos entre dos equipos de la lista (1 = ranking puro). */
        public int minDiff = 2;
        /** 0 = combate fuerte (vale gastar objetos de un solo uso), 1 = aventura (solo objetos que no se gastan). */
        public int mode = 0;
        /** Mochila: id canónico de objeto -> cantidad (se suma a los que llevan los Pokémon del main). */
        public Map<String, Integer> bag = new HashMap<>();
    }

    public static final class MemberInfo {
        public PokeSet poke;
        public String role;
        public List<String> notes;
        /** Objeto recomendado (vacío = ninguno útil disponible). */
        public String recItem = "", recEn = "", recWhy = "", recSource = "";
        public boolean recChanged;

        /** Set en formato Showdown con el objeto recomendado. */
        public String showdown() {
            if (recEn.isEmpty()) return poke.raw;
            String[] lines = poke.raw.split("\n", 2);
            String first = lines[0];
            int at = first.indexOf(" @ ");
            if (at >= 0) first = first.substring(0, at);
            return first + " @ " + recEn + (lines.length > 1 ? "\n" + lines[1] : "");
        }
    }

    public static final class TeamResult {
        public int[] members;
        public List<MemberInfo> info = new ArrayList<>();
        public double score, sMembers, sDef, sCov, sRoles, sSyn;
        public final List<String> good = new ArrayList<>(), bad = new ArrayList<>();
        public final int[] weak = new int[18], resist = new int[18];

        public String showdownText() {
            StringBuilder b = new StringBuilder();
            for (MemberInfo m : info) b.append(m.showdown()).append("\n\n");
            return b.toString().trim() + "\n";
        }
    }

    // ===================================================================
    private final Dex dex;
    private final List<PokeSet> box;
    private final Profile[] pr;
    private final int n;
    private final boolean D;
    private final int combos;
    private Options opt;

    private TeamBuilder(Dex dex, List<PokeSet> box, boolean doubles) {
        this.dex = dex;
        this.box = box;
        this.D = doubles;
        this.n = box.size();
        this.combos = dex.typeCombos.size();
        pr = new Profile[n];
        for (int i = 0; i < n; i++) pr[i] = Profile.build(dex, box.get(i), doubles);
    }

    public static List<TeamResult> build(Dex dex, List<PokeSet> box, Options o) {
        List<PokeSet> use = box.size() > 400 ? new ArrayList<>(box.subList(0, 400)) : box;
        if (use.isEmpty()) return new ArrayList<>();
        return new TeamBuilder(dex, use, o.doubles).run(o);
    }

    // ---------------------------------------------------------------- búsqueda
    private List<TeamResult> run(Options o) {
        this.opt = o;
        int k = Math.min(6, n);
        boolean[][] conflict = new boolean[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) conflict[i][j] = i != j && baseName(pr[i].sp.name).equals(baseName(pr[j].sp.name));

        Map<Long, Double> pool = new HashMap<>();
        if (comb(n, k) <= 60000) {
            int[] idx = new int[k];
            exhaustive(idx, 0, 0, conflict, pool);
        } else {
            beam(k, conflict, pool);
            // Mejora local desde los mejores + arranques aleatorios
            List<long[]> keys = topKeys(pool, 40);
            List<int[]> seeds = new ArrayList<>();
            for (long[] kk : keys) seeds.add(decode(kk[0], k));
            Random rnd = new Random(42);
            for (int r = 0; r < 25; r++) seeds.add(randomTeam(k, conflict, rnd));
            for (int[] s : seeds) climb(s, k, conflict, pool);
        }

        // Selección con diversidad
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(pool.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<int[]> chosen = new ArrayList<>();
        List<Double> chosenScores = new ArrayList<>();
        for (int diff = Math.max(1, o.minDiff); diff >= 1 && chosen.size() < o.nTeams; diff--) {
            for (Map.Entry<Long, Double> e : sorted) {
                if (chosen.size() >= o.nTeams) break;
                int[] t = decode(e.getKey(), k);
                boolean already = false;
                for (int[] c : chosen) if (Arrays.equals(c, t)) already = true;
                if (already) continue;
                boolean ok = true;
                for (int[] c : chosen) if (k - shared(c, t) < Math.min(diff, k)) { ok = false; break; }
                if (ok) { chosen.add(t); chosenScores.add(e.getValue()); }
            }
        }
        // Ordenar por puntuación (por si el relleno rompió el orden)
        Integer[] ord = new Integer[chosen.size()];
        for (int i = 0; i < ord.length; i++) ord[i] = i;
        final List<Double> cs = chosenScores;
        Arrays.sort(ord, (a, b) -> Double.compare(cs.get(b), cs.get(a)));
        List<TeamResult> out = new ArrayList<>();
        for (Integer i : ord) out.add(explain(chosen.get(i)));
        return out;
    }

    private void exhaustive(int[] idx, int pos, int start, boolean[][] conflict, Map<Long, Double> pool) {
        int k = idx.length;
        if (pos == k) {
            pool.put(key(idx, k), eval(idx, k));
            return;
        }
        for (int i = start; i < n; i++) {
            boolean bad = false;
            for (int j = 0; j < pos; j++) if (conflict[idx[j]][i]) { bad = true; break; }
            if (bad) continue;
            idx[pos] = i;
            exhaustive(idx, pos + 1, i + 1, conflict, pool);
        }
    }

    private void beam(int k, boolean[][] conflict, Map<Long, Double> pool) {
        int width = Math.max(40, Math.min(200, 20000 / Math.max(1, n)));
        Map<Long, Double> cur = new HashMap<>();
        for (int i = 0; i < n; i++) cur.put(key(new int[]{i}, 1), eval(new int[]{i}, 1));
        cur = top(cur, width);
        for (int size = 2; size <= k; size++) {
            Map<Long, Double> next = new HashMap<>();
            for (Long key : cur.keySet()) {
                int[] s = decode(key, size - 1);
                for (int c = 0; c < n; c++) {
                    boolean bad = false;
                    for (int x : s) if (x == c || conflict[x][c]) { bad = true; break; }
                    if (bad) continue;
                    int[] t = Arrays.copyOf(s, size);
                    t[size - 1] = c;
                    long kk = key(t, size);
                    if (!next.containsKey(kk)) next.put(kk, eval(t, size));
                }
            }
            cur = top(next, size == k ? width * 3 : width);
        }
        pool.putAll(cur);
    }

    private void climb(int[] start, int k, boolean[][] conflict, Map<Long, Double> pool) {
        int[] t = start.clone();
        double cur = eval(t, k);
        pool.put(key(t, k), cur);
        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 20) {
            improved = false;
            for (int pos = 0; pos < k; pos++) {
                for (int c = 0; c < n; c++) {
                    boolean bad = false;
                    for (int j = 0; j < k; j++) if (j != pos && (t[j] == c || conflict[t[j]][c])) { bad = true; break; }
                    if (bad || t[pos] == c) continue;
                    int old = t[pos];
                    t[pos] = c;
                    long kk = key(t, k);
                    Double known = pool.get(kk);
                    double sc = known != null ? known : eval(t, k);
                    if (known == null) pool.put(kk, sc);
                    if (sc > cur + 1e-9) { cur = sc; improved = true; } else t[pos] = old;
                }
            }
        }
    }

    private int[] randomTeam(int k, boolean[][] conflict, Random rnd) {
        int[] t = new int[k];
        int filled = 0, tries = 0;
        while (filled < k && tries++ < 5000) {
            int c = rnd.nextInt(n);
            boolean ok = true;
            for (int j = 0; j < filled; j++) if (t[j] == c || conflict[t[j]][c]) ok = false;
            if (ok) t[filled++] = c;
        }
        for (int q = 0; filled < k && q < n; q++) { // relleno si la caja casi no tiene opciones válidas
            boolean in = false;
            for (int j = 0; j < filled; j++) if (t[j] == q) in = true;
            if (!in) t[filled++] = q;
        }
        return t;
    }

    private static Map<Long, Double> top(Map<Long, Double> m, int w) {
        if (m.size() <= w) return m;
        List<Map.Entry<Long, Double>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<Long, Double> r = new HashMap<>();
        for (int i = 0; i < w; i++) r.put(l.get(i).getKey(), l.get(i).getValue());
        return r;
    }

    private static List<long[]> topKeys(Map<Long, Double> m, int w) {
        List<Map.Entry<Long, Double>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<long[]> r = new ArrayList<>();
        for (int i = 0; i < Math.min(w, l.size()); i++) r.add(new long[]{l.get(i).getKey()});
        return r;
    }

    private static long key(int[] t, int len) {
        int[] s = Arrays.copyOf(t, len);
        Arrays.sort(s);
        long k = 0;
        for (int x : s) k = (k << 9) | (x + 1);
        return k;
    }

    private static int[] decode(long key, int len) {
        int[] r = new int[len];
        for (int i = len - 1; i >= 0; i--) {
            r[i] = (int) (key & 511) - 1;
            key >>= 9;
        }
        return r;
    }

    private static int shared(int[] a, int[] b) {
        int c = 0;
        for (int x : a) for (int y : b) if (x == y) c++;
        return c;
    }

    private static long comb(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return (long) r;
    }

    private static String baseName(String name) {
        String l = name.toLowerCase();
        String[] keep = {"ho-oh", "porygon-z", "jangmo-o", "hakamo-o", "kommo-o", "wo-chien", "chien-pao", "ting-lu", "chi-yu"};
        for (String k : keep) if (l.startsWith(k)) return k;
        int i = name.indexOf('-');
        return i > 0 ? name.substring(0, i) : name;
    }

    // ---------------------------------------------------------------- evaluación
    private int cnt(int[] idx, int len, long bits) {
        int c = 0;
        for (int i = 0; i < len; i++) if ((pr[idx[i]].roles & bits) != 0) c++;
        return c;
    }

    /** Miembros que aprovechan el clima/terreno sin ser ellos mismos quien lo pone. */
    private int cntAbusers(int[] idx, int len, long setter, long abuser) {
        int c = 0;
        for (int i = 0; i < len; i++) {
            long r = pr[idx[i]].roles;
            if ((r & abuser) != 0 && (r & setter) == 0) c++;
        }
        return c;
    }

    double eval(int[] idx, int len) {
        // ---- jugadores individuales
        double ms = 0;
        for (int i = 0; i < len; i++) ms += pr[idx[i]].score;
        double sMem = ms / len * 30 * (opt.mode == 1 ? 1.25 : 1.0);

        // ---- defensa
        double pen = 0, bonus = 0;
        for (int t = 0; t < 18; t++) {
            int w = 0, r = 0, im = 0, quad = 0;
            for (int i = 0; i < len; i++) {
                float m = pr[idx[i]].def[t];
                if (m > 1) { w++; if (m >= 4) quad++; }
                else if (m < 1) { r++; if (m == 0) im++; }
            }
            int net = w - r;
            if (net >= 2) pen += 1.5 + 0.75 * (net - 2);
            else if (net == 1) pen += 0.5;
            if (w >= 1 && r == 0) pen += 0.7;
            pen += 0.4 * quad;
            if (r >= 2) bonus += 0.1;
            if (im >= 1) bonus += 0.1;
        }
        double sDef = 25 * Math.max(0, 1 - pen / 14) + Math.min(2, bonus);

        // ---- cobertura ofensiva
        int se = 0, walled = 0;
        for (int c = 0; c < combos; c++) {
            float best = 0;
            for (int i = 0; i < len; i++) best = Math.max(best, pr[idx[i]].cov[c]);
            if (best >= 2) se++;
            else if (best <= 0.5f) walled++;
        }
        double fr = se / (double) combos;
        double sCov = 25 * (0.75 * fr * fr + 0.25 * (1 - walled / (double) combos));

        // ---- roles
        double r = roles(idx, len) * (opt.mode == 1 ? 0.7 : 1.0);

        // ---- sinergia
        double sSyn = synergy(idx, len);

        return sMem + sDef + sCov + r + sSyn;
    }

    private double roles(int[] idx, int len) {
        double r = 0;
        int speedCtl = cnt(idx, len, SPEEDCTL | TAILWIND | TRICKROOM);
        double sc = speedCtl + 0.5 * cnt(idx, len, SCARF);
        if (sc >= 1) r += 4;
        if (sc >= 2) r += 1;
        int haz = cnt(idx, len, HAZARD), rem = cnt(idx, len, REMOVAL), rec = cnt(idx, len, RECOVERY);
        int piv = cnt(idx, len, PIVOT), intim = cnt(idx, len, INTIM), fo = cnt(idx, len, FAKEOUT);
        int redir = cnt(idx, len, REDIR), setup = cnt(idx, len, SETUP), prot = cnt(idx, len, PROTECT);
        if (D) {
            if (haz >= 1) r += 0.5;
            if (rem >= 1) r += 0.5;
            if (rec >= 1) r += 0.5;
            if (piv >= 1) r += 2;
            if (intim >= 1) r += 3;
            if (intim >= 2) r += 1;
            if (fo >= 1) r += 3;
            if (fo >= 2) r += 1;
            if (redir >= 1) r += 2;
            if (setup >= 1) r += 1;
            if (prot >= 4) r += 2; else if (prot >= 2) r += 1; else r -= 1;
            if (cnt(idx, len, SPREAD) >= 1) r += 1.5;
        } else {
            if (haz >= 1) r += 3;
            if (rem >= 1) r += 3;
            r += Math.min(2, rec) * 1.5;
            if (piv >= 1) r += 3;
            if (piv >= 2) r += 1;
            if (intim >= 1) r += 1.5;
            if (setup >= 1) r += 2.5;
            if (setup >= 2) r += 1;
            if (cnt(idx, len, PHAZE) >= 1) r += 1;
            if (cnt(idx, len, CLERIC) >= 1) r += 1;
        }
        if (opt.mode == 1) r += Math.min(2, rec) * 1.2 + (intim >= 1 ? 1 : 0); // aventura: aguantar muchos combates seguidos
        if (cnt(idx, len, SCREENS) >= 1) r += 1.5;
        if (cnt(idx, len, PRIORITY) >= 1) r += D ? 1.5 : 2;
        if (cnt(idx, len, STATUS) >= 1) r += 1.5;
        if (cnt(idx, len, DISRUPT) >= 1) r += 1;

        // clima y terrenos
        long or = 0;
        for (int i = 0; i < len; i++) or |= pr[idx[i]].roles;
        if (Long.bitCount(or & WEATHER_MASK) > 1) r -= 3;
        r += weatherSyn(idx, len, SUN, A_SUN);
        r += weatherSyn(idx, len, RAIN, A_RAIN);
        r += weatherSyn(idx, len, SAND, A_SAND);
        r += weatherSyn(idx, len, SNOW, A_SNOW);
        r += weatherSyn(idx, len, ELEC_T, A_ELEC);
        if ((or & (GRASS_T | MISTY_T | PSY_T)) != 0) r += 0.5;

        // Trick Room / Tailwind
        int tr = cnt(idx, len, TRICKROOM), slow = cnt(idx, len, SLOW), fast = cnt(idx, len, FAST);
        if (tr > 0) {
            if (slow >= 2) r += 5; else if (slow == 1) r += 2; else r -= 2;
        } else if (slow >= 2) {
            r -= Math.min(4, 1.5 * (slow - 1));
        }
        if (cnt(idx, len, TAILWIND) > 0 && fast >= 2) r += 2;

        // equilibrio físico/especial
        int phys = 0, spec = 0;
        double top = 0;
        for (int i = 0; i < len; i++) {
            Profile p = pr[idx[i]];
            if (p.off >= 0.4) { if (p.physAttacker) phys++; if (p.specAttacker) spec++; }
            top = Math.max(top, p.off);
        }
        if (Math.min(phys, spec) == 0 && phys + spec >= 3) r -= 2.5;
        if (phys >= 1 && spec >= 1) r += 2;
        if (top >= 0.65) r += 1.5;
        return Math.min(28, r);
    }

    private double weatherSyn(int[] idx, int len, long setter, long abuser) {
        int s = cnt(idx, len, setter), a = cntAbusers(idx, len, setter, abuser);
        if (s > 0) return a > 0 ? 4 + (a >= 2 ? 1 : 0) : 1;
        return a > 0 ? -1 : 0;
    }

    private double synergy(int[] idx, int len) {
        // complemento defensivo: cada debilidad de un miembro cubierta por otro que resista
        int covered = 0, total = 0;
        for (int i = 0; i < len; i++) {
            Profile a = pr[idx[i]];
            for (int t = 0; t < 18; t++) {
                if (a.def[t] > 1) {
                    total++;
                    for (int j = 0; j < len; j++) if (j != i && pr[idx[j]].def[t] < 1) { covered++; break; }
                }
            }
        }
        double frac = total == 0 ? 1 : covered / (double) total;
        double s = 4 + 8 * frac;
        // tipos repetidos
        int[] tc = new int[18];
        for (int i = 0; i < len; i++) for (int t : pr[idx[i]].sp.types) tc[t]++;
        for (int t = 0; t < 18; t++) {
            if (tc[t] == 2) s -= 1;
            else if (tc[t] > 2) s -= 1 + 3 * (tc[t] - 2);
        }
        if (tc[1] > 0 && tc[2] > 0 && tc[4] > 0) s += 1.5;      // núcleo Fuego-Agua-Planta
        if (tc[14] > 0 && tc[16] > 0 && tc[17] > 0) s += 1;     // Dragón-Acero-Hada
        int megas = 0;
        for (int i = 0; i < len; i++) if (!pr[idx[i]].sp.requiredItem.isEmpty()) megas++;
        if (megas > 1) s -= 6 * (megas - 1); // solo una Mega por combate
        int choice = cnt(idx, len, CHOICE);
        if (choice > 3) s -= 2 * (choice - 3);
        return s;
    }

    // ---------------------------------------------------------------- explicación
    private TeamResult explain(int[] team) {
        int k = team.length;
        Integer[] ord = new Integer[k];
        for (int i = 0; i < k; i++) ord[i] = i;
        Arrays.sort(ord, (a, b) -> Double.compare(pr[team[b]].score, pr[team[a]].score));
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = team[ord[i]];

        TeamResult r = new TeamResult();
        r.members = idx;
        r.score = Math.min(100, eval(idx, k) / 1.25);
        double ms = 0;
        for (int x : idx) ms += pr[x].score;
        r.sMembers = ms / k * 30 * (opt.mode == 1 ? 1.25 : 1.0);
        for (int i = 0; i < k; i++) {
            MemberInfo mi = new MemberInfo();
            mi.poke = pr[idx[i]].p;
            mi.role = pr[idx[i]].roleLabel;
            mi.notes = pr[idx[i]].notes;
            r.info.add(mi);
        }
        // defensas
        for (int t = 0; t < 18; t++)
            for (int x : idx) {
                float m = pr[x].def[t];
                if (m > 1) r.weak[t]++; else if (m < 1) r.resist[t]++;
            }
        List<String> weakTxt = new ArrayList<>();
        for (int t = 0; t < 18; t++) {
            if (r.weak[t] - r.resist[t] >= 2 || (r.weak[t] >= 1 && r.resist[t] == 0))
                weakTxt.add(Dex.TYPES_ES[t] + " (" + r.weak[t] + " débiles, " + r.resist[t] + " resisten)");
        }
        if (weakTxt.isEmpty()) r.good.add("Defensa muy equilibrada: ningún tipo te castiga sin respuesta.");
        else r.bad.add("Debilidades del equipo: " + join(weakTxt) + ".");
        List<String> solid = new ArrayList<>();
        for (int t = 0; t < 18; t++) if (r.resist[t] >= 3 && r.weak[t] == 0) solid.add(Dex.TYPES_ES[t]);
        if (!solid.isEmpty()) r.good.add("Muy sólido contra: " + join(solid) + ".");

        // cobertura
        List<String> noSE = new ArrayList<>();
        for (int t = 0; t < 18; t++) {
            int ci = comboIndex(new int[]{t});
            float best = 0;
            if (ci >= 0) for (int x : idx) best = Math.max(best, pr[x].cov[ci]);
            else for (int x : idx) best = Math.max(best, singleBest(pr[x], t));
            if (best < 2) noSE.add(Dex.TYPES_ES[t]);
        }
        if (noSE.isEmpty()) r.good.add("Cobertura: tienes ataque súper eficaz contra los 18 tipos.");
        else if (noSE.size() <= 3) r.good.add("Buena cobertura; sin súper eficaz contra: " + join(noSE) + ".");
        else r.bad.add("Cobertura corta: sin súper eficaz contra " + join(noSE) + ".");

        // roles
        long or = 0;
        for (int x : idx) or |= pr[x].roles;
        List<String> have = new ArrayList<>(), miss = new ArrayList<>();
        role(have, miss, names(idx, TAILWIND | TRICKROOM | SPEEDCTL), "control de velocidad", true, "Control de velocidad");
        if (D) {
            role(have, miss, names(idx, INTIM), "Intimidación", true, "Intimidación");
            role(have, miss, names(idx, FAKEOUT), "Fake Out", true, "Fake Out");
            role(have, miss, names(idx, REDIR), "redirección", false, "Redirección");
        } else {
            role(have, miss, names(idx, HAZARD), "hazards", true, "Hazards");
            role(have, miss, names(idx, REMOVAL), "limpiar hazards", true, "Limpia hazards");
            role(have, miss, names(idx, RECOVERY), "recuperación", true, "Recuperación");
            role(have, miss, names(idx, PIVOT), "pivote", true, "Pivote");
            role(have, miss, names(idx, SETUP), "setup sweeper", false, "Setup");
        }
        role(have, miss, names(idx, SCREENS), "pantallas", false, "Pantallas");
        role(have, miss, names(idx, PRIORITY), "prioridad", false, "Prioridad");
        if (!have.isEmpty()) r.good.add("Roles cubiertos: " + join(have) + ".");
        if (!miss.isEmpty()) r.bad.add("Te falta: " + join(miss) + ".");
        weatherNote(r, idx, SUN, A_SUN, "Sol");
        weatherNote(r, idx, RAIN, A_RAIN, "Lluvia");
        weatherNote(r, idx, SAND, A_SAND, "Tormenta de arena");
        weatherNote(r, idx, SNOW, A_SNOW, "Nieve");
        weatherNote(r, idx, ELEC_T, A_ELEC, "Campo Eléctrico");
        if (Long.bitCount(or & WEATHER_MASK) > 1) r.bad.add("Hay varios climas que se pisan entre sí.");
        int tr = cnt(idx, k, TRICKROOM), slow = cnt(idx, k, SLOW);
        if (tr > 0 && slow >= 2) r.good.add("Trick Room con " + slow + " atacantes lentos que lo aprovechan.");
        else if (tr > 0) r.bad.add("Trick Room pero pocos atacantes lentos que lo aprovechen.");
        else if (slow >= 2) r.bad.add(slow + " atacantes lentos sin Trick Room que los apoye.");

        int phys = 0, spec = 0;
        for (int x : idx) if (pr[x].off >= 0.4) { if (pr[x].physAttacker) phys++; if (pr[x].specAttacker) spec++; }
        if (phys >= 1 && spec >= 1) r.good.add("Ataque mixto: " + phys + " físico(s) y " + spec + " especial(es).");
        else if (phys + spec >= 3) r.bad.add("Todos tus atacantes son del mismo tipo de daño (" + (phys > 0 ? "físico" : "especial") + "): fácil de contrarrestar.");

        // objetos
        recommendItems(r, idx);
        int[] tc = new int[18];
        for (int x : idx) for (int t : pr[x].sp.types) tc[t]++;
        List<String> stack = new ArrayList<>();
        for (int t = 0; t < 18; t++) if (tc[t] > 2) stack.add(Dex.TYPES_ES[t] + " ×" + tc[t]);
        if (!stack.isEmpty()) r.bad.add("Tipos repetidos: " + join(stack) + ".");
        int megaCount = 0;
        for (int x : idx) if (!pr[x].sp.requiredItem.isEmpty()) megaCount++;
        if (megaCount > 1) r.bad.add("Llevas " + megaCount + " Megas pero solo una puede megaevolucionar por combate.");
        if (k < 6) r.bad.add("Solo hay " + k + " Pokémon válidos en tu main (faltan " + (6 - k) + ").");

        // desglose
        r.sDef = defScore(idx, k);
        r.sCov = covScore(idx, k);
        r.sRoles = roles(idx, k) * (opt.mode == 1 ? 0.7 : 1.0);
        r.sSyn = synergy(idx, k);
        return r;
    }

    // ---------------------------------------------------------------- objetos
    private void recommendItems(TeamResult r, int[] idx) {
        int k = idx.length;
        // cantidades disponibles: mochila + objetos que llevan los Pokémon del main
        Map<String, Integer> total = new HashMap<>();
        Map<String, List<Integer>> holders = new HashMap<>();
        for (Map.Entry<String, Integer> e : opt.bag.entrySet()) total.put(e.getKey(), e.getValue());
        for (int i = 0; i < n; i++) {
            String it = ItemAdvisor.canon(pr[i].p.item);
            if (it.isEmpty()) continue;
            total.merge(it, 1, Integer::sum);
            if (!holders.containsKey(it)) holders.put(it, new ArrayList<>());
            holders.get(it).add(i);
        }
        List<List<ItemAdvisor.Cand>> cands = new ArrayList<>();
        List<double[]> triples = new ArrayList<>(); // {miembro, índice cand, valor}
        for (int m = 0; m < k; m++) {
            List<ItemAdvisor.Cand> c = ItemAdvisor.candidates(dex, pr[idx[m]], opt.mode == 1);
            cands.add(c);
            for (int j = 0; j < c.size(); j++)
                if (total.containsKey(c.get(j).id)) triples.add(new double[]{m, j, c.get(j).value});
        }
        Collections.sort(triples, (a, b) -> Double.compare(b[2], a[2]));
        int[] chosen = new int[k];
        Arrays.fill(chosen, -1);
        Map<String, Integer> used = new HashMap<>();
        for (double[] t : triples) {
            int m = (int) t[0];
            if (chosen[m] >= 0 || t[2] < 2.5) continue;
            String id = cands.get(m).get((int) t[1]).id;
            int u = used.containsKey(id) ? used.get(id) : 0;
            if (u >= total.get(id)) continue;
            used.put(id, u + 1);
            chosen[m] = (int) t[1];
        }
        // origen de cada objeto: lo que ya lleva > mochila > Pokémon del banquillo > compañero que lo suelta
        Set<Integer> inTeam = new HashSet<>();
        for (int x : idx) inTeam.add(x);
        Map<String, Integer> bagLeft = new HashMap<>(opt.bag);
        Set<Integer> takenFrom = new HashSet<>();
        String[] source = new String[k];
        boolean[] keeps = new boolean[k];
        for (int m = 0; m < k; m++) {
            if (chosen[m] < 0) continue;
            String id = cands.get(m).get(chosen[m]).id;
            if (id.equals(ItemAdvisor.canon(pr[idx[m]].p.item))) { keeps[m] = true; source[m] = "ya lo lleva"; }
        }
        for (int m = 0; m < k; m++) {
            if (chosen[m] < 0 || keeps[m]) continue;
            String id = cands.get(m).get(chosen[m]).id;
            int left = bagLeft.containsKey(id) ? bagLeft.get(id) : 0;
            if (left > 0) { bagLeft.put(id, left - 1); source[m] = "de la mochila"; continue; }
            String src = null;
            List<Integer> hs = holders.containsKey(id) ? holders.get(id) : new ArrayList<Integer>();
            for (int h : hs) if (!inTeam.contains(h) && !takenFrom.contains(h)) {
                takenFrom.add(h);
                src = "quítaselo a " + pr[h].sp.name + " (no va en este equipo)";
                break;
            }
            if (src == null) for (int h : hs) {
                if (takenFrom.contains(h) || !inTeam.contains(h)) continue;
                int hm = -1;
                for (int q = 0; q < k; q++) if (idx[q] == h) hm = q;
                if (hm >= 0 && hm != m && !keeps[hm]) {
                    takenFrom.add(h);
                    src = "quítaselo a " + pr[h].sp.name + " (de este equipo)";
                    break;
                }
            }
            source[m] = src != null ? src : "disponible";
        }
        for (int m = 0; m < k; m++) {
            MemberInfo mi = r.info.get(m);
            Profile f = pr[idx[m]];
            String curRaw = f.p.item;
            if (chosen[m] >= 0) {
                ItemAdvisor.Cand c = cands.get(m).get(chosen[m]);
                mi.recItem = ItemAdvisor.ITEMS.get(c.id).display();
                mi.recEn = ItemAdvisor.enName(c.id, c.id);
                mi.recWhy = c.why;
                mi.recSource = source[m];
                mi.recChanged = !keeps[m];
            } else if (!curRaw.isEmpty() && !ItemAdvisor.ITEMS.containsKey(ItemAdvisor.canon(curRaw))) {
                mi.recItem = curRaw;
                mi.recEn = curRaw;
                mi.recWhy = "mantiene el que ya lleva (no tengo datos para valorarlo).";
                mi.recSource = "ya lo lleva";
            } else {
                mi.recWhy = "no tienes ningún objeto útil disponible para él.";
            }
        }
    }

    private double defScore(int[] idx, int len) {
        double pen = 0, bonus = 0;
        for (int t = 0; t < 18; t++) {
            int w = 0, r = 0, im = 0, quad = 0;
            for (int i = 0; i < len; i++) {
                float m = pr[idx[i]].def[t];
                if (m > 1) { w++; if (m >= 4) quad++; } else if (m < 1) { r++; if (m == 0) im++; }
            }
            int net = w - r;
            if (net >= 2) pen += 1.5 + 0.75 * (net - 2); else if (net == 1) pen += 0.5;
            if (w >= 1 && r == 0) pen += 0.7;
            pen += 0.4 * quad;
            if (r >= 2) bonus += 0.1;
            if (im >= 1) bonus += 0.1;
        }
        return 25 * Math.max(0, 1 - pen / 14) + Math.min(2, bonus);
    }

    private double covScore(int[] idx, int len) {
        int se = 0, walled = 0;
        for (int c = 0; c < combos; c++) {
            float best = 0;
            for (int i = 0; i < len; i++) best = Math.max(best, pr[idx[i]].cov[c]);
            if (best >= 2) se++; else if (best <= 0.5f) walled++;
        }
        double fr = se / (double) combos;
        return 25 * (0.75 * fr * fr + 0.25 * (1 - walled / (double) combos));
    }

    private int comboIndex(int[] types) {
        for (int c = 0; c < combos; c++) if (Arrays.equals(dex.typeCombos.get(c), types)) return c;
        return -1;
    }

    private float singleBest(Profile p, int defType) {
        float best = 0;
        for (String id : p.p.moveIds) {
            Dex.Move m = dex.moves.get(id);
            if (m == null || m.cat == 'N') continue;
            if (Profile.effPower(m, p.p, Dex.toId(p.p.ability), Dex.toId(p.p.item), D) < Profile.REF * 0.2) continue;
            best = Math.max(best, Dex.CHART[Profile.moveType(m, p.p)][defType]);
        }
        return best;
    }

    private List<String> names(int[] idx, long bits) {
        List<String> l = new ArrayList<>();
        for (int x : idx) if ((pr[x].roles & bits) != 0) l.add(pr[x].sp.name);
        return l;
    }

    private void role(List<String> have, List<String> miss, List<String> who, String missName, boolean important, String label) {
        if (!who.isEmpty()) have.add(label + " (" + join(who) + ")");
        else if (important) miss.add(missName);
    }

    private void weatherNote(TeamResult r, int[] idx, long setter, long abuser, String name) {
        List<String> s = names(idx, setter), a = new ArrayList<>();
        for (int x : idx) if ((pr[x].roles & abuser) != 0 && (pr[x].roles & setter) == 0) a.add(pr[x].sp.name);
        if (!s.isEmpty() && !a.isEmpty())
            r.good.add("Sinergia de " + name + ": " + join(s) + " con " + join(a) + ".");
        else if (s.isEmpty() && !a.isEmpty())
            r.bad.add(join(a) + " aprovecha " + name + " pero no tienes quién lo ponga.");
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(l.get(i));
        }
        return b.toString();
    }
}
