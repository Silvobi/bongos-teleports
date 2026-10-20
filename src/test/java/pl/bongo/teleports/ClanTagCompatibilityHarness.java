package pl.bongo.teleports;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import static pl.bongo.teleports.IntegrationHarness.*;

/** JK8cvtw: reproduce 1.1.0's startup failure, then load the identical data with the fixed binary. */
public final class ClanTagCompatibilityHarness {
    static final String GRADIENT="&#FF0000R&#BF0000O&#800000B&#400000O&#000000T&#550000A&#AA0000I&#FF0000M";
    static final UUID CLAN=UUID.fromString("00000000-0000-0000-0000-000000001111");
    static void stage(Path root,Path binary) throws Exception {
        if(!Files.exists(root.resolve("claim-suite-fixture.txt")) || !Files.isRegularFile(binary)) throw new IllegalStateException("Not a fixture or missing jar");
        try(var files=Files.list(root.resolve("mods"))) { for(var file:files.filter(p->p.getFileName().toString().startsWith("BongosCommunityClans-") && p.toString().endsWith(".jar")).toList()) Files.delete(file); }
        Files.copy(binary,root.resolve("mods").resolve(binary.getFileName()));
    }
    static void fixture(Path root) throws Exception {
        SharedClaimsHarness.root=root;SharedClaimsHarness.fixture();Files.deleteIfExists(root.resolve("world/bongo-statuses.json"));
        var state=new JsonObject();state.addProperty("schemaVersion",1);var clans=new JsonObject();var clan=new JsonObject();
        clan.addProperty("id",CLAN.toString());clan.addProperty("name","ROBOTAIM");clan.addProperty("tag",GRADIENT);clan.addProperty("color",0xFFFFFF);clan.addProperty("createdAt",1_700_000_000_000L);
        var members=new JsonObject();var users=new JsonObject();
        for(String name:List.of("Bcfowner","Bcfmate")) {
            String uuid=SharedClaimsHarness.uuid(name).toString();var member=new JsonObject();member.addProperty("id",uuid);member.addProperty("name",name);member.addProperty("role",name.equals("Bcfowner")?"FOUNDER":"MEMBER");members.add(uuid,member);
            var user=new JsonObject();user.addProperty("quickchat",false);user.addProperty("ignoreInvites",false);user.addProperty("backedUpTeam",true);user.addProperty("lastName",name);users.add(uuid,user);
        }
        clan.add("members",members);clans.add(CLAN.toString(),clan);state.add("clans",clans);state.add("users",users);state.add("invitations",new JsonObject());
        Files.writeString(root.resolve("world/bongo-community-clans.json"),new GsonBuilder().setPrettyPrinting().create().toJson(state));
    }
    static JsonObject clan(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("clans").getAsJsonObject(CLAN.toString()); }
    static boolean color(Component component,String target,int rgb) {
        return component.visit((style,text)->text.equals(target) && style.getColor()!=null && style.getColor().getValue()==rgb?Optional.of(true):Optional.empty(),Style.EMPTY).orElse(false);
    }
    static void tab(Bot observer,UUID id,String expected) throws Exception {
        long end=System.currentTimeMillis()+4000;
        while(System.currentTimeMillis()<end) { var name=observer.tabs.get(id);if(name!=null && name.getString().equals(expected)) return;observer.poll(50); }
        throw new AssertionError("Expected TAB "+expected+": "+observer.tabs);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        Path root=Path.of(args[0]),oldBinary=Path.of(args[1]),fixedBinary=Path.of(args[2]);fixture(root);
        Path file=root.resolve("world/bongo-community-clans.json");byte[] original=Files.readAllBytes(file);
        check(Files.readString(file).contains("\\u0026#FF0000"),"fixture uses the same escaped per-letter HEX syntax as the server data");
        try {
            stage(root,oldBinary);
            boolean failed=false;
            try { start(root); } catch(AssertionError expected) { failed=logs.stream().anyMatch(l->l.contains("ClanException: bad_tag")); }
            finally { stop();Files.write(root.resolve("tag-compatibility-before.log"),logs); }
            check(failed,"old 1.1.0 reproduces the exact bad_tag startup crash");
            check(Arrays.equals(original,Files.readAllBytes(file)),"failed old startup does not modify clan data");
            logs.clear();stage(root,fixedBinary);start(root);
            check(logs.stream().anyMatch(l->l.contains("1 clans loaded")),"fixed server loads the same existing clan without migration");
            check(Arrays.equals(original,Files.readAllBytes(file)),"fixed startup does not rewrite or truncate the existing file");
            try(Bot a=new Bot("Bcfowner","pl_pl");Bot b=new Bot("Bcfmate","pl_pl")) {
                at(a,30.5,3.5);at(b,30.5,3.5);tab(b,a.uuid,"[ROBOTAIM] Bcfowner");
                var display=b.tabs.get(a.uuid);
                check(color(display,"R",0xFF0000) && color(display,"O",0xBF0000) && color(display,"B",0x800000) && color(display,"M",0xFF0000),"TAB renders the original gradient without displaying formatting codes");
                check(color(display,"[",0x555555) && color(display,"]",0x555555) && color(display,"Bcfowner",0xFFFFFF),"brackets remain dark gray and player nickname remains white");
                a.chat("gradient-public-chat");Component chat=b.message("gradient-public-chat");
                check(chat.getString().contains("[ROBOTAIM]") && color(chat,"R",0xFF0000),"public chat preserves inline tag colors");
                a.command("clan chat gradient-private-chat");check(b.message("gradient-private-chat").getString().contains("[ROBOTAIM]"),"private clan chat remains operational");
                b.events.clear();b.command("clan info");var opened=(ClientboundOpenScreenPacket)b.await(e->e instanceof ClientboundOpenScreenPacket,4000);
                b.send(new ServerboundContainerClosePacket(opened.getContainerId()));check(true,"clan info GUI opens with combined tag and claim data");
                a.command("clan top area");a.message("431 m²");check(true,"claim ranking retains all member-owned plots after tag compatibility merge");
                SharedClaimsHarness.trust(b,"BUILD",true);SharedClaimsHarness.trust(b,"CONTAINER",true);SharedClaimsHarness.trust(b,"MANAGE",false);
                a.command("status Buduję");a.message("Ustawiono status");tab(b,a.uuid,"[Buduję] [ROBOTAIM] Bcfowner");
                a.command("afk");b.message("Bcfowner jest teraz AFK.");tab(b,a.uuid,"[AFK] [ROBOTAIM] Bcfowner");
                a.send(new ServerboundMovePlayerPacket.Pos(30.8,1,3.5,true,false));b.message("Bcfowner nie jest już AFK.");tab(b,a.uuid,"[Buduję] [ROBOTAIM] Bcfowner");
                check(true,"BongoUtils statuses and AFK compose correctly with gradient clan prefix");
                check(clan(file).get("tag").getAsString().equals(GRADIENT) && clan(file).getAsJsonObject("members").size()==2,"clan tag and membership survive normal server writes");
            }
            stop();logs.clear();start(root);
            check(clan(file).get("tag").getAsString().equals(GRADIENT),"gradient clan reloads after another real server restart");
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"fixed restart has no data or mixin errors"); }
        } finally { stop();stage(root,fixedBinary);Files.write(root.resolve("tag-compatibility-after.log"),logs); }
    }
}
