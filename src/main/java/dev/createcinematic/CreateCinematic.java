package dev.createcinematic;

import dev.createcinematic.client.CreateCinematicClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point (both physical sides). Client-only setup lives in {@code client.CreateCinematicClient}. */
@Mod(CreateCinematic.ID)
public final class CreateCinematic {
    public static final String ID = "createcinematic";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private static final String PROTOCOL = "1";
    /**
     * Required (no ABSENT accepted): Forge refuses a connection where only one side has this add-on. With only one side
     * registering the stations, every station would either do nothing or open and close at once. It also carries
     * {@link HandMenu.SetValue}, as 1.20.1's menu-button click holds only one byte.
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(id("presence"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public CreateCinematic() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(Stations::registerAll));
        CHANNEL.registerMessage(0, HandMenu.SetValue.class, HandMenu.SetValue::encode, HandMenu.SetValue::decode,
                HandMenu.SetValue::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        HandMenu.MENUS.register(modBus);
        NativeUse.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CreateCinematicClient.init(modBus));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ID, path);
    }
}
