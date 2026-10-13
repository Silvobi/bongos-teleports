package pl.bongo.teleports;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;

public final class Lang {
    private Lang() {}
    private static final Map<String, Properties> LANGUAGES=Map.of("pl",load("pl"),"en",load("en"));
    private static Properties load(String name) {
        Properties result=new Properties();
        try(var input=Lang.class.getResourceAsStream("/bongos_teleports/lang/"+name+".properties")) {
            if(input==null) throw new IllegalStateException("Missing language: "+name);
            result.load(new InputStreamReader(input,StandardCharsets.UTF_8));
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
        return result;
    }
    public static String language(String locale) {
        return locale!=null && locale.toLowerCase(Locale.ROOT).startsWith("pl_")?"pl":"en";
    }
    public static String text(String locale,String key,Object... args) {
        String pattern=LANGUAGES.get(language(locale)).getProperty(key);
        if(pattern==null) throw new IllegalArgumentException("Missing translation: "+key);
        Object[] rendered=Arrays.stream(args).map(a->a instanceof Message m?m.text(locale):a).toArray();
        return String.format(Locale.ROOT,pattern,rendered);
    }
    public static String text(CommandSourceStack source,String key,Object... args) {
        return text(source.getPlayer()==null?null:source.getPlayer().clientInformation().language(),key,args);
    }
    public static Set<String> keys(String language) { return LANGUAGES.get(language).stringPropertyNames(); }
    public static Message message(String key,Object... args) { return new Message(key,List.of(args)); }
    /** Immutable message data can be shared across recipients and queued without freezing a language. */
    public record Message(String key,List<Object> args) {
        public Message { args=List.copyOf(args); }
        public String text(String locale) { return Lang.text(locale,key,args.toArray()); }
    }
}
