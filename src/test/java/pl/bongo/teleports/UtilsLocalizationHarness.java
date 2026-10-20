package pl.bongo.teleports;

import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import static pl.bongo.teleports.IntegrationHarness.*;
import static pl.bongo.teleports.SharedClaimsHarness.label;

/** Polish, English and unsupported-language vanilla clients, including configuration-phase authentication. */
public final class UtilsLocalizationHarness {
    static Dialog shown(Bot bot) throws Exception {
        return ((ClientboundShowDialogPacket)bot.await(e->e instanceof ClientboundShowDialogPacket,4000)).dialog().value();
    }
    static MultiActionDialog multi(Bot bot,String command) throws Exception { bot.events.clear();bot.command(command);return (MultiActionDialog)shown(bot); }
    static void notice(Bot bot,String title,String button,String body) throws Exception {
        bot.events.clear();bot.command("rules");var dialog=(NoticeDialog)shown(bot);
        check(dialog.common().title().getString().equals(title) && dialog.action().button().label().getString().equals(button)
            && ((PlainMessage)dialog.common().body().getFirst()).contents().getString().equals(body),bot.name+" rules dialog in selected/fallback language");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        var root=Path.of(args[0]);SharedClaimsHarness.root=root;SharedClaimsHarness.fixture();
        Path file=root.resolve("config/bongoutils/config.json");String previous=Files.readString(file);
        Files.deleteIfExists(root.resolve("world/bongo-statuses.json"));
        // Old configuration has no language section: default server UI must still fall back to English.
        Files.writeString(file,"{\"allowOffline\":true,\"mineSkinApiKey\":\"\"}");
        Path account=root.resolve("config/bongoutils/accounts/bcfother.json");byte[] oldAccount=Files.exists(account)?Files.readAllBytes(account):null;
        Files.deleteIfExists(account); // Force English-fallback registration; the other two clients exercise login.
        try {
            start(root);
            try(Bot pl=new Bot("Bcfowner","pl_pl");Bot en=new Bot("Bcfmate","en_us");Bot de=new Bot("Bcfother","de_de")) {
                for(var bot:List.of(pl,en,de)) {
                    var auth=(MultiActionDialog)bot.configurationDialogs.getFirst();String expected=bot==pl?"BongoUtils • Logowanie":bot==de?"BongoUtils • Registration":"BongoUtils • Login";
                    check(auth.common().title().getString().equals(expected) && label(auth.actions().getFirst()).equals(bot==pl?"Zaloguj":bot==de?"Register":"Log in"),bot.name+" authentication dialog uses client language before world entry");
                }
                at(pl,30.5,3.5);at(en,10.5,3.5);at(de,15.5,3.5);
                for(var bot:List.of(en,de)) {
                    bot.command("r test");bot.message("nobody to reply to");
                    bot.command("status Building a base");bot.message("Status set: Building a base");
                    bot.command("status "+"x".repeat(33));bot.message("Status must contain 1–32");
                }
                check(true,"reply and status confirmations/errors use English fallback");
                var menu=multi(pl,"skin");check(menu.common().title().getString().equals("BongoUtils • Zmiana skina"),"Polish skin menu");
                for(var bot:List.of(en,de)) { menu=multi(bot,"skin");check(menu.common().title().getString().equals("BongoUtils • Change skin") && label(menu.actions().getFirst()).equals("Username (IGN)"),bot.name+" English fallback skin menu"); }
                menu=multi(en,"skin");SharedClaimsHarness.click(en,menu.actions().get(1));var form=(MultiActionDialog)shown(en);
                var fields=new net.minecraft.nbt.CompoundTag();fields.putString("source","http://example.org/skin.png");
                en.send(new ServerboundCustomClickActionPacket(SharedClaimsHarness.action(form.actions().getFirst()).id(),Optional.of(fields)));
                var error=(MultiActionDialog)shown(en);
                check(((PlainMessage)error.common().body().getFirst()).contents().getString().equals("Enter a direct HTTPS URL to a PNG image (port 443)."),"typed PNG validation error is localized without making an HTTP request");
                notice(pl,"Regulamin serwera","Zamknij","Regulamin nie został jeszcze ustawiony.");
                notice(en,"Server rules","Close","The server rules have not been set yet.");
                notice(de,"Server rules","Close","The server rules have not been set yet.");
                SocialFeaturesHarness.clear(pl,en,de);pl.command("afk");pl.message("Bcfowner jest teraz AFK.");en.message("Bcfowner is now AFK.");de.message("Bcfowner is now AFK.");
                check(true,"one AFK event is Polish for Polish recipient and English for other recipients");
                pl.send(new ServerboundMovePlayerPacket.Pos(30.8,1,3.5,true,false));pl.message("Bcfowner nie jest już AFK.");en.message("Bcfowner is no longer AFK.");de.message("Bcfowner is no longer AFK.");
                check(true,"movement clears AFK with per-recipient localized announcement");
                pl.chat("Hello @Bcfmate and @Bcfother");
                for(var bot:List.of(en,de)) bot.await(e->e instanceof ClientboundSystemChatPacket p && p.overlay() && p.content().getString().equals("Bcfowner mentioned you in chat."),3000);
                check(true,"mention hotbar notification uses recipient's language");
                for(var bot:List.of(en,de)) { bot.command("manageclaim");bot.message("not standing in a claim"); }
                check(true,"claim errors use English fallback in wilderness");
                pl.command("managetrust Bcfmate");pl.drain(250);at(en,30.5,3.5);
                menu=multi(en,"manageclaim");check(menu.common().title().getString().equals("Claim / subdivision #100") && label(menu.actions().getFirst()).startsWith("Explosions:"),"claim panel labels use English for authorized English player");
                var forged=SharedClaimsHarness.action(menu.actions().getFirst());de.send(new ServerboundCustomClickActionPacket(forged.id(),Optional.of(new net.minecraft.nbt.CompoundTag())));de.message("Expired claim form");check(true,"cross-player claim form error uses recipient language");
                cmd("op Bcfmate");menu=multi(en,"changepass");check(menu.common().title().getString().equals("BongoUtils • Change password") && label(menu.actions().getFirst()).equals("Check account"),"password management dialog localized for English operator");
                en.command("ign-whitelist list");en.message("IGN whitelist:");check(true,"IGN whitelist command feedback localized for English operator");
                var config=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var rules=new com.google.gson.JsonObject();rules.addProperty("mode","dialog");rules.addProperty("title","Zasady PL");var entries=new com.google.gson.JsonArray();entries.add("Polska zasada.");rules.add("entries",entries);
                var english=new com.google.gson.JsonObject();english.addProperty("title","English rules");var translated=new com.google.gson.JsonArray();translated.add("English rule.");english.add("entries",translated);rules.add("en",english);config.add("rules",rules);Files.writeString(file,config.toString());
                en.command("bongoutils reload");en.message("Reloaded config/bongoutils/config.json.");
                notice(pl,"Zasady PL","Zamknij","1. Polska zasada.");notice(en,"English rules","Close","1. English rule.");notice(de,"English rules","Close","1. English rule.");
                check(true,"configured English rules and unknown-language fallback work after live reload");
                // Changing client language during play must apply on the next interaction.
                de.send(new ServerboundClientInformationPacket(new net.minecraft.server.level.ClientInformation("pl_pl",2,net.minecraft.world.entity.player.ChatVisiblity.FULL,true,127,net.minecraft.world.entity.HumanoidArm.RIGHT,false,true,net.minecraft.server.level.ParticleStatus.ALL)));
                de.drain(150);de.command("status Zmieniony język");de.message("Ustawiono status: Zmieniony język");check(true,"client language change applies without reconnecting");
                cmd("deop Bcfmate");
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"no localization, auth or mixin errors"); }
        } finally { stop();if(oldAccount!=null) Files.write(account,oldAccount);Files.writeString(file,previous);Files.write(root.resolve("utils-localization-server.log"),logs); }
    }
}
