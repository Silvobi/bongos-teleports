package pl.bongo.teleports;

import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Actual vanilla 26.3 packets, encryption/password authentication, and all Bongo integrations. */
public final class SocialFeaturesHarness {
    static void clear(Bot... bots) throws Exception { for(var b:bots) { b.drain(100);b.events.clear(); } }
    static void tab(Bot observer,UUID player,String text) throws Exception {
        long end=System.currentTimeMillis()+4000;
        while(System.currentTimeMillis()<end) {
            Component value=observer.tabs.get(player);
            if(value!=null && (text.endsWith(" Bcfowner")?value.getString().equals(text):value.getString().contains(text))) { check(true,"TAB: "+value.getString());return; }
            observer.poll(50);
        }
        throw new AssertionError("Missing TAB "+text+"; "+observer.tabs);
    }
    static boolean color(Component c,String text,ChatFormatting color) {
        return c.visit((style,part)->part.equals(text) && TextColor.fromLegacyFormat(color).equals(style.getColor())?Optional.of(true):Optional.empty(),Style.EMPTY).orElse(false);
    }
    static void noPing(Bot b) throws Exception {
        b.drain(250);
        check(b.events.stream().noneMatch(e->e instanceof ClientboundSoundPacket || e instanceof ClientboundSystemChatPacket p && p.overlay()),b.name+" has no mention sound/overlay");
    }
    static void standalone(Path root) throws Exception {
        try {
            start(root);
            try(Bot a=new Bot("Bcfowner","pl_pl");Bot b=new Bot("Bcfmate","pl_pl")) {
                at(a,.5,3.5);at(b,10.5,3.5);
                cmd("team leave Bcfowner");
                a.command("status Bez dodatków");a.message("Ustawiono status");tab(a,a.uuid,"[Bez dodatków] Bcfowner");tab(b,a.uuid,"[Bez dodatków] Bcfowner");
                a.command("afk");b.message("jest teraz AFK.");tab(b,a.uuid,"[AFK] Bcfowner");
                a.send(new ServerboundMovePlayerPacket.Pos(.8,1,3.5,true,false));b.message("nie jest już AFK.");tab(b,a.uuid,"[Bez dodatków] Bcfowner");
                clear(a,b);a.chat("Hej @Bcfmate");check(color(b.message("Hej @Bcfmate"),"@Bcfmate",ChatFormatting.BLUE),"mentions work without GP3D or clans");
                b.await(e->e instanceof ClientboundSoundPacket,3000);
                a.command("m Bcfmate standalone-pm");b.message("standalone-pm");b.command("r standalone-reply");a.message("standalone-reply");check(true,"private aliases work without GP3D or clans");
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"standalone has no runtime errors"); }
        } finally { stop();Files.write(root.resolve("social-standalone-server.log"),logs); }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        var root=Path.of(args[0]);SharedClaimsHarness.root=root;SharedClaimsHarness.fixture();
        Files.deleteIfExists(root.resolve("world/bongo-statuses.json"));
        if(args.length>1) { standalone(root);return; }
        try {
            start(root);
            try(Bot a=new Bot("Bcfowner","pl_pl");Bot b=new Bot("Bcfmate","pl_pl");Bot c=new Bot("Bcfother","pl_pl")) {
                at(a,.5,3.5);at(b,10.5,3.5);at(c,15.5,3.5);
                b.command("r test");b.message("Nie masz jeszcze rozmówcy");check(true,"reply before first PM gives useful error");
                a.command("clan create SocialTeam ST");a.drain(350);a.command("status Buduję bazę");a.message("Ustawiono status");
                tab(b,a.uuid,"[Buduję bazę] [ST] Bcfowner");
                clear(a,b,c);a.command("afk");Component announcement=b.message("Bcfowner jest teraz AFK.");
                check(color(announcement,"Bcfowner jest teraz AFK.",ChatFormatting.GRAY),"AFK announcement is gray");tab(b,a.uuid,"[AFK] [ST] Bcfowner");
                a.command("status Zwiedzam");a.message("Ustawiono status");b.drain(150);
                check(b.tabs.get(a.uuid).getString().startsWith("[AFK]"),"AFK takes precedence over changed status");
                clear(a,b);a.send(new ServerboundMovePlayerPacket.Rot(45,20,true,false));b.drain(250);
                check(!b.events.stream().anyMatch(e->e instanceof Component t && t.getString().contains("nie jest już AFK")),"camera rotation does not clear AFK");
                a.send(new ServerboundMovePlayerPacket.Pos(.8,1,3.5,true,false));
                Component back=b.message("Bcfowner nie jest już AFK.");check(color(back,"Bcfowner nie jest już AFK.",ChatFormatting.GRAY),"movement announcement is gray");
                tab(b,a.uuid,"[Zwiedzam] [ST] Bcfowner");
                clear(a,b);cmd("verify-idle Bcfowner 299");b.drain(200);
                check(!b.events.stream().anyMatch(e->e instanceof Component t && t.getString().equals("Bcfowner jest teraz AFK.")),"299 seconds is below idle threshold");
                cmd("verify-idle Bcfowner 301");b.message("Bcfowner jest teraz AFK.");tab(b,a.uuid,"[AFK]");check(true,"server tick automatically enters AFK after five elapsed minutes");
                a.command("afk");b.message("Bcfowner nie jest już AFK.");tab(b,a.uuid,"[Zwiedzam]");
                b.drain(150);check(!b.tabs.get(a.uuid).getString().contains("AFK"),"manual AFK off resets idle timer");
                a.command("status");a.message("Usunięto status");tab(b,a.uuid,"[ST] Bcfowner");
                check(!b.tabs.get(a.uuid).getString().contains("Zwiedzam"),"empty status command removes custom status");
                a.command("status "+"x".repeat(33));a.message("1–32");check(true,"oversized status is rejected");
                a.command("status Po restarcie");a.message("Ustawiono status");
                clear(a,b,c);a.chat("Hej @bCfMaTe i @Bcfmate oraz @Bcfother!");
                Component own=a.message("Hej @bCfMaTe");Component mentioned=b.message("Hej @bCfMaTe");Component other=c.message("Hej @bCfMaTe");
                check(color(own,"@bCfMaTe",ChatFormatting.YELLOW) && color(own,"@Bcfother",ChatFormatting.YELLOW),"sender sees all exact mentions in yellow");
                check(color(mentioned,"@bCfMaTe",ChatFormatting.BLUE) && color(mentioned,"@Bcfmate",ChatFormatting.BLUE) && color(mentioned,"@Bcfother",ChatFormatting.YELLOW),"target sees only own mentions blue; lookup ignores case");
                check(color(other,"@Bcfother",ChatFormatting.BLUE) && color(other,"@bCfMaTe",ChatFormatting.YELLOW),"same message has independent colors per recipient");
                b.await(e->e instanceof ClientboundSystemChatPacket p && p.overlay() && p.content().getString().contains("Bcfowner"),3000);
                b.await(e->e instanceof ClientboundSoundPacket,3000);b.drain(100);
                check(b.events.stream().noneMatch(e->e instanceof ClientboundSoundPacket),"duplicate mentions cause one sound per message");
                c.await(e->e instanceof ClientboundSoundPacket,3000);noPing(a);
                var packet=(ClientboundPlayerChatPacket)b.await(e->e instanceof ClientboundPlayerChatPacket,2000);
                check(packet.body().content().equals("Hej @bCfMaTe i @Bcfmate oraz @Bcfother!") && packet.unsignedContent().isPresent(),"original chat body is preserved with separate recipient decoration");
                clear(a,b,c);a.chat("email@Bcfmate @Bcfmate123 @Bcfmate_ @NoSuchPlayer");b.message("email@Bcfmate");noPing(b);
                clear(a,b,c);cmd("verify-filtered");b.message("filtered @Bcfmate");noPing(b);
                var filtered=(ClientboundPlayerChatPacket)b.await(e->e instanceof ClientboundPlayerChatPacket,2000);
                check(!filtered.filterMask().isEmpty() && filtered.unsignedContent().isEmpty(),"filter mask and filtered content remain unchanged");
                clear(a,b,c);a.command("clan quickchat");a.drain(100);a.chat("#prywatne @Bcfmate");a.message("prywatne @Bcfmate");noPing(b);
                check(b.messages.stream().noneMatch(t->t.getString().contains("prywatne @Bcfmate")),"canceled clan-only chat neither leaks nor pings outsiders");
                clear(a,b,c);a.command("m Bcfmate alias-test");b.message("alias-test");noPing(c);
                b.command("r odpowiedź-test");a.message("odpowiedź-test");check(true,"/m is vanilla /msg alias and /r replies to sender");
                a.command("msg Bcfother msg-test");c.message("msg-test");c.command("r msg-reply");a.message("msg-reply");
                b.command("tell Bcfowner tell-test");a.message("tell-test");a.command("r tell-reply");b.message("tell-reply");
                c.command("w Bcfowner whisper-test");a.message("whisper-test");a.command("r whisper-reply");c.message("whisper-reply");
                check(true,"native /msg, /tell and /w update reply partner in both directions");
                clear(c);a.close();c.drain(250);c.command("r offline-test");c.message("Ostatni rozmówca jest offline");check(true,"reply to disconnected UUID fails safely");
            }
            stop();logs.clear();start(root);
            try(Bot a=new Bot("Bcfowner","pl_pl");Bot b=new Bot("Bcfmate","pl_pl")) {
                tab(a,a.uuid,"[Po restarcie] [ST] Bcfowner");tab(b,a.uuid,"[Po restarcie] [ST] Bcfowner");check(!b.tabs.get(a.uuid).getString().contains("AFK"),"custom status persists across restart while AFK is transient");
                b.command("r stale-test");b.message("Nie masz jeszcze rozmówcy");check(true,"reply state is cleared at server restart");
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"no runtime or mixin errors"); }
        } finally { stop();Files.write(root.resolve("social-features-server.log"),logs); }
    }
}
