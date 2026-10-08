package pl.bongo.teleports;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class Storage {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final class Config {
        boolean xpCostEnabled = true;
        double xpPerMeter = 0.1;
        double tooCloseDistanceMeters = 50;
        int warmupSeconds = 5;
        int cooldownSeconds = 60;
        int expensiveTeleportThresholdXp = 1000;
        int requestTimeoutSeconds = 60;
        int confirmationTimeoutSeconds = 30;
        int padWarmupSeconds = 3;
        int synchronizerDurability = 10;
        boolean padsAllowCrossDimension = true;
        boolean soundsEnabled = true;
        Map<String,Feedback.SoundSetting> sounds = Feedback.defaults();
        void validate() {
            Feedback.validateSettings(sounds);
            if (!Double.isFinite(xpPerMeter) || xpPerMeter < 0 || xpPerMeter > 1000000)
                throw new IllegalArgumentException("xpPerMeter musi być w przedziale 0..1000000");
            if (!Double.isFinite(tooCloseDistanceMeters) || tooCloseDistanceMeters < 0 || tooCloseDistanceMeters > 60000000)
                throw new IllegalArgumentException("tooCloseDistanceMeters musi być w przedziale 0..60000000");
            if (warmupSeconds < 0 || warmupSeconds > 3600 || cooldownSeconds < 0 || cooldownSeconds > 86400
                || expensiveTeleportThresholdXp < 0 || requestTimeoutSeconds < 1 || requestTimeoutSeconds > 3600
                || confirmationTimeoutSeconds < 1 || confirmationTimeoutSeconds > 3600
                || padWarmupSeconds < 0 || padWarmupSeconds > 3600
                || synchronizerDurability < 1 || synchronizerDurability > 100000)
                throw new IllegalArgumentException("Nieprawidłowy czas lub próg XP w konfiguracji");
        }
    }
    static final class Pad {
        String id, owner, dimension, partner;
        int x,y,z;
        Pad(String owner, String dimension, int x, int y, int z) {
            id=UUID.randomUUID().toString(); this.owner=owner; this.dimension=dimension;
            this.x=x; this.y=y; this.z=z;
        }
        String key() { return dimension+":"+x+":"+y+":"+z; }
    }
    static final class Data {
        int schemaVersion = 1;
        Map<String,Pad> pads = new HashMap<>();
        Set<String> ignored = new HashSet<>();
        Map<String,Long> cooldowns = new HashMap<>();
    }
    static <T> T read(Path path, Class<T> type, T defaults) {
        if (!Files.exists(path)) { write(path,defaults); return defaults; }
        try (var reader=Files.newBufferedReader(path)) {
            T value=GSON.fromJson(reader,type);
            if(value==null) throw new IOException("Pusty plik JSON");
            return value;
        } catch (Exception e) { throw new IllegalStateException("Nie można odczytać "+path+"; oryginał zachowano",e); }
    }
    static void write(Path path, Object value) {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temp,GSON.toJson(value));
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException("Nie można zapisać "+path,e); }
    }
}
