package pl.bongo.teleports.mixin;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.teleports.Teleports;

@Mixin(ResultSlot.class)
public abstract class CraftingSoundMixin {
    @Inject(method="onTake",at=@At("TAIL"))
    private void teleports$crafted(Player player,ItemStack stack,CallbackInfo ci) {
        Teleports.crafted(player,stack);
    }
}
