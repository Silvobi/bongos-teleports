package pl.bongo.teleports;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Runs real vanilla clients against the released universal GP3D jar, never a production world. */
public final class ClaimProtectionHarness {
    static Path root;
    static Storage.Data data() { return Storage.read(root.resolve("world/bongos-teleports.json"),Storage.Data.class,null); }
    static void position(Bot bot,double x,double y,double z) throws Exception {
        cmd("tp "+bot.name+" "+x+" "+y+" "+z); bot.drain(180); bot.events.clear();
    }
    static void flag(Bot owner,String value) throws Exception {
        owner.events.clear(); owner.command("bongoteleports publicpads "+value);
        owner.message(value.equals("on")?"WŁĄCZONE":"WYŁĄCZONE");
    }
    static void step(Bot bot,int x) throws Exception { position(bot,x+.5,1.8125,5.5); }
    static void trust(Bot owner,String command) throws Exception {
        owner.command(command+" PadAlice"); owner.drain(300);
    }
    static void select(Bot bot,int x) throws Exception {
        position(bot,x+.5,1,3.5); click(bot,x,1,5); bot.message("pierwszy pad");
    }
    static void pair(Bot bot,int x) throws Exception {
        position(bot,x+.5,1,3.5); click(bot,x,1,5); bot.message("zsynchronizowane");
    }
    static void fixture() throws Exception {
        var dir=root.resolve("plugins/GriefPreventionData/ClaimData"); Files.createDirectories(dir);
        String owner=UUID.nameUUIDFromBytes("OfflinePlayer:ClaimOwner".getBytes(StandardCharsets.UTF_8)).toString();
        Files.writeString(dir.resolve("100.yml"),"""
            Claim ID: 100
            Lesser Boundary Corner: world;20;-64;0
            Greater Boundary Corner: world;40;319;10
            Owner: OWNER
            Builders: []
            Containers: []
            Accessors: []
            Managers: []
            Children:
              '101':
                Claim ID: 101
                Lesser Boundary Corner: world;35;1;4
                Greater Boundary Corner: world;37;3;6
                Owner: ''
                Builders: []
                Containers: []
                Accessors: []
                Managers: []
                Parent Claim ID: 100
                inheritNothing: true
                Is3D: true
            """.replace("OWNER",owner));
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        root=Path.of(args[0]); fixture();
        Files.deleteIfExists(root.resolve("world/bongos-teleports.json"));
        Storage.Config config=new Storage.Config(); config.xpCostEnabled=false; config.tooCloseDistanceMeters=0;
        config.warmupSeconds=2; config.cooldownSeconds=0; config.padWarmupSeconds=2;
        config.requestTimeoutSeconds=30;
        Storage.write(root.resolve("config/bongos-teleports.json"),config);
        try {
            start(root);
            synchronized(logs) { check(logs.stream().anyMatch(l->l.contains("GriefPrevention3D claim protection enabled")),"native GP3D 19.0.0 adapter detected"); }
            try(Bot a=new Bot("PadAlice","pl_pl"); Bot c=new Bot("ClaimOwner","pl_pl")) {
                cmd("deop PadAlice"); cmd("deop ClaimOwner");
                cmd("gamemode creative PadAlice"); cmd("gamemode creative ClaimOwner");
                cmd("fill -2 0 2 40 0 8 minecraft:stone"); cmd("fill -2 1 2 40 5 8 minecraft:air");
                position(c,30.5,1,3.5); position(a,30.5,1,3.5);
                equip(a,"end_portal_frame","pad"); click(a,30,0,5); a.message("Build Trust");
                check(data().pads.isEmpty(),"foreign claim blocks pad placement without consuming item");
                position(a,.5,1,3.5); click(a,0,0,5); a.message("postawiony");
                check(data().pads.size()==1,"wilderness placement allowed without GP trust");
                a.command("tpa ClaimOwner"); a.message("Access Trust");
                check(true,"TPA denied without destination access");
                trust(c,"accesstrust");
                a.command("tpa ClaimOwner"); a.message("Wysłano"); c.command("tpaccept PadAlice"); a.message("za 2 sekund");
                trust(c,"untrust"); a.message("Access Trust"); a.drain(300);
                check(Math.abs(a.x-.5)<.01 && data().cooldowns.isEmpty(),"revoking Access Trust during TPA warmup cancels teleport and cooldown");
                trust(c,"trust"); position(a,30.5,1,3.5); click(a,30,0,5); a.message("postawiony");
                equip(a,"ender_eye","synchronizer"); select(a,0); pair(a,30);
                check(data().pads.values().stream().allMatch(p->p.partner!=null),"Build Trust permits owner placement and pairing");
                trust(c,"untrust");
                click(a,30,1,5); a.message("Build Trust");
                a.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false,false,false,false,false,true,false)));
                equip(a,"stone_pickaxe","unused");
                a.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,new BlockPos(30,1,5),Direction.UP));
                a.message("Build Trust"); a.send(new ServerboundPlayerInputPacket(net.minecraft.world.entity.player.Input.EMPTY));
                check(data().pads.size()==2,"pad owner cannot pick up pad after Build Trust is revoked");
                step(a,0); a.message("Access Trust"); a.drain(2300);
                check(Math.abs(a.x-.5)<.01,"private claim blocks pad teleport without Access Trust");
                position(a,30.5,1,3.5); a.command("bongoteleports publicpads on"); a.message("Manage Trust");
                check(data().publicPadClaims.isEmpty(),"untrusted player cannot change claim public-pad flag");
                flag(c,"on");
                step(a,0); a.message("Teleportowano przez pad"); a.drain(200);
                check(Math.abs(a.x-30.5)<.01,"per-claim public flag allows untrusted player through pad");
                position(a,.5,1,3.5); a.command("tpa ClaimOwner"); a.message("Access Trust");
                check(true,"public pads do not open TPA");
                step(a,0); a.message("Teleportacja za"); flag(c,"off"); a.message("Access Trust"); a.drain(2200);
                check(Math.abs(a.x-.5)<.01,"disabling public flag during pad warmup cancels arrival");
                flag(c,"on");
                // A restricted 3D subclaim must not inherit the parent public-pad flag.
                position(c,35.5,1,4.5); trust(c,"trust"); position(c,30.5,1,3.5);
                position(a,36.5,1,3.5); equip(a,"end_portal_frame","pad"); click(a,36,0,5); a.message("postawiony");
                equip(a,"ender_eye","synchronizer"); select(a,0); pair(a,36);
                position(c,35.5,1,4.5); trust(c,"untrust");
                step(a,0); a.message("Access Trust");
                check(true,"parent public flag does not open restricted 3D subdivision");
                flag(c,"on");
                position(a,.5,1,3.5); step(a,0); a.message("Teleportowano przez pad"); a.drain(200);
                check(Math.abs(a.x-36.5)<.01,"independent subdivision flag permits landing at its exact Y");
                check(data().publicPadClaims.size()==2,"parent and subdivision have separate persisted flags");
                // Restore the parent pair for restart verification.
                position(c,30.5,1,3.5); trust(c,"trust");
                equip(a,"ender_eye","synchronizer"); select(a,0); pair(a,30); trust(c,"untrust");
            }
            stop(); logs.clear(); start(root);
            try(Bot a=new Bot("PadAlice","pl_pl"); Bot c=new Bot("ClaimOwner","pl_pl")) {
                position(c,30.5,1,3.5); c.command("bongoteleports publicpads status"); c.message("WŁĄCZONE");
                position(a,.5,1,3.5); step(a,0); a.message("Teleportowano przez pad"); a.drain(200);
                check(Math.abs(a.x-30.5)<.01,"public-pad flags and links survive real server restart");
                position(a,.5,1,3.5); a.command("tpa ClaimOwner"); a.message("Access Trust");
                trust(c,"accesstrust"); a.command("tpa ClaimOwner"); a.message("Wysłano");
                c.command("tpaccept PadAlice"); a.message("Pobrano 0 XP");
                check(true,"native Access Trust permits TPA");
            }
        } finally { stop(); Files.write(root.resolve("claim-protection-server.log"),logs); }
    }
}
