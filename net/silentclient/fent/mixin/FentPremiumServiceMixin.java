package net.silentclient.fent.mixin;

import java.util.concurrent.CompletableFuture;
import net.silentclient.common.api.services.premium.BandanaConfig;
import net.silentclient.common.api.services.premium.NametagConfig;
import net.silentclient.common.api.services.premium.PremiumTier;
import net.silentclient.fent.FentPremium;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.an.W")
public abstract class FentPremiumServiceMixin {
   @Shadow
   private volatile PremiumTier f;

   @Inject(method = "tier()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$topTier(CallbackInfoReturnable<CompletableFuture<PremiumTier>> cir) {
      if (FentUnlocks.premium()) {
         cir.setReturnValue(CompletableFuture.completedFuture(PremiumTier.ULTIMATE));
      }
   }

   @Inject(method = "expiresAtMs()J", at = @At("RETURN"), cancellable = true)
   private void fent$neverExpires(CallbackInfoReturnable<Long> cir) {
      if (FentUnlocks.premium()) {
         cir.setReturnValue(0L);
      }
   }

   @Inject(
      method = "setNametagConfig(Lnet/silentclient/common/api/services/premium/NametagConfig;)Ljava/util/concurrent/CompletableFuture;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$saveNametag(NametagConfig config, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      if (config == null || !FentUnlocks.premium() || (this.f != null && this.f.isPremium())) {
         return;
      }
      FentPremium.saveNametag(config);
      net.silentclient.internal.at.h holder = net.silentclient.internal.at.h.a();
      if (holder != null) {
         holder.onNametagChanged(config);
      }
      cir.setReturnValue(CompletableFuture.completedFuture(Boolean.TRUE));
   }

   @Inject(
      method = "setBandanaConfig(Lnet/silentclient/common/api/services/premium/BandanaConfig;)Ljava/util/concurrent/CompletableFuture;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$saveBandana(BandanaConfig config, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      if (config == null || !FentUnlocks.premium() || (this.f != null && this.f.isPremium())) {
         return;
      }
      FentPremium.saveBandana(config);
      net.silentclient.internal.at.h holder = net.silentclient.internal.at.h.a();
      if (holder != null) {
         holder.onBandanaChanged(config);
      }
      cir.setReturnValue(CompletableFuture.completedFuture(Boolean.TRUE));
   }

   @Inject(method = "nametagConfig()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$localNametag(CallbackInfoReturnable<CompletableFuture<NametagConfig>> cir) {
      if (!FentUnlocks.premium() || (this.f != null && this.f.isPremium())) {
         return;
      }
      NametagConfig config = FentPremium.nametag();
      if (config != null) {
         cir.setReturnValue(CompletableFuture.completedFuture(config));
      }
   }

   @Inject(method = "bandanaConfig()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$localBandana(CallbackInfoReturnable<CompletableFuture<BandanaConfig>> cir) {
      if (!FentUnlocks.premium() || (this.f != null && this.f.isPremium())) {
         return;
      }
      BandanaConfig config = FentPremium.bandana();
      if (config != null) {
         cir.setReturnValue(CompletableFuture.completedFuture(config));
      }
   }
}
