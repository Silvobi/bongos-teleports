package pl.bongo.teleports.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LanguageChangeMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleClientInformation",at=@At("TAIL"))
    private void teleports$refreshLanguage(ServerboundClientInformationPacket packet,CallbackInfo ci) {
        player.containerMenu.sendAllDataToRemote();
    }
}
