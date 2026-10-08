package pl.bongo.teleports;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundSource;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Validates sound packets, chat/action-bar routing and countdown priority with vanilla clients. */
public final class FeedbackHarness {
    static boolean sound(Object event,String id) {
        return event instanceof ClientboundSoundPacket p && p.getSound().value().location().toString().equals("minecraft:"+id);
    }
    static void heard(Bot p,String id,String label) throws Exception {
        p.await(e->sound(e,id),5000);check(true,label);
    }
    static void alert(Bot p,String text,boolean overlay,String label) throws Exception {
        p.await(e->e instanceof ClientboundSystemChatPacket chat && chat.overlay()==overlay && chat.content().getString().contains(text),5000);check(true,label);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        Path dir=Path.of(args[0]);Files.deleteIfExists(dir.resolve("world/bongos-teleports.json"));Storage.Config cfg=new Storage.Config();cfg.tooCloseDistanceMeters=0;cfg.cooldownSeconds=0;cfg.warmupSeconds=3;
        cfg.expensiveTeleportThresholdXp=5;cfg.requestTimeoutSeconds=10;cfg.padWarmupSeconds=3;
        Storage.write(dir.resolve("config/bongos-teleports.json"),cfg);
        try {
            start(dir);
            try(Bot a=new Bot("FBAlice","pl_pl");Bot b=new Bot("FBBob","pl_pl")) {
                cmd("op FBAlice");cmd("op FBBob");at(a,.5,.5);at(b,3.5,.5);cmd("experience add FBAlice 1000 points");
                a.events.clear();b.events.clear();a.command("tpa MissingSoundUser");alert(a,"nie ma na serwerze",true,"ordinary errors are action-bar packets");
                heard(a,"block.note_block.bass","error produces bass sound");b.drain(300);
                check(b.events.stream().noneMatch(e->sound(e,"block.note_block.bass")),"private error sound is not broadcast to a nearby player");
                a.command("ignore-tpa");a.message("włączone");a.drain(3000);
                at(b,60.5,.5);a.events.clear();b.events.clear();a.command("tpa FBBob");
                alert(a,"/confirm-tpa",false,"expensive-cost instruction stays in chat");a.drain(450);
                check(a.events.stream().filter(e->sound(e,"block.note_block.bell")).count()==2,"high-cost warning plays two separated bell pulses");
                a.command("confirm-tpa");alert(a,"Wysłano",true,"sent request confirmation is above hotbar");
                alert(b,"/tpaccept FBAlice",false,"incoming request with commands remains in chat");heard(b,"block.note_block.pling","incoming request has pling sound");
                a.events.clear();b.command("tpaccept FBAlice");
                alert(b,"zaakceptowana",true,"recipient acceptance is above hotbar");
                alert(a,"za 3 sekund",true,"TPA countdown is above hotbar");
                a.command("ignore-tpa");a.drain(450);
                check(a.events.stream().noneMatch(e->e instanceof ClientboundSystemChatPacket chat && chat.overlay() && chat.content().getString().contains("Ignorowanie")),"routine toggle alert does not interrupt countdown");
                alert(a,"Pobrano 6 XP",true,"teleport success and exact charge appear above hotbar");a.drain(200);
                var ticks=a.events.stream().filter(e->sound(e,"block.note_block.hat")).map(e->(ClientboundSoundPacket)e).filter(p->p.getSource()==SoundSource.PLAYERS).toList();
                check(ticks.size()==3 && ticks.get(0).getPitch()<ticks.get(2).getPitch(),"TPA ticks once per second and rises in pitch");
                heard(a,"entity.enderman.teleport","teleport sound is delivered");
                check(a.events.stream().noneMatch(e->e instanceof ClientboundSystemChatPacket chat && !chat.overlay() && (chat.content().getString().contains("Pobrano")||chat.content().getString().contains("Ignorowanie"))),"routine statuses do not leak into chat");
                alert(a,"Ignorowanie",true,"deferred routine confirmation is shown after the teleport result");
                a.command("bongoteleports");alert(a,"/tpaccept",false,"command help remains in chat");
                a.events.clear();a.command("verify-feedback-craft pad");heard(a,"entity.experience_orb.pickup","taking crafted pad produces crafting sound");
                a.drain(300);a.events.clear();a.command("verify-feedback-craft synchronizer");heard(a,"entity.experience_orb.pickup","taking crafted synchronizer produces crafting sound");
                at(a,50.5,48.5);cmd("setblock 50 1 50 air");cmd("setblock 60 1 50 air");equip(a,"end_portal_frame","pad");a.events.clear();click(a,50,0,50);
                alert(a,"postawiony",true,"pad placed notification is above hotbar");heard(a,"block.stone.place","placing pad produces stone sound");
                at(a,60.5,48.5);click(a,60,0,50);a.message("postawiony");equip(a,"ender_eye","synchronizer");
                at(a,50.5,48.5);click(a,50,1,50);alert(a,"pierwszy pad",true,"selection instruction without a command is above hotbar");heard(a,"block.amethyst_block.chime","selecting pad produces chime");
                at(a,60.5,48.5);click(a,60,1,50);a.message("zsynchronizowane");heard(a,"block.enchantment_table.use","synchronization produces enchantment sound");
                at(b,52.5,48.5);a.events.clear();b.events.clear();cmd("tp FBAlice 50.5 1.8125 50.5");alert(a,"Teleportacja za 3 s",true,"first pad countdown appears");
                at(a,50.5,48.5);alert(a,"opuściłeś pad",true,"pad cancellation is one action-bar message");
                heard(a,"block.beacon.deactivate","leaving pad produces deactivation sound");
                heard(b,"block.beacon.deactivate","pad cancellation sound is spatial and reaches nearby players");
                a.events.clear();cmd("tp FBAlice 50.5 1.8125 50.5");alert(a,"Teleportacja za 3 s",true,"pad countdown is above hotbar");heard(a,"block.beacon.activate","pad countdown starts with beacon activation");
                alert(a,"Teleportowano przez pad",true,"pad success is one action-bar alert");a.drain(150);
                var padTicks=a.events.stream().filter(e->sound(e,"block.note_block.hat")).map(e->(ClientboundSoundPacket)e).filter(p->p.getSource()==SoundSource.BLOCKS).toList();
                check(padTicks.size()==3 && padTicks.get(0).getPitch()<padTicks.get(2).getPitch(),"pad ticks are spatial and occur once per second");
                at(a,60.5,48.5);heard(a,"block.amethyst_block.chime","leaving dead zone produces unlock sound");
                cfg.sounds.get("tpa_error").sound="minecraft:block.note_block.bell";cfg.sounds.get("tpa_error").volume=.2f;cfg.sounds.get("tpa_error").pitch=.8f;
                Storage.write(dir.resolve("config/bongos-teleports.json"),cfg);a.command("bongoteleports reload");alert(a,"przeładowana",true,"reload result is above hotbar for a player operator");
                a.events.clear();a.command("tpa MissingSoundUser");alert(a,"nie ma na serwerze",true,"error remains an action-bar message after reload");
                var custom=(ClientboundSoundPacket)a.await(e->sound(e,"block.note_block.bell"),3000);
                check(Math.abs(custom.getVolume()-.2f)<.001 && Math.abs(custom.getPitch()-.8f)<.001,"config changes sound ID, volume and pitch");
                cfg.sounds.get("tpa_error").enabled=false;Storage.write(dir.resolve("config/bongos-teleports.json"),cfg);cmd("bongoteleports reload");a.drain(300);a.events.clear();
                a.command("tpa MissingSoundUser");a.message("nie ma na serwerze");a.drain(300);check(a.events.stream().noneMatch(e->sound(e,"block.note_block.bell")),"per-event sound disable works");
                cfg.soundsEnabled=false;Storage.write(dir.resolve("config/bongos-teleports.json"),cfg);cmd("bongoteleports reload");a.drain(300);a.events.clear();a.command("ignore-tpa");a.message("włączone");a.drain(200);
                check(a.events.stream().noneMatch(e->e instanceof ClientboundSoundPacket),"global sound disable works");
                cfg.soundsEnabled=true;cfg.sounds.get("tpa_error").sound="minecraft:this_sound_does_not_exist";Storage.write(dir.resolve("config/bongos-teleports.json"),cfg);
                a.command("bongoteleports reload");alert(a,"Nieznany dźwięk",true,"invalid sound rejects reload with an action-bar error");
            }
        } finally { stop(); }
    }
}
