package dev.createcinematic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point (both physical sides). Client-only setup lives in {@code client.CreateCinematicClient}. */
@Mod(CreateCinematic.ID)
public final class CreateCinematic {
    public static final String ID = "createcinematic";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public CreateCinematic(IEventBus modBus) {
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(Stations::registerAll));
        // A required (non-optional) channel: NeoForge refuses a connection where only one side has this add-on. With only
        // one side registering the stations, every station would either do nothing or open and close at once.
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToClient(Presence.TYPE, Presence.CODEC, (payload, context) -> {}));
        HandMenu.MENUS.register(modBus);
        NativeUse.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    /** Never sent; it only exists so both sides must have the add-on. */
    private record Presence() implements CustomPacketPayload {
        static final Type<Presence> TYPE = new Type<>(id("presence"));
        static final StreamCodec<ByteBuf, Presence> CODEC = StreamCodec.unit(new Presence());

        @Override
        public Type<Presence> type() {
            return TYPE;
        }
    }
}
