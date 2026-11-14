package dev.createcinematic.mixin;

import dev.createcinematic.CreateMenuProvider;
import dev.createcinematic.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create's menu blocks have no vanilla menu provider (they open their menus from their use code), but the host opens
 * a station's menu through {@code state.getMenuProvider}. This hands it one for our stations, on the server only.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Shadow
    protected abstract BlockState asState();

    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void createcinematic$menuProvider(Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
        if (!(level instanceof ServerLevel)) return;
        Stations.Entry entry = Stations.forState(asState());
        if (entry == null) return;
        MenuProvider provider = CreateMenuProvider.of(entry, level, pos);
        if (provider != null) cir.setReturnValue(provider);
    }
}
