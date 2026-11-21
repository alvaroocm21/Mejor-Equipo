package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.List;

/** Un Pokémon del "main" (formato Showdown). */
public final class PokeSet {
    public static final String[] STAT_NAMES = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    /** nombre -> {sube, baja} (índices de stat, -1 = neutra) */
    private static final String[][] NATURES = {
            {"Adamant", "1", "3"}, {"Bashful", "-1", "-1"}, {"Bold", "2", "1"}, {"Brave", "1", "5"},
            {"Calm", "4", "1"}, {"Careful", "4", "3"}, {"Docile", "-1", "-1"}, {"Gentle", "4", "2"},
            {"Hardy", "-1", "-1"}, {"Hasty", "5", "2"}, {"Impish", "2", "3"}, {"Jolly", "5", "3"},
            {"Lax", "2", "4"}, {"Lonely", "1", "2"}, {"Mild", "3", "2"}, {"Modest", "3", "1"},
            {"Naive", "5", "4"}, {"Naughty", "1", "4"}, {"Quiet", "3", "5"}, {"Quirky", "-1", "-1"},
            {"Rash", "3", "4"}, {"Relaxed", "2", "5"}, {"Sassy", "4", "5"}, {"Serious", "-1", "-1"},
            {"Timid", "5", "1"}};

    public String nickname = "", speciesName = "", item = "", ability = "", nature = "Hardy", tera = "";
    public Dex.Species species;
    public int level = 100;
    public int[] evs = new int[6];
    public int[] ivs = {31, 31, 31, 31, 31, 31};
    public final List<String> moveNames = new ArrayList<>();
    public final List<String> moveIds = new ArrayList<>();
    public String hiddenPowerType = "";
    public int[] stats = new int[6];
    public String raw = "";
    public final List<String> warnings = new ArrayList<>();

    public static boolean isNature(String n) {
        return natureRow(n) != null;
    }

    private static String[] natureRow(String n) {
        for (String[] r : NATURES) if (r[0].equalsIgnoreCase(n)) return r;
        return null;
    }

    public int natureUp() {
        String[] r = natureRow(nature);
        return r == null ? -1 : Integer.parseInt(r[1]);
    }

    public int natureDown() {
        String[] r = natureRow(nature);
        return r == null ? -1 : Integer.parseInt(r[2]);
    }

    public String displayName() {
        return species != null ? species.name : speciesName;
    }

    public void computeStats() {
        int up = natureUp(), down = natureDown();
        for (int i = 0; i < 6; i++) {
            int b = species.base[i];
            int core = (2 * b + ivs[i] + evs[i] / 4) * level / 100;
            if (i == 0) {
                stats[0] = b == 1 ? 1 : core + level + 10;
            } else {
                float m = i == up ? 1.1f : (i == down ? 0.9f : 1f);
                stats[i] = (int) ((core + 5) * m);
            }
        }
    }

    public String shortLine() {
        return displayName() + (item.isEmpty() ? "" : " @ " + item);
    }
}
