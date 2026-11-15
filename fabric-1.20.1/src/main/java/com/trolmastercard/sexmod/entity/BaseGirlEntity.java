package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.networking.ModPackets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class of every girl: synced scene state, the {@link Action} state
 * machine, the three GeckoLib controllers ({@code action}, {@code movement},
 * {@code eyes}), scene anchoring and the player placement helpers.
 * <p>
 * <b>Sides.</b> Clients never write tracked data directly; every setter that
 * can be called on the client sends a {@code change_data_parameter} packet
 * instead, and the server applies it. The sound-keyframe listener
 * ({@link #onSoundKeyframe}) only runs on clients, because keyframes fire from
 * rendering.
 * <p>
 * <b>Anchoring.</b> While {@link #IS_ANCHORED} is set, the server pins the girl
 * to {@link #TARGET_POS}/{@link #YAW_ROTATION} every tick ({@link #mobTick()}),
 * and clients do the same in {@link #tick()} so interpolation cannot drift.
 */
public abstract class BaseGirlEntity extends PathAwareEntity implements GeoEntity {
   public static final TrackedData<String> MASTER = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   public static final TrackedData<Boolean> IS_ANCHORED = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   /** Anchor position as {@code x|y|z}. */
   public static final TrackedData<String> TARGET_POS = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   public static final TrackedData<Float> YAW_ROTATION = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.FLOAT);
   /** Persistent girl id, the key every packet uses to address a girl. */
   public static final TrackedData<String> GIRL_ID = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   /** 0 = nude, 1 = dressed. */
   public static final TrackedData<Integer> OUTFIT_INDEX = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.INTEGER);
   public static final TrackedData<String> CUR_ACTION = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   /** Which scene was picked in the menu ({@code animationFollowUp} on the wire). */
   public static final TrackedData<String> GIRL_HAND_STATES = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   /** UUID of the player bound to the running scene, {@code "null"} when unset. */
   public static final TrackedData<String> INTERACTION_PARTNER_UUID = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   public static final TrackedData<String> WALK_SPEED = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);
   public static final TrackedData<String> CUSTOM_NAME = DataTracker.registerData(BaseGirlEntity.class, TrackedDataHandlerRegistry.STRING);

   protected static final Ingredient TEMPTATION_ITEMS = Ingredient.ofItems(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.ENDER_PEARL);

   private static final Set<BaseGirlEntity> SERVER_GIRLS = ConcurrentHashMap.newKeySet();
   private static final Set<BaseGirlEntity> CLIENT_GIRLS = ConcurrentHashMap.newKeySet();
   private static final Map<String, RawAnimation> RAW_ANIMATIONS = new ConcurrentHashMap<>();

   private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

   // Assigned from initGoals(), which the MobEntity constructor calls before
   // field initialisers of this class run, so these must not have initialisers.
   public Goal wanderGoal;
   public Goal watchClosestGirlGoal;

   /** Reference point for {@link #positionPlayerRelative}; cleared on every reset path. */
   public Vec3d cameraOriginPos;
   protected float cameraYaw;
   public Vec3d homePos = Vec3d.ZERO;
   /** Set while the "send home" pearl animation has thrown its pearl. */
   public boolean pearlThrown;

   public AnimationController<BaseGirlEntity> actionController;
   public AnimationController<BaseGirlEntity> movementController;
   public AnimationController<BaseGirlEntity> eyesController;

   private final int[] actionTicks = new int[]{0, 0};
   private Action lastTickAction;
   private String cachedTargetPosRaw;
   private Vec3d cachedTargetPos = Vec3d.ZERO;
   private String cachedInteractionPlayerRaw;
   private UUID cachedInteractionPlayerUUID;
   private boolean removeAsDuplicate;

   protected BaseGirlEntity(EntityType<? extends BaseGirlEntity> type, World world) {
      super(type, world);
      EntityNavigation navigation = this.getNavigation();
      if (navigation instanceof MobNavigation) {
         ((MobNavigation)navigation).setCanPathThroughDoors(true);
         ((MobNavigation)navigation).setCanEnterOpenDoors(true);
      }
   }

   public static DefaultAttributeContainer.Builder createGirlAttributes() {
      return MobEntity.createMobAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 30.0);
   }

   // ---------------------------------------------------------------- registry

   public static void onGirlLoaded(BaseGirlEntity girl) {
      (girl.getWorld().isClient ? CLIENT_GIRLS : SERVER_GIRLS).add(girl);
   }

   public static void onGirlUnloaded(BaseGirlEntity girl) {
      (girl.getWorld().isClient ? CLIENT_GIRLS : SERVER_GIRLS).remove(girl);
   }

   public static void clearServerGirls() {
      SERVER_GIRLS.clear();
   }

   public static void clearClientGirls() {
      CLIENT_GIRLS.clear();
   }

   /** Loaded girls of one side. */
   public static Set<BaseGirlEntity> getGirls(boolean client) {
      return client ? CLIENT_GIRLS : SERVER_GIRLS;
   }

   @Nullable
   public static BaseGirlEntity getServerGirlEntity(UUID girlId) {
      return findByGirlId(SERVER_GIRLS, girlId);
   }

   @Nullable
   public static BaseGirlEntity getClientGirlEntity(UUID girlId) {
      return findByGirlId(CLIENT_GIRLS, girlId);
   }

   @Nullable
   private static BaseGirlEntity findByGirlId(Set<BaseGirlEntity> girls, UUID girlId) {
      if (girlId == null) {
         return null;
      }
      for (BaseGirlEntity girl : girls) {
         if (!girl.isRemoved() && girlId.equals(girl.getGirlId())) {
            return girl;
         }
      }
      return null;
   }

   /** The girl whose running scene is bound to {@code playerUuid} on the given side, or {@code null}. */
   @Nullable
   public static BaseGirlEntity getActiveScene(UUID playerUuid, boolean client) {
      if (playerUuid == null) {
         return null;
      }
      for (BaseGirlEntity girl : getGirls(client)) {
         if (!girl.isRemoved() && playerUuid.equals(girl.getInteractionPlayerUUID())) {
            return girl;
         }
      }
      return null;
   }

   // ------------------------------------------------------------ tracked data

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(GIRL_ID, UUID.randomUUID().toString());
      this.dataTracker.startTracking(OUTFIT_INDEX, 1);
      this.dataTracker.startTracking(CUR_ACTION, Action.NULL.toString());
      this.dataTracker.startTracking(GIRL_HAND_STATES, "");
      this.dataTracker.startTracking(INTERACTION_PARTNER_UUID, "null");
      this.dataTracker.startTracking(IS_ANCHORED, false);
      this.dataTracker.startTracking(YAW_ROTATION, 0.0F);
      this.dataTracker.startTracking(TARGET_POS, "0|0|0");
      this.dataTracker.startTracking(MASTER, "");
      this.dataTracker.startTracking(WALK_SPEED, BaseGirlEntityState.WALK.toString());
      this.dataTracker.startTracking(CUSTOM_NAME, "");
   }

   public UUID getGirlId() {
      try {
         return UUID.fromString(this.dataTracker.get(GIRL_ID));
      } catch (IllegalArgumentException e) {
         UUID id = UUID.randomUUID();
         if (!this.getWorld().isClient) {
            this.dataTracker.set(GIRL_ID, id.toString());
         }
         return id;
      }
   }

   public Action getCurrentAction() {
      return Action.byName(this.dataTracker.get(CUR_ACTION));
   }

   public void setCurrentAction(Action action) {
      Action previousAction = this.getCurrentAction();
      action = action == null ? Action.NULL : action;
      if (previousAction == action) {
         return;
      }
      // An attack never interrupts a running action.
      if (action == Action.ATTACK && previousAction != Action.NULL) {
         return;
      }
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("currentAction", action.toString());
      } else {
         this.dataTracker.set(CUR_ACTION, action.toString());
      }
   }

   public int getOutfitIndex() {
      return this.dataTracker.get(OUTFIT_INDEX);
   }

   public BaseGirlEntityState getWalkType() {
      try {
         return BaseGirlEntityState.valueOf(this.dataTracker.get(WALK_SPEED));
      } catch (IllegalArgumentException e) {
         return BaseGirlEntityState.WALK;
      }
   }

   public void setWalkSpeed(BaseGirlEntityState state) {
      if (this.getWalkType() != state) {
         this.dataTracker.set(WALK_SPEED, state.toString());
      }
   }

   /** Client side: asks the server to change one of this girl's synced values. */
   protected void changeDataParameterFromClient(String key, String value) {
      ModPackets.sendChangeDataParameter(this.getGirlId(), key, value);
   }

   @Nullable
   public UUID getInteractionPlayerUUID() {
      String raw = this.dataTracker.get(INTERACTION_PARTNER_UUID);
      if (!raw.equals(this.cachedInteractionPlayerRaw)) {
         UUID parsed = null;
         if (!"null".equals(raw)) {
            try {
               parsed = UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
               // treated as unset
            }
         }
         this.cachedInteractionPlayerUUID = parsed;
         this.cachedInteractionPlayerRaw = raw;
      }
      return this.cachedInteractionPlayerUUID;
   }

   public void setInteractionPlayerUUID(@Nullable UUID uuid) {
      String value = uuid == null ? "null" : uuid.toString();
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("playerSheHasSexWith", value);
      } else {
         this.dataTracker.set(INTERACTION_PARTNER_UUID, value);
      }
   }

   /** The player bound to the running scene, if they are in this world. */
   @Nullable
   public PlayerEntity getInteractionPlayer() {
      UUID uuid = this.getInteractionPlayerUUID();
      return uuid == null ? null : this.getWorld().getPlayerByUuid(uuid);
   }

   public Vec3d getTargetPosition() {
      String raw = this.dataTracker.get(TARGET_POS);
      if (!raw.equals(this.cachedTargetPosRaw)) {
         try {
            String[] parts = raw.split("\\|");
            this.cachedTargetPos = new Vec3d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
         } catch (RuntimeException e) {
            this.cachedTargetPos = this.getPos();
         }
         this.cachedTargetPosRaw = raw;
      }
      return this.cachedTargetPos;
   }

   public void setTargetPosition(Vec3d pos) {
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("targetPos", pos.x + "f" + pos.y + "f" + pos.z + "f");
      } else {
         this.dataTracker.set(TARGET_POS, pos.x + "|" + pos.y + "|" + pos.z);
      }
   }

   public float getYawRotation() {
      return this.dataTracker.get(YAW_ROTATION);
   }

   /** Server side only. */
   public void setYawRotation(float yaw) {
      this.dataTracker.set(YAW_ROTATION, yaw);
   }

   public boolean isAnchored() {
      return this.dataTracker.get(IS_ANCHORED);
   }

   public void setAnchored(boolean anchored) {
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("shouldbeattargetpos", String.valueOf(anchored));
      } else {
         this.dataTracker.set(IS_ANCHORED, anchored);
      }
   }

   public boolean hasMaster() {
      return !this.dataTracker.get(MASTER).isEmpty();
   }

   @Nullable
   public UUID getMasterUUID() {
      String master = this.dataTracker.get(MASTER);
      if (master.isEmpty()) {
         return null;
      }
      try {
         return UUID.fromString(master);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   @Nullable
   public PlayerEntity getMasterPlayer() {
      UUID master = this.getMasterUUID();
      return master == null ? null : this.getWorld().getPlayerByUuid(master);
   }

   /** Stops following: clears the master and goes back to walking speed. */
   public void goHome() {
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("master", "");
         this.changeDataParameterFromClient("walk speed", BaseGirlEntityState.WALK.toString());
      } else {
         this.dataTracker.set(MASTER, "");
         this.dataTracker.set(WALK_SPEED, BaseGirlEntityState.WALK.toString());
      }
   }

   public abstract String getDisplayNameText();

   /** The name shown in chat: the custom name if one is set, else the character's name. */
   public String getEffectiveDisplayName() {
      String custom = this.dataTracker.get(CUSTOM_NAME);
      if (!custom.isEmpty()) {
         return custom;
      }
      return this.hasCustomName() && this.getCustomName() != null ? this.getCustomName().getString() : this.getDisplayNameText();
   }

   public void setCustomNameOverride(String name) {
      this.dataTracker.set(CUSTOM_NAME, name == null ? "" : name);
   }

   // -------------------------------------------------------------------- AI

   @Override
   protected void initGoals() {
      this.goalSelector.add(0, new SwimGoal(this));
      this.goalSelector.add(2, new TemptGoal(this, 0.4, TEMPTATION_ITEMS, false));
      this.goalSelector.add(3, new LongDoorInteractGoal(this, true));
      this.resetWanderGoals();
   }

   /** (Re-)adds the idle goals that scenes remove while they run. */
   protected void resetWanderGoals() {
      if (this.wanderGoal != null) {
         this.goalSelector.remove(this.wanderGoal);
      }
      if (this.watchClosestGirlGoal != null) {
         this.goalSelector.remove(this.watchClosestGirlGoal);
      }
      this.wanderGoal = new WanderAroundFarGoal(this, 0.35);
      this.watchClosestGirlGoal = new LookAtEntityGoal(this, PlayerEntity.class, 3.0F, 1.0F);
      this.goalSelector.add(5, this.watchClosestGirlGoal);
      this.goalSelector.add(5, this.wanderGoal);
   }

   /** Removes the idle goals so the girl stands still for a scene. */
   public void removeWanderGoals() {
      if (this.wanderGoal != null) {
         this.goalSelector.remove(this.wanderGoal);
      }
      if (this.watchClosestGirlGoal != null) {
         this.goalSelector.remove(this.watchClosestGirlGoal);
      }
   }

   /** Called when a scene releases the girl; subclasses restore their goals here. */
   public void reinitTasks() {
   }

   @Override
   public boolean cannotDespawn() {
      return true;
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   protected void mobTick() {
      super.mobTick();
      if (this.isAnchored()) {
         Vec3d target = this.getTargetPosition();
         float yaw = this.getYawRotation();
         this.updatePositionAndAngles(target.x, target.y, target.z, yaw, 0.0F);
         this.setHeadYaw(yaw);
         this.setBodyYaw(yaw);
      }
      if (this.homePos.equals(Vec3d.ZERO)) {
         this.homePos = Vec3d.of(this.getBlockPos());
      }
   }

   @Override
   public void tick() {
      if (this.removeAsDuplicate && !this.getWorld().isClient) {
         this.discard();
         return;
      }
      super.tick();
      if (this.getWorld().isClient && this.isAnchored()) {
         Vec3d target = this.getTargetPosition();
         float yaw = this.getYawRotation();
         this.updateTrackedPositionAndAngles(target.x, target.y, target.z, yaw, 0.0F, 3, true);
         this.setHeadYaw(yaw);
         this.setBodyYaw(yaw);
      }
      this.tickFollowUpTransitions();
   }

   /** Counts how long the current action has been playing and applies {@link Action#followUp} on the server. */
   protected void tickFollowUpTransitions() {
      Action action = this.getCurrentAction();
      int side = this.getWorld().isClient ? 1 : 0;
      if (action != this.lastTickAction) {
         this.lastTickAction = action;
         this.actionTicks[0] = 0;
         this.actionTicks[1] = 0;
      }
      this.actionTicks[side]++;
      if (action.followUp != null && this.actionTicks[side] >= action.length && !this.getWorld().isClient) {
         this.setCurrentAction(action.followUp);
      }
   }

   /** Ticks the current action has been playing on this side. */
   public int getActionTicks() {
      return this.actionTicks[this.getWorld().isClient ? 1 : 0];
   }

   // ------------------------------------------------------------------- NBT

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putDouble("homeX", this.homePos.x);
      nbt.putDouble("homeY", this.homePos.y);
      nbt.putDouble("homeZ", this.homePos.z);
      nbt.putString("girlID", this.dataTracker.get(GIRL_ID));
      nbt.putInt("outfit", this.getOutfitIndex());
      nbt.putString("master", this.dataTracker.get(MASTER));
      String customName = this.dataTracker.get(CUSTOM_NAME);
      if (!customName.isEmpty()) {
         nbt.putString("sexmod:customname", customName);
      }
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      this.homePos = new Vec3d(nbt.getDouble("homeX"), nbt.getDouble("homeY"), nbt.getDouble("homeZ"));
      if (nbt.contains("outfit")) {
         this.dataTracker.set(OUTFIT_INDEX, nbt.getInt("outfit"));
      }
      this.dataTracker.set(MASTER, nbt.getString("master"));
      String customName = nbt.getString("sexmod:customname");
      if (!customName.isEmpty()) {
         this.setCustomNameOverride(customName);
      }
      String girlId = nbt.getString("girlID");
      if (girlId.isEmpty()) {
         return;
      }
      UUID uuid;
      try {
         uuid = UUID.fromString(girlId);
      } catch (IllegalArgumentException e) {
         return;
      }
      for (BaseGirlEntity other : SERVER_GIRLS) {
         if (other != this && !other.isRemoved() && uuid.equals(other.getGirlId())) {
            SexMod.LOGGER.warn("got a duped {} with id '{}'. Deleted her", this.getDisplayNameText(), uuid);
            this.removeAsDuplicate = true;
            return;
         }
      }
      this.dataTracker.set(GIRL_ID, uuid.toString());
   }

   // ----------------------------------------------------------- interaction

   /** Client side: opens the action menu. Returns {@code false} if the girl is busy. */
   public boolean openInteractionMenu(PlayerEntity player) {
      return false;
   }

   /** Client side: the player picked {@code action} (a lang key) in the menu. */
   public void doAction(String action, UUID playerUuid) {
   }

   /** Server side: start the scene stored in {@link #GIRL_HAND_STATES}. Also called from client sound keyframes. */
   protected void startActionFromHandState() {
   }

   /** Client side: next action when the player holds the "faster" key, or {@code null}. */
   @Nullable
   protected abstract Action getNextAction(Action action);

   /** Client side: finishing action for {@code action}, or {@code null}. */
   @Nullable
   protected abstract Action getCumAction(Action action);

   /** Client side: the local player pressed the "faster" key during this girl's scene. */
   public void triggerFastSexAction() {
      Action next = this.getNextAction(this.getCurrentAction());
      if (next != null) {
         this.setCurrentAction(next);
      }
   }

   /** Client side: the local player finished the scene (jump with a full meter). */
   public void triggerCumAction() {
      Action cum = this.getCumAction(this.getCurrentAction());
      if (cum != null) {
         this.setCurrentAction(cum);
      }
   }

   /** Client side: {@code true} if the player in front of this screen is the one in this girl's scene. */
   public boolean isControlledByLocalPlayer() {
      if (!this.getWorld().isClient) {
         return false;
      }
      UUID local = SexMod.CLIENT.localPlayerUuid();
      return local != null && local.equals(this.getInteractionPlayerUUID());
   }

   /** Client side: {@code true} if the local player is the closest player within 50 blocks. */
   public boolean isLocalPlayerNearby() {
      PlayerEntity closest = this.getWorld().getClosestPlayer(this, 50.0);
      UUID local = SexMod.CLIENT.localPlayerUuid();
      return closest != null && local != null && closest.getUuid().equals(local);
   }

   protected void addToHornyMeterIfControlled(double amount) {
      if (this.isControlledByLocalPlayer()) {
         SexMod.CLIENT.addToHornyMeter(amount);
      }
   }

   /** Starts the scene-entry sequence for {@code playerUuid} (see {@code ModPackets#startScene}). */
   protected void triggerActionSync(boolean walkToPlayer, boolean anchorInFront, UUID playerUuid) {
      if (this.getWorld().isClient) {
         ModPackets.sendStartScene(this.getGirlId(), walkToPlayer, anchorInFront);
      } else {
         ModPackets.startScene(this, playerUuid, walkToPlayer, anchorInFront);
      }
   }

   /**
    * Player-only reset: gives the interaction player their normal state back
    * without releasing the girl (used between the phases of a scene).
    */
   protected void resetGirlState() {
      if (this.getWorld().isClient) {
         if (this.isControlledByLocalPlayer()) {
            this.cameraOriginPos = null;
            ModPackets.sendResetGirl(this.getGirlId(), true);
         }
      } else {
         PlayerEntity player = this.getInteractionPlayer();
         if (player instanceof ServerPlayerEntity) {
            ModPackets.resetPlayer((ServerPlayerEntity)player);
         }
      }
   }

   /** Scene end: clears the action and, for the controlling client, asks the server for the full reset. */
   public void resetCameraAndPhysics() {
      this.cameraOriginPos = null;
      this.setNoGravity(false);
      this.setCurrentAction(Action.NULL);
      if (this.getWorld().isClient && this.isControlledByLocalPlayer()) {
         SexMod.CLIENT.setMovementLocked(false);
         ModPackets.sendResetGirl(this.getGirlId(), false);
      }
   }

   /** Client side: restarts the action animation locally and for every other observer. */
   public void resetAnimationControllerOffset() {
      SexMod.CLIENT.restartActionAnimation(this);
      ModPackets.sendResetController(this.getGirlId());
   }

   // ------------------------------------------------------ player placement

   /** Position {@code distance} blocks in front of the interaction player (the girl's own position if there is none). */
   public Vec3d getVectorTowardPlayer(double distance) {
      PlayerEntity player = this.getInteractionPlayer();
      if (player == null) {
         return this.getPos();
      }
      double yaw = player.getYaw() * (Math.PI / 180.0);
      return player.getPos().add(-Math.sin(yaw) * distance, 0.0, Math.cos(yaw) * distance);
   }

   public Vec3d getFrontOffsetVector() {
      return this.getVectorTowardPlayer(1.0);
   }

   /** Server side: puts the scene player right in front of the girl and turns her to face them. */
   public void snapPlayerToPosition() {
      PlayerEntity player = this.getInteractionPlayer();
      if (player == null) {
         return;
      }
      player.setVelocity(Vec3d.ZERO);
      Vec3d offset = this.getVectorTowardPlayer(0.35);
      player.requestTeleport(offset.x, offset.y, offset.z);
      this.setYawRotation(player.getHeadYaw() + 180.0F);
   }

   protected void alignPlayerToGirl(ServerPlayerEntity player, boolean teleport) {
      player.setVelocity(Vec3d.ZERO);
      if (teleport) {
         Vec3d offset = this.getVectorTowardPlayer(0.35);
         player.requestTeleport(offset.x, offset.y, offset.z);
      }
   }

   /**
    * Moves the scene player to an offset from {@link #cameraOriginPos} (their
    * position when the scene's camera work started), expressed in the frame of
    * {@link #cameraYaw}: {@code dx} sideways, {@code dy} up, {@code dz}
    * forward, and sets their view to {@code cameraYaw + yaw}/{@code pitch}.
    * On the client this is sent to the server, which performs the teleport.
    */
   protected void positionPlayerRelative(double dx, double dy, double dz, float yaw, float pitch) {
      PlayerEntity player = this.getInteractionPlayer();
      if (player == null) {
         SexMod.LOGGER.info("couldnt move camera because the player isn't set");
         return;
      }
      if (this.cameraOriginPos == null) {
         this.cameraOriginPos = player.getPos();
      }
      double side = (this.cameraYaw + 90.0F) * (Math.PI / 180.0);
      double forward = this.cameraYaw * (Math.PI / 180.0);
      Vec3d newPos = this.cameraOriginPos
         .add(-Math.sin(side) * dx, 0.0, Math.cos(side) * dx)
         .add(0.0, dy, 0.0)
         .add(-Math.sin(forward) * dz, 0.0, Math.cos(forward) * dz);
      if (this.getWorld().isClient) {
         ModPackets.sendTeleportPlayer(newPos, this.cameraYaw + yaw, pitch);
      } else if (player instanceof ServerPlayerEntity) {
         ModPackets.teleportPlayer((ServerPlayerEntity)player, newPos, this.cameraYaw + yaw, pitch);
         this.setVelocity(Vec3d.ZERO);
      }
   }

   @Nullable
   protected BlockPos getNearestBed() {
      return GirlBedPlacement.findNearestBed(this.getWorld(), this.getBlockPos(), 22, 3);
   }

   // ------------------------------------------------------------------ chat

   /**
    * Says {@code message} as this girl to every player within 40 blocks.
    * From a client only the scene's own player relays it, so observers do not
    * multiply the line.
    */
   public void sendGirlChatMessage(String message) {
      this.broadcastRaw(String.format("<%s> %s", this.getEffectiveDisplayName(), message));
   }

   /** Sends an already formatted chat line to every player within 40 blocks. */
   protected void broadcastChatAround(String message) {
      this.broadcastRaw(message);
   }

   private void broadcastRaw(String line) {
      if (!this.getWorld().isClient) {
         ModPackets.broadcastChat(this, line);
      } else if (this.isControlledByLocalPlayer()) {
         ModPackets.sendChatMessage(this.getGirlId(), line);
      }
   }

   /** Client side: shows a line from this girl only to the local player. */
   protected void sendChatMessage(String message) {
      if (this.getWorld().isClient) {
         SexMod.CLIENT.showChatMessage(String.format("<%s> %s", this.getEffectiveDisplayName(), message));
      }
   }

   // ---------------------------------------------------------------- sounds

   /** Plays a sound at the girl for this client only (keyframe sounds are triggered per client). */
   public void playSoundAtPosition(SoundEvent sound, float volume, float pitch) {
      if (sound != null) {
         this.getWorld().playSound(this.getX(), this.getY(), this.getZ(), sound, SoundCategory.NEUTRAL, volume, pitch, false);
      }
   }

   public void playSound(SoundEvent sound) {
      this.playSoundAtPosition(sound, 1.0F, 1.0F);
   }

   public void playSoundAtVolume(SoundEvent sound, float volume) {
      this.playSoundAtPosition(sound, volume, 1.0F);
   }

   @Override
   @Nullable
   protected SoundEvent getAmbientSound() {
      return null;
   }

   // ------------------------------------------------------------- animation

   /** Chooses the animation for one controller; {@code state.getController().getName()} tells which. */
   protected abstract PlayState animationPredicate(AnimationState<BaseGirlEntity> state);

   /** Client side: a sound keyframe of the action animation fired. */
   protected abstract void onSoundKeyframe(String sound);

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
      this.actionController = new AnimationController<>(this, "action", 0, this::animationPredicate);
      this.movementController = new AnimationController<>(this, "movement", 0, this::animationPredicate);
      this.eyesController = new AnimationController<>(this, "eyes", 0, this::animationPredicate);
      this.actionController.setSoundKeyframeHandler(event -> this.onSoundKeyframe(event.getKeyframeData().getSound()));
      controllers.add(this.actionController);
      controllers.add(this.movementController);
      controllers.add(this.eyesController);
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   /** Plays {@code animName} on the controller of {@code state}, looping or holding on the last frame. */
   protected void createAnimation(String animName, boolean loop, AnimationState<BaseGirlEntity> state) {
      RawAnimation animation = RAW_ANIMATIONS.computeIfAbsent(
         animName + (loop ? "|loop" : "|hold"),
         key -> RawAnimation.begin().then(animName, loop ? Animation.LoopType.LOOP : Animation.LoopType.HOLD_ON_LAST_FRAME)
      );
      AnimationController<BaseGirlEntity> controller = state.getController();
      if (controller == this.actionController) {
         controller.setTransitionLength(this.getCurrentAction().transitionTick);
      }
      controller.setAnimation(animation);
   }

   /** {@code true} while the girl is moving horizontally (drives walk/idle). */
   protected boolean isMovingHorizontally() {
      return Math.abs(this.prevX - this.getX()) + Math.abs(this.prevZ - this.getZ()) > 0.0;
   }

   public enum BaseGirlEntityState {
      WALK,
      FAST_WALK,
      RUN
   }
}
