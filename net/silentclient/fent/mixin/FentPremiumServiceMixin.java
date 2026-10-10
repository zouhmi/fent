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

// The live premium service (net.silentclient.internal.am.A$i).
// setNametagConfig and setBandanaConfig return false as soon as the tier from /users/me
// is not premium, before any request goes out, so the nametag and bandana editors
// always failed with "Couldn't change ...". Real premium accounts keep the server path,
// everyone else gets a local save plus a local config read.
@Mixin(targets = "net.silentclient.internal.am.A$i")
public abstract class FentPremiumServiceMixin {
   @Shadow
   private volatile PremiumTier i;

   @Inject(
      method = "setNametagConfig(Lnet/silentclient/common/api/services/premium/NametagConfig;)Ljava/util/concurrent/CompletableFuture;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$saveNametag(NametagConfig config, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      if (config == null || this.i.isPremium() || !FentUnlocks.premium()) {
         return;
      }
      FentPremium.saveNametag(config);
      net.silentclient.internal.ar.h holder = net.silentclient.internal.ar.h.a();
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
      if (config == null || this.i.isPremium() || !FentUnlocks.premium()) {
         return;
      }
      FentPremium.saveBandana(config);
      net.silentclient.internal.ar.h holder = net.silentclient.internal.ar.h.a();
      if (holder != null) {
         holder.onBandanaChanged(config);
      }
      cir.setReturnValue(CompletableFuture.completedFuture(Boolean.TRUE));
   }

   @Inject(method = "nametagConfig()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$localNametag(CallbackInfoReturnable<CompletableFuture<NametagConfig>> cir) {
      if (this.i.isPremium() || !FentUnlocks.premium()) {
         return;
      }
      NametagConfig config = FentPremium.nametag();
      if (config != null) {
         cir.setReturnValue(CompletableFuture.completedFuture(config));
      }
   }

   @Inject(method = "bandanaConfig()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$localBandana(CallbackInfoReturnable<CompletableFuture<BandanaConfig>> cir) {
      if (this.i.isPremium() || !FentUnlocks.premium()) {
         return;
      }
      BandanaConfig config = FentPremium.bandana();
      if (config != null) {
         cir.setReturnValue(CompletableFuture.completedFuture(config));
      }
   }
}
