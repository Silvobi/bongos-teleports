package pl.bongo.teleports;

import java.lang.reflect.Method;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/** Optional GP3D adapter. Delegate trust to GP3D, including inheritance, denies and staff bypass. */
final class ClaimProtection {
    enum Trust { ACCESS, BUILD, MANAGE }
    private final NativeAccess access;
    private final Set<String> publicPads;

    ClaimProtection(NativeAccess access, Set<String> publicPads) {
        this.access=access; this.publicPads=publicPads;
    }
    static ClaimProtection detect(Set<String> publicPads, Logger log) {
        if(!FabricLoader.getInstance().isModLoaded("griefprevention3d")) {
            log.info("GriefPrevention3D absent: claim protection disabled");
            return new ClaimProtection(null,publicPads);
        }
        // Universal 19.x relocates the 26.x adapter; older Fabric jars use the original package.
        for(String prefix:List.of("com.griefprevention.fabric.mc26_1", "com.griefprevention.fabric")) {
            try {
                Object repository=Class.forName(prefix+".GriefPreventionFabric")
                    .getMethod("getClaimRepository").invoke(null);
                if(repository==null) continue;
                NativeAccess nativeAccess=new NativeAccess(repository);
                log.info("GriefPrevention3D claim protection enabled ({})",prefix);
                return new ClaimProtection(nativeAccess,publicPads);
            } catch(ClassNotFoundException ignored) {
                // The next package may be the one carried by this jar.
            } catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
                throw new IllegalStateException("Nie można uruchomić integracji GriefPrevention3D; sprawdź zgodność wersji.",e);
            }
        }
        throw new IllegalStateException("GriefPrevention3D jest obecne, ale jego repozytorium claimów jest niedostępne.");
    }
    boolean enabled() { return access!=null; }
    boolean allows(Player player,String world,BlockPos pos,Trust trust,boolean padTeleport) {
        if(access==null) return true;
        Object claim=access.claimAt(world,pos);
        return claim==null || padTeleport && trust==Trust.ACCESS && publicPads.contains(access.key(claim))
            || access.allows(claim,player,trust);
    }
    boolean allows(ServerPlayer player,ServerLevel level,BlockPos pos,Trust trust,boolean padTeleport) {
        return allows(player,level.dimension().identifier().toString(),pos,trust,padTeleport);
    }
    String managedClaim(Player player,String world,BlockPos pos) {
        if(access==null) throw new IllegalStateException("GriefPrevention3D nie jest zainstalowane.");
        Object claim=access.claimAt(world,pos);
        if(claim==null) throw new IllegalStateException("Stań w działce lub poddziałce, której flagę chcesz zmienić.");
        if(!access.allows(claim,player,Trust.MANAGE))
            throw new IllegalStateException("Zarządzanie flagą wymaga Manage Trust w tej działce.");
        return access.key(claim);
    }
    boolean isPublic(String key) { return publicPads.contains(key); }
    void setPublic(String key,boolean enabled) {
        if(enabled) publicPads.add(key); else publicPads.remove(key);
    }
    void prune() {
        if(access!=null) publicPads.retainAll(access.keys());
    }

    /** GP3D exposes lookup publicly but keeps its native player trust evaluator package-private. */
    static final class NativeAccess {
        private final Object repository;
        private final Method find,allows,id,world,owner,getClaims;
        private final Map<Trust,Object> trustLevels=new EnumMap<>(Trust.class);
        NativeAccess(Object repository) throws ReflectiveOperationException {
            this.repository=repository;
            Class<?> type=repository.getClass();
            find=type.getMethod("findClaimAt",String.class,int.class,int.class,int.class,boolean.class,boolean.class);
            getClaims=type.getMethod("getClaims");
            allows=Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals("allows")
                && m.getParameterCount()==3 && m.getParameterTypes()[1]==Player.class).findFirst()
                .orElseThrow(() -> new NoSuchMethodException("GP3D player trust evaluator"));
            allows.setAccessible(true);
            Class<?> snapshot=allows.getParameterTypes()[0];
            id=snapshot.getMethod("id"); world=snapshot.getMethod("worldKey"); owner=snapshot.getMethod("ownerId");
            for(Trust trust:Trust.values()) {
                Object value=Arrays.stream(allows.getParameterTypes()[2].getEnumConstants())
                    .filter(v -> ((Enum<?>)v).name().equals(trust.name())).findFirst()
                    .orElseThrow(() -> new NoSuchFieldException(trust.name()));
                trustLevels.put(trust,value);
            }
        }
        private Object invoke(Method method,Object target,Object... args) {
            try { return method.invoke(target,args); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
                throw new IllegalStateException("Błąd integracji GriefPrevention3D; operacja zablokowana.",e);
            }
        }
        Object claimAt(String dimension,BlockPos pos) {
            // Height and subdivisions MUST both participate, including shaped/nested claims.
            String worldKey=switch(dimension) {
                case "minecraft:overworld" -> "world";
                case "minecraft:the_nether" -> "world_nether";
                case "minecraft:the_end" -> "world_the_end";
                default -> dimension;
            };
            return ((Optional<?>)invoke(find,repository,worldKey,pos.getX(),pos.getY(),pos.getZ(),false,false)).orElse(null);
        }
        boolean allows(Object claim,Player player,Trust trust) {
            return (boolean)invoke(allows,repository,claim,player,trustLevels.get(trust));
        }
        String key(Object claim) {
            return invoke(world,claim)+":"+invoke(id,claim)+":"+invoke(owner,claim);
        }
        Set<String> keys() {
            Set<String> result=new HashSet<>();
            for(Object claim:(Collection<?>)invoke(getClaims,repository)) result.add(key(claim));
            return result;
        }
    }
}
