package pl.bongo.teleports;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.display.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import static pl.bongo.teleports.IntegrationHarness.*;

/** Two vanilla clients inspect the same stored items in different languages. */
public final class LanguageHarness {
    static List<ItemStack> stacks(Object p) {
        if(p instanceof ClientboundContainerSetContentPacket c) { var result=new ArrayList<>(c.items());result.add(c.carriedItem());return result; }
        if(p instanceof ClientboundContainerSetSlotPacket c) return List.of(c.getItem());
        if(p instanceof ClientboundSetCursorItemPacket c) return List.of(c.contents());
        if(p instanceof ClientboundSetPlayerInventoryPacket c) return List.of(c.contents());
        return List.of();
    }
    static ItemStack received(Bot p,String name) throws Exception {
        Object event=p.await(e->stacks(e).stream().anyMatch(s->s.get(DataComponents.ITEM_NAME)!=null && s.get(DataComponents.ITEM_NAME).getString().equals(name)),5000);
        return stacks(event).stream().filter(s->s.get(DataComponents.ITEM_NAME)!=null && s.get(DataComponents.ITEM_NAME).getString().equals(name)).findFirst().orElseThrow();
    }
    static String lore(ItemStack s) { return ItemPresentationTest.description(s); }
    static List<ItemStack> recipeResults(ClientboundRecipeBookAddPacket p) {
        return p.entries().stream().map(e->e.contents().display().result()).filter(d->d instanceof SlotDisplay.ItemStackSlotDisplay)
            .map(d->((SlotDisplay.ItemStackSlotDisplay)d).stack().create()).toList();
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        Path dir=Path.of(args[0]);Storage.write(dir.resolve("config/bongos-teleports.json"),new Storage.Config());
        try {
            start(dir);
            try(Bot pl=new Bot("ItemPL","pl_pl");Bot en=new Bot("ItemEN","en_us")) {
                cmd("op ItemPL");cmd("op ItemEN");at(pl,5.5,3.5);at(en,7.5,3.5);pl.events.clear();en.events.clear();
                equip(pl,"end_portal_frame","pad");equip(en,"end_portal_frame","pad");
                ItemStack a=received(pl,"Pad Teleportacyjny"),b=received(en,"Teleporting Pad");
                check(lore(a).equals("Postaw dwa pady i zsynchronizuj je za pomocą Harmonizatora TP."),"Polish pad name and exact description");
                check(lore(b).equals("Place both pads and sync them with TP Harmonizer."),"English pad name and exact description");
                check(ItemPresentationTest.highlighted(a,"Harmonizatora TP") && ItemPresentationTest.highlighted(b,"TP Harmonizer"),"pad lore highlights the correct harmonizer phrase in yellow");
                equip(pl,"ender_eye","synchronizer");equip(en,"ender_eye","synchronizer");
                a=received(pl,"Harmonizator TP");b=received(en,"TP Harmonizer");
                check(lore(a).contains("Służy do synchronizowania dwóch padów teleportacyjnych."),"Polish harmonizer description");
                check(lore(b).contains("Syncs two teleport pads."),"English harmonizer description");
                check(ItemPresentationTest.highlighted(a,"padów teleportacyjnych") && ItemPresentationTest.highlighted(b,"teleport pads"),"harmonizer highlights the correct pad phrase in yellow");
                check(a.get(DataComponents.LORE).lines().stream().anyMatch(c->ItemPresentationTest.key(c,"key.use"))
                    && b.get(DataComponents.LORE).lines().stream().anyMatch(c->ItemPresentationTest.key(c,"key.use")),"both languages transmit a real client keybind instead of literal BIND");
                decodeRecipeBook=true;
                pl.events.clear();en.events.clear();cmd("recipe give ItemPL bongos_teleports:teleport_pad");cmd("recipe give ItemEN bongos_teleports:teleport_pad");
                var rp=(ClientboundRecipeBookAddPacket)pl.await(e->e instanceof ClientboundRecipeBookAddPacket p && recipeResults(p).stream().anyMatch(s->Teleports.is(s,"pad")),5000);
                var re=(ClientboundRecipeBookAddPacket)en.await(e->e instanceof ClientboundRecipeBookAddPacket p && recipeResults(p).stream().anyMatch(s->Teleports.is(s,"pad")),5000);
                check(recipeResults(rp).stream().anyMatch(s->s.get(DataComponents.ITEM_NAME).getString().equals("Pad Teleportacyjny"))
                    && recipeResults(re).stream().anyMatch(s->s.get(DataComponents.ITEM_NAME).getString().equals("Teleporting Pad")),"recipe-book preview is localized for each viewer");
                cmd("item replace entity ItemPL weapon.mainhand with air");cmd("item replace entity ItemEN weapon.mainhand with air");
                cmd("setblock 5 1 5 chest");cmd("item replace block 5 1 5 container.0 with end_portal_frame[custom_data={bongos_teleports:\"pad\"}] 2");
                cmd("item replace block 5 1 5 container.1 with ender_eye[custom_data={bongos_teleports:\"synchronizer\"}] 1");
                pl.events.clear();en.events.clear();click(pl,5,1,5);click(en,5,1,5);
                var cp=(ClientboundContainerSetContentPacket)pl.await(e->e instanceof ClientboundContainerSetContentPacket c && c.containerId()>0,5000);
                var ce=(ClientboundContainerSetContentPacket)en.await(e->e instanceof ClientboundContainerSetContentPacket c && c.containerId()>0,5000);
                check(cp.items().get(0).get(DataComponents.ITEM_NAME).getString().equals("Pad Teleportacyjny")
                    && ce.items().get(0).get(DataComponents.ITEM_NAME).getString().equals("Teleporting Pad"),"the same chest slot has independent Polish and English names");
                check(cp.items().get(1).get(DataComponents.ITEM_NAME).getString().equals("Harmonizator TP")
                    && ce.items().get(1).get(DataComponents.ITEM_NAME).getString().equals("TP Harmonizer"),"shared harmonizer is localized without changing the stored stack");
                pl.events.clear();pl.send(new ServerboundClientInformationPacket(new ClientInformation("en_us",2,ChatVisiblity.FULL,true,127,HumanoidArm.RIGHT,false,true,ParticleStatus.ALL)));
                var changed=(ClientboundContainerSetContentPacket)pl.await(e->e instanceof ClientboundContainerSetContentPacket c && c.containerId()==cp.containerId(),5000);
                check(changed.items().get(0).get(DataComponents.ITEM_NAME).getString().equals("Teleporting Pad"),"language change refreshes the open chest without reconnecting");
                pl.events.clear();en.events.clear();
                pl.send(new ServerboundContainerClickPacket(cp.containerId(),changed.stateId(),(short)0,(byte)0,ContainerInput.PICKUP,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
                Object pickup=pl.await(e->e instanceof ClientboundSetCursorItemPacket c && Teleports.is(c.contents(),"pad")
                    || e instanceof ClientboundContainerSetContentPacket content && Teleports.is(content.carriedItem(),"pad"),5000);
                ItemStack carried=pickup instanceof ClientboundSetCursorItemPacket c?c.contents():((ClientboundContainerSetContentPacket)pickup).carriedItem();
                check(carried.getCount()==2 && carried.get(DataComponents.ITEM_NAME).getString().equals("Teleporting Pad"),"localized chest items can be picked up with the correct count");
                int state=pickup instanceof ClientboundContainerSetContentPacket c?c.stateId():changed.stateId();
                pl.send(new ServerboundContainerClickPacket(cp.containerId(),state,(short)2,(byte)0,ContainerInput.PICKUP,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
                var moved=(ClientboundContainerSetSlotPacket)en.await(e->e instanceof ClientboundContainerSetSlotPacket c && c.getContainerId()==ce.containerId() && c.getSlot()==2 && Teleports.is(c.getItem(),"pad"),5000);
                check(moved.getItem().getCount()==2 && moved.getItem().get(DataComponents.ITEM_NAME).getString().equals("Teleporting Pad"),"moving the shared stack preserves both items and viewer-local text");
                pl.send(new ServerboundContainerClosePacket(cp.containerId()));en.send(new ServerboundContainerClosePacket(ce.containerId()));
            }
        } finally { stop(); }
    }
}
