package pl.bongo.teleports;

import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import static pl.bongo.teleports.IntegrationHarness.*;
import static pl.bongo.teleports.SharedClaimsHarness.*;

/** Regression for waiting screens and for features lost when parallel Utils branches were combined. */
public final class DialogLifecycleHarness {
    static MultiActionDialog openForm(Bot bot,String command) throws Exception {
        bot.events.clear();bot.command(command);return dialog(bot);
    }
    static void submit(Bot bot,ActionButton button,String field,String value) throws Exception {
        var data=new CompoundTag();data.putString(field,value);
        bot.send(new ServerboundCustomClickActionPacket(action(button).id(),Optional.of(data)));
    }
    static void closed(Bot bot) throws Exception {
        bot.await(p->p instanceof ClientboundClearDialogPacket,3000);
    }
    static String body(MultiActionDialog dialog) {
        return dialog.common().body().stream().filter(b->b instanceof PlainMessage).map(b->((PlainMessage)b).contents().getString()).reduce("",(a,b)->a+"\n"+b);
    }
    static void policy(Dialog dialog,DialogAction expected,String name) {
        check(dialog.common().afterAction()==expected,name+" avoids the native waiting screen");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        Path root=Path.of(args[0]);SharedClaimsHarness.root=root;fixture();
        // This directory is disposable and marker guarded by fixture(). Start from a clean social/mail state.
        Files.deleteIfExists(root.resolve("config/bongoutils/social.json"));
        Files.deleteIfExists(root.resolve("config/bongoutils/mail.json"));
        Files.deleteIfExists(root.resolve("world/bongo-statuses.json"));
        try {
            start(root);
            try(Bot a=new Bot(OWNER,"pl_pl");Bot b=new Bot(MATE,"en_us");Bot c=new Bot(OTHER,"pl_pl")) {
                for(var bot:List.of(a,b,c)) {
                    var auth=(MultiActionDialog)bot.configurationDialogs.getFirst();
                    policy(auth,DialogAction.NONE,"authentication "+bot.name);
                    check(!auth.common().canCloseWithEscape(),"authentication remains mandatory before world entry");
                }
                at(a,30.5,1,3.5);at(b,10.5,1,3.5);at(c,15.5,1,3.5);cmd("op "+OWNER);
                var menu=openForm(a,"skin");policy(menu,DialogAction.CLOSE,"skin menu");
                check(menu.exitAction().orElseThrow().action().isEmpty(),"skin Close is entirely local and needs no server reply");
                click(a,menu.actions().getFirst());var form=dialog(a);policy(form,DialogAction.CLOSE,"IGN form");
                submit(a,form.actions().getFirst(),"source","");form=dialog(a);
                check(!body(form).isBlank(),"invalid skin input returns a usable form");
                click(a,form.exitAction().orElseThrow());menu=dialog(a);click(a,menu.actions().get(1));form=dialog(a);
                policy(form,DialogAction.CLOSE,"URL form");click(a,form.exitAction().orElseThrow());dialog(a);
                form=openForm(a,"changepass");policy(form,DialogAction.CLOSE,"password lookup");
                submit(a,form.actions().getFirst(),"nick",MATE);form=dialog(a);policy(form,DialogAction.CLOSE,"password edit");
                click(a,form.actions().get(1));form=dialog(a);policy(form,DialogAction.CLOSE,"password reset confirmation");
                click(a,form.exitAction().orElseThrow());form=dialog(a);
                click(a,form.exitAction().orElseThrow());form=dialog(a);click(a,form.exitAction().orElseThrow());closed(a);
                check(true,"password navigation and cancellation send a terminal response");
                menu=openForm(a,"manageclaim");policy(menu,DialogAction.CLOSE,"local claim panel");
                click(a,menu.exitAction().orElseThrow());closed(a);
                menu=openForm(a,"manageclaim global");policy(menu,DialogAction.CLOSE,"global claim panel");
                var stale=menu.exitAction().orElseThrow();click(a,stale);closed(a);
                menu=openForm(a,"manageclaim global");click(a,stale);a.message("Nieaktualny");
                click(a,menu.exitAction().orElseThrow());closed(a);
                check(true,"stale claim action cannot consume a newer valid dialog");
                menu=openForm(a,"wiki");policy(menu,DialogAction.CLOSE,"wiki");
                check(menu.exitAction().orElseThrow().action().isEmpty(),"wiki Close works locally");
                a.events.clear();a.command("rules");var rules=((ClientboundShowDialogPacket)a.await(p->p instanceof ClientboundShowDialogPacket,3000)).dialog().value();
                policy(rules,DialogAction.CLOSE,"rules");
                a.command("clan create DialogTeam DT");a.message("DialogTeam");
                menu=openForm(a,"clan delete");policy(menu,DialogAction.NONE,"clan delete countdown");
                check(menu.actions().getFirst().action().isEmpty(),"clicking countdown has no action and keeps the existing screen");
                click(a,menu.exitAction().orElseThrow());closed(a);a.message("Anulowano");
                menu=openForm(a,"clan delete");
                long until=System.currentTimeMillis()+12000;MultiActionDialog ready=null;
                while(System.currentTimeMillis()<until && ready==null) {
                    Object packet=a.poll(100);b.poll(1);c.poll(1);
                    if(packet instanceof ClientboundShowDialogPacket shown && shown.dialog().value() instanceof MultiActionDialog next
                            && next.actions().getFirst().action().isPresent()) ready=next;
                }
                check(ready!=null,"clan countdown becomes confirmable without client waiting screen");
                policy(ready,DialogAction.NONE,"clan delete confirmation");click(a,ready.actions().getFirst());closed(a);
                // Mail survives an offline recipient and a real restart; opening marks it read.
                c.close();a.drain(250);
                menu=openForm(a,"mail send "+OTHER);policy(menu,DialogAction.CLOSE,"mail composer");
                submit(a,menu.actions().getFirst(),"body","");form=dialog(a);policy(form,DialogAction.CLOSE,"mail validation error");
                submit(a,form.actions().getFirst(),"body","Offline dialog regression");closed(a);a.message("Wysłano maila");
                menu=openForm(b,"mail read new");policy(menu,DialogAction.CLOSE,"empty inbox");click(b,menu.exitAction().orElseThrow());closed(b);
                a.command("status Buduję");a.message("Ustawiono status");b.drain(250);
                a.command("ignore "+MATE);a.message("Ignorowanie włączone");b.drain(250);
                a.command("ignore "+MATE);a.message("Ignorowanie wyłączone");b.drain(250);
                check(b.tabs.get(a.uuid)!=null && b.tabs.get(a.uuid).getString().equals("[Buduję] "+OWNER),"ignore refresh does not duplicate TAB status");
                a.command("ignore "+MATE);a.message("Ignorowanie włączone");
                b.events.clear();b.command("m "+OWNER+" blocked");b.message("Cannot message");b.command("r nobody");b.message("nobody to reply to");
                b.events.clear();b.chat("blocked @"+OWNER);b.drain(200);a.events.clear();a.drain(350);
                check(a.events.stream().noneMatch(p->p instanceof ClientboundSoundPacket || p instanceof ClientboundSystemChatPacket chat && chat.overlay()),"ignored public mention emits no ping");
                b.command("tpa "+OWNER);b.message("Teleportation between these players is unavailable.");
                check(true,"blocked PM does not replace reply partner and blocked TPA respects Utils");
                a.command("ignore "+MATE);a.message("Ignorowanie wyłączone");
                a.command("vanish");a.message("Tryb widoczności");b.command("tpa "+OWNER);b.message("Teleportation between these players is unavailable.");
                a.command("vanish");a.message("Tryb widoczności");
                check(true,"full vanish blocks TPA after the merged integration");
            }
            stop();logs.clear();start(root);
            try(Bot c=new Bot(OTHER,"pl_pl")) {
                c.message("Nowe wiadomości w skrzynce pocztowej: 1");
                menuAfterRestart(c);
            }
            synchronized(logs) { check(logs.stream().noneMatch(l->l.contains("ERROR") || l.contains("Mixin apply failed")),"merged server restarts without mixin or storage errors"); }
        } finally { stop();Files.write(root.resolve("dialog-lifecycle-server.log"),logs); }
    }
    static void menuAfterRestart(Bot c) throws Exception {
        var menu=openForm(c,"mail");policy(menu,DialogAction.CLOSE,"mail inbox (bare /mail)");click(c,menu.actions().getFirst());
        var message=dialog(c);policy(message,DialogAction.NONE,"mail message and links");
        check(body(message).contains("Offline dialog regression"),"offline mail survives restart with exact body");
        click(c,message.exitAction().orElseThrow());closed(c);
        menu=openForm(c,"mail read new");check(body(menu).contains("Brak wiadomości"),"reading a message clears its unread state");
        click(c,menu.exitAction().orElseThrow());closed(c);
    }
}
