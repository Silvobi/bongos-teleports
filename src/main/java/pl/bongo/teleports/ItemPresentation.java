package pl.bongo.teleports;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.display.*;
import com.mojang.datafixers.util.Pair;
import java.util.*;

/** Copy-only presentation: one stored item can be shown in different client languages. */
public final class ItemPresentation {
    private ItemPresentation() {}
    static boolean polish(String language) { return language!=null && language.toLowerCase(Locale.ROOT).startsWith("pl_"); }
    static boolean marked(ItemStack stack) { return Teleports.is(stack,"pad") || Teleports.is(stack,"synchronizer"); }
    private static MutableComponent gray(String text) { return Component.literal(text).withStyle(ChatFormatting.GRAY).withStyle(s->s.withItalic(false)); }
    private static MutableComponent yellow(String text) { return Component.literal(text).withStyle(ChatFormatting.YELLOW).withStyle(s->s.withItalic(false)); }
    private static MutableComponent bind(String key) {
        return gray("[").append(Component.keybind(key)).append(gray("]"));
    }
    static List<Component> description(boolean pad,boolean pl) {
        if(pad) return pl
            ? List.of(gray("Postaw dwa pady i zsynchronizuj je"),gray("za pomocą ").append(yellow("Harmonizatora TP")).append(gray(".")))
            : List.of(gray("Place both pads and sync them with"),yellow("TP Harmonizer").append(gray(".")));
        return pl
            ? List.of(gray("Służy do synchronizowania dwóch"),yellow("padów teleportacyjnych").append(gray(". Kliknij ")).append(bind("key.use")),
                gray("na jeden, a potem na drugi pad,"),gray("aby zsynchronizować."))
            : List.of(gray("Syncs two ").append(yellow("teleport pads")).append(gray(". Press ")).append(bind("key.use")),gray("on first and then second pad to sync them."));
    }
    static void apply(ItemStack stack,String language,Storage.Pad selected) {
        if(!marked(stack)) return;
        boolean pad=Teleports.is(stack,"pad"),pl=polish(language),active=!pad && selected!=null;
        String name=pad?(pl?"Pad Teleportacyjny":"Teleporting Pad"):(pl?"Harmonizator TP":"TP Harmonizer");
        ChatFormatting color=active?ChatFormatting.YELLOW:ChatFormatting.WHITE;
        stack.set(DataComponents.ITEM_NAME,Component.literal(name).withStyle(color).withStyle(s->s.withItalic(false)));
        Component custom=stack.get(DataComponents.CUSTOM_NAME);
        if(custom!=null) stack.set(DataComponents.CUSTOM_NAME,custom.copy().withStyle(color));
        List<Component> lore=active?List.of(gray((pl?"Wybrano: ":"Selected: ")+selected.dimension+" ["+selected.x+", "+selected.y+", "+selected.z+"]"),
            gray(pl?"Kliknij ":"Press ").append(bind("key.use")).append(gray(pl?" na drugi pad. ":" on second pad. "))
                .append(bind("key.sneak")).append(gray(" + ")).append(bind("key.attack")).append(gray(pl?": wyczyść wybór.":": clear selection.")))
            :description(pad,pl);
        stack.set(DataComponents.LORE,new ItemLore(lore));
    }
    public static ItemStack forViewer(ItemStack original,String language) {
        if(!marked(original)) return original;
        ItemStack copy=original.copy();apply(copy,language,Teleports.selectedPad(original));return copy;
    }
    private static SlotDisplay slot(SlotDisplay display,String language) {
        if(display instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            ItemStack source=stack.stack().create();
            if(marked(source)) return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(forViewer(source,language)));
        }
        return display;
    }
    private static RecipeDisplay recipe(RecipeDisplay display,String language) {
        if(display instanceof ShapedCraftingRecipeDisplay shaped) {
            SlotDisplay result=slot(shaped.result(),language);
            if(result!=shaped.result()) return new ShapedCraftingRecipeDisplay(shaped.width(),shaped.height(),shaped.ingredients(),result,shaped.craftingStation());
        } else if(display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            SlotDisplay result=slot(shapeless.result(),language);
            if(result!=shapeless.result()) return new ShapelessCraftingRecipeDisplay(shapeless.ingredients(),result,shapeless.craftingStation());
        }
        return display;
    }
    private static SynchedEntityData.DataValue<?> value(SynchedEntityData.DataValue<?> v,String language) {
        if(v.serializer()==EntityDataSerializers.ITEM_STACK && v.value() instanceof ItemStack stack && marked(stack))
            return new SynchedEntityData.DataValue<>(v.id(),EntityDataSerializers.ITEM_STACK,forViewer(stack,language));
        return v;
    }
    @SuppressWarnings("unchecked")
    public static Packet<?> packet(Packet<?> packet,String language) {
        if(packet instanceof ClientboundContainerSetContentPacket p)
            return new ClientboundContainerSetContentPacket(p.containerId(),p.stateId(),p.items().stream().map(s->forViewer(s,language)).toList(),forViewer(p.carriedItem(),language));
        if(packet instanceof ClientboundContainerSetSlotPacket p && marked(p.getItem()))
            return new ClientboundContainerSetSlotPacket(p.getContainerId(),p.getStateId(),p.getSlot(),forViewer(p.getItem(),language));
        if(packet instanceof ClientboundSetPlayerInventoryPacket p && marked(p.contents()))
            return new ClientboundSetPlayerInventoryPacket(p.slot(),forViewer(p.contents(),language));
        if(packet instanceof ClientboundSetCursorItemPacket p && marked(p.contents()))
            return new ClientboundSetCursorItemPacket(forViewer(p.contents(),language));
        if(packet instanceof ClientboundSetEquipmentPacket p)
            return new ClientboundSetEquipmentPacket(p.getEntity(),p.getSlots().stream().map(e->Pair.of(e.getFirst(),forViewer(e.getSecond(),language))).toList());
        if(packet instanceof ClientboundSetEntityDataPacket p)
            return new ClientboundSetEntityDataPacket(p.id(),p.packedItems().stream().<SynchedEntityData.DataValue<?>>map(v->value(v,language)).toList());
        if(packet instanceof ClientboundRecipeBookAddPacket p) {
            var entries=p.entries().stream().map(entry -> {
                var c=entry.contents();var display=recipe(c.display(),language);
                if(display==c.display()) return entry;
                return new ClientboundRecipeBookAddPacket.Entry(new RecipeDisplayEntry(c.id(),display,c.group(),c.category(),c.craftingRequirements()),entry.flags());
            }).toList();
            return new ClientboundRecipeBookAddPacket(entries,p.replace());
        }
        if(packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> packets=new ArrayList<>();
            for(var p:bundle.subPackets()) packets.add((Packet<? super ClientGamePacketListener>)packet(p,language));
            return new ClientboundBundlePacket(packets);
        }
        return packet;
    }
}
