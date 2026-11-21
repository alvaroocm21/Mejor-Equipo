package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector del guardado "main" de Pokémon Rubí Omega / Zafiro Alfa (3DS, 0x76000 bytes, p. ej. extraído con Checkpoint).
 * Extrae equipo, cajas y mochila y los convierte a texto formato Showdown para el resto del motor.
 * Offsets y cifrado según PKHeX (SAV6AO / PokeCrypto.Decrypt67).
 */
public final class Gen6Save {
    public static final int SIZE_ORAS = 0x76000;
    private static final int PARTY = 0x14200, BOX = 0x33000, BAG = 0x400, BOX_NAMES = 0x4400, BOX_NAME_LEN = 0x22;
    private static final int SIZE_STORED = 0xE8, SIZE_PARTY = 0x104;

    public static final class Names {
        public String[] species, moves, abilities, items;
        public String growth; // un dígito por especie: 0 medio-rápido,1 errático,2 fluctuante,3 medio-lento,4 rápido,5 lento

        public static Names load(String species, String moves, String abilities, String items, String growth) {
            Names n = new Names();
            n.species = species.split("\n", -1);
            n.moves = moves.split("\n", -1);
            n.abilities = abilities.split("\n", -1);
            n.items = items.split("\n", -1);
            n.growth = growth.trim();
            return n;
        }
    }

    public static final class Result {
        public final List<String> partySets = new ArrayList<>();
        public final List<List<String>> boxSets = new ArrayList<>();
        public final String[] boxNames = new String[31];
        public final Map<String, Integer> bag = new HashMap<>();
        public final List<String> warnings = new ArrayList<>();

        public int party() { return partySets.size(); }

        public int boxCount(int box) { return boxSets.get(box).size(); }

        /** Cajas de muertos por defecto: las que se llaman "muertos"...; si no hay ninguna, la última caja. */
        public java.util.Set<Integer> defaultIgnored() {
            java.util.Set<Integer> s = new java.util.HashSet<>();
            for (int i = 0; i < 31; i++) if (isDeadBox(boxNames[i])) s.add(i);
            if (s.isEmpty()) s.add(30);
            return s;
        }

        public int boxed(java.util.Set<Integer> ignored) {
            int n = 0;
            for (int i = 0; i < 31; i++) if (!ignored.contains(i)) n += boxCount(i);
            return n;
        }

        public int ignoredCount(java.util.Set<Integer> ignored) {
            int n = 0;
            for (int i : ignored) if (i >= 0 && i < 31) n += boxCount(i);
            return n;
        }

        public List<String> sets(java.util.Set<Integer> ignored) {
            List<String> all = new ArrayList<>(partySets);
            for (int i = 0; i < 31; i++) if (!ignored.contains(i)) all.addAll(boxSets.get(i));
            return all;
        }

        public String text(java.util.Set<Integer> ignored) {
            StringBuilder b = new StringBuilder();
            for (String s : sets(ignored)) b.append(s).append("\n\n");
            return b.toString();
        }
    }

    public static boolean isSave(byte[] d) {
        return d != null && d.length == SIZE_ORAS;
    }

    private static final byte[] BLOCK_POSITION = {
            0, 1, 2, 3, 0, 1, 3, 2, 0, 2, 1, 3, 0, 3, 1, 2,
            0, 2, 3, 1, 0, 3, 2, 1, 1, 0, 2, 3, 1, 0, 3, 2,
            2, 0, 1, 3, 3, 0, 1, 2, 2, 0, 3, 1, 3, 0, 2, 1,
            1, 2, 0, 3, 1, 3, 0, 2, 2, 1, 0, 3, 3, 1, 0, 2,
            2, 3, 0, 1, 3, 2, 0, 1, 1, 2, 3, 0, 1, 3, 2, 0,
            2, 1, 3, 0, 3, 1, 2, 0, 2, 3, 1, 0, 3, 2, 1, 0,
            0, 1, 2, 3, 0, 1, 3, 2, 0, 2, 1, 3, 0, 3, 1, 2,
            0, 2, 3, 1, 0, 3, 2, 1, 1, 0, 2, 3, 1, 0, 3, 2};

    private static final String[] NATURES = {"Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile",
            "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet",
            "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"};
    private static final String[] HP_TYPES = {"Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
            "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark"};

