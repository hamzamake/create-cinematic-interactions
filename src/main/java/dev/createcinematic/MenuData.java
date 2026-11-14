package dev.createcinematic;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A menu provider that carries extra open data for its client menu. Forge 1.20.1's plain {@code openMenu} sends none
 * (NeoForge's {@code writeClientSideData} doesn't exist here), so {@code mixin/ServerPlayerMixin} opens these through
 * {@code NetworkHooks.openScreen} instead, as Create's own use code does.
 */
public interface MenuData {
    void write(FriendlyByteBuf buf);
}
