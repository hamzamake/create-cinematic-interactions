package dev.createcinematic.client;

import dev.createcinematic.HandMenu;
import dev.createcinematic.CreateCinematic;
import dev.createcinematic.Stations;
import dev.dyslexic.cinematic.api.client.AddonScenes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client entry point: registers one scene factory per {@code Stations.ALL} entry. */
@Mod(value = CreateCinematic.ID, dist = Dist.CLIENT)
public final class CreateCinematicClient {
    public CreateCinematicClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(CreateCinematicClient::registerScenes));
        // The host takes a station's menu from its screen opening (and cancels the screen), so the hand menu needs one.
        // It only shows when the host refused the scene (too high, blocked path) and opened "the block's window" instead:
        // then it does Create's plain click (a crank turn, a valve turn, a lever notch, a ding) and closes.
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(HandMenu.TYPE.get(),
                (HandMenu menu, Inventory inventory, Component title) -> new AbstractContainerScreen<>(menu, inventory, title) {
                    @Override
                    protected void containerTick() {
                        int button = minecraft.level == null ? -1 : menu.plainClick(minecraft.level);
                        if (button >= 0 && minecraft.gameMode != null && minecraft.player != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
                            menu.echo(minecraft.level, minecraft.player, button);
                        }
                        onClose();
                    }

                    @Override
                    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {}

                    @Override
                    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {}
                }));
    }

    private static void registerScenes() {
        for (Stations.Entry entry : Stations.ALL) {
            boolean frogport = entry.id().getPath().equals("package_frogport");
            AddonScenes.Factory factory = switch (entry.kind()) {
                case TOOLBOX -> (pos, state, player, side) -> new ToolboxStation(pos, side);
                case SCHEMATICANNON -> (pos, state, player, side) -> new CannonStation(pos, side);
                case SCHEMATIC_TABLE -> (pos, state, player, side) -> new SchematicTableStation(pos, side);
                case PACKAGE_PORT -> (pos, state, player, side) -> new PortStation(pos, side, frogport);
                case REDSTONE_REQUESTER -> (pos, state, player, side) -> new RequesterStation(pos, side);
                case HAND_CRANK -> (pos, state, player, side) -> new HandCrankStation(pos, side, state);
                case VALVE_HANDLE -> (pos, state, player, side) -> new ValveHandleStation(pos, side, state);
                case ANALOG_LEVER -> (pos, state, player, side) -> new AnalogLeverStation(pos, side, state);
                case DESK_BELL -> (pos, state, player, side) -> new DeskBellStation(pos, side, state);
                case SPEED_CONTROLLER -> (pos, state, player, side) -> new SpeedControllerStation(pos, side, state);
            };
            AddonScenes.register(entry.id(), factory);
        }
    }
}
