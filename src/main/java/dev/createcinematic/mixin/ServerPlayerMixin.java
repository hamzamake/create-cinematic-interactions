package dev.createcinematic.mixin;

import dev.createcinematic.MenuData;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The host opens a station's menu with plain {@code openMenu(provider)}, which on Forge 1.20.1 sends no extra data, but
 * Create's client menus (and ours) read some. For our providers only, open through {@code NetworkHooks.openScreen} with
 * that data, as Create's own use code does. Vanilla class only.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void createcinematic$openWithData(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (!(provider instanceof MenuData data)) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.containerMenu != self.inventoryMenu) self.closeContainer(); // vanilla's close, with its packet to the client
        NetworkHooks.openScreen(self, provider, data::write);
        // what vanilla returns: the new container's id, or empty when createMenu refused
        cir.setReturnValue(self.containerMenu != self.inventoryMenu ? OptionalInt.of(self.containerMenu.containerId) : OptionalInt.empty());
    }
}
