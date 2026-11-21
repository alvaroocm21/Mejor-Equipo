package com.mejorequipo.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Análisis individual de un Pokémon del main: defensas, cobertura, rol, ofensiva, bulk... */
final class Profile {
    // ---- bits de rol ----
    static final long HAZARD = 1L, REMOVAL = 1L << 1, RECOVERY = 1L << 2, PIVOT = 1L << 3, SCREENS = 1L << 4,
            SPEEDCTL = 1L << 5, FAKEOUT = 1L << 6, REDIR = 1L << 7, PRIORITY = 1L << 8, SETUP = 1L << 9,
            STATUS = 1L << 10, PHAZE = 1L << 11, INTIM = 1L << 12, TRICKROOM = 1L << 13, TAILWIND = 1L << 14,
            PROTECT = 1L << 15, CLERIC = 1L << 16, DISRUPT = 1L << 17,
            SUN = 1L << 18, RAIN = 1L << 19, SAND = 1L << 20, SNOW = 1L << 21, ELEC_T = 1L << 22,
            GRASS_T = 1L << 23, MISTY_T = 1L << 24, PSY_T = 1L << 25,
            A_SUN = 1L << 26, A_RAIN = 1L << 27, A_SAND = 1L << 28, A_SNOW = 1L << 29, A_ELEC = 1L << 30,
            SLOW = 1L << 32, FAST = 1L << 33, CHOICE = 1L << 34, SCARF = 1L << 35, SPREAD = 1L << 36;
    static final long WEATHER_MASK = SUN | RAIN | SAND | SNOW;

    static final double REF = 50000.0;

    PokeSet p;
    Dex.Species sp;
    final float[] def = new float[18];
    float[] cov;
    double off, bulk, speed, util, score, baseTotal;
    boolean physAttacker, specAttacker;
    int nPhys, nSpec;
    long roles;
    double naturePenalty;
    String roleLabel = "";
    final List<String> notes = new ArrayList<>();
    final Set<String> typeSet = new HashSet<>();

    private static Set<String> S(String... a) {
        return new HashSet<>(Arrays.asList(a));
    }

    private static final Set<String> HAZ = S("stealthrock", "spikes", "toxicspikes", "stickyweb");
    private static final Set<String> REM = S("rapidspin", "defog", "courtchange", "mortalspin", "tidyup");
    private static final Set<String> REC = S("recover", "roost", "softboiled", "slackoff", "synthesis", "moonlight",
            "morningsun", "shoreup", "milkdrink", "healorder", "wish", "strengthsap", "junglehealing", "lifedew",
            "healpulse", "rest", "lunarblessing", "painsplit", "leechseed", "swallow", "recover");
    private static final Set<String> SCR = S("reflect", "lightscreen", "auroraveil");
    private static final Set<String> SPD = S("icywind", "electroweb", "thunderwave", "glare", "stringshot", "scaryface",
            "stickyweb", "nuzzle", "bulldoze", "lowsweep", "snarl", "mudshot", "rocktomb", "pounce", "toxicthread",
            "tickle");
    private static final Set<String> REDIRM = S("followme", "ragepowder", "allyswitch", "spotlight");
    private static final Set<String> SETUPM = S("swordsdance", "nastyplot", "dragondance", "calmmind", "bulkup",
            "quiverdance", "shellsmash", "tailglow", "coil", "bellydrum", "victorydance", "shiftgear", "workup",
            "curse", "growth", "agility", "irondefense", "amnesia", "cosmicpower", "filletaway", "clangoroussoul",
            "honeclaws", "autotomize", "rockpolish", "geomancy", "noretreat", "stuffcheeks", "tidyup", "howl");
    private static final Set<String> STAT = S("willowisp", "thunderwave", "toxic", "glare", "spore", "sleeppowder",
            "hypnosis", "yawn", "nuzzle", "stunspore", "darkvoid", "lovelykiss", "sing", "poisonpowder", "toxicthread",
            "poisongas", "confuseray", "swagger", "encore");
    private static final Set<String> PHZ = S("roar", "whirlwind", "dragontail", "circlethrow", "haze", "clearsmog");
    private static final Set<String> PROT = S("protect", "detect", "kingsshield", "spikyshield", "banefulbunker",
            "silktrap", "obstruct", "burningbulwark", "maxguard");
    private static final Set<String> CLER = S("healbell", "aromatherapy", "lifedew", "junglehealing");
    private static final Set<String> DISR = S("taunt", "encore", "haze", "imprison", "trick", "switcheroo",
            "knockoff", "disable", "torment", "snatch", "partingshot", "faketears", "screech", "metalsound");
    private static final Set<String> VAR_BP = S("gyroball", "electroball", "lowkick", "grassknot", "heavyslam",
            "heatcrash", "punishment", "return", "hiddenpower", "naturalgift", "flail", "reversal", "storedpower",
            "powertrip", "crushgrip", "wringout", "trumpcard", "fling", "spitup", "present", "magnitude", "beatup",
            "terrainpulse", "weatherball", "judgment", "terablast", "revelationdance", "multiattack", "technoblast",
            "foulplay", "bodypress", "psyblade", "hardpress", "ragefist", "lastrespects", "bitterblade");
    private static final Set<String> ZERO_BP = S("counter", "mirrorcoat", "metalburst", "bide", "endeavor",
            "superfang", "naturesmadness", "ruination", "finalgambit", "painsplit", "guardianofalola");

