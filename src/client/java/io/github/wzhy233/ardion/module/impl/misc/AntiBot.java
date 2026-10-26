package io.github.wzhy233.ardion.module.impl.misc;

import com.mojang.authlib.GameProfile;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.Predicate;

public final class AntiBot extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean attackCallbackRegistered;

    private final ModeValue mode = this.registerValue(new ModeValue("mode", this, "custom",
            "custom", "matrix", "intave-heavy", "horizon"));
    private final BooleanValue literalNpc = this.registerValue(new BooleanValue("literal-npc", this, false));
    private final BooleanValue notInTabList = this.registerValue(new BooleanValue("not-in-tab-list", this, false));

    private final BooleanValue invalidGround = this.registerValue(new BooleanValue("invalid-ground", this, true,
            this::isCustomMode));
    private final NumberValue invalidGroundVl = this.registerValue(new NumberValue("invalid-ground-vl", this,
            10.0D, 1.0D, 50.0D, true, () -> this.isCustomMode() && this.invalidGround.getValue()));

    private final BooleanValue conditionDuplicate = this.registerValue(new BooleanValue("duplicate", this, false,
            this::isCustomMode));
    private final BooleanValue conditionNoGameMode = this.registerValue(new BooleanValue("no-game-mode", this,
            true, this::isCustomMode));
    private final BooleanValue conditionIllegalPitch = this.registerValue(new BooleanValue("illegal-pitch", this,
            true, this::isCustomMode));
    private final BooleanValue conditionFakeEntityId = this.registerValue(new BooleanValue("fake-entity-id", this,
            true, this::isCustomMode));
    private final BooleanValue conditionNeedHit = this.registerValue(new BooleanValue("need-hit", this, false,
            this::isCustomMode));
    private final BooleanValue conditionIllegalHealth = this.registerValue(new BooleanValue("illegal-health", this,
            false, this::isCustomMode));
    private final BooleanValue conditionSwung = this.registerValue(new BooleanValue("swung", this, false,
            this::isCustomMode));
    private final BooleanValue conditionCritted = this.registerValue(new BooleanValue("critted", this, false,
            this::isCustomMode));
    private final BooleanValue conditionAttributes = this.registerValue(new BooleanValue("attributes", this, false,
            this::isCustomMode));
    private final BooleanValue conditionIllegalScale = this.registerValue(new BooleanValue("illegal-scale", this,
            false, this::isCustomMode));

    private final BooleanValue alwaysInRadius = this.registerValue(new BooleanValue("always-in-radius", this,
            false, this::isCustomMode));
    private final NumberValue alwaysInRadiusRange = this.registerValue(new NumberValue("always-in-radius-range",
            this, 20.0D, 5.0D, 30.0D, false, () -> this.isCustomMode() && this.alwaysInRadius.getValue()));

    private final BooleanValue age = this.registerValue(new BooleanValue("age", this, false, this::isCustomMode));
    private final NumberValue ageMinimum = this.registerValue(new NumberValue("age-minimum", this, 20.0D,
            0.0D, 120.0D, true, () -> this.isCustomMode() && this.age.getValue()));

    private final BooleanValue armor = this.registerValue(new BooleanValue("armor", this, false, this::isCustomMode));
    private final BooleanValue helmetNothing = armorValue("helmet-nothing", true);
    private final BooleanValue helmetLeather = armorValue("helmet-leather", false);
    private final BooleanValue helmetChain = armorValue("helmet-chain", false);
    private final BooleanValue helmetIron = armorValue("helmet-iron", false);
    private final BooleanValue helmetGold = armorValue("helmet-gold", false);
    private final BooleanValue helmetDiamond = armorValue("helmet-diamond", false);
    private final BooleanValue helmetNetherite = armorValue("helmet-netherite", false);
    private final BooleanValue helmetTurtleScute = armorValue("helmet-turtle-scute", false);
    private final BooleanValue helmetPumpkin = armorValue("helmet-pumpkin", false);
    private final BooleanValue helmetSkull = armorValue("helmet-skull", false);
    private final BooleanValue chestNothing = armorValue("chestplate-nothing", true);
    private final BooleanValue chestLeather = armorValue("chestplate-leather", false);
    private final BooleanValue chestChain = armorValue("chestplate-chain", false);
    private final BooleanValue chestIron = armorValue("chestplate-iron", false);
    private final BooleanValue chestGold = armorValue("chestplate-gold", false);
    private final BooleanValue chestDiamond = armorValue("chestplate-diamond", false);
    private final BooleanValue chestNetherite = armorValue("chestplate-netherite", false);
    private final BooleanValue chestElytra = armorValue("chestplate-elytra", false);
    private final BooleanValue legsNothing = armorValue("leggings-nothing", true);
    private final BooleanValue legsLeather = armorValue("leggings-leather", false);
    private final BooleanValue legsChain = armorValue("leggings-chain", false);
    private final BooleanValue legsIron = armorValue("leggings-iron", false);
    private final BooleanValue legsGold = armorValue("leggings-gold", false);
    private final BooleanValue legsDiamond = armorValue("leggings-diamond", false);
    private final BooleanValue legsNetherite = armorValue("leggings-netherite", false);
    private final BooleanValue bootsNothing = armorValue("boots-nothing", true);
    private final BooleanValue bootsLeather = armorValue("boots-leather", false);
    private final BooleanValue bootsChain = armorValue("boots-chain", false);
    private final BooleanValue bootsIron = armorValue("boots-iron", false);
    private final BooleanValue bootsGold = armorValue("boots-gold", false);
    private final BooleanValue bootsDiamond = armorValue("boots-diamond", false);
    private final BooleanValue bootsNetherite = armorValue("boots-netherite", false);

    private final BooleanValue name = this.registerValue(new BooleanValue("name", this, true, this::isCustomMode));
    private final NumberValue nameMinLength = this.registerValue(new NumberValue("name-min-length", this,
            3.0D, 1.0D, 32.0D, true, () -> this.isCustomMode() && this.name.getValue()));
    private final NumberValue nameMaxLength = this.registerValue(new NumberValue("name-max-length", this,
            16.0D, 1.0D, 32.0D, true, () -> this.isCustomMode() && this.name.getValue()));
    private final BooleanValue nameVanillaChars = this.registerValue(new BooleanValue("name-vanilla-chars", this,
            true, () -> this.isCustomMode() && this.name.getValue()));
    private final BooleanValue nameCyrillicChars = this.registerValue(new BooleanValue("name-cyrillic-chars", this,
            false, () -> this.isCustomMode() && this.name.getValue()));
    private final BooleanValue nameCjkChars = this.registerValue(new BooleanValue("name-cjk-chars", this,
            false, () -> this.isCustomMode() && this.name.getValue()));

    private final Map<Integer, Integer> flyingSet = new HashMap<>();
    private final Set<Integer> hitSet = new HashSet<>();
    private final Set<Integer> notAlwaysInRadiusSet = new HashSet<>();
    private final Set<Integer> swungSet = new HashSet<>();
    private final Set<Integer> crittedSet = new HashSet<>();
    private final Set<Integer> attributesSet = new HashSet<>();
    private final Set<Integer> armorSet = new HashSet<>();

    private final Set<UUID> horizonBotList = new HashSet<>();

    private final Map<UUID, SuspectInfo> intaveSuspectList = new HashMap<>();
    private final Set<UUID> intaveBotList = new HashSet<>();

    private final Set<UUID> matrixSuspectList = new HashSet<>();
    private final Set<UUID> matrixBotList = new HashSet<>();
    private final Map<UUID, MatrixArmorSnapshot> matrixArmorSnapshots = new HashMap<>();

    public AntiBot() {
        super("anti-bot", ModuleCategory.MISC);
        registerAttackCallback();
    }

    private BooleanValue armorValue(String name, boolean defaultValue) {
        return this.registerValue(new BooleanValue(name, this, defaultValue,
                () -> this.isCustomMode() && this.armor.getValue()));
    }

    private boolean isCustomMode() {
        return this.mode.is("custom");
    }

    public boolean isBot(Entity entity) {
        if (!this.isEnabled() || entity == null || entity == mc.player || !(entity instanceof PlayerEntity player)) {
            return false;
        }

        if (this.literalNpc.getValue() && !this.onlinePlayerIds().contains(player.getUuid())) {
            return true;
        }

        if (this.notInTabList.getValue() && this.isMissingFromTabList(player)) {
            return true;
        }

        if (this.isEspBot(player)) {
            return true;
        }

        return switch (this.mode.getValue().toLowerCase(Locale.ROOT)) {
            case "matrix" -> this.matrixBotList.contains(player.getUuid());
            case "intave-heavy" -> this.intaveBotList.contains(player.getUuid());
            case "horizon" -> this.horizonBotList.contains(player.getUuid());
            default -> this.isCustomBot(player);
        };
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    @Listener(Priority.HIGHEST)
    private void onWorldLoad(WorldLoadEvent event) {
        this.reset();
    }

    @Listener(Priority.HIGH)
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST || mc.world == null || mc.player == null) {
            return;
        }

        if (this.isCustomMode()) {
            this.tickCustom();
        }

        if (this.mode.is("matrix")) {
            this.tickMatrix();
        }
    }

    @Listener(Priority.HIGH)
    private void onPacket(PacketReceiveEvent event) {
        Packet<?> packet = event.getPacket();
        if (mc.world == null) {
            return;
        }

        this.handleCustomPacket(packet);

        if (packet instanceof PlayerListS2CPacket playerListPacket) {
            this.handlePlayerListPacket(playerListPacket);
        } else if (packet instanceof PlayerRemoveS2CPacket removePacket) {
            this.handlePlayerRemove(removePacket);
        }
    }

    private void tickCustom() {
        double rangeSquared = square(this.alwaysInRadiusRange.getValue());
        for (PlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) {
                continue;
            }

            if (mc.player.squaredDistanceTo(entity) > rangeSquared) {
                this.notAlwaysInRadiusSet.add(entity.getId());
            }

            if (this.armor.getValue() && !this.isArmorValid(entity)) {
                this.armorSet.add(entity.getId());
            }
        }

        this.armorSet.removeIf(entityId -> {
            Entity entity = mc.world.getEntityById(entityId);
            return !(entity instanceof PlayerEntity player) || this.isArmorValid(player);
        });
    }

    private void tickMatrix() {
        if (this.matrixSuspectList.isEmpty()) {
            return;
        }

        List<UUID> suspects = new ArrayList<>(this.matrixSuspectList);
        for (UUID uuid : suspects) {
            PlayerEntity entity = this.getWorldPlayer(uuid);
            if (entity == null) {
                this.matrixSuspectList.remove(uuid);
                this.matrixArmorSnapshots.remove(uuid);
                continue;
            }

            MatrixArmorSnapshot snapshot = this.matrixArmorSnapshots.get(uuid);
            if (!this.isFullyArmored(entity) && snapshot == null) {
                this.matrixArmorSnapshots.put(uuid, new MatrixArmorSnapshot(this.copyArmor(entity), 1));
                continue;
            }

            if (snapshot != null && snapshot.waitTicks > 0) {
                this.matrixArmorSnapshots.put(uuid, new MatrixArmorSnapshot(snapshot.armor, snapshot.waitTicks - 1));
                continue;
            }

            if ((this.isFullyArmored(entity) || this.updatesArmor(entity, snapshot))
                    && entity.getGameProfile().properties().isEmpty()) {
                this.matrixBotList.add(uuid);
            }

            this.matrixSuspectList.remove(uuid);
            this.matrixArmorSnapshots.remove(uuid);
        }
    }

    private void handleCustomPacket(Packet<?> packet) {
        if (packet instanceof EntityS2CPacket entityPacket) {
            if (!entityPacket.isPositionChanged() || !this.invalidGround.getValue()) {
                return;
            }

            Entity entity = entityPacket.getEntity(mc.world);
            if (entity == null) {
                return;
            }

            int id = entity.getId();
            int currentValue = this.flyingSet.getOrDefault(id, 0);
            if (entity.isOnGround() && entity.lastY != entity.getY()) {
                this.flyingSet.put(id, currentValue + 1);
            } else if (!entity.isOnGround() && currentValue > 0) {
                int newVl = currentValue / 2;
                if (newVl <= 0) {
                    this.flyingSet.remove(id);
                } else {
                    this.flyingSet.put(id, newVl);
                }
            }
            return;
        }

        if (packet instanceof EntityAttributesS2CPacket attributesPacket) {
            this.attributesSet.add(attributesPacket.getEntityId());
            return;
        }

        if (packet instanceof EntityAnimationS2CPacket animationPacket) {
            switch (animationPacket.getAnimationId()) {
                case EntityAnimationS2CPacket.SWING_MAIN_HAND, EntityAnimationS2CPacket.SWING_OFF_HAND ->
                        this.swungSet.add(animationPacket.getEntityId());
                case EntityAnimationS2CPacket.CRIT, EntityAnimationS2CPacket.ENCHANTED_HIT ->
                        this.crittedSet.add(animationPacket.getEntityId());
                default -> {
                }
            }
            return;
        }

        if (packet instanceof EntitiesDestroyS2CPacket destroyPacket) {
            for (int entityId : destroyPacket.getEntityIds()) {
                this.attributesSet.remove(entityId);
                this.flyingSet.remove(entityId);
                this.hitSet.remove(entityId);
                this.notAlwaysInRadiusSet.remove(entityId);
                this.armorSet.remove(entityId);
                this.swungSet.remove(entityId);
                this.crittedSet.remove(entityId);
            }
        }
    }

    private void handlePlayerListPacket(PlayerListS2CPacket packet) {
        if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
            this.handleHorizonAddPlayers(packet);
            this.handleMatrixAddPlayers(packet);
            this.handleIntaveAddPlayers(packet.getEntries());
        }

        if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
            this.handleIntaveUpdateLatency(packet.getEntries());
        }
    }

    private void handlePlayerRemove(PlayerRemoveS2CPacket packet) {
        for (UUID uuid : packet.profileIds()) {
            this.horizonBotList.remove(uuid);
            this.intaveSuspectList.remove(uuid);
            this.intaveBotList.remove(uuid);
            this.matrixSuspectList.remove(uuid);
            this.matrixBotList.remove(uuid);
            this.matrixArmorSnapshots.remove(uuid);
        }
    }

    private void handleHorizonAddPlayers(PlayerListS2CPacket packet) {
        for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
            if (entry.gameMode() == null) {
                this.horizonBotList.add(entry.profileId());
            }
        }
    }

    private void handleIntaveAddPlayers(List<PlayerListS2CPacket.Entry> entries) {
        for (PlayerListS2CPacket.Entry entry : entries) {
            GameProfile profile = entry.profile();
            if (profile == null || entry.latency() < 2 || this.isGameProfileUnique(profile)) {
                continue;
            }

            this.intaveSuspectList.put(entry.profileId(), new SuspectInfo(entry.latency(), System.currentTimeMillis()));
        }
    }

    private void handleIntaveUpdateLatency(List<PlayerListS2CPacket.Entry> entries) {
        for (PlayerListS2CPacket.Entry entry : entries) {
            SuspectInfo suspectInfo = this.intaveSuspectList.get(entry.profileId());
            if (suspectInfo == null) {
                continue;
            }

            int deltaPing = suspectInfo.latency - entry.latency();
            long deltaMs = System.currentTimeMillis() - suspectInfo.timestamp;
            if (deltaPing == suspectInfo.latency && deltaMs <= 15L) {
                this.intaveBotList.add(entry.profileId());
            }

            this.intaveSuspectList.remove(entry.profileId());
        }
    }

    private void handleMatrixAddPlayers(PlayerListS2CPacket packet) {
        for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
            GameProfile profile = entry.profile();
            if (profile == null
                    || entry.latency() < 2
                    || !profile.properties().isEmpty()
                    || this.isGameProfileUnique(profile)) {
                continue;
            }

            if (this.isDuplicate(profile)) {
                this.matrixBotList.add(entry.profileId());
                continue;
            }

            this.matrixSuspectList.add(entry.profileId());
        }
    }

    private boolean isCustomBot(PlayerEntity entity) {
        int entityId = entity.getId();
        if (this.invalidGround.getValue()
                && this.flyingSet.getOrDefault(entityId, 0) >= this.invalidGroundVl.getValue().intValue()) {
            return true;
        }
        if (this.alwaysInRadius.getValue() && !this.notAlwaysInRadiusSet.contains(entityId)) {
            return true;
        }
        if (this.age.getValue() && entity.age < this.ageMinimum.getValue().intValue()) {
            return true;
        }
        if (this.armor.getValue() && this.armorSet.contains(entityId)) {
            return true;
        }
        if (this.name.getValue() && this.hasInvalidName(entity)) {
            return true;
        }

        return this.matchesCustomCondition(entity);
    }

    private boolean matchesCustomCondition(PlayerEntity suspected) {
        if (this.conditionDuplicate.getValue() && this.isDuplicate(suspected.getGameProfile())) {
            return true;
        }
        if (this.conditionNoGameMode.getValue() && this.getPlayerListEntry(suspected.getUuid()) == null) {
            return true;
        }
        if (this.conditionNoGameMode.getValue()) {
            PlayerListEntry entry = this.getPlayerListEntry(suspected.getUuid());
            if (entry != null && entry.getGameMode() == null) {
                return true;
            }
        }
        if (this.conditionIllegalPitch.getValue() && Math.abs(suspected.getPitch()) > 90.0F) {
            return true;
        }
        if (this.conditionFakeEntityId.getValue() && (suspected.getId() < 0 || suspected.getId() > 1_000_000_000)) {
            return true;
        }
        if (this.conditionNeedHit.getValue() && !this.hitSet.contains(suspected.getId())) {
            return true;
        }
        if (this.conditionIllegalHealth.getValue()
                && mc.player != null
                && suspected.getHealth() > mc.player.getMaxHealth()) {
            return true;
        }
        if (this.conditionSwung.getValue() && !this.swungSet.contains(suspected.getId())) {
            return true;
        }
        if (this.conditionCritted.getValue() && !this.crittedSet.contains(suspected.getId())) {
            return true;
        }
        if (this.conditionAttributes.getValue() && !this.attributesSet.contains(suspected.getId())) {
            return true;
        }
        return this.conditionIllegalScale.getValue()
                && suspected.getAttributeValue(EntityAttributes.SCALE) != 1.0D;
    }

    private boolean hasInvalidName(PlayerEntity entity) {
        String name = entity.getNameForScoreboard();
        int min = this.nameMinLength.getValue().intValue();
        int max = this.nameMaxLength.getValue().intValue();
        if (name.length() < min || name.length() > max) {
            return true;
        }

        return (this.nameVanillaChars.getValue() && !name.codePoints().allMatch(AntiBot::isVanillaCharacter))
                || (this.nameCyrillicChars.getValue() && !name.codePoints().allMatch(AntiBot::isCyrillicCharacter))
                || (this.nameCjkChars.getValue() && !name.codePoints().allMatch(AntiBot::isCjkCharacter));
    }

    private boolean isArmorValid(PlayerEntity entity) {
        return this.isSlotArmorValid(entity, EquipmentSlot.HEAD, this.helmetPredicates())
                && this.isSlotArmorValid(entity, EquipmentSlot.CHEST, this.chestPredicates())
                && this.isSlotArmorValid(entity, EquipmentSlot.LEGS, this.legsPredicates())
                && this.isSlotArmorValid(entity, EquipmentSlot.FEET, this.bootsPredicates());
    }

    private boolean isSlotArmorValid(PlayerEntity entity, EquipmentSlot slot, List<Predicate<ItemStack>> predicates) {
        if (predicates.isEmpty()) {
            return true;
        }

        ItemStack stack = entity.getEquippedStack(slot);
        for (Predicate<ItemStack> predicate : predicates) {
            if (predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private List<Predicate<ItemStack>> helmetPredicates() {
        List<Predicate<ItemStack>> predicates = new ArrayList<>();
        this.addBaseArmorPredicates(predicates, this.helmetNothing, this.helmetLeather, this.helmetChain,
                this.helmetIron, this.helmetGold, this.helmetDiamond, this.helmetNetherite,
                Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET,
                Items.DIAMOND_HELMET, Items.NETHERITE_HELMET);
        if (this.helmetTurtleScute.getValue()) {
            predicates.add(stack -> stack.isOf(Items.TURTLE_HELMET));
        }
        if (this.helmetPumpkin.getValue()) {
            predicates.add(stack -> stack.isOf(Items.CARVED_PUMPKIN));
        }
        if (this.helmetSkull.getValue()) {
            predicates.add(stack -> stack.isIn(ItemTags.SKULLS));
        }
        return predicates;
    }

    private List<Predicate<ItemStack>> chestPredicates() {
        List<Predicate<ItemStack>> predicates = new ArrayList<>();
        this.addBaseArmorPredicates(predicates, this.chestNothing, this.chestLeather, this.chestChain,
                this.chestIron, this.chestGold, this.chestDiamond, this.chestNetherite,
                Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE);
        if (this.chestElytra.getValue()) {
            predicates.add(stack -> stack.isOf(Items.ELYTRA));
        }
        return predicates;
    }

    private List<Predicate<ItemStack>> legsPredicates() {
        List<Predicate<ItemStack>> predicates = new ArrayList<>();
        this.addBaseArmorPredicates(predicates, this.legsNothing, this.legsLeather, this.legsChain,
                this.legsIron, this.legsGold, this.legsDiamond, this.legsNetherite,
                Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS,
                Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS);
        return predicates;
    }

    private List<Predicate<ItemStack>> bootsPredicates() {
        List<Predicate<ItemStack>> predicates = new ArrayList<>();
        this.addBaseArmorPredicates(predicates, this.bootsNothing, this.bootsLeather, this.bootsChain,
                this.bootsIron, this.bootsGold, this.bootsDiamond, this.bootsNetherite,
                Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS,
                Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);
        return predicates;
    }

    private void addBaseArmorPredicates(List<Predicate<ItemStack>> predicates, BooleanValue nothing,
                                        BooleanValue leather, BooleanValue chain, BooleanValue iron,
                                        BooleanValue gold, BooleanValue diamond, BooleanValue netherite,
                                        Item leatherItem, Item chainItem, Item ironItem, Item goldItem,
                                        Item diamondItem, Item netheriteItem) {
        if (nothing.getValue()) {
            predicates.add(ItemStack::isEmpty);
        }
        if (leather.getValue()) {
            predicates.add(stack -> stack.isOf(leatherItem));
        }
        if (chain.getValue()) {
            predicates.add(stack -> stack.isOf(chainItem));
        }
        if (iron.getValue()) {
            predicates.add(stack -> stack.isOf(ironItem));
        }
        if (gold.getValue()) {
            predicates.add(stack -> stack.isOf(goldItem));
        }
        if (diamond.getValue()) {
            predicates.add(stack -> stack.isOf(diamondItem));
        }
        if (netherite.getValue()) {
            predicates.add(stack -> stack.isOf(netheriteItem));
        }
    }

    private boolean isDuplicate(GameProfile profile) {
        int count = 0;
        for (PlayerListEntry entry : this.playerListEntries()) {
            GameProfile other = entry.getProfile();
            if (other.name().equals(profile.name()) && !other.id().equals(profile.id())) {
                count++;
            }
        }
        return count == 1;
    }

    private boolean isGameProfileUnique(GameProfile profile) {
        int count = 0;
        for (PlayerListEntry entry : this.playerListEntries()) {
            GameProfile other = entry.getProfile();
            if (other.name().equals(profile.name()) && other.id().equals(profile.id())) {
                count++;
            }
        }
        return count == 1;
    }

    private boolean isMissingFromTabList(PlayerEntity player) {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (network == null) {
            return false;
        }

        for (PlayerListEntry entry : network.getListedPlayerListEntries()) {
            if (entry.getProfile().id().equals(player.getUuid())) {
                return false;
            }
        }
        return true;
    }

    private boolean isEspBot(PlayerEntity player) {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (network == null) {
            return false;
        }

        PlayerListEntry entry = network.getPlayerListEntry(player.getUuid());
        if (entry == null) {
            entry = network.getPlayerListEntry(player.getName().getString());
        }
        if (entry == null) {
            return ServerUtil.isMultiplayer();
        }

        if (!ServerUtil.isJoined(Server.HYPIXEL)) {
            return false;
        }

        String name = player.getName().getString();
        if (name.startsWith("\u00A7k")) {
            return player.isInvisible();
        }
        if (entry.getLatency() < 1) {
            return true;
        }

        Team team = entry.getScoreboardTeam();
        if (team == null || !team.getName().isEmpty()) {
            return false;
        }
        Text prefix = team.getPrefix();
        return team.getColor() == Formatting.RED
                || prefix != null && "\u00A7c".equals(prefix.getString());
    }

    private PlayerListEntry getPlayerListEntry(UUID uuid) {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        return network == null ? null : network.getPlayerListEntry(uuid);
    }

    private Set<UUID> onlinePlayerIds() {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (network == null) {
            return Set.of();
        }
        return new HashSet<>(network.getPlayerUuids());
    }

    private List<PlayerListEntry> playerListEntries() {
        ClientPlayNetworkHandler network = mc.getNetworkHandler();
        if (network == null) {
            return List.of();
        }
        return new ArrayList<>(network.getPlayerList());
    }

    private PlayerEntity getWorldPlayer(UUID uuid) {
        if (mc.world == null) {
            return null;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private boolean isFullyArmored(PlayerEntity entity) {
        return this.isPlayerArmor(entity.getEquippedStack(EquipmentSlot.HEAD), EquipmentSlot.HEAD)
                && entity.getEquippedStack(EquipmentSlot.HEAD).hasGlint()
                && this.isPlayerArmor(entity.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)
                && entity.getEquippedStack(EquipmentSlot.CHEST).hasGlint()
                && this.isPlayerArmor(entity.getEquippedStack(EquipmentSlot.LEGS), EquipmentSlot.LEGS)
                && entity.getEquippedStack(EquipmentSlot.LEGS).hasGlint()
                && this.isPlayerArmor(entity.getEquippedStack(EquipmentSlot.FEET), EquipmentSlot.FEET)
                && entity.getEquippedStack(EquipmentSlot.FEET).hasGlint();
    }

    private boolean isPlayerArmor(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return false;
        }

        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot && slot.isArmorSlot();
    }

    private EnumMap<EquipmentSlot, ItemStack> copyArmor(PlayerEntity entity) {
        EnumMap<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        armor.put(EquipmentSlot.HEAD, entity.getEquippedStack(EquipmentSlot.HEAD).copy());
        armor.put(EquipmentSlot.CHEST, entity.getEquippedStack(EquipmentSlot.CHEST).copy());
        armor.put(EquipmentSlot.LEGS, entity.getEquippedStack(EquipmentSlot.LEGS).copy());
        armor.put(EquipmentSlot.FEET, entity.getEquippedStack(EquipmentSlot.FEET).copy());
        return armor;
    }

    private boolean updatesArmor(PlayerEntity entity, MatrixArmorSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        for (EquipmentSlot slot : EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            if (!ItemStack.areEqual(snapshot.armor.get(slot), entity.getEquippedStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        this.flyingSet.clear();
        this.hitSet.clear();
        this.notAlwaysInRadiusSet.clear();
        this.swungSet.clear();
        this.crittedSet.clear();
        this.attributesSet.clear();
        this.armorSet.clear();
        this.horizonBotList.clear();
        this.intaveSuspectList.clear();
        this.intaveBotList.clear();
        this.matrixSuspectList.clear();
        this.matrixBotList.clear();
        this.matrixArmorSnapshots.clear();
    }

    private static double square(double value) {
        return value * value;
    }

    private static boolean isVanillaCharacter(int value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value == '_';
    }

    private static boolean isCyrillicCharacter(int value) {
        return value >= 0x0400 && value <= 0x052F;
    }

    private static boolean isCjkCharacter(int value) {
        return value >= 0x4E00 && value <= 0x9FA5;
    }

    private static void registerAttackCallback() {
        if (attackCallbackRegistered) {
            return;
        }
        attackCallbackRegistered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player != mc.player) {
                return ActionResult.PASS;
            }

            io.github.wzhy233.ardion.module.impl.misc.AntiBot antiBot =
                    io.github.wzhy233.ardion.module.manager.ModuleManager.getModule(AntiBot.class);
            if (antiBot != null && antiBot.isEnabled()) {
                antiBot.hitSet.add(entity.getId());
            }

            return ActionResult.PASS;
        });
    }

    private record SuspectInfo(int latency, long timestamp) {
    }

    private record MatrixArmorSnapshot(EnumMap<EquipmentSlot, ItemStack> armor, int waitTicks) {
    }
}
