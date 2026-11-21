package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Valora objetos por Pokémon (con motivo) y lee la mochila del usuario. */
public final class ItemAdvisor {
    static final class Item {
        final String id, en, es;

        Item(String en, String es) {
            this.en = en;
            this.es = es;
            this.id = Dex.toId(en);
        }

        String display() {
            return es == null || es.equals(en) ? en : es + " (" + en + ")";
        }
    }

    static final class Cand {
        final String id;
        final double value;
        final String why;

        Cand(String id, double value, String why) {
            this.id = id;
            this.value = value;
            this.why = why;
        }
    }

    static final Map<String, Item> ITEMS = new HashMap<>();
    /** id (inglés o español) -> id canónico */
    static final Map<String, String> ALIAS = new HashMap<>();

    private static void def(String en, String es) {
        Item i = new Item(en, es);
        ITEMS.put(i.id, i);
        ALIAS.put(i.id, i.id);
        if (es != null) ALIAS.put(Dex.toId(es), i.id);
    }

    /** Un objeto potenciador de tipo por tipo (mismo orden que Dex.TYPES). */
    static final String[] TYPE_ITEM = {"silkscarf", "charcoal", "mysticwater", "magnet", "miracleseed", "nevermeltice",
            "blackbelt", "poisonbarb", "softsand", "sharpbeak", "twistedspoon", "silverpowder", "hardstone",
            "spelltag", "dragonfang", "blackglasses", "metalcoat", "fairyfeather"};
    /** Baya que reduce el daño súper eficaz de cada tipo (Normal no tiene). */
    static final String[] RESIST_BERRY = {"chilanberry", "occaberry", "passhoberry", "wacanberry", "rindoberry",
            "yacheberry", "chopleberry", "kebiaberry", "shucaberry", "cobaberry", "payapaberry", "tangaberry",
            "chartiberry", "kasibberry", "habanberry", "colburberry", "babiriberry", "roseliberry"};

    static {
        def("Leftovers", "Restos");
        def("Choice Band", "Cinta Elegida");
        def("Choice Specs", "Gafas Elegidas");
        def("Choice Scarf", "Pañuelo Elegido");
        def("Life Orb", "Vidasfera");
        def("Assault Vest", "Chaleco Asalto");
        def("Focus Sash", "Banda Focus");
        def("Sitrus Berry", "Baya Zidra");
        def("Oran Berry", "Baya Aranja");
        def("Lum Berry", "Baya Ziuela");
        def("Rocky Helmet", "Casco Dentado");
        def("Eviolite", "Mineral Evolutivo");
        def("Heavy-Duty Boots", "Botas Gruesas");
        def("Booster Energy", "Energía Potenciadora");
        def("Expert Belt", "Cinturón Experto");
        def("Weakness Policy", "Seguro Debilidad");
        def("Air Balloon", "Globo Helio");
        def("Light Clay", "Arcilla Refractaria");
        def("Shell Bell", "Campana Concha");
        def("Black Sludge", "Lodo Negro");
        def("Toxic Orb", "Toxiesfera");
        def("Flame Orb", "Llamaesfera");
        def("Muscle Band", "Cinta Fuerte");
        def("Wise Glasses", "Gafas Especiales");
        String[][] t = {{"Silk Scarf", "Pañuelo Seda"}, {"Charcoal", "Carbón"}, {"Mystic Water", "Agua Mística"},
                {"Magnet", "Imán"}, {"Miracle Seed", "Semilla Milagro"}, {"NeverMeltIce", "Antiderretir"},
                {"Black Belt", "Cinturón Negro"}, {"Poison Barb", "Flecha Venenosa"}, {"Soft Sand", "Arena Fina"},
                {"Sharp Beak", "Pico Afilado"}, {"Twisted Spoon", "Cuchara Torcida"}, {"Silver Powder", "Polvo Plata"},
                {"Hard Stone", "Piedra Dura"}, {"Spell Tag", "Hechizo"}, {"Dragon Fang", "Colmillo Dragón"},
                {"Black Glasses", "Gafas de Sol"}, {"Metal Coat", "Revestimiento Metálico"}, {"Fairy Feather", "Pluma Hada"}};
        for (String[] x : t) def(x[0], x[1]);
        String[] b = {"Chilan Berry", "Occa Berry", "Passho Berry", "Wacan Berry", "Rindo Berry", "Yache Berry",
                "Chople Berry", "Kebia Berry", "Shuca Berry", "Coba Berry", "Payapa Berry", "Tanga Berry",
                "Charti Berry", "Kasib Berry", "Haban Berry", "Colbur Berry", "Babiri Berry", "Roseli Berry"};
        for (String x : b) def(x, null);
    }

    public static String canon(String name) {
        String id = Dex.toId(name);
        String c = ALIAS.get(id);
        return c != null ? c : id;
    }

    public static String displayName(String name) {
        Item i = ITEMS.get(canon(name));
        return i != null ? i.display() : name;
    }

