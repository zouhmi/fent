package net.silentclient.fent.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * The old "Transparent Setting Rows" feature forced the server-menu rows to
 * render with a fully transparent background, which left the card area white
 * on setups without a themed backdrop behind it. Transparency here is gone
 * for good: rows always use SilentClient's default row background.
 */
@Mixin(targets = "net.silentclient.internal.L.h")
public abstract class FentRowsMixin {
   // intentionally empty - transparency is disabled everywhere.
}
