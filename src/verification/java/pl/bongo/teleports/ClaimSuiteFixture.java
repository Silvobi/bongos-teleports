package pl.bongo.teleports;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Test artifact only. Cache explicit nonexistent-Mojang-name fixtures; password/encryption remain real. */
final class ClaimSuiteFixture {
    @SuppressWarnings("unchecked") static void start(MinecraftServer server) {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bongoutils")) return;
        Path marker=server.getServerDirectory().resolve("claim-suite-fixture.txt");if(!Files.exists(marker)) return;
        try {
            Class<?> utils=Class.forName("pl.bongo.bongoutils.BongoUtils");Object skins=utils.getField("skins").get(null);
            Field names=skins.getClass().getDeclaredField("names");names.setAccessible(true);
            Class<?> cached=Class.forName("pl.bongo.bongoutils.SkinService$CachedUuid");
            Constructor<?> constructor=cached.getDeclaredConstructor(UUID.class,long.class);constructor.setAccessible(true);
            for(String name:Files.readAllLines(marker)) if(!name.isBlank())
                ((Map<String,Object>)names.get(skins)).put(name.toLowerCase(Locale.ROOT),constructor.newInstance(null,Long.MAX_VALUE));
        } catch(ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException("Could not install claim-suite fixtures",e); }
    }
    static void report(MinecraftServer server,String name) {
        if(!Files.exists(server.getServerDirectory().resolve("claim-suite-fixture.txt"))) throw new IllegalStateException("Not a claim suite server");
        try {
            ServerPlayer player=server.getPlayerList().getPlayerByName(name);if(player==null) throw new IllegalArgumentException(name);
            Object repo=Class.forName("com.griefprevention.fabric.mc26_1.GriefPreventionFabric").getMethod("getClaimRepository").invoke(null);
            Method find=repo.getClass().getDeclaredMethod("findClaimAt",net.minecraft.server.level.ServerLevel.class,net.minecraft.core.BlockPos.class);find.setAccessible(true);
            Object claim=find.invoke(repo,player.level(),player.blockPosition());StringBuilder output=new StringBuilder();
            Class<?> trust=Class.forName("com.griefprevention.claims.ClaimTrustLevel");
            Method allows=Arrays.stream(repo.getClass().getDeclaredMethods()).filter(m->m.getName().equals("allows") && m.getParameterTypes()[1]==net.minecraft.world.entity.player.Player.class).findFirst().orElseThrow();allows.setAccessible(true);
            for(Object value:trust.getEnumConstants()) output.append(value).append(':').append(claim!=null && (boolean)allows.invoke(repo,claim,player,value)).append('\n');
            Files.writeString(server.getServerDirectory().resolve("gp-"+name+".txt"),output);
        } catch(ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
