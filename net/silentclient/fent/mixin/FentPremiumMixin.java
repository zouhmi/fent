package net.silentclient.fent.mixin;

import java.util.concurrent.CompletableFuture;
import net.silentclient.common.api.services.premium.PremiumService;
import net.silentclient.common.api.services.premium.PremiumTier;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Premium state lives in net.silentclient.internal.ar.h.
// b() pulls tier, expiry and config from the premium service; onPremiumChanged() calls b() again.
// Feeding it ULTIMATE covers Premium and Premium+ (and the theme gate that reads o()).
@Mixin(targets = "net.silentclient.internal.ar.h")
public abstract class FentPremiumMixin {
   @Redirect(
      method = "b()V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/services/premium/PremiumService;tier()Ljava/util/concurrent/CompletableFuture;"
      )
   )
   private static CompletableFuture<PremiumTier> fent$topTier(PremiumService service) {
      return FentUnlocks.premium() ? CompletableFuture.completedFuture(PremiumTier.ULTIMATE) : service.tier();
   }

   @Redirect(
      method = "b()V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/services/premium/PremiumService;expiresAtMs()J"
      )
   )
   private static long fent$neverExpires(PremiumService service) {
      return FentUnlocks.premium() ? 0L : service.expiresAtMs();
   }

   @Redirect(
      method = "b()V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/services/premium/PremiumService;expiredAtMs()J"
      )
   )
   private static long fent$neverExpired(PremiumService service) {
      return FentUnlocks.premium() ? 0L : service.expiredAtMs();
   }
}
