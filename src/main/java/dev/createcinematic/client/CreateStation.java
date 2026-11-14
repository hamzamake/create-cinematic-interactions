package dev.createcinematic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.ItemPlacer;
import dev.dyslexic.cinematic.client.Picking;
import dev.dyslexic.cinematic.client.Scene;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

/**
 * A scene on one flat work plane of a Create block: a face (tilt 0), a top (tilt 90) or a slanted tray in between. The
 * camera settles on the plane, the hand follows the cursor between the spots, and every item move is a real menu click
 * ({@link #slotClick}); nothing is decided on the client. All motion runs on {@link Scene#clock()} / {@link Scene#frameDt}.
 */
abstract class CreateStation<M extends AbstractContainerMenu> extends AddonMenuStation<M> {
    /** A place the hand can reach: a menu slot ({@code slot >= 0}) or a named button ({@code button != null}). */
    protected record Spot(int slot, Vec3 point, double w, double h, boolean ghost, @Nullable String button, @Nullable String label) {}

    private static final double PULL_OUT = 0.3;
    private static final double PULL_LENGTH = 0.75;
    private static final double PRESS_LENGTH = 0.25;
    protected static final int FULL_BRIGHT = 0xF000F0;

    protected final String name;
    /** Horizontal unit vector from the block towards the player. */
    protected final Vec3 sideN;
    /** Middle of the work plane, and its axes: {@code across} = the player's right, {@code up} = top of the plane, {@code normal} = out of it. */
    protected final Vec3 origin, across, up, normal;
    /** The plane is nearer lying than standing: hands hover over it instead of reaching at it. */
    protected final boolean flat;
    protected final Quaternionf facing;
    /** Items are modelled facing away from {@code facing}: turned round so they read the right way. */
    protected final Quaternionf itemFacing;
    private final double eyeBack, eyeUp;

    protected List<Spot> spots = List.of();
    protected int hoverIndex = -1;
    /** Per spot, eased every frame: hover glow and how "filled" the slot looks. */
    private double[] glow = new double[0], fill = new double[0];
    /** 0..1: the panel growing in once the menu is there. */
    private double shown;

    private ItemStack pulled = ItemStack.EMPTY;
    private Vec3 pulledFrom = Vec3.ZERO;
    private double pulledAt = -10;
    private double kickAt = -10, kick;
    private String pressed = "";
    private double pressedAt = -10;
    private boolean tookHeld;

    /**
     * @param tilt    degrees the plane leans back from upright (0 = the block's face, 90 = lying flat)
     * @param lift    plane middle above the block centre
     * @param out     plane middle towards the player from the block centre
     * @param eyeBack camera distance towards the player from {@link #lookAt}, {@code eyeUp} above it
     */
    protected CreateStation(BlockPos pos, Direction side, Class<M> type, String name, double tilt, double lift, double out,
            double eyeBack, double eyeUp) {
        super(pos, side, type, 0);
        this.name = name;
        this.sideN = Vec3.atLowerCornerOf(side.getNormal());
        double a = Math.toRadians(tilt);
        this.up = new Vec3(0, Math.cos(a), 0).add(sideN.scale(-Math.sin(a)));
        this.normal = sideN.scale(Math.cos(a)).add(0, Math.sin(a), 0);
        this.across = up.cross(normal);
        this.origin = Vec3.atCenterOf(pos).add(0, lift, 0).add(sideN.scale(out));
        this.flat = tilt > 45;
        this.facing = new Quaternionf().setFromNormalized(new Matrix3f(
                (float) across.x, (float) across.y, (float) across.z,
                (float) up.x, (float) up.y, (float) up.z,
                (float) normal.x, (float) normal.y, (float) normal.z));
        this.itemFacing = new Quaternionf(facing).rotateY((float) Math.PI);
        this.eyeBack = eyeBack;
        this.eyeUp = eyeUp;
    }

    /** The spots the block has right now (rebuilt every frame; keep their number and order stable). */
    protected abstract List<Spot> layout(M menu);

