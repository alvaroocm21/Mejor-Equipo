package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Datos estáticos: tabla de tipos, especies y movimientos (generados desde Pokémon Showdown). */
public final class Dex {
    public static final String[] TYPES = {"Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison",
            "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"};
    public static final String[] TYPES_ES = {"Normal", "Fuego", "Agua", "Eléctrico", "Planta", "Hielo", "Lucha", "Veneno",
            "Tierra", "Volador", "Psíquico", "Bicho", "Roca", "Fantasma", "Dragón", "Siniestro", "Acero", "Hada"};
    /** CHART[atacante][defensor] */
    public static final float[][] CHART = new float[18][18];

    static {
        for (float[] r : CHART) java.util.Arrays.fill(r, 1f);
        row("Normal", "", "Rock,Steel", "Ghost");
        row("Fire", "Grass,Ice,Bug,Steel", "Fire,Water,Rock,Dragon", "");
        row("Water", "Fire,Ground,Rock", "Water,Grass,Dragon", "");
        row("Electric", "Water,Flying", "Electric,Grass,Dragon", "Ground");
        row("Grass", "Water,Ground,Rock", "Fire,Grass,Poison,Flying,Bug,Dragon,Steel", "");
        row("Ice", "Grass,Ground,Flying,Dragon", "Fire,Water,Ice,Steel", "");
        row("Fighting", "Normal,Ice,Rock,Dark,Steel", "Poison,Flying,Psychic,Bug,Fairy", "Ghost");
        row("Poison", "Grass,Fairy", "Poison,Ground,Rock,Ghost", "Steel");
        row("Ground", "Fire,Electric,Poison,Rock,Steel", "Grass,Bug", "Flying");
        row("Flying", "Grass,Fighting,Bug", "Electric,Rock,Steel", "");
        row("Psychic", "Fighting,Poison", "Psychic,Steel", "Dark");
        row("Bug", "Grass,Psychic,Dark", "Fire,Fighting,Poison,Flying,Ghost,Steel,Fairy", "");
        row("Rock", "Fire,Ice,Flying,Bug", "Fighting,Ground,Steel", "");
        row("Ghost", "Psychic,Ghost", "Dark", "Normal");
        row("Dragon", "Dragon", "Steel", "Fairy");
        row("Dark", "Psychic,Ghost", "Fighting,Dark,Fairy", "");
        row("Steel", "Ice,Rock,Fairy", "Fire,Water,Electric,Steel", "");
        row("Fairy", "Fighting,Dragon,Dark", "Fire,Poison,Steel", "");
    }

    private static void row(String att, String se, String nve, String imm) {
        int a = typeIndex(att);
        for (String s : se.split(",")) if (!s.isEmpty()) CHART[a][typeIndex(s)] = 2f;
        for (String s : nve.split(",")) if (!s.isEmpty()) CHART[a][typeIndex(s)] = 0.5f;
        for (String s : imm.split(",")) if (!s.isEmpty()) CHART[a][typeIndex(s)] = 0f;
    }

    public static int typeIndex(String name) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    public static String toId(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }

    public static final class Species {
        public String id, name;
        public int[] types;
        public int[] base = new int[6];
        public String[] abilities;

        public boolean hasType(int t) {
            for (int x : types) if (x == t) return true;
            return false;
        }
    }

    public static final class Move {
        public String id, name, target;
        public int type, bp, acc, prio;
        public char cat; // P, S, N
        public boolean selfSwitch, multihit, fixed, ohko, contact;
    }

    public final Map<String, Species> species = new HashMap<>();
    public final Map<String, Move> moves = new HashMap<>();
    /** Combinaciones de tipos distintas (índices) que existen en el dex. */
    public final List<int[]> typeCombos = new ArrayList<>();

    public static Dex load(String pokedexTsv, String movesTsv) {
        Dex d = new Dex();
        Set<String> seen = new HashSet<>();
        for (String line : pokedexTsv.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 5) continue;
            Species s = new Species();
            s.id = f[0];
            s.name = f[1];
            String[] tt = f[2].split("/");
            s.types = new int[tt.length];
            boolean okTypes = true;
            for (int i = 0; i < tt.length; i++) {
                s.types[i] = typeIndex(tt[i]);
                if (s.types[i] < 0) okTypes = false;
            }
            if (!okTypes) continue; // formas con tipo "Stellar", etc.
            String[] bs = f[3].split(",");
            for (int i = 0; i < 6; i++) s.base[i] = Integer.parseInt(bs[i]);
            s.abilities = f[4].split("\\|");
            d.species.put(s.id, s);
            String key = java.util.Arrays.toString(s.types);
            if (seen.add(key)) d.typeCombos.add(s.types);
        }
        for (String line : movesTsv.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 9) continue;
            Move m = new Move();
            m.id = f[0];
            m.name = f[1];
            m.type = Math.max(0, typeIndex(f[2])); // "Stellar"/"???" -> Normal
            m.cat = f[3].charAt(0);
            m.bp = Integer.parseInt(f[4]);
            m.acc = Integer.parseInt(f[5]);
            m.prio = Integer.parseInt(f[6]);
            m.target = f[7];
            String ex = "," + f[8] + ",";
            m.selfSwitch = ex.contains(",sw,");
            m.multihit = ex.contains(",mh,");
            m.fixed = ex.contains(",fx,");
            m.ohko = ex.contains(",ko,");
            m.contact = ex.contains(",ct,");
            d.moves.put(m.id, m);
        }
        return d;
    }
}