    static Profile build(Dex dex, PokeSet p, boolean doubles) {
        Profile f = new Profile();
        f.p = p;
        f.sp = p.species;
        String ab = Dex.toId(p.ability), it = Dex.toId(p.item);
        for (int t : f.sp.types) f.typeSet.add(Dex.TYPES[t]);

        // ----- defensas -----
        for (int a = 0; a < 18; a++) {
            float m = 1f;
            for (int t : f.sp.types) m *= Dex.CHART[a][t];
            f.def[a] = m;
        }
        switch (ab) {
            case "levitate": case "eartheater": f.def[8] = 0; break;
            case "flashfire": case "wellbakedbody": f.def[1] = 0; break;
            case "waterabsorb": case "stormdrain": f.def[2] = 0; break;
            case "dryskin": f.def[2] = 0; f.def[1] *= 1.25f; break;
            case "voltabsorb": case "lightningrod": case "motordrive": f.def[3] = 0; break;
            case "sapsipper": f.def[4] = 0; break;
            case "thickfat": f.def[1] *= 0.5f; f.def[5] *= 0.5f; break;
            case "heatproof": case "waterbubble": f.def[1] *= 0.5f; break;
            case "fluffy": f.def[1] *= 2f; break;
            case "purifyingsalt": f.def[13] *= 0.5f; break;
            case "filter": case "solidrock": case "prismarmor":
                for (int a = 0; a < 18; a++) if (f.def[a] > 1) f.def[a] *= 0.75f;
                break;
            case "wonderguard":
                for (int a = 0; a < 18; a++) if (f.def[a] <= 1) f.def[a] = 0;
                break;
            default: break;
        }
        if (it.equals("airballoon")) f.def[8] = 0;

        // ----- ofensiva y cobertura -----
        int combos = dex.typeCombos.size();
        f.cov = new float[combos];
        List<Double> powers = new ArrayList<>();
        for (String mid : p.moveIds) {
            Dex.Move m = dex.moves.get(mid);
            if (m == null) continue;
            if (m.cat == 'N') {
                f.roleFromMove(m, doubles);
                continue;
            }
            f.roleFromMove(m, doubles);
            int mt = moveType(m, p);
            double pw = effPower(m, p, ab, it, doubles);
            if (pw <= 0) continue;
            if (m.cat == 'P') f.nPhys++; else f.nSpec++;
            powers.add(pw);
            if (pw >= REF * 0.2 && !m.fixed && !m.ohko) {
                for (int c = 0; c < combos; c++) {
                    float mult = 1f;
                    for (int t : dex.typeCombos.get(c))
                        mult *= (mid.equals("freezedry") && t == 2) ? 2f : Dex.CHART[mt][t];
                    if (mult > f.cov[c]) f.cov[c] = mult;
                }
            }
            if (m.prio > 0 && !mid.equals("fakeout")) f.roles |= PRIORITY;
            if (m.target.equals("allAdjacentFoes") || m.target.equals("allAdjacent")) f.roles |= SPREAD;
        }
        java.util.Collections.sort(powers, java.util.Collections.reverseOrder());
        double best = powers.size() > 0 ? powers.get(0) : 0, second = powers.size() > 1 ? powers.get(1) : 0;
        f.off = Math.min(1, best / REF) * 0.75 + Math.min(1, second / REF) * 0.25;
        f.physAttacker = f.nPhys > f.nSpec || (f.nPhys == f.nSpec && f.nPhys > 0 && f.sp.base[1] >= f.sp.base[3]);
        f.specAttacker = f.nSpec > f.nPhys || (f.nPhys == f.nSpec && f.nSpec > 0 && f.sp.base[3] > f.sp.base[1]);

        // ----- bulk -----
        double pb = (double) p.stats[0] * p.stats[2], sb = (double) p.stats[0] * p.stats[4];
        if (it.equals("assaultvest")) sb *= 1.5;
        if (it.equals("eviolite")) { pb *= 1.3; sb *= 1.3; }
        switch (ab) {
            case "furcoat": pb *= 2; break;
            case "fluffy": pb *= 1.5; break;
            case "icescales": sb *= 2; break;
            case "marvelscale": pb *= 1.3; break;
            case "multiscale": pb *= 1.3; sb *= 1.3; break;
            case "thickfat": case "sturdy": case "regenerator": case "magicguard": case "naturalcure":
                pb *= 1.12; sb *= 1.12; break;
            default: break;
        }
        double scale = Math.pow(p.level / 100.0, 2);
        f.bulk = Math.min(1, Math.sqrt(pb * sb) / scale / 160000.0);

        // ----- velocidad -----
        double spe = p.stats[5] * 100.0 / p.level;
        if (it.equals("choicescarf")) spe *= 1.5;
        f.speed = 0.5 * Math.min(1, spe / 350.0) + 0.5 * Math.min(1, f.sp.base[5] / 130.0);
        int bst = 0;
        for (int b : f.sp.base) bst += b;
        f.baseTotal = Math.max(0, Math.min(1, (bst - 300) / 300.0)); // 300 -> 0, 600+ -> 1

        // ----- roles por objeto/habilidad -----
        if (it.startsWith("choice")) f.roles |= CHOICE;
        if (it.equals("choicescarf")) f.roles |= SCARF;
        f.roleFromAbility(ab);
        if (it.equals("boosterenergy")) f.roles &= ~(A_SUN | A_ELEC); // ya no depende del clima/terreno
        if (f.sp.base[5] <= 60 && f.off >= 0.35 || (p.ivs[5] == 0 && p.natureDown() == 5 && f.off >= 0.35))
            f.roles |= SLOW;
        if (f.sp.base[5] >= 100 && f.off >= 0.35) f.roles |= FAST;

        // ----- utilidad / puntuación individual -----
        long util = f.roles & (HAZARD | REMOVAL | RECOVERY | PIVOT | SCREENS | SPEEDCTL | FAKEOUT | REDIR | PRIORITY
                | SETUP | STATUS | PHAZE | INTIM | TRICKROOM | TAILWIND | CLERIC | DISRUPT | SUN | RAIN | SAND | SNOW
                | ELEC_T | GRASS_T | MISTY_T | PSY_T);
        f.util = Math.min(1, 0.34 * Long.bitCount(util));
        f.analyzeNature(dex);
        f.score = 0.40 * f.off + 0.25 * f.bulk + 0.12 * f.speed + 0.08 * f.util + 0.15 * f.baseTotal - f.naturePenalty;
        f.label(util);
        f.moveNotes(dex);
        return f;
    }