    /** A point on the plane: {@code u} to the player's right, {@code v} up the plane, {@code out} off it. */
    protected Vec3 at(double u, double v, double out) {
        return origin.add(across.scale(u)).add(up.scale(v)).add(normal.scale(out));
    }

    protected Spot slotSpot(int slot, double u, double v, double size, @Nullable String label) {
        return new Spot(slot, at(u, v, 0.02), size, size, false, null, label);
    }

    protected Spot ghostSpot(int slot, double u, double v, double size) {
        return new Spot(slot, at(u, v, 0.02), size, size, true, null, null);
    }

    protected Spot buttonSpot(String button, double u, double v, double w, double h) {
        return new Spot(-1, at(u, v, 0.02), w, h, false, button, null);
    }

    @Nullable
    protected Spot hover() {
        return hoverIndex >= 0 && hoverIndex < spots.size() ? spots.get(hoverIndex) : null;
    }

    @Nullable
    protected static Slot slotOf(AbstractContainerMenu menu, Spot s) {
        return s.slot() >= 0 && s.slot() < menu.slots.size() ? menu.getSlot(s.slot()) : null;
    }

    /** The client's copy of the block entity, or null once the block is gone. */
    @Nullable
    protected <T> T blockEntity(Class<T> type) {
        var level = Minecraft.getInstance().level;
        BlockEntity be = level == null ? null : level.getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    // ---- camera and body -------------------------------------------------------------------------------------------

    protected Vec3 lookAt() {
        return origin;
    }

    @Override
    public List<Vec3> approach(LocalPlayer player) {
        double off = flat ? 0.86 : 1.0;
        Vec3 stand = new Vec3(pos.getX() + 0.5 + side.getStepX() * off, player.getY(), pos.getZ() + 0.5 + side.getStepZ() * off);
        return Math.hypot(stand.x - player.getX(), stand.z - player.getZ()) > 0.06 ? List.of(stand) : List.of();
    }

    @Override
    public CameraRig.Pose lean() {
        Vec3 eye = lookAt().add(sideN.scale(eyeBack)).add(0, eyeUp, 0);
        Vec3 look = lookAt().subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new CameraRig.Pose(eye, yaw, pitch, 0);
    }

    @Override
    public boolean crouches() {
        return true;
    }

    @Override
    public boolean worksAtAnAngle() {
        return true;
    }

    @Override
    public float bodyPitch() {
        return flat ? 50 : 32;
    }

    @Override
    public BlockPos lightPos() {
        return flat ? pos.above() : pos.relative(side);
    }

    @Override
    public SoundEvent touchSound() {
        return SoundEvents.METAL_STEP;
    }

    /** Unused: Create blocks keep their items (no input slots handed to the host). */
    @Override
    protected Vec3 restPoint(int slot) {
        return origin;
    }

    @Override
    public Vec3 rest(boolean right) {
        Vec3 beside = origin.add(across.scale((right ? 1 : -1) * (flat ? 0.4 : 0.28)));
        return flat ? beside.add(sideN.scale(0.4)).add(0, 0.02, 0) : beside.add(sideN.scale(0.45)).add(0, -0.3, 0);
    }

    @Override
    public Vec3 handTarget(Vec3 aim, Vec3 shoulder, boolean carrying, double tap) {
        Vec3 dir = new Vec3(aim.x - shoulder.x, 0, aim.z - shoulder.z).normalize();
        return flat ? aim.add(0, 0.062 + (carrying ? 0.075 : 0.045) - 0.06 * tap, 0).subtract(dir.scale(0.075))
                : aim.add(0, -0.02 - 0.03 * tap, 0).subtract(dir.scale(0.06));
    }

    @Override
    public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        Vec3 dir = new Vec3(hand.x - shoulder.x, 0, hand.z - shoulder.z).normalize();
        return flat ? hand.add(dir.scale(0.075)).add(0, -0.05, 0) : hand.add(dir.scale(0.07)).add(0, -0.043, 0);
    }

    // ---- cursor ----------------------------------------------------------------------------------------------------

