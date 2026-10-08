package pl.bongo.teleports.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;
import pl.bongo.teleports.Teleports;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwingMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handlePunch",at=@At("TAIL"))
    private void teleports$clearSelection(ServerboundPunchPacket packet,CallbackInfo ci) {
        Teleports.clearSynchronizer(player);
    }
}
