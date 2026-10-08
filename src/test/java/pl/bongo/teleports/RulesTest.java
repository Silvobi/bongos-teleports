package pl.bongo.teleports;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void horizontalDistanceAndRounding() {
        assertEquals(5,Rules.cost(0,0,3,4,true,1));
        assertEquals(2,Rules.cost(0,0,1,1,true,1));
        assertEquals(3,Rules.cost(-100,-100,-97,-96,true,0.5));
        assertEquals(0,Rules.cost(0,0,30000,30000,false,1));
        assertEquals(0,Rules.cost(0,0,30000,30000,true,0));
        assertEquals(Integer.MAX_VALUE,Rules.cost(-30000000,0,30000000,0,true,1000000));
        for(int d:new int[]{1,2,3,5,8,10}) assertEquals(1,Rules.cost(0,0,d,0,true,.1));
        assertEquals(2,Rules.cost(0,0,11,0,true,.1));
        assertEquals(5,Rules.cost(0,0,50,0,true,.1));
    }
    @Test void deadZoneIsHorizontalAndInclusive() {
        assertTrue(Rules.inDeadZone(10.5,20.5,10.5,20.5));
        assertTrue(Rules.inDeadZone(11.5,20.5,10.5,20.5));
        assertTrue(Rules.inDeadZone(11.1,21.3,10.5,20.5));
        assertFalse(Rules.inDeadZone(11.501,20.5,10.5,20.5));
        assertFalse(Rules.inDeadZone(11.3,21.3,10.5,20.5));
    }
    @Test void xpLevelBoundariesAndDebits() {
        assertEquals(7,Rules.xpAtLevel(1));
        assertEquals(352,Rules.xpAtLevel(16));
        assertEquals(394,Rules.xpAtLevel(17));
        assertEquals(1507,Rules.xpAtLevel(31));
        assertEquals(1628,Rules.xpAtLevel(32));
        for(int level=1;level<=100;level++) {
            assertEquals(level,Rules.levelForXp(Rules.xpAtLevel(level)));
            assertEquals(level-1,Rules.levelForXp(Rules.xpAtLevel(level)-1));
        }
        assertEquals(0,Rules.levelForXp(0));
        assertEquals(16,Rules.levelForXp(394-5));
    }
    @Test void configRejectsUnsafeNumbers() {
        Storage.Config c=new Storage.Config(); c.validate();
        c.xpPerMeter=Double.NaN; assertThrows(IllegalArgumentException.class,c::validate);
        c.xpPerMeter=1; c.warmupSeconds=-1; assertThrows(IllegalArgumentException.class,c::validate);
        c.warmupSeconds=5; c.synchronizerDurability=0; assertThrows(IllegalArgumentException.class,c::validate);
        c.synchronizerDurability=10; c.tooCloseDistanceMeters=-1; assertThrows(IllegalArgumentException.class,c::validate);
        c.tooCloseDistanceMeters=50; c.padWarmupSeconds=-1; assertThrows(IllegalArgumentException.class,c::validate);
    }
    @Test void persistenceRoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path path) {
        Storage.Data data=new Storage.Data();
        Storage.Pad a=new Storage.Pad("owner","minecraft:overworld",10,33,-10);
        Storage.Pad b=new Storage.Pad("owner","minecraft:the_nether",20,50,30);
        a.partner=b.id; b.partner=a.id;
        data.pads.put(a.id,a); data.pads.put(b.id,b); data.ignored.add("player"); data.cooldowns.put("owner",12345L);
        Storage.write(path.resolve("data.json"),data);
        Storage.Data restored=Storage.read(path.resolve("data.json"),Storage.Data.class,null);
        assertEquals(b.id,restored.pads.get(a.id).partner);
        assertEquals(a.id,restored.pads.get(b.id).partner);
        assertTrue(restored.ignored.contains("player")); assertEquals(12345L,restored.cooldowns.get("owner"));
    }
}
