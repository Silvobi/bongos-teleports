package pl.bongo.teleports;
import java.nio.file.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Input;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Verifies configured recipe durability, non-destructive reset and final-use breakage. */
public final class DurabilityHarness {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        Path dir=Path.of(args[0]);
        Storage.Config config=new Storage.Config(); config.synchronizerDurability=2;
        Storage.write(dir.resolve("config/bongos-teleports.json"),config);
        try {
            start(dir); check(Files.readString(dir.resolve("recipe-verification.txt")).contains("PASS all 6"),"all crafting orders use configured durability of 2");
            try(Bot p=new Bot("DurabilityTest","pl_pl")) {
                cmd("op DurabilityTest"); cmd("gamemode creative DurabilityTest");
                at(p,30.5,28.5); cmd("setblock 30 1 30 air"); equip(p,"end_portal_frame","pad"); click(p,30,0,30); p.message("postawiony");
                at(p,40.5,28.5); cmd("setblock 40 1 30 air"); click(p,40,0,30); p.message("postawiony");
                equip(p,"ender_eye","synchronizer"); at(p,30.5,28.5); click(p,30,1,30); p.message("pierwszy pad");
                p.send(new ServerboundPlayerInputPacket(new Input(false,false,false,false,false,true,false))); p.drain(100);
                p.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,new BlockPos(30,1,30),Direction.UP)); p.message("Harmonizator TP wyczyszczony");
                check(synchronizer(p).getDamageValue()==0,"shift left click on a block clears selection without charge or removing pad");
                p.send(new ServerboundPlayerInputPacket(Input.EMPTY)); click(p,30,1,30); p.message("pierwszy pad");
                at(p,40.5,28.5); click(p,40,1,30); p.message("zsynchronizowane");
                var first=synchronizer(p); check(first.getMaxDamage()==2 && first.getDamageValue()==1,"first successful synchronization leaves one use");
                click(p,40,1,30); p.message("pierwszy pad"); at(p,30.5,28.5); click(p,30,1,30);
                var completion=p.message("Harmonizator TP zużyty");check(completion.getString().contains("zsynchronizowane"),"synchronization and breakage share one action-bar alert");p.drain(200);
                var last=p.events.stream().filter(e->e instanceof ClientboundContainerSetSlotPacket packet && packet.getSlot()==36).map(e->((ClientboundContainerSetSlotPacket)e).getItem()).reduce((a,b)->b).orElseThrow();
                check(last.isEmpty(),"last successful synchronization breaks the synchronizer in Creative");
                Storage.Data saved=Storage.read(dir.resolve("world/bongos-teleports.json"),Storage.Data.class,null);
                var pads=saved.pads.values().stream().filter(pad->pad.owner.equals(p.uuid.toString())).toList();
                check(pads.size()==2 && pads.stream().allMatch(pad->pad.partner!=null),"pair remains linked after synchronizer breaks");
            }
        } finally { stop(); }
    }
}

