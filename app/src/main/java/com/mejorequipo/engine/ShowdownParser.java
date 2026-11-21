package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee el texto del "main" en formato de exportación de Pokémon Showdown. */
public final class ShowdownParser {
    private static final Pattern NATURE = Pattern.compile("^([A-Za-z]+)\\s+Nature.*$", Pattern.CASE_INSENSITIVE);

    public static List<PokeSet> parse(Dex dex, String text, List<String> warnings) {
        List<PokeSet> out = new ArrayList<>();
        PokeSet cur = null;
        StringBuilder raw = new StringBuilder();
        int moveCount = 0;
        String[] lines = text.replace("\r", "").split("\n");
        for (String line0 : lines) {
            String line = line0.trim();
            if (line.startsWith("﻿")) line = line.substring(1).trim();
            if (line.isEmpty() || line.startsWith("===")) {
                finish(dex, out, cur, raw, warnings);
                cur = null;
                raw.setLength(0);
                moveCount = 0;
                continue;
            }
            boolean meta = isMeta(line);
            if (cur != null && moveCount > 0 && !line.startsWith("-") && !meta) {
                // Un set nuevo sin línea en blanco de separación.
                finish(dex, out, cur, raw, warnings);
                cur = null;
                raw.setLength(0);
                moveCount = 0;
            }
            if (cur == null) {
                cur = new PokeSet();
                parseFirstLine(cur, line);
                raw.append(line).append('\n');
                continue;
            }
            raw.append(line).append('\n');
            if (line.startsWith("-")) {
                String mv = line.substring(1).trim();
                int slash = mv.indexOf('/');
                if (slash > 0) mv = mv.substring(0, slash).trim();
                addMove(cur, mv);
                moveCount++;
            } else {
                parseMeta(cur, line);
            }
        }
        finish(dex, out, cur, raw, warnings);
        return out;
    }

    private static boolean isMeta(String line) {
        String l = line.toLowerCase();
        return l.startsWith("ability:") || l.startsWith("level:") || l.startsWith("evs:") || l.startsWith("ivs:")
                || l.startsWith("tera type:") || l.startsWith("shiny:") || l.startsWith("happiness:")
                || l.startsWith("gigantamax:") || l.startsWith("dynamax level:") || l.startsWith("hidden power:")
                || l.startsWith("pokeball:") || l.startsWith("ball:") || NATURE.matcher(line).matches();
    }

    private static void parseFirstLine(PokeSet p, String line) {
        String name = line;
        int at = line.indexOf(" @ ");
        if (at >= 0) {
            name = line.substring(0, at).trim();
            p.item = line.substring(at + 3).trim();
        }
        name = name.replaceAll("\\s*\\((M|F)\\)\\s*$", "").trim();
        if (name.endsWith(")") && name.contains(" (")) {
            int open = name.lastIndexOf(" (");
            p.nickname = name.substring(0, open).trim();
            p.speciesName = name.substring(open + 2, name.length() - 1).trim();
        } else {
            p.speciesName = name;
        }
    }

    private static void parseMeta(PokeSet p, String line) {
        String l = line.toLowerCase();
        if (l.startsWith("ability:")) p.ability = line.substring(8).trim();
        else if (l.startsWith("level:")) p.level = parseInt(line.substring(6), 100);
        else if (l.startsWith("tera type:")) p.tera = line.substring(10).trim();
        else if (l.startsWith("evs:")) parseSpread(line.substring(4), p.evs, false);
        else if (l.startsWith("ivs:")) parseSpread(line.substring(4), p.ivs, true);
        else if (l.startsWith("hidden power:")) p.hiddenPowerType = line.substring(13).trim();
        else {
            Matcher m = NATURE.matcher(line);
            if (m.matches() && PokeSet.isNature(m.group(1))) p.nature = capitalize(m.group(1));
        }
    }

    private static void parseSpread(String s, int[] target, boolean ivs) {
        for (String part : s.split("/")) {
            String[] t = part.trim().split("\\s+");
            if (t.length < 2) continue;
            int v = parseInt(t[0], ivs ? 31 : 0);
            int idx = statIndex(t[1]);
            if (idx >= 0) target[idx] = v;
        }
    }

    private static int statIndex(String n) {
        switch (n.toLowerCase()) {
            case "hp": return 0;
            case "atk": return 1;
            case "def": return 2;
            case "spa": case "spc": case "spatk": return 3;
            case "spd": case "spdef": return 4;
            case "spe": case "spd.": case "speed": return 5;
            default: return -1;
        }
    }

    private static void addMove(PokeSet p, String mv) {
        String id = Dex.toId(mv);
        if (id.startsWith("hiddenpower")) {
            String t = mv.replaceAll("(?i)hidden\\s*power", "").replaceAll("[\\[\\]\\s]", "");
            if (!t.isEmpty() && Dex.typeIndex(t) >= 0) p.hiddenPowerType = t;
            id = "hiddenpower";
            mv = "Hidden Power" + (p.hiddenPowerType.isEmpty() ? "" : " " + p.hiddenPowerType);
        }
        p.moveNames.add(mv);
        p.moveIds.add(id);
    }

    private static void finish(Dex dex, List<PokeSet> out, PokeSet p, StringBuilder raw, List<String> warnings) {
        if (p == null) return;
        p.raw = raw.toString().trim();
        Dex.Species sp = dex.species.get(Dex.toId(p.speciesName));
        if (sp == null && p.speciesName.contains("-")) {
            String base = p.speciesName.substring(0, p.speciesName.indexOf('-'));
            sp = dex.species.get(Dex.toId(base));
            if (sp != null)
                warnings.add("\"" + p.speciesName + "\" no se reconoce como forma; se usa " + sp.name + ".");
        }
        if (sp == null) {
            warnings.add("Pokémon desconocido, se ignora: \"" + p.speciesName + "\"");
            return;
        }
        Dex.Species mega = dex.megas.get(Dex.toId(p.item) + "|" + sp.id);
        if (mega != null) { // lleva su Megapiedra: se evalúa como Mega
            sp = mega;
            p.ability = mega.abilities[0];
        }
        p.species = sp;
        if (p.ability.isEmpty()) p.ability = sp.abilities[0];
        for (String id : p.moveIds)
            if (!dex.moves.containsKey(id))
                warnings.add(sp.name + ": movimiento desconocido \"" + id + "\"");
        if (p.level < 1 || p.level > 100) p.level = 100;
        p.computeStats();
        out.add(p);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
