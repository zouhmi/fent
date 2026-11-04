package net.silentclient.fent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FentMod implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      String version = "?";
      try {
         version = FabricLoader.getInstance()
            .getModContainer("zouhmifent")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
      } catch (Throwable ignored) {
      }
      System.out.println("[Fent] " + version + " loaded, toggles: Modules > Settings > Fent");
   }

   // no-arg ctor must stay public for Fabric's reflection-based entrypoint loader
}
