package pl.bongo.teleports;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.commands.Commands.*;
import static pl.bongo.teleports.Feedback.Event.*;

public final class Teleports implements ModInitializer {
    private static Teleports instance;
    private static final Logger LOG=LoggerFactory.getLogger("bongos_teleports");
    private final Path configPath=FabricLoader.getInstance().getConfigDir().resolve("bongos-teleports.json");
    private Storage.Config config;
    private final Feedback feedback=new Feedback(()->config);
    private Storage.Data data;
    private Path dataPath;
    private MinecraftServer server;
    private final Map<UUID,Request> requests=new HashMap<>();
    private final Map<UUID,Request> confirmations=new HashMap<>();
    private final Map<UUID,Warmup> warmups=new HashMap<>();
    private final Map<String,Storage.Pad> byPosition=new HashMap<>();
    private final Map<UUID,String> onPad=new HashMap<>();
    private final Map<UUID,PadWarmup> padWarmups=new HashMap<>();
    private final Map<UUID,PadLock> padLocks=new HashMap<>();
    private int tickCount;
    private record PadWarmup(String source, String target, long completes) {}
    private record PadLock(String source, String target) {}
    private record Request(UUID sender, UUID target, int quote, long expires) {}
    private record Warmup(UUID target, int quote, Vec3 origin, ServerLevel level, long completes) {}
    private static long now() { return System.currentTimeMillis(); }
    private static void msg(ServerPlayer p,String text) { msg(p,TPA_ERROR,text); }
    private static void msg(ServerPlayer p,Feedback.Event event,String text) {
        if(p!=null && instance!=null) instance.feedback.message(p,event,text);
    }
    private void padMsg(ServerPlayer p,Feedback.Event event,String text,Storage.Pad pad) {
        feedback.messageOnly(p,event,text);
        feedback.spatial(level(pad),Vec3.atCenterOf(pos(pad)),event);
    }
    public static void crafted(net.minecraft.world.entity.player.Player player,ItemStack stack) {
        if(!(player instanceof ServerPlayer p) || instance==null) return;
        if(is(stack,"pad")) instance.feedback.sound(p,PAD_CRAFTED);
        else if(is(stack,"synchronizer")) instance.feedback.sound(p,SYNCHRONIZER_CRAFTED);
    }
    private ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
    private static String name(ServerPlayer p) { return p.getGameProfile().name(); }
    private static String dim(ServerLevel l) { return l.dimension().identifier().toString(); }
    private static BlockPos pos(Storage.Pad p) { return new BlockPos(p.x,p.y,p.z); }
    private static String key(ServerLevel l,BlockPos p) { return dim(l)+":"+p.getX()+":"+p.getY()+":"+p.getZ(); }

