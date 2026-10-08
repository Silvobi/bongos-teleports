package pl.bongo.teleports;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;
import java.util.*;

/** No hard dependency: ordinary standalone Teleports behavior is retained without Bongo Utils. */
final class SocialBridge {
    private static Method allowed;
    private static boolean initialized;
    private static void initialize() {
        if (initialized) return;
        if (!FabricLoader.getInstance().isModLoaded("bongoutils")) {initialized=true;return;}
        try { allowed=Class.forName("pl.bongo.bongoutils.Social").getMethod("canTeleport",UUID.class,UUID.class); initialized=true; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Bongo Utils social integration unavailable; update Bongo Utils to 1.4.0",e); }
    }
    static boolean allowed(ServerPlayer a, ServerPlayer b) {
        initialize(); if (allowed==null) return true;
        try { return (boolean)allowed.invoke(null,a.getUUID(),b.getUUID()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot check Bongo Utils social state",e); }
    }
    static List<String> names(CommandSourceStack source) { return List.copyOf(source.getOnlinePlayerNames()); }
}
