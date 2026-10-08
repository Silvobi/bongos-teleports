package pl.bongo.teleports;

import io.netty.buffer.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.nbt.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.*;
import net.minecraft.util.Crypt;
import com.mojang.serialization.Lifecycle;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;
import java.nio.file.*;
import javax.crypto.*;

/** Headless clients using the vanilla 26.3 protocol against an isolated server. */
public final class IntegrationHarness {
    static int port=25582;
    static boolean decodeRecipeBook=false;
    static Process process;
    static PrintWriter console;
    static final List<String> logs=Collections.synchronizedList(new ArrayList<>());
    static void check(boolean value,String label) { if(!value) throw new AssertionError(label); System.out.println("PASS: "+label); }
    static void cmd(String command) throws Exception { console.println(command); console.flush(); Thread.sleep(150); }
    static void at(Bot bot,double x,double z) throws Exception { cmd("tp "+bot.name+" "+x+" 1 "+z); bot.drain(200); }
    static void click(Bot bot,int x,int y,int z) throws Exception {
        bot.send(new ServerboundUseItemOnPacket(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(x+.5,y+1,z+.5),Direction.UP,new BlockPos(x,y,z),false),1));
    }
    static void equip(Bot bot,String item,String kind) throws Exception {
        cmd("item replace entity "+bot.name+" weapon.mainhand with minecraft:"+item+"[minecraft:custom_data={bongos_teleports:\""+kind+"\"}] 1"); bot.drain(100);
    }
    static net.minecraft.world.item.ItemStack synchronizer(Bot bot) throws Exception {
        bot.drain(200);
        return bot.events.stream().filter(e->e instanceof ClientboundContainerSetSlotPacket)
            .map(e->((ClientboundContainerSetSlotPacket)e).getItem()).filter(s->Teleports.is(s,"synchronizer"))
            .reduce((a,b)->b).orElseThrow();
    }
    static void color(Bot bot,int x,int rgb,String label) throws Exception {
        bot.await(e -> e instanceof ClientboundLevelParticlesPacket p && Math.abs(p.x()-(x+.5))<.01
            && p.particle() instanceof net.minecraft.core.particles.DustParticleOptions dust
            && Math.abs(dust.getColor().x-((rgb>>16)&255)/255f)<.01
            && Math.abs(dust.getColor().y-((rgb>>8)&255)/255f)<.01
            && Math.abs(dust.getColor().z-(rgb&255)/255f)<.01,3000);
        check(true,label);
    }
    static void start(Path dir) throws Exception {
        Files.deleteIfExists(dir.resolve("recipe-verification.txt"));
        process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xms256M","-Xmx1500M","-jar","fabric-server-launch.jar","nogui").directory(dir.toFile()).redirectErrorStream(true).start();
        console=new PrintWriter(process.getOutputStream(),true,StandardCharsets.UTF_8);
        Thread reader=new Thread(() -> { try(var r=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) { String line; while((line=r.readLine())!=null) logs.add(line); } catch(IOException e) {} }); reader.setDaemon(true); reader.start();
        long until=System.currentTimeMillis()+90000;
        while(System.currentTimeMillis()<until && process.isAlive()) {
            synchronized(logs) { if(logs.stream().anyMatch(l->l.contains("Bongo's Teleports ready:")) && Files.exists(dir.resolve("recipe-verification.txt"))) return; }
            Thread.sleep(100);
        }
        throw new AssertionError("Server startup failed: "+logs);
    }
    static void stop() throws Exception {
        if(process!=null && process.isAlive()) { console.println("stop"); console.flush(); if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)) process.destroy(); }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY); });
        Path dir=Path.of(args[0]);
        Files.deleteIfExists(dir.resolve("world/bongos-teleports.json"));
        Storage.Config initial=new Storage.Config(); initial.xpPerMeter=1; initial.tooCloseDistanceMeters=0;
        initial.warmupSeconds=1; initial.cooldownSeconds=2; initial.expensiveTeleportThresholdXp=50;
        initial.requestTimeoutSeconds=2; initial.confirmationTimeoutSeconds=2;
        Storage.write(dir.resolve("config/bongos-teleports.json"),initial);
        try {
            start(dir); check(Files.readString(dir.resolve("recipe-verification.txt")).contains("PASS all 6"),"real server recipe matching and output components");
            try(Bot a=new Bot("TpaAlice","pl_pl"); Bot b=new Bot("TpaBob","pl_pl"); Bot c=new Bot("TpaOther","pl_pl")) {
                cmd("op TpaAlice"); cmd("op TpaBob"); cmd("op TpaOther");
                cmd("gamemode survival TpaAlice"); cmd("gamemode survival TpaBob");
                at(a,.5,.5); at(b,3.5,4.5); at(c,30.5,.5);
                cmd("experience set TpaAlice 0 levels"); cmd("experience set TpaAlice 0 points"); cmd("experience add TpaAlice 200 points"); cmd("experience add TpaOther 200 points"); a.drain(200);
                a.command("tpa Nobody123"); a.message("nie ma na serwerze"); check(true,"unknown nickname");
                a.command("tpa TpaAlice"); a.message("do siebie"); check(true,"self request rejected");
                b.command("ignore-tpa"); b.message("włączone"); a.command("tpa TpaBob"); a.message("ignoruje");
                b.command("ignore-tpa"); b.message("wyłączone"); check(true,"ignore toggle blocks requests");
                a.command("tpa TpaBob"); a.message("koszt: 5 XP"); b.message("/tpaccept TpaAlice");
                b.command("tpdeny TpaAlice"); a.message("odrzucił"); check(true,"recipient denial");
                a.command("tpa TpaBob"); a.message("Wysłano"); b.command("tpaccept TpaAlice"); a.message("za 1 sekund"); a.message("Pobrano 5 XP"); a.drain(200);
                check(Math.abs(a.x-3.5)<.01 && Math.abs(a.z-4.5)<.01,"accepted TPA reaches target");
                check(a.totalXp==195,"exact XP debit across levels (200 minus 5)");
                a.command("tpa TpaBob"); a.message("Następna teleportacja"); check(true,"cooldown after success");
                a.drain(2100); at(a,.5,.5); at(b,3.5,4.5);
                a.command("tpa TpaBob"); a.message("Wysłano"); b.command("tpaccept TpaAlice"); a.message("za 1 sekund");
                at(a,1.5,.5); a.message("poruszyłeś"); a.drain(1000); check(a.totalXp==195,"movement cancels warmup without charge");
                at(a,.5,.5); at(b,100.5,.5);
                a.command("tpa TpaBob"); a.message("100 XP - aby kontynuować"); b.drain(200);
                a.command("confirm-tpa"); a.message("Wysłano"); b.message("chce się"); b.command("tpaccept TpaAlice"); a.message("za 1 sekund"); a.message("Pobrano 100 XP"); a.drain(100);
                check(a.totalXp==95,"expensive teleport requires explicit confirmation");
                a.drain(2100); at(a,.5,.5); at(b,200.5,.5); a.command("tpa TpaBob"); a.message("Brak wystarczającego XP"); check(true,"insufficient XP rejected");
                at(b,3.5,4.5); a.command("tpa TpaBob"); a.message("Wysłano"); b.command("tpaccept TpaAlice"); a.message("za 1 sekund");
                at(b,30.5,.5); a.message("Koszt wzrósł"); a.drain(150); check(a.totalXp==95,"target movement cannot silently increase approved price");
                at(b,3.5,4.5); a.command("tpa TpaBob"); a.message("Wysłano"); a.message("wygasła"); check(true,"request expiry");
                a.command("tpa TpaBob"); a.message("Wysłano"); c.command("tpa TpaBob"); c.message("Wysłano"); b.command("tpaccept"); b.message("kilka próśb"); a.command("tpacancel"); a.message("anulowana"); c.command("tpacancel"); c.message("anulowana"); check(true,"multiple requests require sender selection");
                cmd("gamemode creative TpaAlice"); at(a,10.5,8.5); cmd("setblock 10 1 10 air"); cmd("setblock 20 1 10 air"); cmd("setblock 10 2 10 air");
                equip(a,"end_portal_frame","pad"); click(a,10,0,10); a.message("postawiony");
                color(a,10,0xFF5555,"unpaired pad emits red particles");
                at(a,20.5,8.5); click(a,20,0,10); a.message("postawiony");
                equip(a,"ender_eye","synchronizer"); at(a,10.5,8.5); click(a,10,1,10); a.message("pierwszy pad");
                a.drain(200); check(a.events.stream().filter(e->e instanceof ClientboundContainerSetSlotPacket).map(e->((ClientboundContainerSetSlotPacket)e).getItem()).anyMatch(s->s.get(DataComponents.LORE)!=null && s.get(DataComponents.LORE).lines().stream().anyMatch(l->l.getString().contains("Wybrano:"))),"selection status delivered in vanilla lore");
                var active=synchronizer(a);
                check(active.get(DataComponents.ITEM_NAME).getStyle().getColor().getValue()==0xFFFF55
                    && active.get(DataComponents.LORE).lines().stream().allMatch(line->line.getStyle().getColor().getValue()==0xAAAAAA),"yellow selected name and gray lore");
                check(active.getMaxDamage()==10 && active.getDamageValue()==0,"legacy synchronizer receives durability without charge");
                a.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false,false,false,false,false,true,false)));
                a.send(ServerboundPunchPacket.INSTANCE); a.message("Harmonizator TP wyczyszczony");
                var cleared=synchronizer(a); check(cleared.has(DataComponents.LORE) && cleared.get(DataComponents.LORE).lines().stream().noneMatch(l->l.getString().contains("Wybrano:")) && cleared.getDamageValue()==0,"shift left click in air restores description without using durability");
                a.send(new ServerboundPlayerInputPacket(net.minecraft.world.entity.player.Input.EMPTY));
                click(a,10,1,10); a.message("pierwszy pad");
                at(a,20.5,8.5); click(a,20,1,10); a.message("zsynchronizowane");
                var completed=synchronizer(a);
                check(completed.has(DataComponents.LORE) && completed.get(DataComponents.LORE).lines().stream().noneMatch(l->l.getString().contains("Wybrano:")) && completed.get(DataComponents.ITEM_NAME).getStyle().getColor().getValue()==0xFFFFFF
                    && completed.getDamageValue()==1,"successful synchronization clears lore/name and consumes exactly one use in Creative");
                color(a,20,0x55FF55,"paired idle pad emits green particles");
                at(b,10.5,8.5); equip(b,"ender_eye","synchronizer"); click(b,10,1,10); b.message("tylko własne"); check(true,"pad ownership protection");
                cmd("tp TpaAlice 10.5 1.8125 10.5");
                a.await(e -> e instanceof ClientboundSystemChatPacket p && p.overlay() && p.content().getString().contains("Teleportacja za 3 s"),2000);
                color(a,10,0xFFFF55,"warmup emits yellow particles and countdown appears above hotbar");
                check(Math.abs(a.x-10.5)<.01,"pad does not teleport immediately");
                at(a,10.5,8.5); a.message("opuściłeś pad"); a.drain(3100); check(Math.abs(a.x-10.5)<.01 && Math.abs(a.z-8.5)<.01,"leaving pad cancels countdown");
                cmd("tp TpaAlice 10.5 1.8125 10.5"); a.message("przez pad"); a.drain(3500);
                check(Math.abs(a.x-20.5)<.01,"paired pad teleport and no bouncing back");
                a.events.removeIf(e->e instanceof ClientboundLevelParticlesPacket);
                color(a,10,0xAAAAAA,"both pads turn gray while traveler stays in dead zone");
                at(b,10.5,8.5); cmd("tp TpaBob 10.5 1.8125 10.5"); b.message("Para padów jest zablokowana"); check(true,"dead zone blocks the source pad for another player");
                cmd("tp TpaAlice 21.3 1.8125 10.5"); a.drain(1200); a.events.removeIf(e->e instanceof ClientboundLevelParticlesPacket); color(a,20,0xAAAAAA,"leaving pad by 0.8m keeps dead zone lock");
                at(a,20.5,8.5); cmd("setblock 10 2 10 stone"); cmd("tp TpaAlice 20.5 1.8125 10.5"); a.message("bezpiecznego miejsca"); a.drain(150); check(Math.abs(a.x-20.5)<.01,"blocked pad destination prevents suffocation");
                cmd("setblock 10 2 10 air"); b.command("ignore-tpa"); b.message("włączone");
                at(a,.5,.5);
            }
            stop(); logs.clear(); start(dir);
            try(Bot a=new Bot("TpaAlice","pl_pl"); Bot b=new Bot("TpaBob","pl_pl")) {
                at(a,.5,.5); at(b,3.5,4.5); a.command("tpa TpaBob"); a.message("ignoruje"); check(true,"ignore persisted across server restart");
                at(a,10.5,8.5); cmd("tp TpaAlice 10.5 1.8125 10.5"); a.message("przez pad"); a.drain(100); check(Math.abs(a.x-20.5)<.01,"paired pads persisted across restart");
                at(b,10.5,8.5); b.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,new BlockPos(10,1,10),Direction.UP)); b.message("innego gracza"); check(true,"outsider cannot remove a pad");
                at(a,10.5,8.5); a.send(new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false,false,false,false,false,true,false))); a.drain(100);
                equip(a,"stone_pickaxe","unused");
                a.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,new BlockPos(10,1,10),Direction.UP)); a.message("Pad podniesiony");
                var saved=Storage.read(dir.resolve("world/bongos-teleports.json"),Storage.Data.class,null);
                check(saved.pads.size()==1 && saved.pads.values().stream().allMatch(p->p.partner==null),"pickup persistently removes the pad and reciprocal link");
                at(a,20.5,8.5); a.drain(3100);
                cmd("tp TpaAlice 20.5 1.8125 10.5"); a.message("nie jest połączony"); check(true,"owner pickup breaks both ends of the link");
                initial.xpPerMeter=.1; initial.tooCloseDistanceMeters=50; initial.expensiveTeleportThresholdXp=1000;
                Storage.write(dir.resolve("config/bongos-teleports.json"),initial); cmd("bongoteleports reload");
                b.command("ignore-tpa"); b.message("wyłączone"); at(a,.5,.5); at(b,49.5,.5);
                a.command("tpa TpaBob"); a.message("Zbyt blisko"); check(true,"TPA under 50m rejected");
                at(b,50.5,.5); a.command("tpa TpaBob"); a.message("koszt: 5 XP"); a.command("tpacancel"); a.message("anulowana");
                check(true,"exactly 50m allowed and costs 5 XP at new default rate");
                initial.tooCloseDistanceMeters=0; Storage.write(dir.resolve("config/bongos-teleports.json"),initial); cmd("bongoteleports reload");
                at(b,8.5,.5); a.command("tpa TpaBob"); a.message("koszt: 1 XP"); a.command("tpacancel"); a.message("anulowana");
                check(true,"too-close threshold configurable to zero and short distance rounds up to 1 XP");
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") && (l.contains("bongos_teleports") || l.contains("recipe"))),"no mod or recipe loading errors"); }
        } finally { stop(); Files.write(dir.resolve("integration-server.log"),logs); }
    }
    static final class Bot implements AutoCloseable {
        final String name; UUID uuid; double x,z; int totalXp;
        final Socket socket;
        InputStream input; OutputStream output;
        StreamCodec inbound=LoginProtocols.CLIENTBOUND.codec(),outbound=HandshakeProtocols.SERVERBOUND.codec();
        int compression=-1;
        RegistryAccess access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final List<Component> messages=new ArrayList<>();
        final Map<UUID,Component> tabs=new HashMap<>();
        final Map<String,ClientboundSetPlayerTeamPacket.Parameters> teams=new HashMap<>();
        final List<Object> events=new ArrayList<>();
        final List<ClientboundResourcePackPushPacket> configurationPacks=new ArrayList<>();
        final Map<Integer,net.minecraft.network.protocol.PacketType<?>> types=new HashMap<>();
        @FunctionalInterface interface PackReply {
            void handle(Bot bot,ClientboundResourcePackPushPacket pack) throws Exception;
        }
        Bot(String name,String language) throws Exception {
            this(name,language,(bot,pack)-> {
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.ACCEPTED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DOWNLOADED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            });
        }
        Bot(String name,String language,PackReply packReply) throws Exception {
            this.name=name; uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            socket=new Socket("127.0.0.1",port); socket.setSoTimeout(20000); input=socket.getInputStream();output=socket.getOutputStream();
            send(new ClientIntentionPacket(SharedConstants.getProtocolVersion(),"localhost",port,ClientIntent.LOGIN));
            outbound=LoginProtocols.SERVERBOUND.codec();send(new ServerboundHelloPacket(name,uuid));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundHelloPacket hello) {
                    var secret=Crypt.generateSecretKey();send(new ServerboundKeyPacket(secret,hello.getPublicKey(),hello.getChallenge()));
                    input=new CipherInputStream(input,Crypt.getCipher(2,secret));output=new CipherOutputStream(output,Crypt.getCipher(1,secret));
                } else if(packet instanceof ClientboundLoginCompressionPacket threshold) compression=threshold.getCompressionThreshold();
                else if(packet instanceof ClientboundLoginDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundLoginFinishedPacket finished) { uuid=finished.gameProfile().id(); break; }
            }
            send(ServerboundLoginAcknowledgedPacket.INSTANCE);inbound=ConfigurationProtocols.CLIENTBOUND.codec();outbound=ConfigurationProtocols.SERVERBOUND.codec();
            var options=new ClientInformation(language,2,ChatVisiblity.FULL,true,127,HumanoidArm.RIGHT,false,true,ParticleStatus.ALL);
            send(new ServerboundClientInformationPacket(options));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundSelectKnownPacks) send(new ServerboundSelectKnownPacks(List.of()));
                else if(packet instanceof ClientboundResourcePackPushPacket pack) {
                    configurationPacks.add(pack);
                    packReply.handle(this,pack);
                }
                else if(packet instanceof ClientboundShowDialogPacket shown) {
                    MultiActionDialog dialog=(MultiActionDialog)shown.dialog().value();
                    CompoundTag fields=new CompoundTag();fields.putString("password","clans-test-password");fields.putString("repeat","clans-test-password");
                    CustomAll action=(CustomAll)dialog.actions().getFirst().action().orElseThrow();
                    send(new ServerboundCustomClickActionPacket(action.id(),Optional.of(fields)));
                } else if(packet instanceof ClientboundRegistryDataPacket data && data.registry().equals(Registries.CHAT_TYPE)) {
                    var chatTypes=new MappedRegistry<ChatType>(Registries.CHAT_TYPE,Lifecycle.stable());
                    for(var entry:data.entries()) chatTypes.register(ResourceKey.create(Registries.CHAT_TYPE,entry.id()),ChatType.DIRECT_CODEC.parse(NbtOps.INSTANCE,entry.data().orElseThrow()).getOrThrow(),RegistrationInfo.BUILT_IN);
                    chatTypes.freeze();
                    List<Registry<?>> registries=new ArrayList<>(access.registries().map(RegistryAccess.RegistryEntry::value).toList());registries.add(chatTypes);
                    access=new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
                } else if(packet instanceof ClientboundKeepAlivePacket alive) send(new ServerboundKeepAlivePacket(alive.getId()));
                else if(packet instanceof ClientboundPingPacket ping) send(new ServerboundPongPacket(ping.getId()));
                else if(packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundFinishConfigurationPacket) break;
            }
            send(ServerboundFinishConfigurationPacket.INSTANCE);
            outbound=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access),new GameProtocols.Context() {
                public boolean hasInfiniteMaterials(){return false;}public boolean canUseCommandBlocks(){return false;}
            }).codec();
            GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,index)->types.put(index,type));
            send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            send(new ServerboundPlayerLoadedPacket());
            drain(150);
        }
        String teamPrefix() { return teams.values().stream().filter(p->p.playerPrefix().getString().startsWith("[★")).findFirst().orElseThrow().playerPrefix().getString(); }
        void command(String command) throws Exception { send(new ServerboundChatCommandPacket(command)); }
        void chat(String message) throws Exception { send(new ServerboundChatPacket(message,Instant.now(),0,Optional.empty(),new LastSeenMessages.Update(0,new BitSet(),LastSeenMessages.Update.IGNORE_CHECKSUM))); }
        Component message(String contains) throws Exception {
            Object p=await(e->e instanceof Component c && c.getString().contains(contains),5000);return (Component)p;
        }
        Object await(Predicate<Object> predicate,long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(System.currentTimeMillis()<deadline) {
                for(int i=0;i<events.size();i++) if(predicate.test(events.get(i))) return events.remove(i);
                poll(100);
            }
            throw new AssertionError(name+" timed out; recent chat="+messages.stream().map(Component::getString).toList());
        }
        void drain(long millis) throws Exception { long until=System.currentTimeMillis()+millis;do{poll(5);}while(System.currentTimeMillis()<until); }
        Object poll(long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(input.available()==0 && socket.getInputStream().available()==0) {if(System.currentTimeMillis()>=deadline)return null;Thread.sleep(2);}
            var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(frame()),access);
            Object result=null;
            try {
                var type=types.get(buffer.readVarInt());
                if(type==CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE) send(new ServerboundKeepAlivePacket(ClientboundKeepAlivePacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_PING) send(new ServerboundPongPacket(ClientboundPingPacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_DISCONNECT) throw new AssertionError(ClientboundDisconnectPacket.STREAM_CODEC.decode(buffer).reason());
                else if(type==CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG) result=ClientboundShowDialogPacket.STREAM_CODEC.decode(buffer);
                else if(type==CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH) result=ClientboundResourcePackPushPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_ADD_ENTITY) result=ClientboundAddEntityPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA) result=ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES) result=ClientboundRemoveEntitiesPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SOUND) result=ClientboundSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_STOP_SOUND) result=ClientboundStopSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_POSITION) {
                    var p=ClientboundPlayerPositionPacket.STREAM_CODEC.decode(buffer);var c=p.change();x=c.position().x;z=c.position().z;send(new ServerboundAcceptTeleportationPacket(p.id(),c.position().x,c.position().y,c.position().z,c.yRot(),c.xRot()));
                } else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE) {
                    var p=ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
                    if(p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) for(var e:p.entries()) tabs.put(e.profileId(),e.displayName());
                    result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM) {
                    var p=ClientboundSetPlayerTeamPacket.STREAM_CODEC.decode(buffer);p.getParameters().ifPresent(params->teams.put(p.getName(),params));
                    if(p.getTeamAction()==ClientboundSetPlayerTeamPacket.Action.REMOVE) teams.remove(p.getName());result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT) { var chat=ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer); events.add(chat); result=chat.content(); }
                else if(type==GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES) result=ClientboundLevelParticlesPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_CHAT) {
                    var p=ClientboundPlayerChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.unsignedContent().orElseGet(()->Component.literal(p.body().content())));
                } else if(type==GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT) {
                    var p=ClientboundDisguisedChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.message());
                } else if(type==GamePacketTypes.CLIENTBOUND_OPEN_SCREEN) result=ClientboundOpenScreenPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SET_EXPERIENCE) { var xp=ClientboundSetExperiencePacket.STREAM_CODEC.decode(buffer); totalXp=xp.getTotalExperience(); result=xp; }
                else if(type==GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT) result=ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT) result=ClientboundContainerSetContentPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SET_CURSOR_ITEM) result=ClientboundSetCursorItemPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SET_PLAYER_INVENTORY) result=ClientboundSetPlayerInventoryPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_ADD && decodeRecipeBook) result=ClientboundRecipeBookAddPacket.STREAM_CODEC.decode(buffer);
            } finally {buffer.release();}
            if(result!=null){events.add(result);if(result instanceof Component c)messages.add(c);}
            return result;
        }
        Packet<?> read() throws Exception {ByteBuf b=Unpooled.wrappedBuffer(frame());try{return(Packet<?>)inbound.decode(b);}finally{b.release();}}
        byte[] frame() throws Exception {
            int size=varInt(input);if(size<0||size>8388608)throw new IOException("Bad frame size");byte[] bytes=new byte[size];
            for(int offset=0;offset<size;){int count=input.read(bytes,offset,size-offset);if(count<0)throw new EOFException();offset+=count;}
            if(compression>=0){var compressed=new ByteArrayInputStream(bytes);int original=varInt(compressed);bytes=original==0?compressed.readAllBytes():new InflaterInputStream(compressed).readAllBytes();}return bytes;
        }
        void send(Packet<?> packet) throws Exception {
            ByteBuf buffer=Unpooled.buffer();byte[] bytes;
            try{outbound.encode(buffer,packet);bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);}finally{buffer.release();}
            if(compression>=0){var frame=new ByteArrayOutputStream();if(bytes.length>=compression){varInt(frame,bytes.length);var deflate=new DeflaterOutputStream(frame);deflate.write(bytes);deflate.finish();}else{varInt(frame,0);frame.write(bytes);}bytes=frame.toByteArray();}
            varInt(output,bytes.length);output.write(bytes);output.flush();
        }
        public void close() throws IOException {socket.close();}
    }
    static int varInt(InputStream input)throws IOException {int value=0;for(int i=0;i<5;i++){int b=input.read();if(b<0)throw new EOFException();value|=(b&127)<<(i*7);if((b&128)==0)return value;}throw new IOException("Bad VarInt");}
    static void varInt(OutputStream output,int value)throws IOException {do{int b=value&127;value>>>=7;output.write(b|(value==0?0:128));}while(value!=0);}
}


