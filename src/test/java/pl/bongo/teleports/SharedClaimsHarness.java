package pl.bongo.teleports;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Three production mods plus GP3D, real encrypted/password-authenticated vanilla clients. */
public final class SharedClaimsHarness {
    static Path root;static final String OWNER="Bcfowner",MATE="Bcfmate",OTHER="Bcfother";
    static UUID uuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)); }
    static void at(Bot bot,double x,double y,double z) throws Exception { cmd("tp "+bot.name+" "+x+" "+y+" "+z);bot.drain(200);bot.events.clear(); }
    static MultiActionDialog dialog(Bot bot) throws Exception { return (MultiActionDialog)((ClientboundShowDialogPacket)bot.await(e->e instanceof ClientboundShowDialogPacket,5000)).dialog().value(); }
    static String label(ActionButton b) {
        try {
            var field=Arrays.stream(b.getClass().getRecordComponents()).filter(c->c.getType()==CommonButtonData.class).findFirst().orElseThrow();
            Object common=field.getAccessor().invoke(b);
            return ((net.minecraft.network.chat.Component)common.getClass().getMethod("label").invoke(common)).getString();
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static ActionButton button(MultiActionDialog dialog,String label) {
        return dialog.actions().stream().filter(b->label(b).startsWith(label)).findFirst()
            .orElseThrow(()->new AssertionError("Missing button "+label+": "+dialog.actions().stream().map(SharedClaimsHarness::label).toList()));
    }
    static CustomAll action(ActionButton button) { return (CustomAll)button.action().orElseThrow(); }
    static void click(Bot bot,ActionButton button) throws Exception { bot.send(new ServerboundCustomClickActionPacket(action(button).id(),Optional.of(new CompoundTag()))); }
    static MultiActionDialog open(Bot bot,boolean global) throws Exception { bot.events.clear();bot.command(global?"manageclaim global":"manageclaim");return dialog(bot); }
    static ActionButton change(Bot bot,MultiActionDialog menu,String flag,String value) throws Exception {
        click(bot,button(menu,flag));var edit=dialog(bot);click(bot,button(edit,value));var confirmation=dialog(bot);
        ActionButton save=button(confirmation,"Zastosuj");click(bot,save);dialog(bot);return save;
    }
    static ActionButton set(Bot bot,boolean global,String flag,String value) throws Exception { return change(bot,open(bot,global),flag,value); }
    static String key(long id,UUID owner) { return "world:"+id+":"+owner; }
    static JsonObject state() throws Exception { return Storage.GSON.fromJson(Files.readString(root.resolve("world/bongo-claim-flags.json")),JsonObject.class); }
    static String local(long id,String flag) throws Exception {
        var claims=state().getAsJsonObject("claims");var row=claims.getAsJsonObject(key(id,uuid(OWNER)));return row==null || !row.has(flag)?null:row.get(flag).getAsString();
    }
    static void nativeValue(long id,String field,boolean value) throws Exception {
        String yaml=Files.readString(root.resolve("plugins/GriefPreventionData/ClaimData/"+id+".yml"));
        check(yaml.lines().anyMatch(l->l.equals(field+": "+value)),"native "+field+" on #"+id+" = "+value);
    }
    static void trust(Bot bot,String permission,boolean expected) throws Exception {
        Path file=root.resolve("gp-"+bot.name+".txt");Files.deleteIfExists(file);cmd("verify-gp "+bot.name);
        check(Files.readString(file).contains(permission+":"+expected+"\n"),bot.name+" native "+permission+"="+expected);
    }
    static String claim(long id,int x1,int x2,UUID owner,boolean explosions,String children) {
        return "Claim ID: "+id+"\nLesser Boundary Corner: world;"+x1+";-64;0\nGreater Boundary Corner: world;"+x2+";319;"+(id==100?10:9)+"\nOwner: '"+(owner==null?"":owner)+"'\nExplosives Allowed: "+explosions+"\nBuilders: []\nContainers: []\nAccessors: []\nManagers: []\n"+children;
    }
    static void fixture() throws Exception {
        if(!Files.exists(root.resolve("claim-suite-fixture.txt"))) throw new IllegalStateException("Not an isolated claim fixture server");
        Files.createDirectories(root.resolve("config"));var gp=root.resolve("plugins/GriefPreventionData");Files.createDirectories(gp.resolve("ClaimData"));
        Files.writeString(gp.resolve("config.yml"),"GriefPrevention:\n  Claims:\n    Mode:\n      world: Survival\n    PvPToggle:\n      Claim:\n        Enabled: true\n      Subdivision:\n        Enabled: false\n");
        try(var files=Files.list(gp.resolve("ClaimData"))) { for(Path file:files.filter(p->p.toString().endsWith(".yml")).toList()) Files.delete(file); }
        String children="Children:\n  '101':\n    Claim ID: 101\n    Lesser Boundary Corner: world;34;1;4\n    Greater Boundary Corner: world;36;3;6\n    Owner: ''\n    Is3D: true\n    inheritNothing: true\n  '102':\n    Claim ID: 102\n    Lesser Boundary Corner: world;34;10;4\n    Greater Boundary Corner: world;36;12;6\n    Owner: ''\n    Is3D: true\n  '103':\n    Claim ID: 103\n    Lesser Boundary Corner: world;34;20;4\n    Greater Boundary Corner: world;36;22;6\n    Owner: ''\n    Is3D: true\n    Denied:\n    - '"+uuid(MATE)+"#build'\n";
        Files.writeString(gp.resolve("ClaimData/100.yml"),claim(100,20,40,uuid(OWNER),false,children));
        Files.writeString(gp.resolve("ClaimData/200.yml"),claim(200,60,69,uuid(MATE),false,""));
        Files.writeString(gp.resolve("ClaimData/300.yml"),claim(300,90,99,uuid(OWNER),true,""));
        Files.writeString(gp.resolve("ClaimData/400.yml"),claim(400,120,129,null,false,""));
        for(String file:List.of("bongo-community-clans.json","bongo-claim-flags.json","bongos-teleports.json")) Files.deleteIfExists(root.resolve("world/"+file));
        Storage.Data legacy=new Storage.Data();legacy.publicPadClaims.add(key(300,uuid(OWNER)));Storage.write(root.resolve("world/bongos-teleports.json"),legacy);
        Storage.Config config=new Storage.Config();config.xpCostEnabled=false;config.tooCloseDistanceMeters=0;config.padWarmupSeconds=1;config.warmupSeconds=1;config.cooldownSeconds=0;
        Storage.write(root.resolve("config/bongos-teleports.json"),config);
    }
    static void withoutUtils() throws Exception {
        if(!Files.exists(root.resolve("claim-suite-fixture.txt"))) throw new IllegalStateException("Not a fixture server");
        try {
            start(root);
            try(Bot a=new Bot(OWNER.toLowerCase(Locale.ROOT),"pl_pl");Bot b=new Bot(MATE.toLowerCase(Locale.ROOT),"pl_pl")) {
                at(a,30.5,1,3.5);at(b,30.5,1,3.5);
                a.command("clan invite "+b.name);a.drain(300);b.command("clan accept");b.drain(400);
                trust(b,"BUILD",true);trust(b,"CONTAINER",true);trust(b,"MANAGE",false);
                a.command("bongoteleports publicpads on");a.message("WŁĄCZONE");
                check(true,"clan native Build Trust and standalone teleport flags work without BongoUtils");
            }
        } finally { stop();Files.write(root.resolve("without-utils-server.log"),logs); }
    }
    static void noGp() throws Exception {
        if(!Files.exists(root.resolve("claim-suite-fixture.txt"))) throw new IllegalStateException("Not a fixture server");
        try {
            start(root);
            try(Bot a=new Bot(OWNER,"pl_pl");Bot c=new Bot(OTHER,"pl_pl")) {
                at(a,.5,1,3.5);at(c,10.5,1,3.5);
                a.command("manageclaim global");a.message("GriefPrevention3D");
                a.command("clan top area");a.message("wymaga GriefPrevention3D");
                check(true,"optional GP absence gives clear panel and ranking errors without breaking other mods");
                a.command("tpa "+OTHER);a.message("Wysłano");c.command("tpaccept "+OWNER);a.message("Pobrano 0 XP");a.drain(200);
                check(Math.abs(a.x-10.5)<.01,"TPA remains operational with all Bongo mods and no GP3D");
                a.events.clear();a.command("clan info");a.await(e->e instanceof ClientboundOpenScreenPacket,4000);
                check(true,"existing clan GUI remains operational without GP3D");
            }
        } finally { stop();Files.write(root.resolve("no-gp-shared-server.log"),logs); }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        root=Path.of(args[0]);
        if(args.length>1 && args[1].equals("without-utils")) { withoutUtils();return; }
        if(args.length>1 && args[1].equals("no-gp")) { noGp();return; }
        fixture();
        try {
            start(root);
            try(Bot a=new Bot(OWNER,"pl_pl");Bot b=new Bot(MATE,"pl_pl");Bot c=new Bot(OTHER,"en_us")) {
                check(a.uuid.equals(uuid(OWNER)),"real BongoUtils encrypted/password login and normalized UUID");
                cmd("gamemode creative "+OWNER);cmd("gamemode creative "+MATE);cmd("gamemode creative "+OTHER);
                at(a,30.5,1,3.5);at(b,30.5,1,3.5);at(c,.5,1,3.5);
                var menu=open(a,false);check(menu.actions().size()==6,"vanilla dialog lists four native and both custom flags");
                check(local(300,"bongos_teleports:public_pads").equals("on"),"legacy public-pad flag imported into shared manager");
                b.command("manageclaim");b.message("Manage Trust");check(true,"untrusted player cannot open claim panel");
                c.command("manageclaim");c.message("not standing in a claim");check(true,"local command rejects wilderness");
                set(a,true,"Eksplozje:","Włączone");nativeValue(100,"Explosives Allowed",true);
                set(a,true,"Eksplozje:","Wyłączone");nativeValue(100,"Explosives Allowed",false);nativeValue(300,"Explosives Allowed",true);
                check("on".equals(local(300,"gp3d:explosions")),"nondefault old GP flags become local exceptions");
                menu=open(a,false);at(a,90.5,1,3.5);ActionButton oldSave=change(a,menu,"Eksplozje:","Włączone");
                check("on".equals(local(100,"gp3d:explosions")),"moving after opening keeps original claim pinned");
                String before=Files.readString(root.resolve("world/bongo-claim-flags.json"));click(a,oldSave);a.message("Nieaktualny");click(b,oldSave);b.message("Nieaktualny");
                check(before.equals(Files.readString(root.resolve("world/bongo-claim-flags.json"))),"replayed and cross-player tokens cannot mutate flags");
                at(a,30.5,1,3.5);a.command("claimexplosions off");a.drain(400);
                set(a,true,"Eksplozje:","Włączone");nativeValue(100,"Explosives Allowed",false);
                set(a,false,"Eksplozje:","Dziedzicz");nativeValue(100,"Explosives Allowed",true);
                a.command("claimexplosions on");a.drain(400);set(a,true,"Eksplozje:","Wyłączone");nativeValue(100,"Explosives Allowed",true);
                check("on".equals(local(100,"gp3d:explosions")),"native GP no-op flag command still creates explicit override");
                set(a,false,"PvP:","Włączone");nativeValue(100,"PvP Toggle Set",true);
                set(a,false,"PvP:","Dziedzicz");check(!Files.readString(root.resolve("plugins/GriefPreventionData/ClaimData/100.yml")).contains("PvP Toggle Set: true"),"reset removes explicit native PvP marker");
                set(a,true,"Publiczne pady", "Włączone");set(a,false,"Publiczne pady","Wyłączone");
                check("off".equals(local(100,"bongos_teleports:public_pads")),"explicit local off takes precedence over owner global on");
                // Real pads consult the shared flag rather than an independent checkbox copy.
                cmd("setblock 0 0 5 stone");cmd("setblock 30 0 5 stone");cmd("setblock 0 1 5 air");cmd("setblock 30 1 5 air");
                at(a,.5,1,3.5);equip(a,"end_portal_frame","pad");IntegrationHarness.click(a,0,0,5);a.message("postawiony");
                at(a,30.5,1,3.5);IntegrationHarness.click(a,30,0,5);a.message("postawiony");
                equip(a,"ender_eye","synchronizer");at(a,.5,1,3.5);IntegrationHarness.click(a,0,1,5);a.message("pierwszy pad");at(a,30.5,1,3.5);IntegrationHarness.click(a,30,1,5);a.message("zsynchronizowane");
                at(c,.5,1.8125,5.5);c.message("Access Trust");check(true,"shared local off blocks real public-pad teleport");
                set(a,false,"Publiczne pady","Dziedzicz");at(c,.5,1,3.5);at(c,.5,1.8125,5.5);c.message("Teleportowano przez pad");c.drain(200);check(Math.abs(c.x-30.5)<.01,"reset inherits global flag and opens actual pad");at(c,10.5,1,3.5);
                a.command("clan create ClaimTeam CT");a.drain(300);a.command("clan invite "+MATE);a.drain(200);b.command("clan accept");b.drain(400);
                at(b,30.5,1,3.5);trust(b,"BUILD",true);trust(b,"CONTAINER",true);trust(b,"ACCESS",true);trust(b,"MANAGE",false);trust(b,"EDIT",false);
                b.command("manageclaim");b.message("Manage Trust");check(true,"clan Build Trust does not grant panel management");
                // Vanilla item placement and a vanilla chest both use the patched GP protection engine.
                cmd("setblock 31 0 5 stone");cmd("setblock 31 1 5 air");equip(b,"stone","unused");IntegrationHarness.click(b,31,0,5);b.drain(250);
                cmd("execute if block 31 1 5 stone run tellraw "+MATE+" \"native-build-ok\"");b.message("native-build-ok");check(true,"clan member actually places vanilla block in another member's claim");
                cmd("setblock 30 1 6 chest");b.events.clear();IntegrationHarness.click(b,30,1,6);
                var opened=(ClientboundOpenScreenPacket)b.await(e->e instanceof ClientboundOpenScreenPacket,5000);
                b.send(new ServerboundContainerClosePacket(opened.getContainerId()));check(true,"clan member actually opens claimed chest");
                at(b,35.5,2,5.5);trust(b,"BUILD",false);at(a,35.5,2,4.5);
                set(a,false,"Dostęp członków", "Build Trust");trust(b,"BUILD",true);
                at(a,35.5,11,4.5);at(b,35.5,11,5.5);trust(b,"BUILD",true);
                set(a,false,"Dostęp członków","Wyłączone");trust(b,"BUILD",false);
                check(true,"restricted and ordinary 3D subdivisions enforce independent local clan access");
                at(a,35.5,21,4.5);at(b,35.5,21,5.5);set(a,false,"Dostęp członków","Build Trust");trust(b,"BUILD",false);
                check(true,"explicit native deny still blocks a local clan Build Trust grant");
                at(a,30.5,1,3.5);set(a,true,"Dostęp członków","Wyłączone");at(b,30.5,1,3.5);trust(b,"BUILD",false);
                set(a,true,"Dostęp członków","Build Trust");trust(b,"BUILD",true);
                a.events.clear();a.command("clan top area");a.message("431 m²");check(true,"ranking counts three player-owned root claims and 431 m², excludes subclaims/admin land");
                // Opening a form is not a lasting permission grant.
                a.command("managetrust "+MATE);a.drain(350);var authorized=open(b,false);a.command("untrust "+MATE);a.drain(350);
                click(b,button(authorized,"Publiczne pady"));b.message("Utracono uprawnienia");check(true,"permission revoked while dialog open is rechecked on response");
                a.command("clan kick "+MATE);a.drain(400);trust(b,"BUILD",false);trust(b,"CONTAINER",false);trust(b,"ACCESS",false);
                a.events.clear();a.command("clan top claims");a.message("331 m²");check(true,"kick revokes live clan trust and removes departing member's land from ranking");
                a.command("accesstrust "+MATE);a.drain(350);trust(b,"ACCESS",true);trust(b,"BUILD",false);check(true,"manual GP trust remains independent of clan membership");
                // Legacy import is one-time: reset must remain reset after restarting all mods.
                at(a,90.5,1,3.5);set(a,false,"Publiczne pady","Dziedzicz");check(local(300,"bongos_teleports:public_pads")==null,"legacy flag can return to inheritance");
                set(a,true,"Eksplozje Withera:","Włączone");
                cmd("adjustbonusclaimblocks "+OWNER+" 10000");at(a,160.5,1,5.5);a.command("claim create 5");a.drain(1400);
                List<Path> created;
                try(var files=Files.list(root.resolve("plugins/GriefPreventionData/ClaimData"))) {
                    created=files.filter(p->p.toString().endsWith(".yml") && !Set.of("100.yml","200.yml","300.yml","400.yml").contains(p.getFileName().toString())).toList();
                }
                check(created.size()==1,"native claim command creates a future player claim");
                long newId=Long.parseLong(created.getFirst().getFileName().toString().replace(".yml",""));nativeValue(newId,"Wither Explosions Allowed",true);
                var future=open(a,false);check(label(button(future,"Publiczne pady")).contains("globalne właściciela"),"new claim inherits custom owner defaults automatically");
            }
            stop();logs.clear();start(root);
            try(Bot a=new Bot(OWNER,"pl_pl");Bot b=new Bot(MATE,"pl_pl")) {
                at(a,30.5,1,3.5);at(b,30.5,1,3.5);var menu=open(a,false);
                check(label(button(menu,"Publiczne pady")).contains("globalne właściciela"),"owner defaults survive real server restart");
                check(local(300,"bongos_teleports:public_pads")==null,"legacy import does not resurrect reset local flag after restart");
                trust(b,"ACCESS",true);trust(b,"BUILD",false);nativeValue(100,"Explosives Allowed",true);
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"no integration or mixin errors after restart"); }
        } finally { stop();Files.write(root.resolve("shared-claims-server.log"),logs); }
    }
}
