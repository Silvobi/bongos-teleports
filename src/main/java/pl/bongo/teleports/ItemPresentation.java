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
    static boolean polish(String language) { return Lang.language(language).equals("pl"); }
    static boolean marked(ItemStack stack) { return Teleports.is(stack,"pad") || Teleports.is(stack,"synchronizer"); }
    private static MutableComponent gray(String text) { return Component.literal(text).withStyle(ChatFormatting.GRAY).withStyle(s->s.withItalic(false)); }
    private static MutableComponent yellow(String text) { return Component.literal(text).withStyle(ChatFormatting.YELLOW).withStyle(s->s.withItalic(false)); }
    private static MutableComponent bind(String key) {
        return gray("[").append(Component.keybind(key)).append(gray("]"));
    }
    static List<Component> description(boolean pad,boolean pl) {
        String locale=pl?"pl_pl":"en_us";
        if(pad) return List.of(gray(Lang.text(locale,"pad_lore_1")),
            gray(Lang.text(locale,"pad_lore_2")).append(yellow(Lang.text(locale,"harmonizer_lore_name"))).append(gray(".")));
        return List.of(gray(Lang.text(locale,"sync_lore_1")),
            yellow(Lang.text(locale,"sync_lore_2")).append(gray(Lang.text(locale,"click"))).append(bind("key.use")),
            gray(Lang.text(locale,"sync_lore_3")),gray(Lang.text(locale,"sync_lore_4")));
    }
    static void apply(ItemStack stack,String language,Storage.Pad selected) {
        if(!marked(stack)) return;
        boolean pad=Teleports.is(stack,"pad"),active=!pad && selected!=null;
        String name=Lang.text(language,pad?"pad_name":"harmonizer_name");
        ChatFormatting color=active?ChatFormatting.YELLOW:ChatFormatting.WHITE;
        stack.set(DataComponents.ITEM_NAME,Component.literal(name).withStyle(color).withStyle(s->s.withItalic(false)));
        Component custom=stack.get(DataComponents.CUSTOM_NAME);
        if(custom!=null) stack.set(DataComponents.CUSTOM_NAME,custom.copy().withStyle(color));
        List<Component> lore=active?List.of(gray(Lang.text(language,"selected",selected.dimension,selected.x,selected.y,selected.z)),
            gray(Lang.text(language,"press")).append(bind("key.use")).append(gray(Lang.text(language,"second_pad")))
                .append(bind("key.sneak")).append(gray(" + ")).append(bind("key.attack")).append(gray(Lang.text(language,"clear_selection"))))
            :description(pad,polish(language));
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
