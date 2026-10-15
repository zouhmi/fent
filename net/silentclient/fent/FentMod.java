package net.silentclient.fent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FentMod implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      String version = FabricLoader.getInstance()
         .getModContainer("zouhmifent")
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("?");
      System.out.println("[Fent] " + version + " loaded, toggles: Modules > Settings > Fent");
   }
}
