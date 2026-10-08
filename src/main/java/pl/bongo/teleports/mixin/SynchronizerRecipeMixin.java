package pl.bongo.teleports.mixin;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.teleports.Teleports;

@Mixin(ShapelessRecipe.class)
public abstract class SynchronizerRecipeMixin {
    @Inject(method="assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"))
    private void teleports$durability(CraftingInput input,CallbackInfoReturnable<ItemStack> ci) {
        Teleports.initializeCraftedSynchronizer(ci.getReturnValue());
    }
}
