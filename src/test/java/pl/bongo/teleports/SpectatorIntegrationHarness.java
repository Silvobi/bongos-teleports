package pl.bongo.teleports;

import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.GameType;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Regression for real spectator mode: inspect initialization, changes, owner controls and entity tracking. */
public final class SpectatorIntegrationHarness {
    private static ClientboundPlayerInfoUpdatePacket.Entry entry(Bot viewer,UUID target,GameType expected) throws Exception {
        var packet=(ClientboundPlayerInfoUpdatePacket)viewer.await(e->e instanceof ClientboundPlayerInfoUpdatePacket p
                && p.entries().stream().anyMatch(x->x.profileId().equals(target) && x.gameMode()==expected),5000);
        return packet.entries().stream().filter(x->x.profileId().equals(target)).findFirst().orElseThrow();
    }
    private static boolean spawn(Object event,UUID target) {
        return event instanceof ClientboundAddEntityPacket add && add.getUUID().equals(target);
    }
    private static void noSpectatorInfo(Bot viewer,UUID target) throws Exception {
        viewer.drain(300);
        check(viewer.events.stream().filter(e->e instanceof ClientboundPlayerInfoUpdatePacket)
                .map(e->(ClientboundPlayerInfoUpdatePacket)e).flatMap(p->p.entries().stream())
                .noneMatch(x->x.profileId().equals(target) && x.gameMode()==GameType.SPECTATOR),
                viewer.name+" never receives the target's real spectator mode");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound())h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        Path dir=Path.of(args[0]);port=25585;
        if(!Files.exists(dir.resolve(".bongo-spectator-test")))throw new IllegalArgumentException("Use an isolated spectator test server");
        try {
            start(dir);
            try(Bot owner=new Bot("SpOwner26","pl_pl");Bot viewer=new Bot("SpViewer26","en_us")) {
                cmd("gamemode creative SpOwner26");cmd("gamemode creative SpViewer26");
                at(owner,5.5,3.5);at(viewer,7.5,3.5);
                var initial=(ClientboundAddEntityPacket)viewer.await(e->spawn(e,owner.uuid),5000);
                entry(viewer,owner.uuid,GameType.CREATIVE);owner.drain(200);viewer.drain(200);
                owner.events.clear();viewer.events.clear();cmd("gamemode spectator SpOwner26");
                owner.await(e->e instanceof ClientboundGameEventPacket p && p.getEvent()==ClientboundGameEventPacket.CHANGE_GAME_MODE
                        && p.getParam()==GameType.SPECTATOR.getId(),5000);
                entry(owner,owner.uuid,GameType.SPECTATOR);check(true,"owner receives real spectator mode and controls");
                var masked=entry(viewer,owner.uuid,GameType.SURVIVAL);
                check(masked.listed() && masked.listOrder()==0 && (masked.displayName()==null || !masked.displayName().getStyle().isItalic()),
                        "spectator keeps an ordinary listed TAB entry without spectator styling or sorting");
                viewer.await(e->e instanceof ClientboundRemoveEntitiesPacket p && p.entityIds().contains(initial.getId()),5000);
                check(true,"entering spectator immediately removes the world entity from observers");
                noSpectatorInfo(viewer,owner.uuid);
                try(Bot late=new Bot("SpLate26","de_de")) {
                    at(late,8.5,3.5);entry(late,owner.uuid,GameType.SURVIVAL);late.drain(300);
                    check(late.events.stream().noneMatch(e->spawn(e,owner.uuid)),"late join gets masked TAB data without the spectator world entity");
                    cmd("gamemode spectator SpLate26");entry(late,late.uuid,GameType.SPECTATOR);
                    noSpectatorInfo(late,owner.uuid);
                    check(late.events.stream().noneMatch(e->spawn(e,owner.uuid)),"another spectator cannot receive an incorrectly rendered target entity");
                }
                viewer.events.clear();cmd("execute in minecraft:the_nether run tp SpOwner26 0 80 0");owner.drain(300);
                cmd("execute in minecraft:overworld run tp SpOwner26 5.5 1 3.5");owner.drain(300);
                noSpectatorInfo(viewer,owner.uuid);
                check(viewer.events.stream().noneMatch(e->spawn(e,owner.uuid)),"dimension round-trip does not reveal the spectator world entity");
                owner.close();Thread.sleep(700);viewer.events.clear();
                try(Bot rejoined=new Bot("SpOwner26","pl_pl")) {
                    entry(rejoined,rejoined.uuid,GameType.SPECTATOR);entry(viewer,rejoined.uuid,GameType.SURVIVAL);
                    viewer.drain(300);check(viewer.events.stream().noneMatch(e->spawn(e,rejoined.uuid)),"spectator reconnect remains masked and untracked");
                    viewer.events.clear();cmd("gamemode creative SpOwner26");
                    entry(viewer,rejoined.uuid,GameType.CREATIVE);entry(rejoined,rejoined.uuid,GameType.CREATIVE);
                    viewer.await(e->spawn(e,rejoined.uuid),5000);check(true,"leaving spectator restores the real normal mode and world entity");
                    viewer.events.clear();cmd("gamemode survival SpOwner26");entry(viewer,rejoined.uuid,GameType.SURVIVAL);
                    cmd("gamemode spectator SpOwner26");entry(viewer,rejoined.uuid,GameType.SURVIVAL);noSpectatorInfo(viewer,rejoined.uuid);
                    check(true,"repeated game-mode changes stay masked");
                }
            }
        } finally { stop(); }
    }
}