    static int moveType(Dex.Move m, PokeSet p) {
        if (m.id.equals("hiddenpower") && !p.hiddenPowerType.isEmpty()) {
            int t = Dex.typeIndex(p.hiddenPowerType);
            if (t >= 0) return t;
        }
        return m.type;
    }

    static double effPower(Dex.Move m, PokeSet p, String ab, String it, boolean doubles) {
        if (m.cat == 'N' || m.fixed || m.ohko || ZERO_BP.contains(m.id)) return 0;
        double bp = m.bp;
        if (bp == 0) bp = VAR_BP.contains(m.id) ? defaultVarBp(m.id) : 0;
        if (bp == 0) return 0;
        if (m.multihit) bp *= multiMult(m.id);
        int mt = moveType(m, p);
        double stab = p.species.hasType(mt) ? (ab.equals("adaptability") ? 2.0 : 1.5)
                : (ab.equals("protean") || ab.equals("libero") ? 1.5 : 1.0);
        double stat;
        if (m.id.equals("bodypress")) stat = p.stats[2];
        else if (m.id.equals("foulplay")) stat = 250;
        else stat = m.cat == 'P' ? p.stats[1] : p.stats[3];
        stat = stat * 100.0 / p.level;
        double mod = 1;
        if (m.cat == 'P' && (ab.equals("hugepower") || ab.equals("purepower"))) mod *= 2;
        if (ab.equals("technician") && bp <= 60) mod *= 1.5;
        if (ab.equals("sheerforce")) mod *= 1.15;
        if (ab.equals("toughclaws") && m.contact) mod *= 1.25;
        if (it.equals("choiceband") && m.cat == 'P') mod *= 1.5;
        if (it.equals("choicespecs") && m.cat == 'S') mod *= 1.5;
        if (it.equals("lifeorb")) mod *= 1.3;
        if (it.equals("expertbelt")) mod *= 1.1;
        double acc = m.acc == 0 ? 1.0 : Math.max(0.5, m.acc / 100.0);
        double spread = doubles && (m.target.equals("allAdjacentFoes") || m.target.equals("allAdjacent")) ? 1.3 : 1.0;
        return bp * stab * stat * mod * acc * spread;
    }

