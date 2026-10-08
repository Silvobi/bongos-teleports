package pl.bongo.teleports;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Vanilla sound packets and one action-bar scheduler shared by both teleport systems. */
final class Feedback {
    enum Event {
        TPA_REQUEST_SENT("ui.button.click",.35f,1f),
        TPA_REQUEST_RECEIVED("block.note_block.pling",.65f,1f),
        TPA_COST_WARNING("block.note_block.bell",.5f,1f,2),
        TPA_COST_CONFIRMED("ui.button.click",.35f,1.15f),
        TPA_ACCEPTED("entity.experience_orb.pickup",.45f,1.15f),
        TPA_DENIED("block.note_block.bass",.5f,.8f),
        TPA_WARMUP_START("block.beacon.power_select",.3f,1f),
        TPA_WARMUP_TICK("block.note_block.hat",.25f,1f),
        TPA_TELEPORT("entity.enderman.teleport",.65f,1f),
        TPA_CANCELLED("block.beacon.deactivate",.35f,1f),
        TPA_EXPIRED("block.note_block.bass",.25f,.8f),
        TPA_ERROR("block.note_block.bass",.4f,.75f),
        IGNORE_ENABLED("ui.button.click",.35f,1.2f),
        IGNORE_DISABLED("ui.button.click",.35f,.8f),
        PAD_CRAFTED("entity.experience_orb.pickup",.25f,1.15f),
        SYNCHRONIZER_CRAFTED("entity.experience_orb.pickup",.25f,1.15f),
        PAD_PLACED("block.stone.place",.65f,1f),
        PAD_PICKED_UP("block.stone.break",.6f,1f),
        SYNCHRONIZER_SELECTED("block.amethyst_block.chime",.5f,1.1f),
        SYNCHRONIZER_CLEARED("ui.button.click",.35f,1f),
        PADS_SYNCHRONIZED("block.enchantment_table.use",.55f,1f),
        SYNCHRONIZER_BROKEN("entity.item.break",.6f,1f),
        PAD_UNLINKED("block.beacon.deactivate",.35f,.9f),
        PAD_WARMUP_START("block.beacon.activate",.3f,1f),
        PAD_WARMUP_TICK("block.note_block.hat",.25f,1f),
        PAD_TELEPORT("entity.enderman.teleport",.65f,1f),
        PAD_CANCELLED("block.beacon.deactivate",.35f,1f),
        PAD_ERROR("block.note_block.bass",.35f,.75f),
        PAD_UNLOCKED("block.amethyst_block.chime",.25f,1.3f),
        CONFIG_RELOADED("ui.button.click",.35f,1f),
        CONFIG_ERROR("block.note_block.bass",.4f,.75f);