    @Override
    public void updateCursor(boolean off) {
        clearCursor();
        double t = Scene.clock() - pulledAt;
        if (t >= 0 && t < PULL_OUT) {
            aim = pulledFrom.add(normal.scale(0.04 + 0.22 * Anim.outCubic(t / PULL_OUT))); // the hand draws the item out
            return;
        }
        double[] mouse = off || menu() == null ? null : Picking.mouseGui();
        if (mouse == null) {
            return;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            double[] at = Picking.toGui(s.point());
            double[] ex = Picking.toGui(s.point().add(across.scale(s.w())));
            double[] ey = Picking.toGui(s.point().add(up.scale(s.h())));
            if (at == null || ex == null || ey == null) {
                continue;
            }
            // the spot's rectangle as seen on screen: write the mouse offset as p*(half width) + q*(half height)
            double ax = ex[0] - at[0], ay = ex[1] - at[1], bx = ey[0] - at[0], by = ey[1] - at[1];
            double mx = mouse[0] - at[0], my = mouse[1] - at[1];
            double det = ax * by - ay * bx;
            if (Math.abs(det) < 1e-6) {
                continue; // seen edge-on
            }
            double p = (mx * by - my * bx) / det, q = (ax * my - ay * mx) / det;
            boolean inside = Math.abs(p) <= Math.max(1, 6 / Math.hypot(ax, ay)) && Math.abs(q) <= Math.max(1, 6 / Math.hypot(bx, by));
            double d = Math.hypot(mx, my);
            if (inside && d < best) {
                best = d;
                hoverIndex = i;
            }
        }
        Spot s = hover();
        if (s != null) {
            aim = s.point().add(normal.scale(0.04));
        }
    }

