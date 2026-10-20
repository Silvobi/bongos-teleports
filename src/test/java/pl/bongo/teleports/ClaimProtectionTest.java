package pl.bongo.teleports;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimProtectionTest {
    public enum NativeTrust { ACCESS, BUILD, MANAGE }
    public record Claim(Long id,String worldKey,UUID ownerId) {}
    public static final class Repository {
        final Claim main=new Claim(1L,"world",UUID.randomUUID());
        final Claim sub=new Claim(2L,"world",main.ownerId());
        boolean grant, broken;
        NativeTrust requested;
        String lastWorld;
        public Collection<Claim> getClaims() { return List.of(main,sub); }
        public Optional<Claim> findClaimAt(String world,int x,int y,int z,boolean ignoreHeight,boolean ignoreSubclaims) {
            lastWorld=world;
            assertFalse(ignoreHeight); assertFalse(ignoreSubclaims);
            if(broken) throw new IllegalStateException("native lookup failed");
            if(!world.equals(main.worldKey()) || x<0 || z<0) return Optional.empty();
            return Optional.of(y>=10 && y<=20?sub:main);
        }
        boolean allows(Claim claim,Player player,NativeTrust trust) { requested=trust; return grant; }
    }
    @Test void nativeTrustAndExactThreeDimensionalLookup() throws Exception {
        Repository repo=new Repository();
        Set<String> flags=new HashSet<>();
        var nativeAccess=new ClaimProtection.NativeAccess(repo);
        var protection=new ClaimProtection(nativeAccess,flags);
        assertTrue(protection.allows(null,"minecraft:the_nether",BlockPos.ZERO,ClaimProtection.Trust.BUILD,false));
        assertEquals("world_nether",repo.lastWorld);
        assertTrue(protection.allows(null,"minecraft:the_end",BlockPos.ZERO,ClaimProtection.Trust.BUILD,false));
        assertEquals("world_the_end",repo.lastWorld);
        assertTrue(protection.allows(null,"custom:moon",BlockPos.ZERO,ClaimProtection.Trust.BUILD,false));
        assertEquals("custom:moon",repo.lastWorld);
        assertFalse(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.ACCESS,false));
        assertEquals(NativeTrust.ACCESS,repo.requested);
        repo.grant=true;
        assertTrue(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.BUILD,false));
        assertEquals(NativeTrust.BUILD,repo.requested);
        String main=protection.managedClaim(null,"minecraft:overworld",BlockPos.ZERO);
        assertEquals(NativeTrust.MANAGE,repo.requested);
        protection.setPublic(main,true); repo.grant=false;
        assertTrue(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.ACCESS,true));
        assertFalse(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.ACCESS,false),"public pads never open TPA");
        assertFalse(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.BUILD,true),"public pads never grant building");
        assertFalse(protection.allows(null,"minecraft:overworld",new BlockPos(0,15,0),ClaimProtection.Trust.ACCESS,true),"parent public flag does not open 3D subdivision");
        assertThrows(IllegalStateException.class,()->protection.managedClaim(null,"minecraft:overworld",BlockPos.ZERO));
        repo.grant=true;
        String sub=protection.managedClaim(null,"minecraft:overworld",new BlockPos(0,15,0));
        protection.setPublic(sub,true); protection.setPublic(main,false); repo.grant=false;
        assertTrue(protection.allows(null,"minecraft:overworld",new BlockPos(0,15,0),ClaimProtection.Trust.ACCESS,true));
        assertFalse(protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.ACCESS,true));
        assertThrows(IllegalStateException.class,()->protection.managedClaim(null,"minecraft:overworld",new BlockPos(-1,15,0)));
        repo.broken=true;
        assertThrows(IllegalStateException.class,()->protection.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.ACCESS,true),"lookup failures must never allow teleportation");
    }
    @Test void optionalIntegrationAndStaleFlagCleanup() throws Exception {
        var absent=new ClaimProtection(null,new HashSet<>());
        assertTrue(absent.allows(null,"minecraft:overworld",BlockPos.ZERO,ClaimProtection.Trust.BUILD,false));
        assertThrows(IllegalStateException.class,()->absent.managedClaim(null,"minecraft:overworld",BlockPos.ZERO));
        Repository repo=new Repository(); Set<String> flags=new HashSet<>(Set.of("deleted-claim"));
        var access=new ClaimProtection.NativeAccess(repo);
        String valid=access.key(repo.main); flags.add(valid);
        new ClaimProtection(access,flags).prune();
        assertEquals(Set.of(valid),flags);
    }
    @Test void publicFlagsSurviveSaveAndOldDataDefaultsToPrivate(@org.junit.jupiter.api.io.TempDir java.nio.file.Path path) {
        var data=new Storage.Data(); data.publicPadClaims.add("minecraft:overworld:1:owner");
        Storage.write(path.resolve("data.json"),data);
        assertEquals(data.publicPadClaims,Storage.read(path.resolve("data.json"),Storage.Data.class,null).publicPadClaims);
        assertTrue(Storage.GSON.fromJson("{\"schemaVersion\":1}",Storage.Data.class).publicPadClaims.isEmpty());
    }
}
