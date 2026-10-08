package pl.bongo.teleports;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Tests real vanilla protocol clients against both production mods on an isolated server. */
public final class SocialIntegrationHarness {
    static void clear(Bot... bots) throws Exception { for(Bot bot:bots){bot.drain(180);bot.events.clear();} }
    static void absent(Bot bot,String text) throws Exception {
        bot.drain(550);check(bot.events.stream().noneMatch(e -> e instanceof Component c && c.getString().contains(text)),bot.name+" does not receive "+text);
    }
    static ClientboundPlayerInfoUpdatePacket.Entry tab(Bot bot,UUID id) throws Exception {
        bot.drain(550);
        return bot.events.stream().filter(e -> e instanceof ClientboundPlayerInfoUpdatePacket).map(e -> (ClientboundPlayerInfoUpdatePacket)e)
                .flatMap(p -> p.entries().stream()).filter(e -> e.profileId().equals(id)).reduce((a,b)->b).orElseThrow();
    }
    static boolean removed(Bot bot,UUID id) { return bot.events.stream().anyMatch(e -> e instanceof ClientboundPlayerInfoRemovePacket p && p.profileIds().contains(id)); }
    static void suggestions(Bot bot,String query,String forbidden) throws Exception {
        bot.events.clear();bot.send(new ServerboundCommandSuggestionPacket(123,query));
        var reply=(ClientboundCommandSuggestionsPacket)bot.await(e -> e instanceof ClientboundCommandSuggestionsPacket,3000);
        check(reply.suggestions().stream().noneMatch(s -> s.text().contains(forbidden)),"suggestions hide "+forbidden+" for "+query);
    }
    static void log(String text) throws Exception {
        long until=System.currentTimeMillis()+5000;
        do { synchronized(logs){ if(logs.stream().anyMatch(s->s.contains(text)))return; } Thread.sleep(25); }while(System.currentTimeMillis()<until);
        throw new AssertionError("Missing server log: "+text+"; logs="+logs);
    }
    static void statusHides(String hidden,int expected) throws Exception {
        try(var socket=new java.net.Socket("127.0.0.1",port)) {
            socket.setSoTimeout(3000);var body=new java.io.ByteArrayOutputStream();var data=new java.io.DataOutputStream(body);
            varInt(body,0);varInt(body,SharedConstants.getCurrentVersion().protocolVersion());byte[] host="localhost".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            varInt(body,host.length);body.write(host);data.writeShort(port);varInt(body,1);
            var out=socket.getOutputStream();varInt(out,body.size());out.write(body.toByteArray());out.write(new byte[]{1,0});out.flush();
            var input=socket.getInputStream();int length=varInt(input);var bytes=new java.io.ByteArrayInputStream(input.readNBytes(length));
            check(varInt(bytes)==0,"status response packet");String json=new String(bytes.readNBytes(varInt(bytes)),java.nio.charset.StandardCharsets.UTF_8);
            var players=com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("players");
            check(players.get("online").getAsInt()==expected && !players.toString().contains(hidden),"server status hides vanished nick and online count");
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if(!h.areComponentsBound())h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        Path dir=Path.of(args[0]);port=25583;
        if(!Files.exists(dir.resolve(".bongo-social-test")))throw new IllegalArgumentException("Use an isolated server with .bongo-social-test marker");
        Files.createDirectories(dir.resolve("config/bongoutils"));
        Files.writeString(dir.resolve("config/bongoutils/social.json"),"{\"schemaVersion\":1,\"names\":{},\"ignores\":{},\"modes\":{},\"fakeQuit\":[]}");
        Files.deleteIfExists(dir.resolve("world/bongo-community-clans.json"));
        Storage.Config config=new Storage.Config();config.xpCostEnabled=false;config.tooCloseDistanceMeters=0;config.warmupSeconds=4;config.cooldownSeconds=0;
        Storage.write(dir.resolve("config/bongos-teleports.json"),config);
        try {
            start(dir);cmd("gamerule minecraft:spawn_mobs false");
            try(Bot a=new Bot("SQAlice_26","pl_pl");Bot b=new Bot("SQBob_26","en_us");Bot admin=new Bot("SQAdmin_26","pl_pl")) {
                cmd("op SQAdmin_26");at(a,.5,.5);at(b,4.5,.5);at(admin,2.5,4.5);clear(a,b,admin);
                a.command("clan create SocialQA SQ");a.drain(300);a.command("clan invite SQBob_26");b.drain(250);b.command("clan accept");b.drain(300);
                a.command("clan invite SQAdmin_26");admin.drain(250);admin.command("clan accept");admin.drain(300);clear(a,b,admin);
                a.command("vanish");a.message("Unknown or incomplete command");check(true,"ordinary player cannot vanish");
                a.command("ignore SQBob_26");a.message("Ignorowanie włączone");
                var toA=tab(a,b.uuid);var toB=tab(b,a.uuid);
                check(toA.displayName().getString().equals("SQBob_26"),"ignorer still sees original nick in TAB");
                check(toB.displayName().getString().equals("Ignored"),"ignored player sees localized anonymous TAB label");
                check(toA.gameMode()==GameType.CREATIVE && toB.gameMode()==GameType.CREATIVE
                        && toA.latency()==-1 && toB.latency()==-1 && toB.displayName().getStyle().isItalic()
                        && toA.displayName().getStyle().isItalic() && toB.displayName().getStyle().getColor().getValue()==0xAAAAAA,
                        "TAB style and disconnected ping are bilateral without changing client-visible game mode");
                clear(a,b,admin);b.chat("ignore-chat-from-b");absent(a,"ignore-chat-from-b");admin.message("ignore-chat-from-b");
                a.chat("ignore-chat-from-a");absent(b,"ignore-chat-from-a");admin.message("ignore-chat-from-a");
                a.command("clan chat blocked-clan-chat");absent(b,"blocked-clan-chat");admin.message("blocked-clan-chat");check(true,"ignore also filters clan chat");
                b.command("msg SQAlice_26 blocked-msg");b.message("Cannot message");absent(a,"blocked-msg");
                a.command("tell SQBob_26 blocked-alias");a.message("Nie można wysłać");absent(b,"blocked-alias");
                a.command("tpa SQBob_26");a.message("niedostępna");b.command("tpa SQAlice_26");b.message("niedostępna");check(true,"ignore blocks TPA both ways");
                a.command("ignore SQBob_26");a.message("wyłączone");clear(a,b,admin);
                a.command("tpa SQBob_26");b.message("chce się");b.command("tpaccept SQAlice_26");a.message("za 4 sekund");
                b.command("ignore SQAlice_26");b.message("Ignore enabled");a.message("ustawienia ignorowania");check(true,"ignore cancels warm-up immediately");
                b.command("ignore SQAlice_26");b.message("Ignore disabled");clear(a,b,admin);
                cmd("gamemode survival SQAdmin_26");admin.command("semi-vanish");admin.message("SEMI");var semi=tab(a,admin.uuid);check(!semi.listed(),"semi vanish unlists TAB");
                cmd("verify-social-mob SQAdmin_26");log("SOCIAL-MOB: seen=true target=true pickable=true pushable=true");check(true,"semi vanish retains mob targeting and physical interaction");
                suggestions(a,"/msg SQ","SQAdmin_26");suggestions(a,"/tpa SQ","SQAdmin_26");
                suggestions(a,"/clan invite SQ","SQAdmin_26");
                a.command("msg SQAdmin_26 explicit-semi-message");admin.message("explicit-semi-message");check(true,"semi vanish still accepts explicitly named private messages");
                admin.command("semi-vanish");admin.message("VISIBLE");clear(a,b,admin);
                admin.command("vanish fake");admin.message("VANISH");a.message("left the game");b.message("left the game");
                a.drain(300);b.drain(300);check(removed(a,admin.uuid)&&removed(b,admin.uuid),"vanish removes TAB profiles");
                cmd("verify-social-mob SQAdmin_26");log("SOCIAL-MOB: seen=false target=false pickable=false pushable=false");check(true,"full vanish blocks mob sensing, targeting and physical interaction");
                statusHides("SQAdmin_26",2);
                check(a.events.stream().anyMatch(e->e instanceof ClientboundRemoveEntitiesPacket),"vanish removes tracked world entity");
                suggestions(a,"/msg SQ","SQAdmin_26");suggestions(a,"/tpa SQ","SQAdmin_26");
                clear(a,b,admin);a.command("msg SQAdmin_26 full-secret");absent(admin,"full-secret");
                a.command("list");a.drain(300);check(a.events.stream().filter(e->e instanceof Component).noneMatch(e->((Component)e).getString().contains("SQAdmin_26")),"list hides vanished nick");
                admin.chat("vanished-public-chat");absent(a,"vanished-public-chat");absent(b,"vanished-public-chat");
                cmd("setblock 2 1 4 chest");clear(a,b,admin);
                click(admin,2,1,4);admin.await(e->e instanceof ClientboundOpenScreenPacket,3000);a.drain(500);b.drain(500);
                check(a.events.stream().noneMatch(e->e instanceof ClientboundBlockEventPacket),"vanished player opens chest without public lid animation");
                admin.send(new ServerboundContainerClosePacket(1));
                admin.command("vanish");admin.message("VISIBLE");a.message("joined the game");
                a.drain(500);check(a.events.stream().anyMatch(e->e instanceof ClientboundAddEntityPacket p && p.getUUID().equals(admin.uuid)),"unvanish re-pairs world entity");
                var unhidden=tab(a,admin.uuid);check(unhidden.listed(),"unvanish restores TAB listing");
                clear(a,b,admin);admin.command("vanish");admin.message("VANISH");absent(a,"left the game");check(true,"plain vanish emits no fake quit");
                admin.command("vanish");admin.message("VISIBLE");
                a.command("clan transfer SQAdmin_26");a.drain(300);admin.drain(300);
                cmd("experience set SQBob_26 12 levels");cmd("experience set SQAlice_26 27 levels");
                cmd("item replace entity SQBob_26 hotbar.0 with minecraft:diamond 3");cmd("item replace entity SQAlice_26 hotbar.0 with minecraft:emerald 5");
                a.command("ignore SQBob_26");a.message("włączone");
                cmd("bongoutils migrate swap SQAlice_26 SQBob_26");log("Obaj gracze muszą być offline");
                b.command("ignore SQAdmin_26");b.message("Ignore enabled");admin.command("vanish");admin.message("VANISH");
            }
            Thread.sleep(700);
            Path data=dir.resolve("world/players/data");UUID aId=UUID.nameUUIDFromBytes("OfflinePlayer:sqalice_26".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            UUID bId=UUID.nameUUIDFromBytes("OfflinePlayer:sqbob_26".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] aBefore=Files.readAllBytes(data.resolve(aId+".dat")),bBefore=Files.readAllBytes(data.resolve(bId+".dat"));
            cmd("bongoutils migrate move SQAlice_26 SQBob_26");log("Nick docelowy ma dane");
            check(Arrays.equals(aBefore,Files.readAllBytes(data.resolve(aId+".dat"))) && Arrays.equals(bBefore,Files.readAllBytes(data.resolve(bId+".dat"))),"MOVE refuses occupied target without changing files");
            cmd("bongoutils migrate swap SQAlice_26 SQBob_26");log("Migracja SWAP zakończona");
            var aAfter=NbtIo.readCompressed(data.resolve(aId+".dat"),NbtAccounter.unlimitedHeap());var bAfter=NbtIo.readCompressed(data.resolve(bId+".dat"),NbtAccounter.unlimitedHeap());
            check(aAfter.getIntOr("XpLevel",-1)==12 && bAfter.getIntOr("XpLevel",-1)==27,"SWAP exchanges XP in actual playerdata");
            check(aAfter.toString().contains("diamond") && bAfter.toString().contains("emerald"),"SWAP exchanges inventory in actual playerdata");
            cmd("bongoutils migrate override SQAlice_26 SQBob_26");log("Migracja OVERRIDE zakończona");
            check(!Files.exists(data.resolve(aId+".dat")) && NbtIo.readCompressed(data.resolve(bId+".dat"),NbtAccounter.unlimitedHeap()).getIntOr("XpLevel",-1)==12,"OVERRIDE replaces target and consumes source data");
            stop();logs.clear();start(dir);
            try(Bot a=new Bot("SQAlice_26","pl_pl");Bot b=new Bot("SQBob_26","en_us")) {
                a.drain(200);b.drain(200);b.command("ignore");b.message("SQAdmin_26");check(true,"migrated ignore relations reload across restart");
                clear(a,b);
                try(Bot admin=new Bot("SQAdmin_26","pl_pl")) {
                    a.drain(500);b.drain(500);
                    check(a.events.stream().noneMatch(e->e instanceof Component c && c.getString().contains("SQAdmin_26")),"persisted vanish suppresses real join announcement");
                    check(a.events.stream().noneMatch(e->e instanceof ClientboundAddEntityPacket p && p.getUUID().equals(admin.uuid)),"persisted vanish suppresses world spawn for new connection");
                    check(a.events.stream().filter(e->e instanceof ClientboundPlayerInfoUpdatePacket).flatMap(e->((ClientboundPlayerInfoUpdatePacket)e).entries().stream()).noneMatch(e->e.profileId().equals(admin.uuid)),"persisted vanish suppresses TAB initialization");
                    cmd("bongoutils visibility SQAdmin_26 visible");log("Widoczność SQAdmin_26: VISIBLE");check(true,"console can restore offline-migrated or persistent visibility");
                }
            }
            check(logs.stream().noneMatch(s->s.contains("Mixin apply")||s.contains("InjectionError")||s.contains("[Server thread/ERROR]")),"server logs contain no mixin or runtime errors");
        } finally {
            stop();Files.write(dir.resolve("social-integration.log"),logs);
        }
        System.out.println("PASS: social and migration integration");
    }
}