        final String sound; final float volume,pitch; final int pulses;
        Event(String sound,float volume,float pitch) { this(sound,volume,pitch,1); }
        Event(String sound,float volume,float pitch,int pulses) { this.sound="minecraft:"+sound;this.volume=volume;this.pitch=pitch;this.pulses=pulses; }
        String key() { return name().toLowerCase(Locale.ROOT); }
        boolean error() { return this==TPA_ERROR || this==PAD_ERROR || this==CONFIG_ERROR; }
    }
    static final class SoundSetting {
        boolean enabled=true;
        String sound;
        float volume,pitch;
        SoundSetting(String sound,float volume,float pitch) { this.sound=sound;this.volume=volume;this.pitch=pitch; }
    }
    static Map<String,SoundSetting> defaults() {
        Map<String,SoundSetting> result=new LinkedHashMap<>();
        for(Event e:Event.values()) result.put(e.key(),new SoundSetting(e.sound,e.volume,e.pitch));
        return result;
    }
    private static final Pattern COMMAND=Pattern.compile("/(?:tpa|tpaccept|tpdeny|confirm-tpa|tpacancel|ignore-tpa|bongoteleports)\\b");
    static boolean needsChat(String text) { return COMMAND.matcher(text).find(); }
    static void validateSettings(Map<String,SoundSetting> map) {
        if(map==null) throw new IllegalArgumentException("sounds nie może być null");
        defaults().forEach(map::putIfAbsent);
        for(var entry:map.entrySet()) {
            SoundSetting s=entry.getValue();
            if(s==null || s.sound==null || Identifier.tryParse(s.sound)==null || !Float.isFinite(s.volume) || s.volume<0 || s.volume>4
                || !Float.isFinite(s.pitch) || s.pitch<.5f || s.pitch>2f)
                throw new IllegalArgumentException("Nieprawidłowe ustawienie dźwięku: "+entry.getKey());
        }
    }
    static void validateRegistry(Map<String,SoundSetting> map) {
        for(var entry:map.entrySet()) if(BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(entry.getValue().sound))==null)
            throw new IllegalArgumentException("Nieznany dźwięk vanilla: "+entry.getValue().sound+" ("+entry.getKey()+")");
    }
    private final Supplier<Storage.Config> config;
    private record Notice(String text,boolean error) {}
    private static final class Display {
        Notice notice; long expires,nextSend;
        Display(Notice n,long now) { notice=n;expires=now+(n.error?1400:2800); }
    }
    private static final class Countdown {
        int lastSecond=-1,total; String text; ServerLevel level; Vec3 position;
    }
    private record ScheduledSound(long due,UUID player,Event event,float pitch) {}
    private final Map<UUID,Display> displays=new HashMap<>();
    private final Map<UUID,Deque<Notice>> queued=new HashMap<>();
    private final Map<UUID,Map<String,Countdown>> countdowns=new HashMap<>();
    private final Map<UUID,Map<Event,Long>> soundTimes=new HashMap<>();
    private final List<ScheduledSound> scheduled=new ArrayList<>();
    Feedback(Supplier<Storage.Config> config) { this.config=config; }
    private static long now() { return System.currentTimeMillis(); }
    private static Component text(Notice notice) {
        return Component.literal(notice.text).withStyle(notice.error?ChatFormatting.RED:ChatFormatting.YELLOW);
    }
    void message(ServerPlayer p,Event event,String message) {
        if(p==null) return;
        sound(p,event);
        messageOnly(p,event,message);
    }
    void messageOnly(ServerPlayer p,Event event,String message) {
        if(p==null) return;
        if(needsChat(message)) { p.sendSystemMessage(Component.literal("[Teleport] "+message)); return; }
        Notice notice=new Notice(message,event.error());
        if(!notice.error && countdowns.containsKey(p.getUUID())) {
            Deque<Notice> q=queued.computeIfAbsent(p.getUUID(),id->new ArrayDeque<>());
            if(q.isEmpty() || !q.peekLast().equals(notice)) { if(q.size()==3) q.removeFirst();q.addLast(notice); }
            return;
        }
        Display display=new Display(notice,now()); displays.put(p.getUUID(),display);
        p.sendOverlayMessage(text(notice)); display.nextSend=now()+800;
    }
    void countdown(ServerPlayer p,String lane,int seconds,String message,ServerLevel level,Vec3 position) {
        if(seconds<=0) return;
        Map<String,Countdown> lanes=countdowns.computeIfAbsent(p.getUUID(),id->new LinkedHashMap<>());
        Countdown c=lanes.get(lane);
        Event start=lane.equals("pad")?Event.PAD_WARMUP_START:Event.TPA_WARMUP_START;
        Event tick=lane.equals("pad")?Event.PAD_WARMUP_TICK:Event.TPA_WARMUP_TICK;
        if(c==null) {
            c=new Countdown(); c.total=seconds;lanes.put(lane,c);
            displays.remove(p.getUUID());
            if(level==null) sound(p,start); else spatial(level,position,start);
        }
        c.text=message;c.level=level;c.position=position;
        if(c.lastSecond!=seconds) {
            float multiplier=1+(Math.min(8,c.total-seconds)*.08f);
            if(level==null) sound(p,tick,multiplier); else spatial(level,position,tick,multiplier);
            c.lastSecond=seconds;
        }
        Display d=displays.get(p.getUUID());
        if(d==null || !d.notice.error || d.expires<=now()) showCountdown(p,lanes);
    }
    private void showCountdown(ServerPlayer p,Map<String,Countdown> lanes) {
        Countdown c=lanes.getOrDefault("pad",lanes.get("tpa"));
        if(c!=null) p.sendOverlayMessage(text(new Notice(c.text,false)));
    }
    void endCountdown(ServerPlayer p,String lane) {
        if(p==null) return;
        Map<String,Countdown> lanes=countdowns.get(p.getUUID());
        if(lanes!=null) { lanes.remove(lane);if(lanes.isEmpty()) countdowns.remove(p.getUUID()); }
        // Deferred routine confirmations are shown after the final teleport result has expired.
    }
    void tick(MinecraftServer server) {
        long time=now();
        scheduled.removeIf(s -> {
            if(s.due>time) return false;
            ServerPlayer p=server.getPlayerList().getPlayer(s.player);
            if(p!=null) sendPrivate(p,s.event,s.pitch); return true;
        });
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            UUID id=p.getUUID();Display d=displays.get(id);
            if(d!=null && d.expires<=time) { displays.remove(id);d=null; }
            if(d!=null && d.notice.error) {
                if(time>=d.nextSend) { p.sendOverlayMessage(text(d.notice));d.nextSend=time+800; } continue;
            }
            if(countdowns.containsKey(id)) continue; // Refreshed by the active teleport each 250 ms.
            if(d==null) {
                Deque<Notice> q=queued.get(id);
                if(q!=null && !q.isEmpty()) { d=new Display(q.removeFirst(),time);displays.put(id,d); }
            }
            if(d!=null && time>=d.nextSend) { p.sendOverlayMessage(text(d.notice));d.nextSend=time+800; }
        }
    }
    void sound(ServerPlayer p,Event event) { sound(p,event,1); }
    private void sound(ServerPlayer p,Event event,float multiplier) {
        if(p==null) return;
        long time=now();Map<Event,Long> recent=soundTimes.computeIfAbsent(p.getUUID(),id->new EnumMap<>(Event.class));
        if(time-recent.getOrDefault(event,0L)<250) return;
        recent.put(event,time);sendPrivate(p,event,multiplier);
        for(int pulse=1;pulse<event.pulses;pulse++) scheduled.add(new ScheduledSound(time+pulse*180,p.getUUID(),event,multiplier));
    }
    private Holder<SoundEvent> resolve(Event event) {
        Storage.Config cfg=config.get();SoundSetting setting=cfg.sounds.get(event.key());
        if(!cfg.soundsEnabled || !setting.enabled || setting.volume==0) return null;
        return BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(setting.sound)).orElse(null);
    }
    private void sendPrivate(ServerPlayer p,Event event,float multiplier) {
        Holder<SoundEvent> sound=resolve(event);if(sound==null) return;
        SoundSetting s=config.get().sounds.get(event.key());
        p.connection.send(new ClientboundSoundPacket(sound,SoundSource.PLAYERS,p.getX(),p.getY(),p.getZ(),s.volume,Math.min(2,s.pitch*multiplier),p.getRandom().nextLong()));
    }
    void spatial(ServerLevel level,Vec3 position,Event event) { spatial(level,position,event,1); }
    private void spatial(ServerLevel level,Vec3 position,Event event,float multiplier) {
        if(level==null || position==null) return;
        Holder<SoundEvent> sound=resolve(event);if(sound==null) return;
        SoundSetting s=config.get().sounds.get(event.key());
        level.playSeededSound(null,position.x,position.y,position.z,sound,SoundSource.BLOCKS,s.volume,Math.min(2,s.pitch*multiplier),level.getRandom().nextLong());
    }
    void forget(UUID id) {
        displays.remove(id);queued.remove(id);countdowns.remove(id);soundTimes.remove(id);scheduled.removeIf(s->s.player.equals(id));
    }
    void clear() { displays.clear();queued.clear();countdowns.clear();soundTimes.clear();scheduled.clear(); }
}
