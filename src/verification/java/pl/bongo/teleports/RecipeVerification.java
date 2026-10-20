package pl.bongo.teleports;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;
import java.nio.file.*;
public final class RecipeVerification implements ModInitializer {
    public void onInitialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)-> {
            dispatcher.register(net.minecraft.commands.Commands.literal("verify-filtered").requires(source->source.getEntity()==null).executes(c->{
                var server=c.getSource().getServer();
                if(!Files.exists(server.getServerDirectory().resolve("claim-suite-fixture.txt"))) throw new IllegalStateException("Not a fixture server");
                var sender=server.getPlayerList().getPlayerByName("Bcfowner");var target=server.getPlayerList().getPlayerByName("Bcfmate");
                var message=net.minecraft.network.chat.PlayerChatMessage.unsigned(sender.getUUID(),"filtered @Bcfmate");
                var mask=new net.minecraft.network.chat.FilterMask(18);mask.setFiltered(0);
                target.connection.sendPlayerChatMessage(message.filter(mask),net.minecraft.network.chat.ChatType.bind(net.minecraft.network.chat.ChatType.CHAT,sender.createCommandSourceStack()));return 1;
            }));
            dispatcher.register(net.minecraft.commands.Commands.literal("verify-idle").requires(source->source.getEntity()==null)
                .then(net.minecraft.commands.Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(net.minecraft.commands.Commands.argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,301)).executes(c->{
                    ClaimSuiteFixture.idle(c.getSource().getServer(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"seconds"));return 1;
                }))));
            dispatcher.register(net.minecraft.commands.Commands.literal("verify-gp")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(net.minecraft.commands.Commands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                    ClaimSuiteFixture.report(c.getSource().getServer(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name"));return 1;
                })));
            dispatcher.register(net.minecraft.commands.Commands.literal("verify-feedback-craft")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(net.minecraft.commands.Commands.argument("kind",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    var p=c.getSource().getPlayerOrException();
                    String kind=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"kind");
                    var input=new net.minecraft.world.inventory.TransientCraftingContainer(p.inventoryMenu,3,3);
                    if(kind.equals("pad")) {
                        for(int slot:new int[]{1,3,5,7}) input.setItem(slot,new ItemStack(Items.STONE_BRICKS));
                        input.setItem(4,new ItemStack(Items.ENDER_EYE));
                    } else {
                        input.setItem(0,new ItemStack(Items.ENDER_PEARL));input.setItem(1,new ItemStack(Items.GOLD_INGOT));input.setItem(2,new ItemStack(Items.REDSTONE));
                    }
                    var crafting=CraftingInput.of(3,3,input.getItems());
                    var recipe=p.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,crafting,p.level()).orElseThrow();
                    var result=new net.minecraft.world.inventory.ResultContainer();
                    ItemStack output=recipe.value().assemble(crafting);result.setItem(0,output.copy());result.setRecipeUsed(recipe);
                    // Real ResultSlot take path (never the recipe preview/assemble path).
                    var slot=new net.minecraft.world.inventory.ResultSlot(p,input,result,0,0,0);
                    slot.remove(1);slot.onTake(p,output);return 1;
                })));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            ClaimSuiteFixture.start(s);
            var manager=s.getRecipeManager(); var level=s.overworld();
            var brick=new ItemStack(Items.STONE_BRICKS); var eye=new ItemStack(Items.ENDER_EYE);
            var input=CraftingInput.of(3,3,List.of(ItemStack.EMPTY,brick,ItemStack.EMPTY,brick,eye,brick,ItemStack.EMPTY,brick,ItemStack.EMPTY));
            var recipe=manager.getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
            var output=recipe.value().assemble(input);
            if(!Teleports.is(output,"pad") || output.getCount()!=1) throw new AssertionError("Pad recipe or components invalid");
            Item[] ingredients={Items.ENDER_PEARL,Items.GOLD_INGOT,Items.REDSTONE};
            int count=0;
            for(int a=0;a<3;a++) for(int b=0;b<3;b++) for(int c=0;c<3;c++) {
                if(a==b || a==c || b==c) continue;
                var mix=CraftingInput.of(3,1,List.of(new ItemStack(ingredients[a]),new ItemStack(ingredients[b]),new ItemStack(ingredients[c])));
                var r=manager.getRecipeFor(RecipeType.CRAFTING,mix,level).orElseThrow();
                var result=r.value().assemble(mix);
                var cfg=Storage.read(s.getServerDirectory().resolve("config/bongos-teleports.json"),Storage.Config.class,new Storage.Config());
                if(!Teleports.is(result,"synchronizer") || result.getMaxStackSize()!=1 || result.getMaxDamage()!=cfg.synchronizerDurability || result.getDamageValue()!=0 || !result.has(net.minecraft.core.component.DataComponents.LORE)
                    || !Boolean.TRUE.equals(result.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)))
                    throw new AssertionError("Synchronizer recipe or components invalid");
                count++;
            }
            try { Files.writeString(s.getServerDirectory().resolve("recipe-verification.txt"),"PASS pad recipe; PASS all "+count+" synchronizer permutations, custom data, glint and max stack."); }
            catch(Exception e) { throw new RuntimeException(e); }
        });
    }
}
