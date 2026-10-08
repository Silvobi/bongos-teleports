package pl.bongo.teleports;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemPresentationTest {
    @BeforeAll static void boot() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
    }
    static String description(ItemStack stack) { return String.join(" ",stack.get(DataComponents.LORE).lines().stream().map(Component::getString).toList()); }
    static boolean highlighted(ItemStack stack,String phrase) {
        boolean[] match={false};
        stack.get(DataComponents.LORE).lines().forEach(line->line.visit((Style style,String text)-> {
            if(text.equals(phrase) && style.getColor()!=null && style.getColor().getValue()==0xFFFF55) match[0]=true;
            return Optional.empty();
        },Style.EMPTY));return match[0];
    }
    static boolean key(Component component,String name) {
        return component.getContents() instanceof KeybindContents bind && bind.getName().equals(name)
            || component.getSiblings().stream().anyMatch(child->key(child,name));
    }
    @Test void padNamesAndExactDescriptions() {
        ItemStack source=Teleports.item("pad");
        ItemStack pl=ItemPresentation.forViewer(source,"pl_pl"),en=ItemPresentation.forViewer(source,"en_us");
        assertEquals("Pad Teleportacyjny",pl.get(DataComponents.ITEM_NAME).getString());
        assertEquals("Teleporting Pad",en.get(DataComponents.ITEM_NAME).getString());
        assertEquals("Postaw dwa pady i zsynchronizuj je za pomocą Harmonizatora TP.",description(pl));
        assertEquals("Place both pads and sync them with TP Harmonizer.",description(en));
        assertTrue(highlighted(pl,"Harmonizatora TP"));assertTrue(highlighted(en,"TP Harmonizer"));
        assertEquals("Teleporting Pad",source.get(DataComponents.ITEM_NAME).getString());
    }
    @Test void harmonizerNamesLoreHighlightsAndRealKeybind() {
        ItemStack source=Teleports.item("synchronizer");
        ItemStack pl=ItemPresentation.forViewer(source,"pl_pl"),en=ItemPresentation.forViewer(source,"en_us");
        assertEquals("Harmonizator TP",pl.get(DataComponents.ITEM_NAME).getString());
        assertEquals("TP Harmonizer",en.get(DataComponents.ITEM_NAME).getString());
        assertEquals("Służy do synchronizowania dwóch padów teleportacyjnych. Kliknij [key.use] na jeden, a potem na drugi pad, aby zsynchronizować.",description(pl));
        assertEquals("Syncs two teleport pads. Press [key.use] on first and then second pad to sync them.",description(en));
        assertTrue(highlighted(pl,"padów teleportacyjnych"));assertTrue(highlighted(en,"teleport pads"));
        assertTrue(pl.get(DataComponents.LORE).lines().stream().anyMatch(c->key(c,"key.use")));
    }
    @Test void selectedDescriptionAndPreservedGameplayComponents() {
        ItemStack source=Teleports.item("synchronizer");source.setDamageValue(4);
        Storage.Pad selected=new Storage.Pad("owner","minecraft:overworld",10,40,-5);
        ItemPresentation.apply(source,"pl_pl",selected);
        assertEquals(0xFFFF55,source.get(DataComponents.ITEM_NAME).getStyle().getColor().getValue());
        assertTrue(description(source).contains("Wybrano:"));
        assertTrue(source.get(DataComponents.LORE).lines().stream().allMatch(l->l.getStyle().getColor().getValue()==0xAAAAAA));
        assertTrue(source.get(DataComponents.LORE).lines().stream().anyMatch(c->key(c,"key.attack")));
        ItemStack copy=ItemPresentation.forViewer(source,"en_us");
        assertEquals(4,copy.getDamageValue());assertEquals(10,copy.getMaxDamage());
        assertEquals(source.get(DataComponents.CUSTOM_DATA),copy.get(DataComponents.CUSTOM_DATA));
        assertTrue(Boolean.TRUE.equals(copy.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
        assertEquals(4,source.getDamageValue());
        assertSame(copy.getItem(),source.getItem());
    }
    @Test void unrelatedItemsAndOtherLanguages() {
        var stone=new ItemStack(Items.STONE);assertSame(stone,ItemPresentation.forViewer(stone,"pl_pl"));
        assertEquals("TP Harmonizer",ItemPresentation.forViewer(Teleports.item("synchronizer"),"de_de").get(DataComponents.ITEM_NAME).getString());
    }
}