    private static double defaultVarBp(String id) {
        switch (id) {
            case "gyroball": case "electroball": case "heavyslam": case "heatcrash": case "foulplay": return 80;
            case "return": case "judgment": case "terablast": case "weatherball": case "terrainpulse": return 90;
            case "bodypress": case "psyblade": case "multiattack": case "technoblast": case "revelationdance": return 85;
            case "lastrespects": case "ragefist": case "hardpress": case "bitterblade": return 75;
            default: return 65;
        }
    }

    private static double multiMult(String id) {
        switch (id) {
            case "doublekick": case "dualwingbeat": case "doubleironbash": case "bonemerang": case "geargrind":
            case "twineedle": case "dualchop": case "doublehit": case "dragondarts": case "twinbeam": return 2;
            case "populationbomb": return 5;
            case "tripleaxel": case "triplekick": return 3.5;
            default: return 2.8;
        }
    }

    private void roleFromMove(Dex.Move m, boolean doubles) {
        String id = m.id;
        if (HAZ.contains(id)) roles |= HAZARD;
        if (REM.contains(id)) roles |= REMOVAL;
        if (REC.contains(id)) roles |= RECOVERY;
        if (m.selfSwitch) roles |= PIVOT;
        if (SCR.contains(id)) roles |= SCREENS;
        if (SPD.contains(id)) roles |= SPEEDCTL;
        if (id.equals("tailwind")) roles |= TAILWIND;
        if (id.equals("trickroom")) roles |= TRICKROOM;
        if (id.equals("fakeout") || id.equals("upperhand")) roles |= FAKEOUT;
        if (REDIRM.contains(id) && !id.equals("allyswitch")) roles |= REDIR;
        if (SETUPM.contains(id)) roles |= SETUP;
        if (STAT.contains(id)) roles |= STATUS;
        if (PHZ.contains(id)) roles |= PHAZE;
        if (PROT.contains(id)) roles |= PROTECT;
        if (CLER.contains(id)) roles |= CLERIC;
        if (DISR.contains(id)) roles |= DISRUPT;
        switch (id) {
            case "raindance": roles |= RAIN; break;
            case "sunnyday": roles |= SUN; break;
            case "sandstorm": roles |= SAND; break;
            case "snowscape": case "hail": case "chillyreception": roles |= SNOW; break;
            case "electricterrain": roles |= ELEC_T; break;
            case "grassyterrain": roles |= GRASS_T; break;
            case "mistyterrain": roles |= MISTY_T; break;
            case "psychicterrain": roles |= PSY_T; break;
            case "solarbeam": case "solarblade": roles |= A_SUN; break;
            case "thunder": case "hurricane": roles |= A_RAIN; break;
            default: break;
        }
    }

    private void roleFromAbility(String ab) {
        switch (ab) {
            case "intimidate": roles |= INTIM; break;
            case "drought": case "orichalcumpulse": case "desolateland": roles |= SUN; break;
            case "drizzle": case "primordialsea": roles |= RAIN; break;
            case "sandstream": roles |= SAND; break;
            case "snowwarning": roles |= SNOW; break;
            case "electricsurge": case "hadroncharge": roles |= ELEC_T; break;
            case "grassysurge": case "seedsower": roles |= GRASS_T; break;
            case "mistysurge": roles |= MISTY_T; break;
            case "psychicsurge": roles |= PSY_T; break;
            case "chlorophyll": case "solarpower": case "protosynthesis": case "flowergift": case "leafguard":
                roles |= A_SUN; break;
            case "swiftswim": case "raindish": roles |= A_RAIN; break;
            case "sandrush": case "sandforce": case "sandveil": roles |= A_SAND; break;
            case "slushrush": case "icebody": case "snowcloak": roles |= A_SNOW; break;
            case "quarkdrive": case "surgesurfer": roles |= A_ELEC; break;
            default: break;
        }
    }

