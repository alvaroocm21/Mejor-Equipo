import com.mejorequipo.engine.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Guardado sintético: cifra PK6 con el algoritmo inverso y comprueba que Gen6Save los recupera. */
public class SaveTest {
    static final byte[] BP = {0,1,2,3,0,1,3,2,0,2,1,3,0,3,1,2,0,2,3,1,0,3,2,1,1,0,2,3,1,0,3,2,2,0,1,3,3,0,1,2,2,0,3,1,3,0,2,1,1,2,0,3,1,3,0,2,2,1,0,3,3,1,0,2,2,3,0,1,3,2,0,1,1,2,3,0,1,3,2,0,2,1,3,0,3,1,2,0,2,3,1,0,3,2,1,0,0,1,2,3,0,1,3,2,0,2,1,3,0,3,1,2,0,2,3,1,0,3,2,1,1,0,2,3,1,0,3,2};
    static void w16(byte[] d,int o,int v){d[o]=(byte)v;d[o+1]=(byte)(v>>8);}
    static void w32(byte[] d,int o,long v){w16(d,o,(int)v);w16(d,o+2,(int)(v>>16));}
    static int r16(byte[] d,int o){return (d[o]&255)|((d[o+1]&255)<<8);}
    static void xor(byte[] d,int from,int to,long seed){long s=seed;for(int o=from;o+1<to;o+=2){s=(0x41C64E6DL*s+0x6073L)&0xFFFFFFFFL;int v=r16(d,o)^(int)((s>>16)&0xFFFF);d[o]=(byte)v;d[o+1]=(byte)(v>>8);}}
    static byte[] encrypt(byte[] plain,int len){
        long pv=(r16(plain,0)|((long)r16(plain,2)<<16))&0xFFFFFFFFL; int sv=(int)((pv>>13)&31);
        byte[] e=plain.clone();
        for(int i=0;i<4;i++) System.arraycopy(plain,8+i*56,e,8+BP[sv*4+i]*56,56);
        xor(e,8,232,pv); if(len>232) xor(e,232,len,pv); return e;
    }
    static int idx(String[] a,String n){for(int i=0;i<a.length;i++) if(a[i].trim().equals(n)) return i; throw new RuntimeException(n);}
    static byte[] pk(Gen6Save.Names nm,String sp,int form,String item,String ab,int nat,int[] ev,int[] iv,String[] mv,long exp,int ec,int len){
        byte[] p=new byte[len]; w32(p,0,ec); w16(p,8,idx(nm.species,sp)); w16(p,0x0A,item==null?0:idx(nm.items,item));
        w32(p,0x10,exp); p[0x14]=(byte)idx(nm.abilities,ab); p[0x1C]=(byte)nat; p[0x1D]=(byte)(form<<3);
        p[0x1E]=(byte)ev[0];p[0x1F]=(byte)ev[1];p[0x20]=(byte)ev[2];p[0x21]=(byte)ev[5];p[0x22]=(byte)ev[3];p[0x23]=(byte)ev[4];
        long v=iv[0]|iv[1]<<5|iv[2]<<10|iv[5]<<15|iv[3]<<20|iv[4]<<25; w32(p,0x74,v&0xFFFFFFFFL);
        for(int i=0;i<mv.length;i++) w16(p,0x5A+2*i,idx(nm.moves,mv[i]));
        if(len>232) p[0xEC]=50; return p;
    }
    public static void main(String[] a) throws Exception {
        String d=a[0];
        java.util.function.Function<String,String> rd=f->{try{return new String(Files.readAllBytes(Paths.get(d,f)),StandardCharsets.UTF_8);}catch(Exception e){throw new RuntimeException(e);}};
        Gen6Save.Names nm=Gen6Save.Names.load(rd.apply("gen6_species.txt"),rd.apply("gen6_moves.txt"),rd.apply("gen6_abilities.txt"),rd.apply("gen6_items.txt"),rd.apply("gen6_growth.txt"));
        byte[] save=new byte[Gen6Save.SIZE_ORAS];
        int[] ev={4,252,0,0,0,252}, iv={31,31,31,30,31,31};
        byte[] party=encrypt(pk(nm,"Blaziken",0,"Blazikenite","Blaze",13,ev,iv,new String[]{"Flare Blitz","Close Combat","Swords Dance","Hidden Power"},1059860L,0x12345678,260),260);
        System.arraycopy(party,0,save,0x14200,260); save[0x14200+6*260]=1;
        byte[] box=encrypt(pk(nm,"Landorus",1,"Leftovers","Intimidate",3,ev,iv,new String[]{"Earthquake","U-turn"},800000L,0x0BADF00D,232),232);
        System.arraycopy(box,0,save,0x33000+232*(3*30+5),232);
        byte[] box2=encrypt(pk(nm,"Nidoran-M",0,null,"Poison Point",0,ev,iv,new String[]{"Peck"},1000L,0x00000777,232),232);
        System.arraycopy(box2,0,save,0x33000+232*(30*30+29),232);
        w16(save,0x400+0,idx(nm.items,"Leftovers")); w16(save,0x400+2,2);
        w16(save,0x400+4,idx(nm.items,"Choice Scarf")); w16(save,0x400+6,1);
        w16(save,0x400+0xA70,idx(nm.items,"Occa Berry")); w16(save,0x400+0xA72,3);
        Gen6Save.Result r=Gen6Save.read(save,nm);
        Files.write(Paths.get("/tmp/g6.txt"), r.text().getBytes(StandardCharsets.UTF_8)); System.out.println("party="+r.party+" box="+r.boxed+" bag="+r.bag+" warn="+r.warnings);
        Dex dex=Dex.load(rd.apply("pokedex.tsv"),rd.apply("moves.tsv"));
        List<String> w=new ArrayList<>(); List<PokeSet> box3=ShowdownParser.parse(dex,r.text(),w);
        for(PokeSet p:box3) System.out.println(p.species.name+" lvl "+p.level+" "+p.ability+" "+Arrays.toString(p.stats)+" "+p.moveIds+" hp="+p.hiddenPowerType);
        System.out.println("warn "+w);
    }
}
