package net.silentclient.fent.mixin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.common.api.services.cosmetics.EquippedCosmetics;
import net.silentclient.fent.FentShare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquippedCosmetics.class)
public abstract class FentSharedCosmeticsMixin {
   @Shadow
   public abstract String getUuid();

   @Inject(method = "getItems()Ljava/util/Map;", at = @At("RETURN"), cancellable = true)
   private void fent$mergeSharedCosmetics(CallbackInfoReturnable<Map<CosmeticType, String>> cir) {
      Map<CosmeticType, String> shared = FentShare.remoteItems(this.getUuid());
      if (shared == null || shared.isEmpty()) {
         return;
      }
      Map<CosmeticType, String> merged = new EnumMap<CosmeticType, String>(CosmeticType.class);
      Map<CosmeticType, String> current = cir.getReturnValue();
      if (current != null) {
         merged.putAll(current);
      }
      merged.putAll(shared);
      cir.setReturnValue(Collections.unmodifiableMap(merged));
   }
}