    // ----- naturalezas -----
    private void analyzeNature(Dex dex) {
        int up = p.natureUp(), down = p.natureDown();
        int total = nPhys + nSpec;
        boolean neutral = up < 0;
        String sug = null;
        if (total == 0) {
            if (up == 1 || up == 3) { naturePenalty += 0.05; sug = "no tiene ataques dañinos, la subida a un stat de ataque se desperdicia"; }
        } else {
            if (up == 1 && nPhys == 0) { naturePenalty += 0.12; sug = bestNature(true); }
            if (up == 3 && nSpec == 0) { naturePenalty += 0.12; sug = bestNature(true); }
            if (down == 1 && nPhys >= 2 && !hasId("foulplay")) { naturePenalty += 0.10; sug = bestNature(true); }
            if (down == 3 && nSpec >= 2) { naturePenalty += 0.10; sug = bestNature(true); }
            if (neutral && (p.evs[1] + p.evs[3] + p.evs[5] >= 252)) { naturePenalty += 0.05; sug = bestNature(false); }
        }
        if (down == 5 && sp.base[5] >= 80 && !hasId("trickroom") && !hasId("gyroball") && !hasId("electroball")
                && (roles & SLOW) == 0 && p.ivs[5] != 0) {
            naturePenalty += 0.04;
            if (sug == null) sug = "baja la Velocidad de un Pokémon base " + sp.base[5] + " (¿lo usas en Trick Room?)";
        }
        if (sug != null) notes.add("Naturaleza " + Es.nature(p.nature) + ": " + sug);
        int evSum = 0;
        for (int e : p.evs) evSum += e;
        if (evSum == 0) notes.add("Sin EVs definidos: estadísticas infravaloradas.");
        else if (evSum > 510) notes.add("EVs suman " + evSum + " (máx. 510).");
    }

    private String bestNature(boolean full) {
        boolean phys = physAttacker || (!specAttacker && sp.base[1] >= sp.base[3]);
        boolean fast = sp.base[5] >= 80 && (roles & SLOW) == 0;
        String n = phys ? (fast ? "Jolly" : "Adamant") : (fast ? "Timid" : "Modest");
        return "prueba " + Es.nature(n) + " (usa más movimientos " + (phys ? "físicos" : "especiales") + ").";
    }

    private boolean hasId(String id) {
        return p.moveIds.contains(id);
    }

    private void label(long util) {
        String r;
        boolean offensive = off >= 0.45;
        if ((roles & SETUP) != 0 && offensive) r = "Sweeper (setup)";
        else if (offensive && speed >= 0.7 && bulk < 0.35) r = physAttacker ? "Atacante físico rápido" : "Atacante especial rápido";
        else if (offensive && bulk >= 0.45) r = physAttacker ? "Tanque físico ofensivo" : "Tanque especial ofensivo";
        else if (offensive) r = physAttacker ? "Atacante físico" : "Atacante especial";
        else if (bulk >= 0.45) r = "Pared / Soporte";
        else r = "Soporte";
        if ((roles & PIVOT) != 0) r += " · pivote";
        if ((roles & HAZARD) != 0) r += " · hazards";
        if ((roles & REMOVAL) != 0) r += " · limpia hazards";
        if ((roles & FAKEOUT) != 0) r += " · Fake Out";
        if ((roles & REDIR) != 0) r += " · redirección";
        if ((roles & INTIM) != 0) r += " · Intimidación";
        if ((roles & TRICKROOM) != 0) r += " · Trick Room";
        if ((roles & TAILWIND) != 0) r += " · Tailwind";
        roleLabel = r;
    }

    private void moveNotes(Dex dex) {
        int dmg = 0;
        boolean stab = false;
        for (String id : p.moveIds) {
            Dex.Move m = dex.moves.get(id);
            if (m == null) continue;
            if (m.cat != 'N') {
                dmg++;
                if (sp.hasType(moveType(m, p))) stab = true;
            }
        }
        int bst = 0;
        for (int b : sp.base) bst += b;
        String bases = "Ataque base " + sp.base[1] + ", At. Esp. base " + sp.base[3];
        if (nPhys >= 2 && sp.base[3] >= sp.base[1] + 25) {
            naturePenalty += 0.08;
            notes.add("Usa sobre todo ataques físicos pero rinde más con especiales (" + bases + ").");
        } else if (nSpec >= 2 && sp.base[1] >= sp.base[3] + 25) {
            naturePenalty += 0.08;
            notes.add("Usa sobre todo ataques especiales pero rinde más con físicos (" + bases + ").");
        }
        if (sp.canEvolve) notes.add("Puede evolucionar: sus stats base actuales suman " + bst + " y mejorarán al evolucionar.");
        else if (bst < 400) notes.add("Stats base bajos (total " + bst + "): le costará en combates fuertes.");
        if (p.moveIds.size() < 4) notes.add("Solo tiene " + p.moveIds.size() + " movimientos definidos.");
        if (dmg > 0 && !stab && off >= 0.2) notes.add("Ningún ataque con STAB (mismo tipo).");
    }
}
