package net.silentclient.fent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FentMod implements ClientModInitializer {
   // Load marker, and the one line that names the build in latest.log - a stale jar in the mods
   // folder is otherwise invisible. The Unlocker toggle states are not printed here: at init the
   // client's settings store has not been read yet, so they would all read as their defaults; the
   // Unlocker page shows the real states. Toggles are in Modules > Settings > Unlocker.
   @Override
   public void onInitializeClient() {
      String version = FabricLoader.getInstance()
         .getModContainer("zouhmifent")
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("?");
      System.out.println("[Fent] " + version + " loaded, toggles: Modules > Settings > Unlocker");
   }
}
