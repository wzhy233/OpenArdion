package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class KillEffect extends Module {
    private static final int KILL_CONFIRM_TICKS = 100;
    private static final int BLOOD_PARTICLE_AMOUNT = 250;
    private static final BlockState[] BLOOD_BLOCK_STATES = {
            Blocks.REDSTONE_BLOCK.getDefaultState(),
            Blocks.RED_CONCRETE.getDefaultState(),
            Blocks.NETHER_WART_BLOCK.getDefaultState()
    };
    private final BooleanValue lighting = this.registerValue(new BooleanValue("lighting", this, false));
    private final BooleanValue blood = this.registerValue(new BooleanValue("blood", this, true));
    private final Map<Integer, TrackedPlayer> attackedPlayers = new HashMap<>();
    private int nextLightningEntityId = -1;

    public KillEffect() {
        super("kill-effect", ModuleCategory.RENDER);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && player instanceof ClientPlayerEntity clientPlayer) {
                this.handleAttack(clientPlayer, entity);
            }
            return ActionResult.PASS;
        });
    }

    private void handleAttack(ClientPlayerEntity player, Entity target) {
        if (!this.isEnabled() || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == player) {
            return;
        }

        this.attackedPlayers.put(targetPlayer.getId(), new TrackedPlayer(targetPlayer));
    }

    @Listener
    public void onPacketReceive(PacketReceiveEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || this.attackedPlayers.isEmpty()) {
            return;
        }

        if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (!isDeathStatus(packet.getStatus())) {
                return;
            }

            Entity entity = packet.getEntity(client.world);
            if (entity instanceof PlayerEntity target) {
                this.triggerKill(target.getId(), target);
            }
            return;
        }

        if (event.getPacket() instanceof EntitiesDestroyS2CPacket packet) {
            for (int entityId : packet.getEntityIds()) {
                TrackedPlayer tracked = this.attackedPlayers.get(entityId);
                if (tracked == null) {
                    continue;
                }

                Entity entity = client.world.getEntityById(entityId);
                if (entity instanceof PlayerEntity target) {
                    tracked.update(target);
                }
                this.triggerKill(entityId, entity);
            }
        }
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.world == null) {
            this.attackedPlayers.clear();
            return;
        }

        Iterator<Map.Entry<Integer, TrackedPlayer>> iterator = this.attackedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedPlayer tracked = iterator.next().getValue();
            PlayerEntity target = tracked.player;
            if (target == null || target == client.player) {
                iterator.remove();
                continue;
            }

            tracked.update(target);
            if (target.getHealth() <= 0.0F || !target.isAlive() || target.isRemoved()) {
                this.spawnKillParticles(tracked.box());
                iterator.remove();
                continue;
            }

            tracked.ticksLeft--;
            if (tracked.ticksLeft <= 0 || target.isRemoved()) {
                iterator.remove();
            }
        }
    }

    @Override
    protected void onDisable() {
        this.attackedPlayers.clear();
    }

    private void triggerKill(int entityId, Entity entity) {
        TrackedPlayer tracked = this.attackedPlayers.remove(entityId);
        if (tracked == null) {
            return;
        }

        if (entity instanceof PlayerEntity target) {
            tracked.update(target);
        }
        this.spawnKillParticles(tracked.box());
    }

    private void spawnKillParticles(Box box) {
        if (box == null) {
            return;
        }

        if (this.blood.getValue()) {
            this.spawnBloodBlocks(box);
        }

        if (this.lighting.getValue()) {
            this.spawnLightningStrike(box);
        }
    }

    private void spawnLightningStrike(Box box) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }

        double x = (box.minX + box.maxX) * 0.5D;
        double y = box.minY;
        double z = (box.minZ + box.maxZ) * 0.5D;

        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        lightning.setId(this.nextClientEntityId(world));
        lightning.setCosmetic(true);
        lightning.refreshPositionAfterTeleport(x, y, z);
        world.addEntity(lightning);
        world.setLightningTicksLeft(2);
    }

    private int nextClientEntityId(ClientWorld world) {
        while (world.getEntityById(this.nextLightningEntityId) != null) {
            this.nextLightningEntityId--;
            if (this.nextLightningEntityId >= 0) {
                this.nextLightningEntityId = -1;
            }
        }

        return this.nextLightningEntityId--;
    }

    private void spawnBloodBlocks(Box box) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < BLOOD_PARTICLE_AMOUNT; i++) {
            BlockState state = BLOOD_BLOCK_STATES[random.nextInt(BLOOD_BLOCK_STATES.length)];
            double x = randomBetween(random, box.minX, box.maxX);
            double y = randomBetween(random, box.minY + box.getLengthY() * 0.25D, box.maxY);
            double z = randomBetween(random, box.minZ, box.maxZ);
            double velocityX = random.nextDouble(-0.08D, 0.08D);
            double velocityY = random.nextDouble(0.04D, 0.16D);
            double velocityZ = random.nextDouble(-0.08D, 0.08D);
            world.addParticleClient(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    private static boolean isDeathStatus(byte status) {
        return status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES
                || status == EntityStatuses.ADD_DEATH_PARTICLES;
    }

    private static double randomBetween(ThreadLocalRandom random, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return 0.0D;
        }
        if (max <= min) {
            return min;
        }
        return random.nextDouble(min, max);
    }

    private static final class TrackedPlayer {
        private final PlayerEntity player;
        private Box lastBox;
        private int ticksLeft = KILL_CONFIRM_TICKS;

        private TrackedPlayer(PlayerEntity player) {
            this.player = player;
            this.update(player);
        }

        private void update(PlayerEntity player) {
            this.lastBox = player.getBoundingBox();
        }

        private Box box() {
            return this.lastBox;
        }
    }
}
