package com.mejorequipo.engine;

import java.util.HashMap;
import java.util.Map;

/** Nombres en español de movimientos, habilidades y naturalezas (si no hay dato, devuelve el original). */
public final class Es {
    private static final Map<String, String> MOVES = new HashMap<>(), ABILITIES = new HashMap<>(), NATURES = new HashMap<>();

    private Es() {}

    /** Carga el TSV "tipo\tid\tnombre" (m = movimiento, a = habilidad, n = naturaleza). */
    public static void load(String tsv) {
        for (String line : tsv.split("\n")) {
            String[] f = line.split("\t");
            if (f.length < 3) continue;
            Map<String, String> m = f[0].equals("m") ? MOVES : f[0].equals("a") ? ABILITIES : f[0].equals("n") ? NATURES : null;
            if (m != null) m.put(f[1], f[2]);
        }
    }

    public static String move(String id, String fallback) {
        String s = MOVES.get(id);
        return s != null ? s : fallback;
    }

    /** Movimiento con el tipo en español para Poder Oculto. */
    public static String move(String id, String fallback, String hiddenPowerType) {
        String s = move(id, fallback);
        if (id.equals("hiddenpower") && hiddenPowerType != null && !hiddenPowerType.isEmpty()) {
            int t = Dex.typeIndex(hiddenPowerType);
            if (t >= 0) return s + " " + Dex.TYPES_ES[t];
        }
        return s;
    }

    public static String ability(String name) {
        String s = ABILITIES.get(Dex.toId(name));
        return s != null ? s : name;
    }

    public static String nature(String name) {
        String s = NATURES.get(Dex.toId(name));
        return s != null ? s : name;
    }
}