    static String enName(String id, String fallback) {
        Item i = ITEMS.get(id);
        return i != null ? i.en : fallback;
    }

    // ------------------------------------------------------------ mochila
    private static final Pattern P1 = Pattern.compile("^(\\d+)\\s*[xX×]\\s*(.+)$");
    private static final Pattern P2 = Pattern.compile("^(.+?)\\s*[xX×:]\\s*(\\d+)$");
    private static final Pattern P3 = Pattern.compile("^(.+?)\\s+(\\d+)$");

    /** Lee líneas tipo "Restos x2", "2x Leftovers", "Leftovers: 2" o solo "Leftovers". Devuelve id -> cantidad. */
    public static Map<String, Integer> parseBag(String text) {
        Map<String, Integer> bag = new HashMap<>();
        for (String line0 : text.replace("\r", "").split("\n")) {
            String line = line0.trim().replaceFirst("^[-•*]\\s*", "");
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || line.startsWith("===")) continue;
            String name = line;
            int count = 1;
            Matcher m;
            if ((m = P1.matcher(line)).matches()) { count = Integer.parseInt(m.group(1)); name = m.group(2); }
            else if ((m = P2.matcher(line)).matches()) { name = m.group(1); count = Integer.parseInt(m.group(2)); }
            else if ((m = P3.matcher(line)).matches()) { name = m.group(1); count = Integer.parseInt(m.group(2)); }
            String id = canon(name.trim());
            if (id.isEmpty() || count <= 0) continue;
            Integer old = bag.get(id);
            bag.put(id, (old == null ? 0 : old) + count);
        }
        return bag;
    }

    // ------------------------------------------------------------ valoración
    /** Objetos de un solo uso (se gastan): solo para combates importantes. */
    static boolean isConsumable(String id) {
        if (id.equals("sitrusberry") || id.equals("oranberry") || id.equals("lumberry") || id.equals("focussash")
                || id.equals("weaknesspolicy") || id.equals("airballoon")) return true;
        for (String b : RESIST_BERRY) if (b.equals(id)) return true;
        return id.endsWith("berry");
    }

    public static boolean consumable(String name) {
        return isConsumable(canon(name));
    }

    static List<Cand> candidates(Dex dex, Profile f, boolean adventure) {
        PokeSet p = f.p;
        Dex.Species sp = f.sp;
        String ab = Dex.toId(p.ability);
        String cur = canon(p.item);
        List<Cand> out = new ArrayList<>();
        if (!sp.requiredItem.isEmpty()) { // Mega: debe conservar su piedra
            ITEMS.putIfAbsent(sp.requiredItem, new Item(p.item, null));
            out.add(new Cand(sp.requiredItem, 50, "Megaevolución: la piedra es lo que le permite evolucionar en combate."));
            return out;
        }
        int nStatus = 0, nDmg = f.nPhys + f.nSpec;
        double bestPw = 0;
        int bestType = -1, bestStabType = -1;
        double bestStabPw = 0;
        boolean contactMove = false;
        for (String id : p.moveIds) {
            Dex.Move m = dex.moves.get(id);
            if (m == null) continue;
            if (m.cat == 'N') { nStatus++; continue; }
            double pw = Profile.effPower(m, p, ab, "", false);
            int mt = Profile.moveType(m, p);
            if (pw > bestPw) { bestPw = pw; bestType = mt; }
            if (sp.hasType(mt) && pw > bestStabPw) { bestStabPw = pw; bestStabType = mt; }
            if (m.contact) contactMove = true;
        }
        boolean attacker = f.off >= 0.4 && nDmg >= 2;
        boolean allAttacks = nStatus == 0 && nDmg >= 3;
        boolean physical = f.physAttacker;
        boolean frail = f.bulk < 0.3;
        long r = f.roles;

        if (sp.canEvolve)
            add(out, "eviolite", 7.5, "todavía puede evolucionar: sube un 50 % la Defensa y la Def. Esp.");
        if (attacker && allAttacks && physical && f.nSpec == 0)
            add(out, "choiceband", 6.0 + 2 * f.off, "todos sus movimientos son ataques físicos: +50 % de Ataque sin perder nada.");
        if (attacker && allAttacks && !physical && f.nPhys == 0)
            add(out, "choicespecs", 6.0 + 2 * f.off, "todos sus movimientos son ataques especiales: +50 % de Ataque Esp. sin perder nada.");
        if (attacker && nStatus == 0 && sp.base[5] >= 55 && sp.base[5] <= 105)
            add(out, "choicescarf", 4.5 + (sp.base[5] >= 75 ? 0.5 : 0), "su Velocidad es media: +50 % le deja adelantarse a rivales más rápidos.");
        if (attacker && f.off >= 0.5)
            add(out, "lifeorb", 5.0 + f.off, "pega fuerte (+30 % de daño) y mantiene sus movimientos de estado.");
        if (f.bulk >= 0.4 || (r & Profile.RECOVERY) != 0)
            add(out, "leftovers", 4.5 + (f.bulk >= 0.5 ? 0.7 : 0), "es resistente: se cura un poco cada turno y aguanta más.");
        if (nStatus == 0 && nDmg >= 3 && f.bulk >= 0.3 && p.stats[4] <= p.stats[2] * 1.15)
            add(out, "assaultvest", 5.2, "no usa movimientos de estado: +50 % de Def. Esp. gratis.");
        if (frail && attacker)
            add(out, "focussash", 5.0, "es frágil: asegura que aguante un golpe y pueda actuar.");
        if (f.bulk >= 0.3)
            add(out, "sitrusberry", 3.5, "recupera vida al bajar de la mitad; vale para casi cualquiera.");
        add(out, "oranberry", 2.0, "cura un poco de vida una vez; opción de relleno.");
        if (f.bulk >= 0.5 && p.stats[2] >= p.stats[4] && nDmg < 4)
            add(out, "rockyhelmet", 4.2, "es una pared física: castiga a quien le ataque por contacto.");
        if (f.def[12] >= 2)
            add(out, "heavydutyboots", 4.0 + (f.def[12] >= 4 ? 2.5 : 0), "es débil a Roca: evita perder vida con Trampa Rocas y otras trampas.");
        if (ab.equals("protosynthesis") || ab.equals("quarkdrive"))
            add(out, "boosterenergy", 6.5, "activa su habilidad sin depender de clima/terreno.");
        if (f.def[8] >= 2 && !ab.equals("levitate"))
            add(out, "airballoon", 3.6, "es débil a Tierra: es inmune hasta que lo golpeen.");
        if (f.bulk >= 0.5 && f.off >= 0.5)
            add(out, "weaknesspolicy", 4.0, "aguanta un golpe súper eficaz y luego sube mucho sus stats ofensivos.");
        if ((r & Profile.SETUP) != 0)
            add(out, "lumberry", 4.0, "usa movimientos de mejora: la baya evita que un estado alterado le corte el setup.");
        else
            add(out, "lumberry", 2.5, "cura un estado alterado una vez.");
        if ((r & Profile.SCREENS) != 0)
            add(out, "lightclay", 5.5, "pone pantallas: duran 8 turnos en vez de 5.");
        if (attacker)
            add(out, "expertbelt", 3.0 + f.off * 0.5, "sube un 20 % el daño de los golpes súper eficaces.");
        if (attacker && physical) add(out, "muscleband", 2.7, "+10 % a sus ataques físicos.");
        if (attacker && !physical) add(out, "wiseglasses", 2.7, "+10 % a sus ataques especiales.");
        if (nDmg >= 1 && f.off >= 0.3)
            add(out, "shellbell", 2.5, "recupera vida con cada golpe que da.");
        if (sp.hasType(7) && f.bulk >= 0.3)
            add(out, "blacksludge", 4.7, "al ser de tipo Veneno se cura cada turno (a otros les hace daño).");
        if (ab.equals("guts") || ab.equals("quickfeet") || ab.equals("marvelscale") || ab.equals("poisonheal")
                || ab.equals("toxicboost") || ab.equals("flareboost"))
            add(out, ab.equals("flareboost") || ab.equals("guts") && physical ? "flameorb" : "toxicorb", 6.0,
                    "su habilidad (" + p.ability + ") se activa con un estado alterado propio.");
        if (bestStabType >= 0 && attacker)
            add(out, TYPE_ITEM[bestStabType], 3.6 + f.off * 0.6, "potencia un 20 % su ataque principal de tipo "
                    + Dex.TYPES_ES[bestStabType] + " (con STAB).");
        else if (bestType >= 0 && attacker)
            add(out, TYPE_ITEM[bestType], 3.0, "potencia un 20 % su mejor ataque (" + Dex.TYPES_ES[bestType] + ").");
        for (int t = 1; t < 18; t++) {
            if (f.def[t] == 2 && !(t == 12 && f.def[12] >= 4))
                add(out, RESIST_BERRY[t], 2.8, "es débil a " + Dex.TYPES_ES[t] + ": la baya reduce a la mitad el primer golpe de ese tipo.");
        }

        // bonus por mantener lo que ya lleva (evita cambios inútiles)
        List<Cand> res = new ArrayList<>();
        for (Cand c : out) if (!(adventure && isConsumable(c.id))) res.add(c.id.equals(cur) ? new Cand(c.id, c.value + 0.8, c.why) : c);
        Collections.sort(res, (a, b) -> Double.compare(b.value, a.value));
        return res;
    }

    private static void add(List<Cand> out, String id, double value, String why) {
        for (Cand c : out) if (c.id.equals(id)) return;
        if (ITEMS.containsKey(id)) out.add(new Cand(id, value, why));
    }
}
