package pl.bongo.teleports;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LangTest {
    @Test void localeSelectionAndFallback() {
        assertEquals("pl",Lang.language("PL_pl"));
        for(String locale:new String[]{null,"","en_us","de_de","pl"}) {
            assertEquals("en",Lang.language(locale));
            assertEquals("Request accepted.",Lang.text(locale,"request_accepted"));
        }
        assertEquals("Prośba zaakceptowana.",Lang.text("pl_pl","request_accepted"));
    }
    @Test void everyTranslationHasMatchingArgumentsAndFormats() throws Exception {
        assertEquals(Lang.keys("pl"),Lang.keys("en"));
        Properties pl=load("pl"),en=load("en");
        for(String key:Lang.keys("pl")) {
            int count=pl.getProperty(key).split("%s",-1).length-1;
            assertEquals(count,en.getProperty(key).split("%s",-1).length-1,key);
            Object[] args=new Object[count];Arrays.fill(args,"VALUE");
            for(String locale:List.of("pl_pl","en_us")) {
                String value=Lang.text(locale,key,args);
                assertFalse(value.isBlank(),key);assertFalse(value.contains("%s"),key);
            }
        }
    }
    private static Properties load(String language) throws Exception {
        Properties result=new Properties();
        try(var input=LangTest.class.getResourceAsStream("/bongos_teleports/lang/"+language+".properties")) {
            result.load(new InputStreamReader(input,StandardCharsets.UTF_8));
        }
        return result;
    }
    @Test void sharedAndQueuedMessagesCanChangeLanguageWithoutChangingData() {
        var message=Lang.message("request_sent","MiXeD",12,60);
        assertEquals("Wysłano prośbę do MiXeD (koszt: 12 XP). Ważna 60 sekund.",message.text("pl_pl"));
        assertEquals("Sent a request to MiXeD (cost: 12 XP). Valid for 60 seconds.",message.text("en_us"));
        assertEquals(message.text("en_us"),message.text("fr_fr"));
        var countdown=Lang.message("tpa_countdown",Lang.message("player_label"),3,12);
        assertTrue(countdown.text("pl_pl").contains("do gracza"));
        assertTrue(countdown.text("en_us").contains("to the player"));
        assertTrue(Lang.message("request_received","MiXeD","MiXeD","MiXeD").text("en_us").contains("/tpaccept MiXeD or /tpdeny MiXeD"));
        assertTrue(Feedback.needsChat(Lang.message("cost_confirmation",12).text("en_us")));
    }
}
