import com.mejorequipo.engine.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Prueba de humo del motor en JVM:  java -cp out RunTest <assets> <main.txt> [dobles] */
public class RunTest {
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        Dex dex = Dex.load(new String(Files.readAllBytes(Paths.get(dir, "pokedex.tsv")), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(Paths.get(dir, "moves.tsv")), StandardCharsets.UTF_8));
        List<String> warn = new ArrayList<>();
        List<PokeSet> box = ShowdownParser.parse(dex, new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8), warn);
        System.out.println("Pokes: " + box.size() + "  avisos: " + warn);
        if (a.length > 3) { // prueba de estrés: caja grande aleatoria
            java.util.Random rnd = new java.util.Random(7);
            List<Dex.Species> all = new ArrayList<>(dex.species.values());
            List<Dex.Move> mv = new ArrayList<>(dex.moves.values());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Integer.parseInt(a[3]); i++) {
                Dex.Species sp = all.get(rnd.nextInt(all.size()));
                sb.append(sp.name).append("\nAbility: ").append(sp.abilities[0]).append("\nEVs: 252 HP / 252 Atk / 4 Spe\nAdamant Nature\n");
                for (int j = 0; j < 4; j++) sb.append("- ").append(mv.get(rnd.nextInt(mv.size())).name).append("\n");
                sb.append("\n");
            }
            box = ShowdownParser.parse(dex, sb.toString(), warn);
            System.out.println("Caja aleatoria: " + box.size());
        }
        TeamBuilder.Options o = new TeamBuilder.Options();
        o.doubles = a.length > 2 && a[2].equals("dobles");
        long t0 = System.currentTimeMillis();
        List<TeamBuilder.TeamResult> res = TeamBuilder.build(dex, box, o);
        System.out.println("Tiempo: " + (System.currentTimeMillis() - t0) + " ms");
        int i = 1;
        for (TeamBuilder.TeamResult r : res) {
            System.out.printf("%n#%d  %.1f  (miembros %.1f, def %.1f, cob %.1f, roles %.1f, sin %.1f)%n", i++, r.score,
                    r.sMembers, r.sDef, r.sCov, r.sRoles, r.sSyn);
            for (TeamBuilder.MemberInfo m : r.info)
                System.out.println("  - " + m.poke.shortLine() + "  [" + m.role + "] " + m.notes);
            for (String s : r.good) System.out.println("  + " + s);
            for (String s : r.bad) System.out.println("  ! " + s);
        }
    }
}