    @Override
    public void clearCursor() {
        super.clearCursor();
        hoverIndex = -1;
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    @Override
    public void beforeFrame() {
        M menu = menu();
        spots = menu == null ? List.of() : layout(menu);
        if (glow.length != spots.size()) {
            glow = new double[spots.size()];
            fill = new double[spots.size()];
        }
        double dt = Scene.frameDt;
        shown = Anim.approach(shown, menu == null ? 0 : 1, 7, dt);
        for (int i = 0; i < spots.size(); i++) {
            Slot slot = slotOf(menu, spots.get(i));
            glow[i] = Anim.approach(glow[i], i == hoverIndex ? 1 : 0, 16, dt);
            fill[i] = Anim.approach(fill[i], slot != null && slot.hasItem() ? 1 : 0, 12, dt);
        }
        if (menu != null && !tookHeld) {
            tookHeld = true;
            if (takesHeld()) {
                takeHeld(menu);
            }
        }
    }

    /** Whether an item the player walked up holding starts in the working hand when the block has a slot for it. */
    protected boolean takesHeld() {
        return false;
    }

    private void takeHeld(M menu) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (held.isEmpty() || !menu.getCarried().isEmpty()) {
            return;
        }
        boolean wanted = spots.stream().anyMatch(s -> {
            Slot slot = slotOf(menu, s);
            return slot != null && !s.ghost() && !slot.hasItem() && slot.mayPlace(held);
        });
        if (!wanted) {
            return;
        }
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == player.getInventory().selected) {
                slotClick(slot.index, 0, false);
                return;
            }
        }
    }

    @Override
    protected void act(int button, boolean shift) {
        M menu = menu();
        Spot s = hover();
        if (menu == null || s == null || pulling()) {
            return;
        }
        if (s.button() != null) {
            if (press(menu, s.button(), button)) {
                pressed = s.button();
                pressedAt = Scene.clock();
            }
            return;
        }
        Slot slot = slotOf(menu, s);
        if (slot == null) {
            return;
        }
        if (s.ghost()) {
            // Create's GhostItemMenu copies whatever is held (or clears the slot) itself
            slotClick(slot.index, 0, false);
            kick(0.12);
            sound(menu.getCarried().isEmpty() ? SoundEvents.ITEM_FRAME_REMOVE_ITEM : SoundEvents.ITEM_FRAME_ADD_ITEM, 0.4f, 1.4f);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            if (!slot.mayPlace(menu.getCarried())) {
                deny();
                return;
            }
            slotClick(slot.index, button, false);
            kick(0.2);
            sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1.0f);
            return;
        }
        if (!slot.hasItem()) {
            emptyClick(menu, s);
            return;
        }
        if (!slot.mayPickup(Minecraft.getInstance().player)) {
            deny();
            return;
        }
        if (shift) {
            // straight to the inventory: it slides out of the block and drops past the camera, if any of it fit
            ItemStack before = slot.getItem().copy();
            slotClick(slot.index, 0, true); // handleInventoryMouseClick applies the click locally first
            if (ItemStack.matches(before, slot.getItem())) {
                deny();
                return;
            }
            pulled = before;
            pulledFrom = s.point();
            pulledAt = Scene.clock();
        } else {
            slotClick(slot.index, button, false);
            kick(0.12);
        }
        sound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.5f, 1.1f);
    }

    /** An empty slot was clicked with an empty hand. */
    protected void emptyClick(M menu, Spot s) {
        deny();
    }

    /** A button spot was clicked; true when it gave way (it dips). */
    protected boolean press(M menu, String button, int mouseButton) {
        return false;
    }

    /** 0..1..0 while a just-pressed button dips and comes back. */
    protected double dip(String button) {
        double t = Scene.clock() - pressedAt;
        return button.equals(pressed) && t >= 0 && t < PRESS_LENGTH ? Anim.hump(t / PRESS_LENGTH) : 0;
    }

    protected boolean pulling() {
        return Scene.clock() - pulledAt < PULL_LENGTH;
    }

    @Override
    public boolean busy() {
        return super.busy() || pulling();
    }

    @Override
    public void onEnd() {
        super.onEnd();
        spots = List.of();
        hoverIndex = -1;
        aim = null;
        pulled = ItemStack.EMPTY;
        pulledAt = pressedAt = kickAt = -10;
        shown = 0;
    }

    protected void kick(double amount) {
        kick = amount;
        kickAt = Scene.clock();
    }

    @Override
    public double cameraKick() {
        double t = Scene.clock() - kickAt;
        return t >= 0 && t < 0.6 ? kick * Math.exp(-13 * t) : 0;
    }

    /** The left mouse button as the window sees it. MouseHandler#isLeftPressed only updates with no screen open, and a scene is one. */
    protected static boolean leftHeld() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    protected void sound(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(sound, SoundSource.BLOCKS, volume, pitch, RandomSource.create(), pos));
    }

    // ---- text ------------------------------------------------------------------------------------------------------

    protected static MutableComponent tr(String key, Object... args) {
        return Component.translatable("createcinematic." + key, args);
    }

    @Override
    public Component hint() {
        return tr("hint." + name);
    }

    @Override
    public List<Component> tooltip(ItemStack carried) {
        M menu = menu();
        Spot s = hover();
        if (menu == null || s == null) {
            return null;
        }
        if (s.button() != null) {
            return buttonTooltip(menu, s.button());
        }
        Slot slot = slotOf(menu, s);
        if (slot == null) {
            return null;
        }
        ItemStack in = slot.getItem();
        List<Component> lines = new ArrayList<>();
        if (!in.isEmpty()) {
            lines.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), in));
        }
        if (s.label() != null) {
            lines.add(tr("slot." + s.label()).withStyle(ChatFormatting.AQUA));
        }
        if (s.ghost()) {
            lines.add(tr(carried.isEmpty() ? (in.isEmpty() ? "ghost_empty" : "ghost_clear") : "ghost_set").withStyle(ChatFormatting.GRAY));
        } else if (!carried.isEmpty()) {
            lines.add(slot.mayPlace(carried) ? tr("put_in").withStyle(ChatFormatting.GRAY) : tr("wont_fit").withStyle(ChatFormatting.RED));
        } else if (in.isEmpty()) {
            lines.addAll(emptyLines(menu, s));
        } else {
            lines.add(tr("take").withStyle(ChatFormatting.GRAY));
        }
        lines.addAll(notes(menu, s));
        return lines;
    }

    /** What an empty slot says to an empty hand. */
    protected List<Component> emptyLines(M menu, Spot s) {
        return List.of(tr("empty").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Extra lines under every slot tooltip of this block. */
    protected List<Component> notes(M menu, Spot s) {
        return List.of();
    }

    protected List<Component> buttonTooltip(M menu, String button) {
        return List.of(tr("button." + button));
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    /** What a slot shows (its item, by default). */
    protected ItemStack display(M menu, Spot s, Slot slot) {
        return slot.getItem();
    }

    /** The number written on a slot's item, or null. */
    @Nullable
    protected String count(M menu, Spot s, Slot slot) {
        int n = slot.getItem().getCount();
        return n > 1 ? String.valueOf(n) : null;
    }

    /** Buttons, bars and text the slots themselves don't show; {@code grow} 0..1 while the panel appears. */
    protected void drawExtras(M menu, PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light, double grow) {
    }

    @Override
    public void renderScene(PoseStack ps, MultiBufferSource buffers, Camera camera, int light) {
        M menu = menu();
        if (outro() || menu == null) {
            return;
        }
        Vec3 cam = camera.getPosition();
        double grow = Anim.smoother(shown);
        for (int i = 0; i < spots.size() && i < fill.length; i++) {
            Spot s = spots.get(i);
            Slot slot = slotOf(menu, s);
            if (slot == null) {
                continue;
            }
            // a framed socket for every slot, so it's clear where things go
            Vec3 c = s.point().subtract(normal.scale(0.015));
            rect(ps, buffers, cam, c, (s.w() + 0.012) * grow, (s.h() + 0.012) * grow, s.ghost() ? 0xFF6E86B8 : 0xFF8A93A6);
            rect(ps, buffers, cam, c.add(normal.scale(0.002)), s.w() * grow, s.h() * grow, 0xFF15171C);
            ItemStack show = display(menu, s, slot);
            if (!show.isEmpty()) {
                boolean block = ItemPlacer.isBlock(show);
                float size = (float) (s.w() * (block ? 1.05 : 1.7) * grow * (0.55 + 0.45 * Anim.smoother(fill[i])));
                ItemPlacer.drawOriented(ps, buffers, cam, show, s.point().add(normal.scale(block ? size / 2 : 0.012)), itemFacing, size, light);
                String n = count(menu, s, slot);
                if (n != null && grow > 0.9) {
                    Vec3 corner = s.point().add(across.scale(s.w() * 0.6)).add(up.scale(-s.h() * 0.6)).add(normal.scale(block ? size + 0.012 : 0.035));
                    text(ps, buffers, cam, Component.literal(n), corner, 0.0042f, 0xFFFFFFFF);
                }
            }
            if (s.label() != null && grow > 0.9) {
                text(ps, buffers, cam, tr("slot." + s.label()), c.add(up.scale(s.h() + 0.04)).add(normal.scale(0.004)), 0.0034f, 0xFFE8F6FF);
            }
        }
        drawExtras(menu, ps, buffers, cam, light, grow);

        double t = Scene.clock() - pulledAt;
        if (t >= 0 && t < PULL_LENGTH && !pulled.isEmpty()) {
            Vec3 out = pulledFrom.add(normal.scale(0.24));
            Vec3 at;
            float size = 0.2f;
            if (t < PULL_OUT) {
                at = pulledFrom.add(normal.scale(0.24 * Anim.outCubic(t / PULL_OUT)));
            } else {
                // then it drops down past the camera into the inventory
                double k = Anim.inCubic((t - PULL_OUT) / (PULL_LENGTH - PULL_OUT));
                Vec3 pocket = cam.add(out.subtract(cam).normalize().scale(0.35)).add(0, -0.45, 0);
                at = Anim.lerp(out, pocket, k).add(0, 0.08 * Anim.hump(k), 0);
                size *= (float) (1 - 0.5 * k);
            }
            ItemPlacer.drawOriented(ps, buffers, cam, pulled, at, itemFacing, size, light);
        }
    }

    @Override
    public void renderWorld(PoseStack ps, MultiBufferSource buffers, Vec3 cam, int light) {
        M menu = menu();
        if (menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        for (int i = 0; i < spots.size() && i < glow.length; i++) {
            if (glow[i] < 0.02) {
                continue;
            }
            Spot s = spots.get(i);
            Slot slot = slotOf(menu, s);
            boolean refused = slot != null && !s.ghost() && !carried.isEmpty() && !slot.mayPlace(carried);
            outline(ps, buffers, cam, s, fade(refused ? 0xFFFF4D4D : 0xFF8CF2FF, glow[i]));
        }
    }

    /** {@code argb} with its alpha scaled by {@code k}. */
    protected static int fade(int argb, double k) {
        return ((int) ((argb >>> 24) * Anim.clamp01(k)) << 24) | (argb & 0xFFFFFF);
    }

    /** A flat coloured quad (both windings, so it shows whichever way it's culled). */
    protected static void quad(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        for (Vec3 v : new Vec3[] {a, b, c, d, d, c, b, a}) {
            vc.addVertex(m, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z)).setColor(argb);
        }
    }

    /** A rectangle lying in the plane, centred on {@code c}, half-sizes {@code w} x {@code h}. */
    protected void rect(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 c, double w, double h, int argb) {
        Vec3 x = across.scale(w), y = up.scale(h);
        quad(ps, buffers, cam, c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), argb);
    }

    /** A rectangle on the plane at (u, v), {@code out} off it. */
    protected void panel(PoseStack ps, MultiBufferSource buffers, Vec3 cam, double u, double v, double w, double h, double out, int argb) {
        rect(ps, buffers, cam, at(u, v, out), w, h, argb);
    }

    /** A bar filled from the left to {@code filled} (0..1). */
    protected void bar(PoseStack ps, MultiBufferSource buffers, Vec3 cam, double u, double v, double w, double h, double filled, int argb) {
        panel(ps, buffers, cam, u, v, w + 0.006, h + 0.006, 0.006, 0xFF15171C);
        double k = Anim.clamp01(filled);
        if (k > 0.001) {
            panel(ps, buffers, cam, u - w * (1 - k), v, w * k, h, 0.009, argb);
        }
    }

    private void outline(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Spot s, int argb) {
        Vec3 c = s.point().add(normal.scale(0.004));
        double w = s.w() + 0.014, h = s.h() + 0.014, line = 0.007;
        rect(ps, buffers, cam, c.add(up.scale(h)), w + line, line, argb);
        rect(ps, buffers, cam, c.add(up.scale(-h)), w + line, line, argb);
        rect(ps, buffers, cam, c.add(across.scale(w)), line, h - line, argb);
        rect(ps, buffers, cam, c.add(across.scale(-w)), line, h - line, argb);
    }

    /** A line of text lying in the plane, centred on {@code at}. */
    protected void text(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Component text, Vec3 at, float scale, int argb) {
        if ((argb >>> 24) < 8) {
            return; // the font reads near-zero alpha as opaque
        }
        Font font = Minecraft.getInstance().font;
        Vec3 p = at.subtract(cam);
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(facing);
        ps.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f, argb, false, ps.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        ps.popPose();
    }

    /** A button plate with its caption; it dips while pressed and greys out when {@code enabled} is false. */
    protected void plate(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Spot s, @Nullable Component caption, int argb, boolean enabled, double grow) {
        double out = 0.004 - 0.012 * dip(s.button());
        Vec3 c = s.point().add(normal.scale(out - 0.012));
        rect(ps, buffers, cam, c.subtract(normal.scale(0.002)), (s.w() + 0.008) * grow, (s.h() + 0.008) * grow, 0xFF15171C);
        rect(ps, buffers, cam, c, s.w() * grow, s.h() * grow, enabled ? argb : 0xFF4A4F5A);
        if (caption != null && grow > 0.9) {
            text(ps, buffers, cam, caption, c.add(normal.scale(0.004)), 0.0034f, enabled ? 0xFFFFFFFF : 0xFF9AA0AA);
        }
    }

    /** A status line under the host's hotbar strip. */
    protected static void statusLine(net.minecraft.client.gui.GuiGraphics gui, Font font, Component text, float alpha) {
        int a = (int) (Anim.clamp01(alpha) * 255);
        if (a >= 8) {
            gui.drawCenteredString(font, text, gui.guiWidth() / 2, 42, (a << 24) | 0xBFEFFF);
        }
    }
}
