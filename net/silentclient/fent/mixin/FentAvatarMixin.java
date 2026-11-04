package net.silentclient.fent.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.L.h")
public abstract class FentAvatarMixin {
   @Inject(method = "m(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"))
   private static void fent$primeAvatar(String username, CallbackInfoReturnable<String> cir) {
   }
}
