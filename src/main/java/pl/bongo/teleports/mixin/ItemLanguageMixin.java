package pl.bongo.teleports.mixin;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pl.bongo.teleports.ItemPresentation;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ItemLanguageMixin {
    @ModifyVariable(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Packet<?> teleports$localizedItems(Packet<?> packet) {
        if((Object)this instanceof ServerGamePacketListenerImpl handler)
            return ItemPresentation.packet(packet,handler.player.clientInformation().language());
        return packet;
    }
}