    private static int u8(byte[] d, int o) { return d[o] & 0xFF; }
    private static int u16(byte[] d, int o) { return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8); }
    private static long u32(byte[] d, int o) { return (u16(d, o) | ((long) u16(d, o + 2) << 16)) & 0xFFFFFFFFL; }

    /** Descifra in situ un Pokémon de 232 (caja) o 260 (equipo) bytes. */
    static byte[] decrypt(byte[] src, int off, int len) {
        byte[] d = new byte[len];
        System.arraycopy(src, off, d, 0, len);
        long pv = u32(d, 0);
        int sv = (int) ((pv >> 13) & 31);
        xor(d, 8, SIZE_STORED, pv);
        if (len > SIZE_STORED) xor(d, SIZE_STORED, len, pv);
        byte[] out = d.clone();
        for (int i = 0; i < 4; i++) {
            int from = BLOCK_POSITION[sv * 4 + i];
            System.arraycopy(d, 8 + from * 56, out, 8 + i * 56, 56);
        }
        return out;
    }

    private static void xor(byte[] d, int from, int to, long seed) {
        long s = seed & 0xFFFFFFFFL;
        for (int o = from; o + 1 < to; o += 2) {
            s = (0x41C64E6DL * s + 0x6073L) & 0xFFFFFFFFL;
            int x = (int) (s >> 16) & 0xFFFF;
            int v = u16(d, o) ^ x;
            d[o] = (byte) v;
            d[o + 1] = (byte) (v >> 8);
        }
    }

    private static long expFor(int growth, int n) {
        long n3 = (long) n * n * n;
        switch (growth) {
            case 1: // errático
                if (n <= 50) return n3 * (100 - n) / 50;
                if (n <= 68) return n3 * (150 - n) / 100;
                if (n <= 98) return n3 * ((1911 - 10 * n) / 3) / 500;
                return n3 * (160 - n) / 100;
            case 2: // fluctuante
                if (n <= 15) return n3 * ((n + 1) / 3 + 24) / 50;
                if (n <= 35) return n3 * (n + 14) / 50;
                return n3 * (n / 2 + 32) / 50;
            case 3: return 6 * n3 / 5 - 15L * n * n + 100L * n - 140; // medio-lento
            case 4: return 4 * n3 / 5; // rápido
            case 5: return 5 * n3 / 4; // lento
            default: return n3; // medio-rápido
        }
    }

    static int levelFromExp(int growth, long exp) {
        int lv = 1;
        for (int n = 2; n <= 100; n++) if (expFor(growth, n) <= exp) lv = n;
        return lv;
    }

    private static String formSuffix(int species, int form) {
        if (form == 0) return "";
        switch (species) {
            case 386: return new String[]{"", "-Attack", "-Defense", "-Speed"}[Math.min(form, 3)];
            case 413: return form == 1 ? "-Sandy" : form == 2 ? "-Trash" : "";
            case 479: return new String[]{"", "-Heat", "-Wash", "-Frost", "-Fan", "-Mow"}[Math.min(form, 5)];
            case 487: return "-Origin";
            case 492: return "-Sky";
            case 550: return form == 1 ? "-Blue-Striped" : "";
            case 641: case 642: case 645: return "-Therian";
            case 646: return form == 1 ? "-White" : form == 2 ? "-Black" : "";
            case 647: return "-Resolute";
            case 678: return "-F";
            case 710: case 711: return form == 1 ? "-Small" : form == 2 ? "-Large" : form == 3 ? "-Super" : "";
            case 720: return "-Unbound";
            default: return "";
        }
    }

    public static Result read(byte[] d, Names nm) {
        Result r = new Result();
        for (int i = 0; i < 31; i++) { r.boxNames[i] = ""; r.boxSets.add(new ArrayList<String>()); }
        if (!isSave(d)) {
            r.warnings.add("El archivo no tiene el tamaño de un guardado de Rubí Omega / Zafiro Alfa (483328 bytes).");
            return r;
        }
        int partyCount = u8(d, PARTY + 6 * SIZE_PARTY);
        if (partyCount > 6) partyCount = 6;
        for (int i = 0; i < partyCount; i++) {
            String s = convert(decrypt(d, PARTY + i * SIZE_PARTY, SIZE_PARTY), true, nm);
            if (s != null) r.partySets.add(s);
        }
        for (int b = 0; b < 31; b++) {
            r.boxNames[b] = boxName(d, b);
            List<String> list = new ArrayList<>();
            for (int sl = 0; sl < 30; sl++) {
                int off = BOX + SIZE_STORED * (b * 30 + sl);
                if (u32(d, off) == 0 && u16(d, off + 8) == 0) continue; // hueco vacío
                String s = convert(decrypt(d, off, SIZE_STORED), false, nm);
                if (s != null) list.add(s);
            }
            r.boxSets.set(b, list);
        }
        // mochila: bolsillo de objetos (400 huecos) y bayas (72), cada hueco = id u16 + cantidad u16
        readPouch(d, BAG + 0x000, 400, nm, r.bag);
        readPouch(d, BAG + 0xA70, 72, nm, r.bag);
        if (r.partySets.isEmpty() && r.boxed(new java.util.HashSet<Integer>()) == 0)
            r.warnings.add("Guardado leído pero no encuentro Pokémon (¿es de Rubí Omega / Zafiro Alfa?).");
        return r;
    }

    static String boxName(byte[] d, int box) {
        int o = BOX_NAMES + box * BOX_NAME_LEN;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < BOX_NAME_LEN / 2; i++) {
            int c = u16(d, o + 2 * i);
            if (c == 0 || c == 0xFFFF) break;
            b.append((char) c);
        }
        return b.toString();
    }

    /** Cajas de Pokémon muertos del Nuzlocke ("MUERTOS", "Muerto", "Los muertos"...). */
    static boolean isDeadBox(String name) {
        String n = Dex.toId(name);
        return n.contains("muert") || n.equals("dead") || n.contains("graveyard") || n.contains("cementerio");
    }

    private static void readPouch(byte[] d, int base, int slots, Names nm, Map<String, Integer> bag) {
        for (int i = 0; i < slots; i++) {
            int id = u16(d, base + i * 4), cnt = u16(d, base + i * 4 + 2);
            if (id <= 0 || id >= nm.items.length || cnt <= 0 || cnt > 999) continue;
            String name = nm.items[id].trim();
            if (name.isEmpty() || name.equals("???")) continue;
            String k = ItemAdvisor.canon(name);
            Integer old = bag.get(k);
            bag.put(k, (old == null ? 0 : old) + cnt);
        }
    }

    /** Convierte un PK6 descifrado a texto Showdown, o null si es un hueco/huevo/dato inválido. */
    static String convert(byte[] p, boolean party, Names nm) {
        int species = u16(p, 0x08);
        if (species < 1 || species >= nm.species.length) return null;
        long iv = u32(p, 0x74);
        if (((iv >> 30) & 1) == 1) return null; // huevo
        int item = u16(p, 0x0A), ability = u8(p, 0x14), nature = u8(p, 0x1C), form = u8(p, 0x1D) >> 3;
        if (nature >= NATURES.length || ability >= nm.abilities.length || item >= nm.items.length) return null;
        int[] ev = {u8(p, 0x1E), u8(p, 0x1F), u8(p, 0x20), u8(p, 0x22), u8(p, 0x23), u8(p, 0x21)}; // HP Atk Def SpA SpD Spe
        int[] iv6 = {(int) (iv & 31), (int) (iv >> 5 & 31), (int) (iv >> 10 & 31), (int) (iv >> 20 & 31),
                (int) (iv >> 25 & 31), (int) (iv >> 15 & 31)};
        int level;
        int growth = species < nm.growth.length() ? nm.growth.charAt(species) - '0' : 0;
        level = levelFromExp(growth, u32(p, 0x10));
        if (party) {
            int pl = u8(p, 0xEC);
            if (pl >= 1 && pl <= 100) level = pl;
        }
        StringBuilder b = new StringBuilder();
        b.append(nm.species[species].trim()).append(formSuffix(species, form));
        String itemName = item > 0 ? nm.items[item].trim() : "";
        if (!itemName.isEmpty()) b.append(" @ ").append(itemName);
        b.append("\nAbility: ").append(ability > 0 ? nm.abilities[ability].trim() : "");
        b.append("\nLevel: ").append(level);
        StringBuilder evs = new StringBuilder();
        for (int i = 0; i < 6; i++)
            if (ev[i] > 0) evs.append(evs.length() > 0 ? " / " : "").append(ev[i]).append(' ').append(PokeSet.STAT_NAMES[i]);
        if (evs.length() > 0) b.append("\nEVs: ").append(evs);
        b.append('\n').append(NATURES[nature]).append(" Nature");
        StringBuilder ivs = new StringBuilder();
        for (int i = 0; i < 6; i++)
            if (iv6[i] != 31) ivs.append(ivs.length() > 0 ? " / " : "").append(iv6[i]).append(' ').append(PokeSet.STAT_NAMES[i]);
        if (ivs.length() > 0) b.append("\nIVs: ").append(ivs);
        int hpBits = (iv6[0] & 1) | (iv6[1] & 1) << 1 | (iv6[2] & 1) << 2 | (iv6[5] & 1) << 3 | (iv6[3] & 1) << 4 | (iv6[4] & 1) << 5;
        for (int i = 0; i < 4; i++) {
            int mv = u16(p, 0x5A + 2 * i);
            if (mv <= 0 || mv >= nm.moves.length) continue;
            String name = nm.moves[mv].trim();
            if (name.equals("Hidden Power")) name += " " + HP_TYPES[hpBits * 15 / 63];
            b.append("\n- ").append(name);
        }
        return b.toString();
    }
}
