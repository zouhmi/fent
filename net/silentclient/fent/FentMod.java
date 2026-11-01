package net.silentclient.fent;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;

public final class FentMod implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
         dispatcher.register(ClientCommandManager.literal("fentshare")
            .then(ClientCommandManager.literal("export").executes(context -> {
               String code = FentShare.shareCode();
               context.getSource().sendFeedback(class_2561.method_43470(
                  code.isEmpty() ? "No cosmetic selections to share." : "Share code: " + code));
               return code.isEmpty() ? 0 : 1;
            }))
            .then(ClientCommandManager.literal("import")
               .then(ClientCommandManager.argument("player-uuid", StringArgumentType.word())
                  .then(ClientCommandManager.argument("code", StringArgumentType.greedyString()).executes(context -> {
                     String playerId = StringArgumentType.getString(context, "player-uuid");
                     String code = StringArgumentType.getString(context, "code");
                     boolean imported = FentShare.applyShareCode(code, playerId);
                     context.getSource().sendFeedback(class_2561.method_43470(imported
                        ? "Imported cosmetic appearance for " + playerId
                        : "Could not import that appearance code."));
                     return imported ? 1 : 0;
                  })))))
      );
      String version = FabricLoader.getInstance()
         .getModContainer("zouhmifent")
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("?");
      System.out.println("[Fent] " + version + " loaded, toggles: Modules > Settings > Fent");
   }
}
