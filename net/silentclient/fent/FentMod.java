package net.silentclient.fent;

import net.fabricmc.api.ClientModInitializer;

public final class FentMod implements ClientModInitializer {
   // Load marker. Toggles are in Modules > Settings > Unlocker.
   @Override
   public void onInitializeClient() {
      System.out.println("[Fent] loaded, toggles: Modules > Settings > Unlocker");
   }
}
