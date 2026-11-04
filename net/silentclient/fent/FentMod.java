package net.silentclient.fent;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;

public final class FentMod implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      try {
         LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("fentshare");

         root.then(ClientCommandManager.literal("export").executes(context -> {
            String code = FentShare.shareCode();
            context.getSource().sendFeedback(class_2561.method_43470(
               code.isEmpty() ? "No cosmetic selections to share." : "Share code: " + code));
            return code.isEmpty() ? 0 : 1;
         }));

         root.then(ClientCommandManager.literal("import")
            .then(ClientCommandManager.argument("player-uuid", StringArgumentType.word())
               .then(ClientCommandManager.argument("code", StringArgumentType.greedyString()).executes(context -> {
                  String playerId = StringArgumentType.getString(context, "player-uuid");
                  String code = StringArgumentType.getString(context, "code");
                  boolean imported = FentShare.applyShareCode(code, playerId);
                  context.getSource().sendFeedback(class_2561.method_43470(imported
                     ? "Imported cosmetic appearance for " + playerId
                     : "Could not import that appearance code."));
                  return imported ? 1 : 0;
               }))));

         // single "api" node: info via executes, url change via nested argument
         // (avoiding duplicate-literal overwrite that could drop subcommands)
         LiteralArgumentBuilder<FabricClientCommandSource> api = ClientCommandManager.literal("api");
         api.executes(context -> {
            String url = FentShare.apiUrl();
            if (url == null) {
               context.getSource().sendFeedback(class_2561.method_43470(
                  "Sync API is off. Usage: /fentshare api <url> (or 'off')."));
            } else {
               context.getSource().sendFeedback(class_2561.method_43470(
                  "Sync API: " + url + (FentShare.apiEnabled() ? " (on)" : " (sharing toggle off)")
                     + (FentShare.defaultUrl() ? " (default)" : "")
                     + " | name: " + FentShare.syncName()
                     + " | key: " + (FentShare.apiKey().isEmpty() ? "none" : "set")));
            }
            return 1;
         });
         api.then(ClientCommandManager.argument("url", StringArgumentType.greedyString()).executes(context -> {
            String url = StringArgumentType.getString(context, "url").trim();
            if (url.equalsIgnoreCase("off") || url.equalsIgnoreCase("clear")) {
               FentShare.setApiUrl("");
               context.getSource().sendFeedback(class_2561.method_43470("Sync API is off."));
            } else {
               FentShare.setApiUrl(url);
               if (FentShare.apiUrl() == null) {
                  context.getSource().sendFeedback(class_2561.method_43470(
                     "Invalid URL (must start with http:// or https://)."));
               } else {
                  context.getSource().sendFeedback(class_2561.method_43470(
                     "Sync API set to " + url));
               }
            }
            FentShare.resync();
            return 1;
         }));
         root.then(api);

         root.then(ClientCommandManager.literal("key").executes(context -> {
            String manual = "";
            try {
               manual = FentUnlocks.string("fent.api.key");
            } catch (Throwable ignored) {
            }
            String status = FentShare.apiKey().isEmpty() ? "No sync key set."
               : (manual == null || manual.trim().isEmpty() ? "Sync key is set (automatic)."
                  : "Sync key is set (manual).");
            context.getSource().sendFeedback(class_2561.method_43470(status));
            return 1;
         }));
         root.then(ClientCommandManager.literal("key")
            .then(ClientCommandManager.argument("key", StringArgumentType.word()).executes(context -> {
               String key = StringArgumentType.getString(context, "key");
               if (key.equalsIgnoreCase("off") || key.equalsIgnoreCase("clear")) {
                  key = "";
               }
               FentShare.setApiKey(key);
               context.getSource().sendFeedback(class_2561.method_43470(
                  key.isEmpty() ? "API key cleared." : "API key set."));
               FentShare.resync();
               return 1;
            })));

         root.then(ClientCommandManager.literal("name").executes(context -> {
            context.getSource().sendFeedback(class_2561.method_43470(
               "Sync name: " + FentShare.syncName()));
            return 1;
         }));
         root.then(ClientCommandManager.literal("name")
            .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(context -> {
               String name = StringArgumentType.getString(context, "name");
               FentShare.setSyncName(name);
               context.getSource().sendFeedback(class_2561.method_43470(
                  "Sync name set to " + FentShare.syncName()));
               FentShare.resync();
               return 1;
            })));

         root.then(ClientCommandManager.literal("resync").executes(context -> {
            FentShare.resync();
            context.getSource().sendFeedback(class_2561.method_43470("Resynced shared cosmetics."));
            return 1;
         }));

         ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(root);
         });

         String version = FabricLoader.getInstance()
            .getModContainer("zouhmifent")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
         FentShare.warm();
         System.out.println("[Fent] " + version + " loaded, toggles: Modules > Settings > Fent");
      } catch (Throwable throwable) {
         System.err.println("[Fent] command registration failed: " + throwable);
      }
   }

   // no-arg ctor must stay public for Fabric's reflection-based entrypoint loader
}
