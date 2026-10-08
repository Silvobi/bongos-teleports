package pl.bongo.teleports;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackTest {
    @Test void commandInstructionsStayInChat() {
        for(String command:new String[]{"/tpa Bongo","/tpaccept Bongo","/tpdeny Bongo","'/confirm-tpa'.","/tpacancel.","/bongoteleports"})
            assertTrue(Feedback.needsChat("Wpisz "+command),command);
        for(String message:new String[]{"Zbyt blisko (X/Z).","Pady zsynchronizowane.","Kliknij PPM.","Shift + LPM: wyczyść wybór.","Pobrano 5 XP.","Konfiguracja przeładowana."})
            assertFalse(Feedback.needsChat(message),message);
    }
    @Test void partialSoundConfigRetainsDefaultsAndValidates() {
        var map=new java.util.LinkedHashMap<String,Feedback.SoundSetting>();
        var custom=new Feedback.SoundSetting("minecraft:block.note_block.bell",.2f,.8f);custom.enabled=false;
        map.put("tpa_error",custom);Feedback.validateSettings(map);
        assertEquals(Feedback.Event.values().length,map.size());assertSame(custom,map.get("tpa_error"));
        custom.volume=Float.NaN;assertThrows(IllegalArgumentException.class,()->Feedback.validateSettings(map));
        custom.volume=.2f;custom.pitch=3;assertThrows(IllegalArgumentException.class,()->Feedback.validateSettings(map));
    }
}