    @Override public void onInitialize() {
        instance=this;
        config=Storage.read(configPath,Storage.Config.class,new Storage.Config()); config.validate();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            Feedback.validateRegistry(config.sounds);Storage.write(configPath,config);
            server=s; dataPath=s.getWorldPath(LevelResource.ROOT).resolve("bongos-teleports.json");
            data=Storage.read(dataPath,Storage.Data.class,new Storage.Data());
            if(data.schemaVersion!=1 || data.pads==null || data.ignored==null || data.cooldowns==null)
                throw new IllegalStateException("Nieprawidłowy plik danych teleportów: "+dataPath);
            data.pads.values().forEach(p -> byPosition.put(p.key(),p));
            LOG.info("Bongo's Teleports ready: {} pads",data.pads.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { if(data!=null) save(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server=null; data=null; requests.clear(); confirmations.clear(); warmups.clear();
            byPosition.clear(); onPad.clear(); padWarmups.clear(); padLocks.clear();
            feedback.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s) -> disconnect(h.player));
        ServerTickEvents.END_SERVER_TICK.register(s -> { if(data!=null) tick(); });
        registerCommands();
        UseBlockCallback.EVENT.register((p,l,hand,hit) -> {
            if(!(p instanceof ServerPlayer sp) || data==null) return InteractionResult.PASS;
            ItemStack stack=p.getItemInHand(hand);
            Storage.Pad pad=padAt(sp.level(),hit.getBlockPos());
            if(pad!=null) {
                if(is(stack,"synchronizer")) { sync(sp,stack,pad); return InteractionResult.SUCCESS; }
                // Do not let vanilla eyes turn pads into portal frames with eyes.
                if(stack.is(Items.ENDER_EYE)) { msg(sp,PAD_ERROR,"Użyj Harmonizatora TP, aby połączyć pady."); return InteractionResult.FAIL; }
            }
            if(is(stack,"pad")) { place(sp,stack,hit.getBlockPos().relative(hit.getDirection())); return InteractionResult.SUCCESS; }
            if(is(stack,"synchronizer")) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((p,l,hand) -> is(p.getItemInHand(hand),"synchronizer") ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((p,l,hand,bp,face) -> {
            if(!(p instanceof ServerPlayer sp) || data==null) return InteractionResult.PASS;
            if(clearSynchronizer(sp)) return InteractionResult.FAIL;
            Storage.Pad pad=padAt(sp.level(),bp);
            if(pad==null) return InteractionResult.PASS;
            if(!usable(sp) || sp.gameMode.getGameModeForPlayer().isBlockPlacingRestricted()) return InteractionResult.FAIL;
            if(!pad.owner.equals(sp.getUUID().toString())) msg(sp,PAD_ERROR,"Ten pad należy do innego gracza.");
            else if(!sp.isShiftKeyDown()) msg(sp,PAD_ERROR,"Aby podnieść pad, przytrzymaj Shift i kliknij go lewym przyciskiem.");
            else {
                unlink(pad); data.pads.remove(pad.id); byPosition.remove(pad.key());
                sp.level().setBlockAndUpdate(bp,Blocks.AIR.defaultBlockState()); save();
                Block.popResource(sp.level(),bp,item("pad")); padMsg(sp,PAD_PICKED_UP,"Pad podniesiony. Połączenie usunięte.",pad);
            }
            return InteractionResult.FAIL;
        });
        AttackEntityCallback.EVENT.register((p,l,hand,entity,hit) ->
            p instanceof ServerPlayer sp && clearSynchronizer(sp) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
    private void save() { Storage.write(dataPath,data); }
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((d,registry,environment) -> {
            d.register(literal("tpa").then(argument("nickname",StringArgumentType.word())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(),b))
                .executes(c -> request(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"nickname")))));
            d.register(literal("confirm-tpa").executes(c -> confirm(c.getSource().getPlayerOrException())));
            for(String cmd:List.of("tpaccept","tpdeny")) {
                boolean accept=cmd.equals("tpaccept");
                d.register(literal(cmd).executes(c -> respond(c.getSource().getPlayerOrException(),null,accept))
                    .then(argument("nickname",StringArgumentType.word()).suggests((c,b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(),b))
                    .executes(c -> respond(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"nickname"),accept))));
            }
            d.register(literal("ignore-tpa").executes(c -> {
                ServerPlayer p=c.getSource().getPlayerOrException(); String id=p.getUUID().toString();
                boolean ignored=data.ignored.add(id); if(!ignored) data.ignored.remove(id);
                if(ignored) cancelFor(p.getUUID(),"Odbiorca włączył ignorowanie próśb.");
                save(); msg(p,ignored?IGNORE_ENABLED:IGNORE_DISABLED,ignored?"Ignorowanie próśb TPA włączone.":"Ignorowanie próśb TPA wyłączone."); return 1;
            }));
            d.register(literal("tpacancel").executes(c -> {
                ServerPlayer p=c.getSource().getPlayerOrException();
                Request r=requests.remove(p.getUUID()); if(r!=null) msg(player(r.target),TPA_CANCELLED,name(p)+" anulował prośbę.");
                boolean cancelled=confirmations.remove(p.getUUID())!=null;
                boolean warming=warmups.remove(p.getUUID())!=null;
                feedback.endCountdown(p,"tpa");
                msg(p,r!=null||cancelled||warming?TPA_CANCELLED:TPA_ERROR,r!=null||cancelled||warming?"Teleportacja anulowana.":"Brak aktywnej teleportacji."); return 1;
            }));
            d.register(literal("bongoteleports").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("/tpa <nick>, /tpaccept [nick], /tpdeny [nick], /confirm-tpa, /ignore-tpa, /tpacancel. Pady: PPM harmonizatorem; wejdź, aby teleportować; Shift+LPM podnosi własny pad."),false); return 1;
            }).then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
                try {
                    Storage.Config next=Storage.read(configPath,Storage.Config.class,new Storage.Config()); next.validate();Feedback.validateRegistry(next.sounds);
                    config=next;
                    requests.values().forEach(r -> msg(player(r.sender),TPA_CANCELLED,"Teleportacja anulowana: konfiguracja została przeładowana."));
                    confirmations.keySet().forEach(id -> msg(player(id),TPA_CANCELLED,"Potwierdzenie anulowane: konfiguracja została przeładowana."));
                    warmups.keySet().forEach(id -> { feedback.endCountdown(player(id),"tpa");msg(player(id),TPA_CANCELLED,"Teleportacja anulowana: konfiguracja została przeładowana."); });
                    requests.clear(); confirmations.clear(); warmups.clear();
                    padWarmups.keySet().forEach(id -> action(player(id),"Teleportacja przez pad anulowana: konfiguracja przeładowana."));
                    padWarmups.clear();
                    if(c.getSource().getPlayer()!=null) msg(c.getSource().getPlayer(),CONFIG_RELOADED,"Konfiguracja teleportów przeładowana.");
                    else c.getSource().sendSuccess(() -> Component.literal("Konfiguracja teleportów przeładowana."),false); return 1;
                } catch(Exception e) {
                    if(c.getSource().getPlayer()!=null) msg(c.getSource().getPlayer(),CONFIG_ERROR,e.getMessage());
                    else c.getSource().sendFailure(Component.literal(e.getMessage())); return 0;
                }
            })));
        });
    }
    private static boolean usable(ServerPlayer p) { return p.isAlive() && !p.isSpectator(); }
    private boolean ready(ServerPlayer p) {
        if(!usable(p)) { msg(p,"Teleportacja jest niedostępna po śmierci lub w trybie widza."); return false; }
        if(p.isPassenger() || p.isSleeping()) { msg(p,"Najpierw opuść pojazd lub łóżko."); return false; }
        long left=data.cooldowns.getOrDefault(p.getUUID().toString(),0L)-now();
        if(left>0) { msg(p,"Następna teleportacja za "+((left+999)/1000)+" sekund."); return false; }
        return true;
    }
    private boolean validTarget(ServerPlayer p,ServerPlayer t) {
        if(t==null) { msg(p,"Gracza o takim nicku nie ma na serwerze."); return false; }
        if(p==t) { msg(p,"Nie możesz teleportować się do siebie."); return false; }
        if(data.ignored.contains(t.getUUID().toString())) { msg(p,"Ten gracz ignoruje prośby o teleportację."); return false; }
        if(!usable(t)) { msg(p,"Odbiorca jest martwy lub w trybie widza."); return false; }
        if(p.level()!=t.level()) { msg(p,"TPA jest dostępne tylko w tym samym wymiarze."); return false; }
        if(Math.hypot(p.getX()-t.getX(),p.getZ()-t.getZ())<config.tooCloseDistanceMeters) {
            msg(p,"Zbyt blisko, aby się teleportować. Minimalna odległość: "+config.tooCloseDistanceMeters+" m (X/Z)."); return false;
        }
        return true;
    }
    private int cost(ServerPlayer p,ServerPlayer t) { return Rules.cost(p.getX(),p.getZ(),t.getX(),t.getZ(),config.xpCostEnabled,config.xpPerMeter); }
    static long xp(ServerPlayer p) {
        return Rules.xpAtLevel(p.experienceLevel)+Math.round(p.experienceProgress*p.getXpNeededForNextLevel());
    }
    private boolean canPay(ServerPlayer p,int amount) {
        if(xp(p)<amount) { msg(p,"Brak wystarczającego XP. Koszt: "+amount+" XP; posiadasz: "+xp(p)+" XP."); return false; }
        return true;
    }
    private int request(ServerPlayer p,String nickname) {
        if(!ready(p)) return 0;
        if(requests.containsKey(p.getUUID()) || confirmations.containsKey(p.getUUID()) || warmups.containsKey(p.getUUID())) {
            msg(p,"Masz już aktywną prośbę. Użyj /tpacancel."); return 0;
        }
        ServerPlayer t=server.getPlayerList().getPlayerByName(nickname);
        if(!validTarget(p,t)) return 0;
        int quote=cost(p,t); if(!canPay(p,quote)) return 0;
        Request r=new Request(p.getUUID(),t.getUUID(),quote,now()+1000L*config.requestTimeoutSeconds);
        if(quote>0 && quote>=config.expensiveTeleportThresholdXp) {
            confirmations.put(p.getUUID(),new Request(r.sender,r.target,r.quote,now()+1000L*config.confirmationTimeoutSeconds));
            msg(p,TPA_COST_WARNING,"Koszt teleportacji wyniesie "+quote+" XP - aby kontynuować wpisz '/confirm-tpa'.");
        } else sendRequest(r);
        return 1;
    }
    private void sendRequest(Request r) {
        requests.put(r.sender,r);
        msg(player(r.sender),TPA_REQUEST_SENT,"Wysłano prośbę do "+name(player(r.target))+" (koszt: "+r.quote+" XP). Ważna "+config.requestTimeoutSeconds+" sekund.");
        msg(player(r.target),TPA_REQUEST_RECEIVED,name(player(r.sender))+" chce się do Ciebie teleportować. /tpaccept "+name(player(r.sender))+" lub /tpdeny "+name(player(r.sender))+".");
    }
    private int confirm(ServerPlayer p) {
        Request r=confirmations.remove(p.getUUID());
        if(r==null || r.expires<=now()) { msg(p,"Brak aktywnego potwierdzenia lub potwierdzenie wygasło."); return 0; }
        ServerPlayer t=player(r.target);
        if(!ready(p) || !validTarget(p,t)) return 0;
        int quote=cost(p,t); if(!canPay(p,quote)) return 0;
        if(quote>r.quote) {
            confirmations.put(p.getUUID(),new Request(r.sender,r.target,quote,now()+1000L*config.confirmationTimeoutSeconds));
            msg(p,TPA_COST_WARNING,"Koszt wzrósł do "+quote+" XP. Aby zaakceptować nową cenę, wpisz /confirm-tpa."); return 0;
        }
        feedback.sound(p,TPA_COST_CONFIRMED);sendRequest(new Request(r.sender,r.target,quote,now()+1000L*config.requestTimeoutSeconds)); return 1;
    }
    private int respond(ServerPlayer t,String nickname,boolean accept) {
        List<Request> incoming=requests.values().stream().filter(r -> r.target.equals(t.getUUID()) && r.expires>now()
            && (nickname==null || player(r.sender)!=null && name(player(r.sender)).equalsIgnoreCase(nickname))).toList();
        if(incoming.isEmpty()) { msg(t,"Brak aktywnej prośby o teleportację."); return 0; }
        if(incoming.size()>1) { msg(t,"Masz kilka próśb. Podaj nick: /"+(accept?"tpaccept":"tpdeny")+" <nickname>."); return 0; }
        Request r=incoming.getFirst(); requests.remove(r.sender); ServerPlayer p=player(r.sender);
        if(!accept) { msg(p,TPA_DENIED,name(t)+" odrzucił prośbę o teleportację."); msg(t,TPA_DENIED,"Prośba odrzucona."); return 1; }
        if(p==null || !ready(p) || !validTarget(p,t)) { msg(t,"Nie można rozpocząć teleportacji."); return 0; }
        int amount=cost(p,t);
        if(amount>r.quote) { msg(p,TPA_COST_WARNING,"Koszt wzrósł do "+amount+" XP. Wyślij ponownie /tpa "+name(t)+"."); msg(t,TPA_CANCELLED,"Prośba anulowana: zmienił się koszt."); return 0; }
        if(!canPay(p,amount)) { msg(t,"Nadawca nie ma wystarczającego XP."); return 0; }
        warmups.put(p.getUUID(),new Warmup(t.getUUID(),r.quote,p.position(),p.level(),now()+1000L*config.warmupSeconds));
        feedback.sound(p,TPA_ACCEPTED);
        feedback.countdown(p,"tpa",config.warmupSeconds,"Teleportuję do "+name(t)+" za "+config.warmupSeconds+" sekund... (koszt teleportacji - "+amount+" XP).",null,null);
        msg(t,TPA_ACCEPTED,"Prośba zaakceptowana."); return 1;
    }
    private void disconnect(ServerPlayer p) {
        if(data!=null) {
            cancelFor(p.getUUID(),"Gracz opuścił serwer.");onPad.remove(p.getUUID());padWarmups.remove(p.getUUID());
            PadLock lock=padLocks.remove(p.getUUID());if(lock!=null) unlocked(lock);
            feedback.forget(p.getUUID());
        }
    }
    private void cancelFor(UUID id,String reason) {
        requests.entrySet().removeIf(e -> {
            Request r=e.getValue(); if(!r.sender.equals(id)&&!r.target.equals(id)) return false;
            msg(player(r.sender),TPA_CANCELLED,reason); msg(player(r.target),TPA_CANCELLED,reason); return true;
        });
        confirmations.entrySet().removeIf(e -> {
            if(e.getKey().equals(id)||e.getValue().target.equals(id)) { msg(player(e.getKey()),TPA_CANCELLED,reason); return true; } return false;
        });
        warmups.entrySet().removeIf(e -> {
            if(e.getKey().equals(id)||e.getValue().target.equals(id)) { feedback.endCountdown(player(e.getKey()),"tpa");msg(player(e.getKey()),TPA_CANCELLED,reason); return true; } return false;
        });
    }
    private void tick() {
        long time=now();
        requests.entrySet().removeIf(e -> {
            if(e.getValue().expires>time) return false;
            msg(player(e.getKey()),TPA_EXPIRED,"Prośba o teleportację wygasła."); msg(player(e.getValue().target),TPA_EXPIRED,"Prośba o teleportację wygasła."); return true;
        });
        confirmations.entrySet().removeIf(e -> {
            if(e.getValue().expires>time) return false; msg(player(e.getKey()),TPA_EXPIRED,"Potwierdzenie kosztu wygasło."); return true;
        });
        var it=warmups.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next(); ServerPlayer p=player(entry.getKey()); Warmup w=entry.getValue(); ServerPlayer t=player(w.target);
            if(p==null) { it.remove(); continue; }
            if(!usable(p) || p.level()!=w.level || p.position().distanceToSqr(w.origin)>0.0001 || p.isPassenger() || p.isSleeping()) {
                feedback.endCountdown(p,"tpa");msg(p,TPA_CANCELLED,"Teleportacja anulowana: poruszyłeś się, zmieniłeś wymiar lub nie możesz się teleportować."); it.remove(); continue;
            }
            if(time<w.completes) {
                if(tickCount%5==0) feedback.countdown(p,"tpa",(int)((w.completes-time+999)/1000),"Teleportuję do "+(t==null?"gracza":name(t))+" za "+((w.completes-time+999)/1000)+" sekund... (koszt teleportacji - "+(t==null?w.quote:cost(p,t))+" XP).",null,null);
                continue;
            }
            it.remove();
            feedback.endCountdown(p,"tpa");
            if(!validTarget(p,t)) continue;
            int amount=cost(p,t);
            if(amount>w.quote) { msg(p,TPA_COST_WARNING,"Koszt wzrósł do "+amount+" XP. Wyślij ponownie /tpa "+name(t)+"."); continue; }
            if(!canPay(p,amount)) continue;
            Vec3 dest=t.position();
            if(!safe(p,t.level(),dest)) { msg(p,"Teleportacja anulowana: miejsce docelowe jest niebezpieczne."); continue; }
            ServerLevel originLevel=p.level();Vec3 origin=p.position();
            if(!teleport(p,t.level(),dest)) { msg(p,"Teleportacja nie powiodła się."); continue; }
            debit(p,amount);
            data.cooldowns.put(p.getUUID().toString(),time+1000L*config.cooldownSeconds); save();
            feedback.spatial(originLevel,origin,TPA_TELEPORT);feedback.spatial(t.level(),dest,TPA_TELEPORT);
            feedback.messageOnly(p,TPA_TELEPORT,"Teleportowano do "+name(t)+". Pobrano "+amount+" XP.");
        }
        // Release pair locks before processing new countdowns, independent of player iteration order.
        padLocks.entrySet().removeIf(e -> {
            ServerPlayer p=player(e.getKey());
            boolean release=p==null || !usable(p) || (!inDeadZone(p,data.pads.get(e.getValue().source)) && !inDeadZone(p,data.pads.get(e.getValue().target)));
            if(release) unlocked(e.getValue());return release;
        });
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            for(int slot=0;slot<p.getInventory().getContainerSize();slot++) {
                ItemStack stack=p.getInventory().getItem(slot);
                if(is(stack,"synchronizer")) normalizeSynchronizer(stack);
                else if(is(stack,"pad")) ItemPresentation.apply(stack,"en_us",null);
            }
            tickPad(p,time);
        }
        if(++tickCount%10==0) particles();
        feedback.tick(server);
    }
    private static void debit(ServerPlayer p,int amount) {
        if(amount==0) return;
        long left=xp(p)-amount;
        int level=Rules.levelForXp(left);
        p.setExperienceLevels(level);
        p.setExperiencePoints((int)(left-Rules.xpAtLevel(level)));
        p.totalExperience=(int)Math.min(Integer.MAX_VALUE,left);
    }
    private static boolean teleport(ServerPlayer p,ServerLevel l,Vec3 v) {
        boolean success=p.teleportTo(l,v.x,v.y,v.z,Set.of(),p.getYRot(),p.getXRot(),false);
        if(success) { p.setDeltaMovement(Vec3.ZERO); p.fallDistance=0; }
        return success;
    }
    private static boolean safe(ServerPlayer p,ServerLevel l,Vec3 v) {
        var box=p.getBoundingBox().move(v.subtract(p.position()));
        return l.getWorldBorder().isWithinBounds(box) && l.noCollision(p,box)
            && l.getBlockState(BlockPos.containing(v)).getFluidState().isEmpty()
            && l.getBlockState(BlockPos.containing(v).above()).getFluidState().isEmpty()
            && l.getBlockState(BlockPos.containing(v).below()).isSolid()
            && !l.getBlockState(BlockPos.containing(v).below()).is(Blocks.MAGMA_BLOCK)
            && !l.getBlockState(BlockPos.containing(v)).is(Blocks.FIRE)
            && !l.getBlockState(BlockPos.containing(v)).is(Blocks.SOUL_FIRE);
    }

    static boolean is(ItemStack stack,String kind) {
        if(!stack.is(kind.equals("pad")?Items.END_PORTAL_FRAME:Items.ENDER_EYE)) return false;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("bongos_teleports","").equals(kind);
    }
    static ItemStack item(String kind) {
        ItemStack s=new ItemStack(kind.equals("pad")?Items.END_PORTAL_FRAME:Items.ENDER_EYE);
        CompoundTag tag=new CompoundTag(); tag.putString("bongos_teleports",kind);
        s.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        if(kind.equals("synchronizer")) initializeCraftedSynchronizer(s);
        else ItemPresentation.apply(s,"en_us",null);
        return s;
    }
    public static void initializeCraftedSynchronizer(ItemStack stack) {
        if(!is(stack,"synchronizer")) return;
        stack.set(DataComponents.MAX_STACK_SIZE,1);
        stack.set(DataComponents.MAX_DAMAGE,instance==null?10:instance.config.synchronizerDurability);
        stack.set(DataComponents.DAMAGE,0);
        resetSynchronizer(stack);
    }
    private static Component styled(String text,ChatFormatting color) {
        return Component.literal(text).withStyle(color).withStyle(s -> s.withItalic(false));
    }
    private static void resetSynchronizer(ItemStack stack) {
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.remove("selected"); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        ItemPresentation.apply(stack,"en_us",null);
    }
    private void normalizeSynchronizer(ItemStack stack) {
        stack.set(DataComponents.MAX_STACK_SIZE,1);
        if(!stack.has(DataComponents.MAX_DAMAGE)) stack.set(DataComponents.MAX_DAMAGE,config.synchronizerDurability);
        if(!stack.has(DataComponents.DAMAGE)) stack.set(DataComponents.DAMAGE,0);
        String selected=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("selected","");
        Storage.Pad pad=data.pads.get(selected);
        if(pad==null) { resetSynchronizer(stack); return; }
        ItemPresentation.apply(stack,"en_us",pad);
    }
    static Storage.Pad selectedPad(ItemStack stack) {
        if(instance==null || instance.data==null) return null;
        String id=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("selected","");
        return instance.data.pads.get(id);
    }
    public static boolean clearSynchronizer(ServerPlayer p) {
        ItemStack stack=p.getMainHandItem();
        if(!p.isShiftKeyDown() || !is(stack,"synchronizer") || !usable(p)) return false;
        boolean selected=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("selected");
        resetSynchronizer(stack);
        if(selected) msg(p,SYNCHRONIZER_CLEARED,"Harmonizator TP wyczyszczony.");
        return true;
    }
    private void place(ServerPlayer p,ItemStack s,BlockPos bp) {
        ServerLevel l=p.level();
        if(!usable(p) || p.gameMode.getGameModeForPlayer().isBlockPlacingRestricted()) { msg(p,PAD_ERROR,"Nie możesz teraz postawić pada."); return; }
        if(!l.mayInteract(p,bp) || !l.getWorldBorder().isWithinBounds(bp) || l.isOutsideBuildHeight(bp)
            || !l.getBlockState(bp).isAir() || !l.getBlockState(bp.below()).isSolid()
            || !l.getBlockState(bp.above()).isAir() || !l.getBlockState(bp.above(2)).isAir()
            || !l.getEntities(null,new net.minecraft.world.phys.AABB(bp)).isEmpty()) {
            msg(p,PAD_ERROR,"Nie można postawić pada: potrzeba wolnego miejsca i stabilnego podłoża."); return;
        }
        Storage.Pad pad=new Storage.Pad(p.getUUID().toString(),dim(l),bp.getX(),bp.getY(),bp.getZ());
        if(!l.setBlockAndUpdate(bp,Blocks.END_PORTAL_FRAME.defaultBlockState())) { msg(p,PAD_ERROR,"Nie udało się postawić pada."); return; }
        data.pads.put(pad.id,pad); byPosition.put(pad.key(),pad); save();
        if(!p.isCreative()) s.shrink(1);
        padMsg(p,PAD_PLACED,"Pad Teleportacyjny postawiony. Kliknij go harmonizatorem.",pad);
    }
    private void unlink(Storage.Pad pad) {
        Storage.Pad other=data.pads.get(pad.partner);
        if(other!=null && pad.id.equals(other.partner)) { other.partner=null;feedback.spatial(level(other),Vec3.atCenterOf(pos(other)),PAD_UNLINKED); }
        pad.partner=null;
        padWarmups.entrySet().removeIf(e -> {
            if(!e.getValue().source.equals(pad.id) && !e.getValue().target.equals(pad.id)) return false;
            action(player(e.getKey()),"Teleportacja przez pad anulowana: zmieniło się połączenie."); return true;
        });
        padLocks.entrySet().removeIf(e -> e.getValue().source.equals(pad.id)||e.getValue().target.equals(pad.id));
    }
    private void sync(ServerPlayer p,ItemStack stack,Storage.Pad pad) {
        if(!usable(p)) return;
        normalizeSynchronizer(stack);
        if(!pad.owner.equals(p.getUUID().toString())) { msg(p,PAD_ERROR,"Możesz synchronizować tylko własne pady."); return; }
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String selected=tag.getStringOr("selected",""); Storage.Pad first=data.pads.get(selected);
        if(first==null) {
            tag.putString("selected",pad.id); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            normalizeSynchronizer(stack);
            padMsg(p,SYNCHRONIZER_SELECTED,"Wybrano pierwszy pad. Kliknij drugi pad harmonizatorem.",pad); return;
        }
        if(first==pad) { resetSynchronizer(stack); msg(p,SYNCHRONIZER_CLEARED,"Wybór pada wyczyszczony."); return; }
        if(!first.owner.equals(p.getUUID().toString())) { msg(p,PAD_ERROR,"Pierwszy pad należy do innego gracza."); return; }
        if(!exists(first) || !exists(pad)) { msg(p,PAD_ERROR,"Jeden z padów już nie istnieje. Wybierz pierwszy pad ponownie."); resetSynchronizer(stack); return; }
        if(!config.padsAllowCrossDimension && !first.dimension.equals(pad.dimension)) { msg(p,PAD_ERROR,"Pady muszą być w tym samym wymiarze."); return; }
        unlink(first); unlink(pad); first.partner=pad.id; pad.partner=first.id; save();
        resetSynchronizer(stack);
        int damage=stack.getDamageValue()+1;
        boolean broken=damage>=stack.getMaxDamage();
        if(broken) { stack.shrink(1);feedback.sound(p,SYNCHRONIZER_BROKEN); }
        else stack.setDamageValue(damage);
        padMsg(p,PADS_SYNCHRONIZED,broken?"Pady zsynchronizowane — Harmonizator TP zużyty.":"Pady zsynchronizowane. Wejdź na pad, aby się teleportować.",pad);
    }
    private ServerLevel level(Storage.Pad p) { return server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(p.dimension))); }
    private boolean exists(Storage.Pad p) {
        ServerLevel l=level(p); if(l==null) return false;
        l.getChunkAt(pos(p));
        return l.getBlockState(pos(p)).is(Blocks.END_PORTAL_FRAME);
    }
    private Storage.Pad padAt(ServerLevel l,BlockPos bp) {
        Storage.Pad pad=byPosition.get(key(l,bp));
        if(pad!=null && !l.getBlockState(bp).is(Blocks.END_PORTAL_FRAME)) {
            unlink(pad); data.pads.remove(pad.id); byPosition.remove(pad.key()); save(); return null;
        }
        return pad;
    }
    private static void action(ServerPlayer p,String text) {
        if(p!=null) {
            instance.feedback.endCountdown(p,"pad");
            PadWarmup w=instance.padWarmups.get(p.getUUID());
            Storage.Pad pad=w==null?null:instance.data.pads.get(w.source);
            if(pad==null) msg(p,PAD_CANCELLED,text);
            else instance.padMsg(p,PAD_CANCELLED,text,pad);
        }
    }
    private void unlocked(PadLock lock) {
        for(String id:List.of(lock.source,lock.target)) {
            Storage.Pad pad=data.pads.get(id);
            if(pad!=null) feedback.spatial(level(pad),Vec3.atCenterOf(pos(pad)),PAD_UNLOCKED);
        }
    }
    private static boolean inDeadZone(ServerPlayer p,Storage.Pad pad) {
        return pad!=null && dim(p.level()).equals(pad.dimension)
            && Rules.inDeadZone(p.getX(),p.getZ(),pad.x+.5,pad.z+.5);
    }
    private boolean locked(String id) {
        return padLocks.values().stream().anyMatch(l -> l.source.equals(id)||l.target.equals(id));
    }
    private boolean busy(String id,UUID except) {
        return padWarmups.entrySet().stream().anyMatch(e -> !e.getKey().equals(except)
            && (e.getValue().source.equals(id)||e.getValue().target.equals(id)));
    }
    private void cancelPad(ServerPlayer p,String reason) {
        if(padWarmups.containsKey(p.getUUID())) { action(p,reason);padWarmups.remove(p.getUUID()); }
    }
    private void tickPad(ServerPlayer p,long time) {
        UUID id=p.getUUID();
        if(!usable(p) || p.isPassenger() || p.isSleeping()) {
            cancelPad(p,"Teleportacja przez pad anulowana."); onPad.remove(id); return;
        }
        Storage.Pad pad=padAt(p.level(),BlockPos.containing(p.getX(),p.getY()-.1,p.getZ()));
        if(pad==null) { cancelPad(p,"Teleportacja anulowana: opuściłeś pad."); onPad.remove(id); return; }
        String previous=onPad.put(id,pad.id);
        PadWarmup w=padWarmups.get(id);
        if(w!=null && !w.source.equals(pad.id)) { cancelPad(p,"Teleportacja anulowana: opuściłeś pad."); w=null; }
        if(w==null && pad.id.equals(previous)) return;
        if(locked(pad.id)) { cancelPad(p,"Para padów jest zablokowana."); msg(p,PAD_ERROR,"Para padów jest zablokowana: gracz musi opuścić martwą strefę 1 m."); return; }
        Storage.Pad target=data.pads.get(pad.partner);
        if(target==null || !pad.id.equals(target.partner)) { cancelPad(p,"Pad nie jest połączony."); msg(p,PAD_ERROR,"Ten pad nie jest połączony."); return; }
        if(w!=null && !w.target.equals(target.id)) { cancelPad(p,"Teleportacja anulowana: zmieniło się połączenie."); return; }
        if(!exists(pad) || !exists(target)) { unlink(pad); save(); msg(p,PAD_ERROR,"Połączenie pada jest nieaktualne."); return; }
        if(!config.padsAllowCrossDimension && !pad.dimension.equals(target.dimension)) { cancelPad(p,"Teleportacja między wymiarami jest wyłączona."); msg(p,PAD_ERROR,"Teleportacja między wymiarami jest wyłączona."); return; }
        if(busy(pad.id,id) || busy(target.id,id)) { msg(p,PAD_ERROR,"Para padów odlicza teleportację innego gracza."); return; }
        boolean started=w==null;
        if(started) {
            w=new PadWarmup(pad.id,target.id,time+1000L*config.padWarmupSeconds);
            padWarmups.put(id,w);
        }
        if(time<w.completes) {
            if(started || tickCount%5==0) feedback.countdown(p,"pad",(int)((w.completes-time+999)/1000),"Teleportacja za "+((w.completes-time+999)/1000)+" s — pozostań na padzie.",p.level(),Vec3.atCenterOf(pos(pad)));
            return;
        }
        padWarmups.remove(id);
        feedback.endCountdown(p,"pad");
        ServerLevel l=level(target); Vec3 destination=new Vec3(target.x+.5,target.y+1,target.z+.5);
        if(!safe(p,l,destination)) { msg(p,PAD_ERROR,"Teleportacja zablokowana: nad drugim padem nie ma bezpiecznego miejsca."); return; }
        ServerLevel originLevel=p.level();Vec3 origin=p.position();
        if(teleport(p,l,destination)) {
            warmups.remove(id); onPad.put(id,target.id); padLocks.put(id,new PadLock(pad.id,target.id));
            feedback.endCountdown(p,"tpa");
            feedback.spatial(originLevel,origin,PAD_TELEPORT);feedback.spatial(l,destination,PAD_TELEPORT);
            feedback.messageOnly(p,PAD_TELEPORT,"Teleportowano przez pad. Opuść martwą strefę 1 m, aby odblokować pady.");
        } else msg(p,PAD_ERROR,"Teleportacja nie powiodła się.");
    }
    private void particles() {
        for(Storage.Pad pad:new ArrayList<>(data.pads.values())) {
            ServerLevel l=level(pad);
            if(l==null || !l.getChunkSource().hasChunk(pad.x>>4,pad.z>>4)) continue;
            if(!l.getBlockState(pos(pad)).is(Blocks.END_PORTAL_FRAME)) continue;
            Storage.Pad other=data.pads.get(pad.partner);
            int color=other==null || !pad.id.equals(other.partner)?0xFF5555
                :locked(pad.id)?0xAAAAAA:busy(pad.id,null)?0xFFFF55:0x55FF55;
            l.sendParticles(new DustParticleOptions(color,1f),pad.x+.5,pad.y+1.25,pad.z+.5,6,.28,.08,.28,0);
        }
    }
}

