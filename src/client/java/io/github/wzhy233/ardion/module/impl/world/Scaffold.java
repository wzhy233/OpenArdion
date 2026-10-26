package io.github.wzhy233.ardion.module.impl.world;

import io.github.wzhy233.ardion.event.impl.client.ClickEvent;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.impl.render.UpdateHeldItemEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.player.AutoMLG;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.player.InventoryUtil;
import io.github.wzhy233.ardion.util.rotation.RayTraceUtil;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.util.rotation.RotationUtil;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TallBlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class Scaffold extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int OFFHAND_SLOT = -2;
    private static final float ROTATION_DEGREES = 180.0F;

    private static final Set<Block> BLACKLISTED_BLOCKS = Set.of(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
            Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.STONE_BUTTON, Blocks.LEVER, Blocks.TALL_GRASS,
            Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.RAIL, Blocks.CORNFLOWER, Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM, Blocks.VINE, Blocks.SUNFLOWER, Blocks.LADDER, Blocks.FURNACE,
            Blocks.SAND, Blocks.CACTUS, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTING_TABLE,
            Blocks.COBWEB, Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH, Blocks.FLOWER_POT
    );

    private final NumberValue rotationTicks = this.registerValue(new NumberValue("rotation-ticks", this,
            3.0D, 1.0D, 5.0D, true));

    private float correctYaw;
    private float correctPitch;
    private float rotYaw;
    private float rotPitch;
    private int startY;
    private boolean requireClutch;
    private int oldSlot = -1;
    private int blockSlot = -1;
    private int blockCount = -1;
    private BlockData pos;

    public Scaffold() {
        super("scaffold", ModuleCategory.WORLD);
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!InventoryUtil.isItemValid(stack) || stack.getItem() instanceof TallBlockItem) {
            return false;
        }

        String name = stack.getName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }

        Block block = blockItem.getBlock();
        return !(block instanceof FlowerBlock)
                && !(block instanceof BushBlock)
                && !(block instanceof FungusBlock)
                && !(block instanceof CropBlock)
                && !(block instanceof SlabBlock)
                && !BLACKLISTED_BLOCKS.contains(block);
    }

    public int getRemainingBlocks() {
        if (mc.player == null) {
            return 0;
        }

        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isValidStack(stack)) {
                count += stack.getCount();
            }
        }
        ItemStack offHandStack = mc.player.getOffHandStack();
        if (isValidStack(offHandStack)) {
            count += offHandStack.getCount();
        }
        return count;
    }

    public int getDisplayBlockColor() {
        ItemStack stack = getDisplayBlockStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return 0xBFC5CF;
        }

        try {
            MapColor mapColor = blockItem.getBlock().getDefaultState().getMapColor(
                    mc.world,
                    mc.player == null ? BlockPos.ORIGIN : mc.player.getBlockPos()
            );
            return mapColor == null ? 0xBFC5CF : mapColor.color & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            return 0xBFC5CF;
        }
    }

    private ItemStack getDisplayBlockStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack selectedStack = this.getStack(this.blockSlot);
        if (isValidStack(selectedStack)) {
            return selectedStack;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isValidStack(stack)) {
                return stack;
            }
        }
        ItemStack offHandStack = mc.player.getOffHandStack();
        return isValidStack(offHandStack) ? offHandStack : ItemStack.EMPTY;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) {
            return;
        }
        this.oldSlot = mc.player.getInventory().getSelectedSlot();
        this.rotYaw = mc.player.getYaw();
        this.rotPitch = mc.player.getPitch();
        this.correctYaw = this.rotYaw;
        this.correctPitch = this.rotPitch;
        this.pos = null;
        this.blockSlot = -1;
        this.blockCount = -1;
        this.startY = MathHelper.floor(mc.player.getY());
        this.requireClutch = !mc.player.isOnGround();
    }

    @Override
    protected void onDisable() {
        if (!this.shouldYieldToAutoMLG()) {
            RotationManager.get().setActive(false);
        }
        if (mc.player == null) {
            return;
        }

        boolean holdingJump = isHoldingJump();
        boolean holdingSneak = isHoldingSneak();
        mc.options.jumpKey.setPressed(holdingJump);
        mc.options.sneakKey.setPressed(holdingSneak);
        mc.options.useKey.setPressed(false);
        if (this.oldSlot >= 0 && this.oldSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(this.oldSlot);
        }
        this.oldSlot = -1;
        this.blockSlot = -1;
        this.blockCount = -1;
    }

    @Listener
    private void onWorldChange(WorldLoadEvent event) {
        this.setEnabled(false);
    }

    @Listener(Priority.HIGH)
    private void onEarlyTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE
                || mc.player == null
                || mc.world == null
                || mc.player.isCreative()
                || mc.player.isSpectator()
                || mc.currentScreen != null) {
            return;
        }
        if (this.shouldYieldToAutoMLG()) {
            this.pos = null;
            this.blockSlot = -1;
            this.blockCount = -1;
            return;
        }

        this.selectBlockSlot();
        if (this.isHotbarSlot(this.blockSlot) && mc.player.getInventory().getSelectedSlot() != this.blockSlot) {
            mc.player.getInventory().setSelectedSlot(this.blockSlot);
        }

        boolean holdingJump = isHoldingJump();
        float rotationSpeed = this.getRotationSpeed(holdingJump);

        this.pos = this.getBlockData();
        if (this.pos != null) {
            float[] correctRotation = this.getCorrectRotation(this.pos, this.rotYaw, this.rotPitch);
            this.correctYaw = correctRotation[0];
            this.correctPitch = correctRotation[1];
            this.rotYaw = RotationUtil.rotateToYaw(rotationSpeed, this.rotYaw, this.correctYaw);
            this.rotPitch = this.correctPitch;
        }

        if (mc.player.isOnGround()) {
            this.requireClutch = false;
        }
        if (mc.player.hurtTime > 0 && mc.player.getVelocity().y > 1.0E-4D) {
            this.requireClutch = true;
        }
        if (holdingJump || this.requireClutch || mc.player.isOnGround()) {
            this.startY = MathHelper.floor(mc.player.getY());
        }

        mc.options.jumpKey.setPressed(isMovementInput() || holdingJump);
        if (mc.player.isOnGround() && isMovementInput()) {
            this.rotYaw = RotationUtil.rotateToYaw(180.0F, this.rotYaw, mc.player.getYaw());
        }
    }

    @Listener
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null
                || this.shouldYieldToAutoMLG() || !this.checkBlocks()) {
            return;
        }

        RotationManager.get().setRotations(new Rotation(this.rotYaw, this.rotPitch),
                this.getRotationSpeed(isHoldingJump()), true, false);
        if (mc.currentScreen == null) {
            this.placeBlock();
        }
    }

    @Listener
    private void onClick(ClickEvent event) {
        if (this.shouldYieldToAutoMLG()) {
            return;
        }
        event.setCancelled(true);
    }

    @Listener
    private void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (event.getHand() != Hand.MAIN_HAND
                || mc.player == null
                || this.shouldYieldToAutoMLG()
                || this.oldSlot < 0
                || this.oldSlot > 8
                || mc.player.getInventory().getSelectedSlot() == this.oldSlot) {
            return;
        }

        event.setItemStack(mc.player.getInventory().getStack(this.oldSlot));
    }

    private void selectBlockSlot() {
        if (mc.player == null) {
            return;
        }

        ItemStack held = this.getStack(this.blockSlot);
        int count = isValidStack(held) ? held.getCount() : 0;
        this.blockCount = Math.min(this.blockCount, count);
        if (count == 0 && this.blockCount <= 0) {
            this.blockCount = -1;
        }
        if (this.blockCount > 0) {
            return;
        }

        int start = this.isHotbarSlot(this.blockSlot)
                ? this.blockSlot
                : mc.player.getInventory().getSelectedSlot();
        if (this.blockCount == 0 && this.isHotbarSlot(this.blockSlot)) {
            start--;
        }
        boolean currentStale = this.blockCount == 0 && count > 0;

        for (int i = start; i > start - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            if (currentStale && hotbarSlot == this.blockSlot) {
                continue;
            }
            ItemStack candidate = mc.player.getInventory().getStack(hotbarSlot);
            if (!isValidStack(candidate)) {
                continue;
            }
            this.blockSlot = hotbarSlot;
            this.blockCount = candidate.getCount();
            return;
        }

        ItemStack offHandStack = mc.player.getOffHandStack();
        if (isValidStack(offHandStack) && !(currentStale && this.blockSlot == OFFHAND_SLOT)) {
            this.blockSlot = OFFHAND_SLOT;
            this.blockCount = offHandStack.getCount();
        }
    }

    private float[] getCorrectRotation(BlockData data, float lastYaw, float lastPitch) {
        Vec3d hitVec = new Vec3d(
                data.pos().getX() + 0.5D + data.facing().getOffsetX() * 0.5D,
                data.pos().getY() + 0.5D + data.facing().getOffsetY() * 0.5D,
                data.pos().getZ() + 0.5D + data.facing().getOffsetZ() * 0.5D
        );

        Vec3d eyes = mc.player.getEyePos();
        double dx = hitVec.x - eyes.x;
        double dy = hitVec.y - eyes.y;
        double dz = hitVec.z - eyes.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        yaw = lastYaw + MathHelper.wrapDegrees(yaw - lastYaw);
        pitch = lastPitch + MathHelper.wrapDegrees(pitch - lastPitch);
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    private Direction getBestFacing(BlockPos blockPos, BlockPos targetPos) {
        double best = 0.0D;
        Direction bestFacing = null;

        for (Direction facing : Direction.values()) {
            if (facing == Direction.DOWN) {
                continue;
            }

            BlockPos pos = blockPos.offset(facing);
            if (pos.getY() > targetPos.getY()) {
                continue;
            }

            double dx = pos.getX() + 0.5D - (targetPos.getX() + 0.5D);
            double dy = pos.getY() + 0.5D - (targetPos.getY() + 0.5D);
            double dz = pos.getZ() + 0.5D - (targetPos.getZ() + 0.5D);
            double distance = dx * dx + dy * dy + dz * dz;
            if (bestFacing == null || distance < best || (distance == best && facing == Direction.UP)) {
                best = distance;
                bestFacing = facing;
            }
        }
        return bestFacing;
    }

    private BlockData getBlockData() {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos targetPos = new BlockPos(
                MathHelper.floor(mc.player.getX()),
                this.startY - 1,
                MathHelper.floor(mc.player.getZ())
        );

        if (!isReplaceable(targetPos)) {
            return null;
        }

        List<BlockPos> positions = new ArrayList<>();
        double reach = Math.max(1.0D, mc.player.getBlockInteractionRange());
        double reachSquared = reach * reach;

        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);

                    if (isReplaceable(pos)
                            || isContainer(pos)
                            || mc.player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > reachSquared
                            || (!mc.player.isOnGround() && pos.getY() >= this.startY)) {
                        continue;
                    }

                    for (Direction facing : Direction.values()) {
                        if (facing != Direction.DOWN && isReplaceable(pos.offset(facing))) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            return null;
        }

        positions.sort(Comparator.comparingDouble(pos -> {
            double dx = pos.getX() + 0.5D - (targetPos.getX() + 0.5D);
            double dy = pos.getY() + 0.5D - (targetPos.getY() + 0.5D);
            double dz = pos.getZ() + 0.5D - (targetPos.getZ() + 0.5D);
            return dx * dx + dy * dy + dz * dz;
        }));

        BlockPos blockPos = positions.get(0);
        Direction facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private void placeBlock() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || this.pos == null || !this.checkBlocks()) {
            return;
        }

        BlockPos neighbor = this.pos.pos();
        Direction side = this.pos.facing();
        BlockPos placePos = neighbor.offset(side);
        if (!isReplaceable(placePos)) {
            return;
        }

        Rotation rotations = RotationManager.get().getRotations();
        HitResult hitResult = RayTraceUtil.rayCast(1.0F, rotations == null ? new Rotation(this.rotYaw, this.rotPitch) : rotations);
        if (!(hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK
                || !blockHitResult.getBlockPos().equals(neighbor)
                || blockHitResult.getSide() != side) {
            return;
        }

        Vec3d hitVec = new Vec3d(
                neighbor.getX() + 0.5D + side.getOffsetX() * 0.5D,
                neighbor.getY() + 0.5D + side.getOffsetY() * 0.5D,
                neighbor.getZ() + 0.5D + side.getOffsetZ() * 0.5D
        );
        ActionResult result = mc.interactionManager.interactBlock(
                mc.player,
                this.handForSlot(this.blockSlot),
                new BlockHitResult(hitVec, side, neighbor, false)
        );
        if (result.isAccepted()) {
            if (this.blockCount > 0) {
                this.blockCount--;
            }
            mc.player.swingHand(this.handForSlot(this.blockSlot));
        }
    }

    private boolean checkBlocks() {
        if (mc.player == null || this.blockCount <= 0 || !isValidStack(this.getStack(this.blockSlot))) {
            return false;
        }
        return this.blockSlot == OFFHAND_SLOT || this.isHotbarSlot(this.blockSlot)
                && mc.player.getInventory().getSelectedSlot() == this.blockSlot;
    }

    private ItemStack getStack(int slot) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        if (slot == OFFHAND_SLOT) {
            return mc.player.getOffHandStack();
        }
        return this.isHotbarSlot(slot) ? mc.player.getInventory().getStack(slot) : ItemStack.EMPTY;
    }

    private Hand handForSlot(int slot) {
        return slot == OFFHAND_SLOT ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private float getRotationSpeed(boolean holdingJump) {
        int ticks = holdingJump ? 1 : this.rotationTicks.getValue().intValue();
        return ROTATION_DEGREES / Math.max(1, ticks);
    }

    private boolean shouldYieldToAutoMLG() {
        AutoMLG autoMLG = ModuleManager.getModule(AutoMLG.class);
        return autoMLG != null && autoMLG.isEnabled() && autoMLG.isRotationPriorityActive();
    }

    private boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    private static boolean isReplaceable(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).isReplaceable();
    }

    private static boolean isContainer(BlockPos pos) {
        if (mc.world == null) {
            return false;
        }
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);
        return blockEntity instanceof Inventory;
    }

    private static boolean isMovementInput() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    private static boolean isHoldingJump() {
        return isKeyDown(mc.options.jumpKey);
    }

    private static boolean isHoldingSneak() {
        return isKeyDown(mc.options.sneakKey);
    }

    private static boolean isKeyDown(KeyBinding keyBinding) {
        return InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
    }

    private record BlockData(BlockPos pos, Direction facing) {
    }
}
