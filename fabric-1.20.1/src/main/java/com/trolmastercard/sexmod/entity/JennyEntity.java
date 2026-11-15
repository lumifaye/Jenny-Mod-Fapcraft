package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.entity.api.IBeddableSexGirl;
import com.trolmastercard.sexmod.entity.api.IEllie;
import com.trolmastercard.sexmod.networking.ModPackets;
import com.trolmastercard.sexmod.potion.HornyPotion;
import com.trolmastercard.sexmod.util.RotationHelper;
import com.trolmastercard.sexmod.util.SoundHandler;
import com.trolmastercard.sexmod.util.VectorMath;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Jenny. Her scenes are driven by the sound keyframes of
 * {@code jenny.animation.json}: each animation names its own steps
 * ({@code <scene>MSG<n>}) and its end ({@code <scene>Done}), and
 * {@link #onSoundKeyframe} maps those names to chat lines, sounds, camera
 * placement and the next {@link Action}.
 * <p>
 * Keyframes fire on every client that renders her. Sounds and local effects
 * run for everyone; anything that changes shared state is sent only by
 * {@link #isDrivingClient() one client}, so two observers cannot push the
 * scene forward twice.
 */
public class JennyEntity extends AbstractGirlNpcEntity implements IEllie, IBeddableSexGirl {
   /** Synced: she is under the horny effect, which makes her scenes free. */
   public static final TrackedData<Boolean> HORNY = DataTracker.registerData(JennyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

   /** How long she waits on the bed for the player before giving up. */
   private static final int BED_WAIT_TIMEOUT_TICKS = 1200;

   /** Server: walking to the bed picked by {@link #goToSexBed()}. */
   public boolean walkingToBed;
   /** Server: gliding in front of the player before the scene starts. */
   public boolean sceneEntryLerpActive;
   /** Server: waiting on the bed for the scene player to step up to her. */
   public boolean snapInPending;
   private int sceneEntryLerpTicks;
   private int bedWalkTicks;
   private int bedWaitTicks;
   /** Client: which variant of the fast loop is playing. */
   public boolean doggyHard;
   private int fastMoanCount;
   private boolean paizuriCameraAligned;

   public JennyEntity(EntityType<? extends JennyEntity> type, World world) {
      super(type, world);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(HORNY, false);
   }

   @Override
   public String getDisplayNameText() {
      return "Jenny";
   }

   @Override
   protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
      return 1.64F;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_SIGH);
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   public boolean isHorny() {
      return this.dataTracker.get(HORNY);
   }

   // ------------------------------------------------------------ server tick

   @Override
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient) {
         boolean horny = this.hasStatusEffect(HornyPotion.HORNY);
         if (this.isHorny() != horny) {
            this.dataTracker.set(HORNY, horny);
         }
      }
   }

   @Override
   protected void mobTick() {
      super.mobTick();
      this.tickBedWait();
      this.tickWalkToBed();
      this.tickSceneEntry();
   }

   /**
    * On the bed, waiting: the scene starts once the player bound by
    * {@code set_player_for_girl} steps up to her. Unlike 1.12.2, only that
    * player can trigger it (there, any player walking past was pulled in),
    * and she gives up after a minute.
    */
   private void tickBedWait() {
      if (!this.snapInPending) {
         this.bedWaitTicks = 0;
         return;
      }
      PlayerEntity player = this.getInteractionPlayer();
      if (!(player instanceof ServerPlayerEntity) || ++this.bedWaitTicks > BED_WAIT_TIMEOUT_TICKS) {
         this.snapInPending = false;
         this.setCurrentAction(Action.NULL);
         ModPackets.resetGirl(this);
         return;
      }
      if (player.getPos().distanceTo(this.getPos()) >= 0.5) {
         return;
      }
      this.snapInPending = false;
      ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
      serverPlayer.requestTeleport(this.getX(), this.getY(), this.getZ());
      this.alignPlayerToGirl(serverPlayer, false);
      this.cameraOriginPos = null;
      this.positionPlayerRelative(0.0, 0.0, 0.4, 0.0F, 60.0F);
      this.cameraOriginPos = null;
      this.setCurrentAction(Action.DOGGYSTART);
      ModPackets.enterScene(serverPlayer);
   }

   private void tickWalkToBed() {
      if (!this.walkingToBed) {
         return;
      }
      Vec3d target = this.getTargetPosition();
      if (this.getPos().distanceTo(target) >= 0.6 && this.bedWalkTicks <= 200) {
         this.bedWalkTicks++;
         // Re-path twice in case the first path got stuck on a door or a corner.
         if (this.bedWalkTicks == 60 || this.bedWalkTicks == 120) {
            this.getNavigation().stop();
            this.getNavigation().startMovingTo(target.x, target.y, target.z, 0.35);
         }
         return;
      }
      this.walkingToBed = false;
      this.bedWalkTicks = 0;
      this.dataTracker.set(IS_ANCHORED, true);
      this.noClip = true;
      this.setNoGravity(true);
      this.setVelocity(Vec3d.ZERO);
      this.setCurrentAction(Action.STARTDOGGY);
   }

   private void tickSceneEntry() {
      if (!this.sceneEntryLerpActive) {
         return;
      }
      this.sceneEntryLerpTicks++;
      if (this.sceneEntryLerpTicks <= 40) {
         this.setYaw(this.getYawRotation());
         this.setTargetPosition(this.getFrontOffsetVector());
         this.setNoGravity(false);
         Vec3d pos = RotationHelper.lerpVec3d(this.getPos(), this.getTargetPosition(), 40 - this.sceneEntryLerpTicks);
         this.setPosition(pos.x, pos.y, pos.z);
         return;
      }
      this.sceneEntryLerpActive = false;
      this.sceneEntryLerpTicks = 0;
      PlayerEntity player = this.getInteractionPlayer();
      if (player != null) {
         this.setYawRotation(player.getYaw() + 180.0F);
      }
      this.dataTracker.set(IS_ANCHORED, true);
      this.getNavigation().stop();
      if (this.isHorny()) {
         this.startActionFromHandState();
      } else {
         this.setCurrentAction(Action.PAYMENT);
      }
   }

   // ------------------------------------------------------------ interaction

   @Override
   protected ActionResult interactMob(PlayerEntity player, Hand hand) {
      if (hand != Hand.MAIN_HAND) {
         return ActionResult.PASS;
      }
      if (this.getWorld().isClient && !this.openInteractionMenu(player)) {
         this.sendChatMessage(SexMod.CLIENT.translate("jenny.dialogue.busy"));
      }
      return ActionResult.success(this.getWorld().isClient);
   }

   @Override
   public boolean openInteractionMenu(PlayerEntity player) {
      UUID local = SexMod.CLIENT.localPlayerUuid();
      if (local == null || this.getInteractionPlayerUUID() != null || this.getCurrentAction() != Action.NULL) {
         return false;
      }
      if (this.hasMaster() && !local.equals(this.getMasterUUID())) {
         return false;
      }
      boolean dressed = this.getOutfitIndex() == 1;
      String[] options = new String[]{
         "action.names.blowjob", "action.names.boobjob", "action.names.doggy", dressed ? "action.names.strip" : "action.names.dressup"
      };
      ItemStack[] costs = null;
      if (!this.isHorny()) {
         costs = new ItemStack[]{
            new ItemStack(Items.EMERALD, 3),
            new ItemStack(Items.ENDER_PEARL, 2),
            new ItemStack(Items.DIAMOND, 2),
            dressed ? new ItemStack(Items.GOLD_INGOT, 1) : ItemStack.EMPTY
         };
      }
      SexMod.CLIENT.openActionMenu(this, options, costs);
      return true;
   }

   @Override
   public void doAction(String action, UUID playerUuid) {
      super.doAction(action, playerUuid);
      switch (action) {
         case "action.names.blowjob":
            this.startPaidScene("blowjob", playerUuid);
            break;
         case "action.names.boobjob":
            this.startPaidScene("boobjob", playerUuid);
            break;
         case "action.names.doggy":
            this.startPaidScene("doggy", playerUuid);
            break;
         case "action.names.strip":
            this.startPaidScene("strip", playerUuid);
            break;
         case "action.names.dressup":
            this.setCurrentAction(Action.STRIP);
            break;
         case "action.names.followme":
            PlayerEntity player = SexMod.CLIENT.localPlayer();
            this.sendChatMessage(String.format(SexMod.CLIENT.translate("jenny.dialogue.followme"), player == null ? "" : player.getName().getString()));
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_HAPPYOH));
            break;
         case "action.names.stopfollowme":
            this.sendChatMessage(SexMod.CLIENT.translate("jenny.dialogue.bye"));
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_SADOH));
            break;
         case "action.names.gohome":
            this.sendChatMessage(SexMod.CLIENT.translate("jenny.dialogue.bye2"));
            break;
         default:
      }
   }

   private void startPaidScene(String scene, UUID playerUuid) {
      this.changeDataParameterFromClient("animationFollowUp", scene);
      this.triggerActionSync(true, true, playerUuid);
      SexMod.CLIENT.setMovementLocked(true);
   }

   @Override
   public void onArriveHome() {
      this.sendChatMessage("Alright, this is my new Home~");
      this.playSound(SoundHandler.GIRLS_JENNY_HAPPYOH[1]);
   }

   /** Server side: walk to the free side of the nearest bed, then get on it. */
   @Override
   public void goToSexBed() {
      BlockPos bedPos = this.getNearestBed();
      if (bedPos == null) {
         this.sendGirlChatMessage("no bed in sight...");
         this.setCurrentAction(Action.NULL);
         ModPackets.resetGirl(this);
         return;
      }
      Vec3d bedVec = Vec3d.of(bedPos);
      int side = GirlBedPlacement.findFreeSide(this, bedVec);
      if (side == -1) {
         this.sendGirlChatMessage("bed is obscured...");
         this.setCurrentAction(Action.NULL);
         ModPackets.resetGirl(this);
         return;
      }
      this.removeWanderGoals();
      Vec3d bedSide = bedVec.add(GirlBedPlacement.SIDE_OFFSETS[side][0]);
      this.setAnchored(false);
      this.setYawRotation(GirlBedPlacement.SIDE_YAWS[side]);
      this.setTargetPosition(bedSide);
      this.cameraYaw = this.getYawRotation();
      this.getNavigation().stop();
      this.getNavigation().startMovingTo(bedSide.x, bedSide.y, bedSide.z, 0.35);
      this.walkingToBed = true;
      this.bedWalkTicks = 0;
   }

   @Override
   public void setDismounted() {
      this.sceneEntryLerpActive = true;
      this.sceneEntryLerpTicks = 0;
   }

   @Override
   public void reinitTasks() {
      this.walkingToBed = false;
      this.sceneEntryLerpActive = false;
      this.snapInPending = false;
      this.resetWanderGoals();
   }

   // ----------------------------------------------------------- action flow

   @Override
   public void setCurrentAction(Action action) {
      Action current = this.getCurrentAction();
      // A finish cannot fall back into the loop it came from (late packets from the fast/slow toggle).
      if (current == Action.DOGGYCUM && (action == Action.DOGGYSLOW || action == Action.DOGGYFAST)) {
         return;
      }
      if (current == Action.CUMBLOWJOB && (action == Action.THRUSTBLOWJOB || action == Action.SUCKBLOWJOB)) {
         return;
      }
      if (current == Action.PAIZURI_CUM && (action == Action.PAIZURI_SLOW || action == Action.PAIZURI_FAST)) {
         return;
      }
      super.setCurrentAction(action);
      if (!this.getWorld().isClient && action != current && (current == Action.STARTBLOWJOB || current == Action.PAIZURI_START)) {
         PlayerEntity player = this.getInteractionPlayer();
         if (player != null) {
            Vec3d offset = VectorMath.rotateByYaw(new Vec3d(0.0, 0.0, 0.2), this.getYawRotation() + 180.0F);
            player.requestTeleport(player.getX() + offset.x, player.getY(), player.getZ() + offset.z);
         }
      }
   }

   @Override
   protected Action getCumAction(Action action) {
      if (action == Action.SUCKBLOWJOB || action == Action.THRUSTBLOWJOB) {
         this.positionPlayerRelative(0.0, 0.0, 0.0, 0.0F, 70.0F);
         return Action.CUMBLOWJOB;
      }
      if (action == Action.DOGGYSLOW || action == Action.DOGGYFAST) {
         return Action.DOGGYCUM;
      }
      if (action == Action.PAIZURI_FAST || action == Action.PAIZURI_SLOW) {
         return Action.PAIZURI_CUM;
      }
      return null;
   }

   @Override
   protected Action getNextAction(Action action) {
      switch (action) {
         case SUCKBLOWJOB:
            return Action.THRUSTBLOWJOB;
         case DOGGYSLOW:
            return Action.DOGGYFAST;
         case PAIZURI_SLOW:
            if (this.paizuriCameraAligned) {
               this.paizuriCameraAligned = false;
               this.positionPlayerRelative(0.0, 0.0, 0.2, 0.0F, 70.0F);
            }
            return Action.PAIZURI_FAST;
         default:
            return null;
      }
   }

   /**
    * Starts the scene picked in the menu. Runs on the server (scene entry
    * with the horny effect, which skips the payment animation) and on the
    * driving client (end of the payment / strip animations).
    */
   @Override
   protected void startActionFromHandState() {
      switch (this.dataTracker.get(GIRL_HAND_STATES)) {
         case "strip":
            this.resetGirlState();
            this.setCurrentAction(Action.STRIP);
            break;
         case "blowjob":
            this.setCurrentAction(Action.STARTBLOWJOB);
            break;
         case "boobjob":
            // Dressed: undress first; the end of that animation calls this method again.
            if (this.getOutfitIndex() != 0) {
               this.setCurrentAction(Action.STRIP);
               return;
            }
            this.setCurrentAction(Action.PAIZURI_START);
            break;
         case "doggy":
            if (this.getOutfitIndex() != 0) {
               this.setCurrentAction(Action.STRIP);
               this.resetGirlState();
               return;
            }
            if (this.getWorld().isClient) {
               this.resetCameraAndPhysics();
               ModPackets.sendGirlToSex(this.getGirlId());
            } else {
               this.setCurrentAction(Action.NULL);
               PlayerEntity player = this.getInteractionPlayer();
               ModPackets.resetGirl(this);
               if (player instanceof ServerPlayerEntity) {
                  ModPackets.resetPlayer((ServerPlayerEntity)player);
               }
               this.goToSexBed();
            }
            break;
         default:
      }
      if (this.getWorld().isClient) {
         this.changeDataParameterFromClient("animationFollowUp", "");
      } else {
         this.dataTracker.set(GIRL_HAND_STATES, "");
      }
   }

   // -------------------------------------------------------------- animation

   @Override
   protected PlayState animationPredicate(AnimationState<BaseGirlEntity> state) {
      Action action = this.getCurrentAction();
      switch (state.getController().getName()) {
         case "eyes":
            this.createAnimation(action == Action.NULL ? "animation.jenny.fhappy" : "animation.jenny.null", true, state);
            break;
         case "movement":
            if (action != Action.NULL) {
               this.createAnimation("animation.jenny.null", true, state);
            } else if (this.hasVehicle()) {
               this.createAnimation("animation.jenny.sit", true, state);
            } else if (this.isMovingHorizontally()) {
               switch (this.getWalkType()) {
                  case RUN:
                     this.createAnimation("animation.jenny.run", true, state);
                     break;
                  case FAST_WALK:
                     this.createAnimation("animation.jenny.fastwalk", true, state);
                     break;
                  case WALK:
                  default:
                     this.createAnimation("animation.jenny.walk", true, state);
               }
            } else {
               this.createAnimation("animation.jenny.idle", true, state);
            }
            break;
         case "action":
            String animation = this.actionAnimation(action);
            if (animation != null) {
               this.createAnimation("animation.jenny." + animation, isLoopingAction(action), state);
            }
            break;
         default:
      }
      return PlayState.CONTINUE;
   }

   private String actionAnimation(Action action) {
      switch (action) {
         case NULL:
            return "null";
         case STRIP:
            return "strip";
         case PAYMENT:
            return "payment";
         case STARTBLOWJOB:
            return "blowjobintro";
         case SUCKBLOWJOB:
            return "blowjobsuck";
         case THRUSTBLOWJOB:
            return "blowjobthrust";
         case CUMBLOWJOB:
            return "blowjobcum";
         case STARTDOGGY:
            return "doggygoonbed";
         case WAITDOGGY:
            return "doggywait";
         case DOGGYSTART:
            return "doggystart";
         case DOGGYSLOW:
            return "doggyslow";
         case DOGGYFAST:
            return "doggyfast_" + (this.doggyHard ? "hard" : "soft");
         case DOGGYCUM:
            return "doggycum";
         case PAIZURI_START:
            return "paizuri_start";
         case PAIZURI_SLOW:
            return "paizuri_slow";
         case PAIZURI_FAST:
            return "paizuri_fast";
         case PAIZURI_CUM:
            return "paizuri_cum";
         case ATTACK:
            return "attack" + this.nextAttack;
         case BOW:
            return "bowcharge";
         case RIDE:
            return "ride";
         case SIT:
            return "sit";
         case THROW_PEARL:
            return "throwpearl";
         case DOWNED:
            return "downed";
         case WAVE:
            return "wave";
         case WAVE_IDLE:
            return "wave_idle";
         default:
            return null;
      }
   }

   private static boolean isLoopingAction(Action action) {
      return Action.isAny(
         action,
         Action.NULL,
         Action.SUCKBLOWJOB,
         Action.THRUSTBLOWJOB,
         Action.WAITDOGGY,
         Action.DOGGYSLOW,
         Action.DOGGYFAST,
         Action.PAIZURI_SLOW,
         Action.PAIZURI_FAST,
         Action.RIDE,
         Action.SIT,
         Action.DOWNED,
         Action.WAVE,
         Action.WAVE_IDLE
      );
   }

   /**
    * {@code true} on the one client that reports this girl's keyframe-driven
    * state changes to the server: the scene's own player, or, outside a scene,
    * the client whose player is closest to her.
    */
   private boolean isDrivingClient() {
      if (!this.getWorld().isClient) {
         return false;
      }
      return this.getInteractionPlayerUUID() != null ? this.isControlledByLocalPlayer() : this.isLocalPlayerNearby();
   }

   private void say(String key) {
      this.sendGirlChatMessage(SexMod.CLIENT.translate(key));
   }

   /** A chat line spoken by the scene's player. */
   private void sayAsPlayer(String key) {
      PlayerEntity player = SexMod.CLIENT.localPlayer();
      if (player != null) {
         this.broadcastChatAround("<" + player.getName().getString() + "> " + SexMod.CLIENT.translate(key));
      }
   }

   private void restartIfFastKeyHeld() {
      if (this.isControlledByLocalPlayer() && SexMod.CLIENT.isFastKeyDown()) {
         this.resetAnimationControllerOffset();
      }
   }

   @Override
   protected void onSoundKeyframe(String sound) {
      boolean controlled = this.isControlledByLocalPlayer();
      boolean driving = this.isDrivingClient();
      switch (sound) {
         // ---- companion
         case "attackSound":
            this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
            break;
         case "attackDone":
            if (++this.nextAttack == 3) {
               this.nextAttack = 0;
            }
            if (driving) {
               this.setCurrentAction(Action.NULL);
            }
            break;
         case "pearl":
            if (driving) {
               ModPackets.sendCompanionHome(this.getGirlId());
            }
            break;

         // ---- undress / dress
         case "becomeNude":
            if (driving) {
               this.changeDataParameterFromClient("currentModel", this.getOutfitIndex() == 1 ? "0" : "1");
            }
            break;
         case "stripDone":
            if (driving) {
               if (!"boobjob".equals(this.dataTracker.get(GIRL_HAND_STATES))) {
                  this.resetCameraAndPhysics();
               }
               this.startActionFromHandState();
            }
            break;
         case "stripMSG1":
            this.say("jenny.dialogue.hihi");
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_GIGGLE));
            break;

         // ---- payment
         case "paymentMSG1":
            this.say("jenny.dialogue.huh");
            this.playSound(SoundHandler.GIRLS_JENNY_HUH[1]);
            break;
         case "paymentMSG2":
            this.playSoundAtVolume(SoundHandler.MISC_PLOB[0], 0.5F);
            if (controlled) {
               switch (this.dataTracker.get(GIRL_HAND_STATES)) {
                  case "strip":
                     this.sayAsPlayer("jenny.dialogue.showBobsandveganapls");
                     break;
                  case "blowjob":
                     this.sayAsPlayer("jenny.dialogue.giveblowjob");
                     break;
                  case "doggy":
                     this.sayAsPlayer("jenny.dialogue.givesex");
                     break;
                  case "boobjob":
                     this.sayAsPlayer("jenny.dialogue.givebooba");
                     break;
                  default:
               }
            }
            break;
         case "paymentMSG3":
            this.say("jenny.dialogue.hehe");
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_GIGGLE));
            break;
         case "paymentMSG4":
            this.playSoundAtVolume(SoundHandler.MISC_PLOB[0], 0.25F);
            break;
         case "paymentDone":
            if (driving) {
               this.startActionFromHandState();
            }
            break;
         case "sexUiOn":
            if (controlled) {
               SexMod.CLIENT.showHornyMeter();
            }
            break;

         // ---- blowjob intro
         case "bjiMSG1":
            this.say("jenny.dialogue.blowjobtext1");
            this.playSound(SoundHandler.GIRLS_JENNY_MMM[8]);
            this.cameraYaw = this.getYaw() + 180.0F;
            if (controlled) {
               SexMod.CLIENT.resetHornyMeter();
            }
            break;
         case "bjiMSG2":
            this.say("jenny.dialogue.blowjobtext2");
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[8]);
            break;
         case "bjiMSG3":
            this.say("jenny.dialogue.blowjobtext3");
            this.playSound(SoundHandler.GIRLS_JENNY_AFTERSESSIONMOAN[0]);
            break;
         case "bjiMSG4":
            this.playSound(SoundHandler.MISC_BELLJINGLE[0]);
            break;
         case "bjiMSG5":
            this.say("jenny.dialogue.blowjobtext4");
            this.playSoundAtVolume(SoundHandler.GIRLS_JENNY_HMPH[1], 0.5F);
            if (controlled) {
               SexMod.CLIENT.resetHornyMeter();
            }
            break;
         case "bjiMSG6":
            this.say("jenny.dialogue.blowjobtext5");
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[8]);
            break;
         case "bjiMSG7":
            this.say("jenny.dialogue.blowjobtext6");
            this.playSound(SoundHandler.GIRLS_JENNY_GIGGLE[4]);
            break;
         case "bjiMSG8":
            if (controlled) {
               this.sayAsPlayer("jenny.dialogue.blowjobtext7");
            }
            this.playSoundAtVolume(SoundHandler.MISC_PLOB[0], 0.5F);
            break;
         case "bjiMSG9":
            this.say("jenny.dialogue.blowjobtext8");
            this.playSound(SoundHandler.GIRLS_JENNY_GIGGLE[2]);
            break;
         case "bjiMSG10":
            if (controlled) {
               this.positionPlayerRelative(-0.65, -0.8, -0.25, 60.0F, -3.0F);
            }
            break;
         case "bjiMSG11":
            this.restartIfFastKeyHeld();
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_LIPSOUND));
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "bjiMSG12":
            if (this.random.nextInt(5) == 0) {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_BJMOAN));
            }
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_LIPSOUND));
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "bjiDone":
            if (controlled) {
               this.setCurrentAction(Action.SUCKBLOWJOB);
               SexMod.CLIENT.showHornyMeter();
            }
            break;

         // ---- blowjob loops and finish
         case "bjtMSG1":
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MMM));
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_LIPSOUND));
            this.addToHornyMeterIfControlled(0.04);
            break;
         case "bjtDone":
            if (controlled) {
               this.setCurrentAction(Action.SUCKBLOWJOB);
            }
            break;
         case "bjtReady":
         case "paizuriReady":
            this.restartIfFastKeyHeld();
            break;
         case "bjcMSG1":
            this.playSound(SoundHandler.GIRLS_JENNY_BJMOAN[1]);
            break;
         case "bjcMSG2":
            this.playSound(SoundHandler.GIRLS_JENNY_BJMOAN[7]);
            if (controlled) {
               SexMod.CLIENT.hideHornyMeter();
            }
            break;
         case "bjcMSG3":
            this.playSound(SoundHandler.GIRLS_JENNY_AFTERSESSIONMOAN[1]);
            break;
         case "bjcMSG4":
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[0]);
            break;
         case "bjcMSG5":
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[1]);
            break;
         case "bjcMSG6":
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[2]);
            break;
         case "bjcMSG7":
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[3]);
            break;
         case "bjcBlackScreen":
            if (controlled) {
               SexMod.CLIENT.playBlackScreenTransition();
            }
            break;
         case "bjcDone":
         case "paizuri_cumDone":
         case "doggyCumDone":
            if (controlled) {
               SexMod.CLIENT.resetHornyMeter();
               SexMod.CLIENT.hideHornyMeter();
               this.paizuriCameraAligned = false;
               this.resetCameraAndPhysics();
            }
            break;

         // ---- bed scene
         case "doggyGoOnBedMSG1":
            this.playSound(SoundHandler.MISC_BEDRUSTLE[0]);
            this.cameraYaw = this.getYaw();
            break;
         case "doggyGoOnBedMSG2":
            this.sendChatMessage(SexMod.CLIENT.translate("jenny.dialogue.doggytext1"));
            this.playSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING[9]);
            break;
         case "doggyGoOnBedMSG3":
            this.sendChatMessage(SexMod.CLIENT.translate("jenny.dialogue.doggytext2"));
            this.playSound(SoundHandler.GIRLS_JENNY_GIGGLE[0]);
            break;
         case "doggyGoOnBedMSG4":
            this.playSoundAtVolume(SoundHandler.MISC_SLAP[0], 0.75F);
            break;
         case "doggyGoOnBedDone":
            if (driving) {
               ModPackets.sendSetPlayerForGirl(this.getGirlId());
               this.setCurrentAction(Action.WAITDOGGY);
            }
            break;
         case "doggystartMSG1":
            this.playSound(SoundHandler.MISC_TOUCH[0]);
            break;
         case "doggystartMSG2":
            this.playSound(SoundHandler.MISC_TOUCH[1]);
            break;
         case "doggystartMSG3":
            this.playSoundAtVolume(SoundHandler.MISC_BEDRUSTLE[1], 0.5F);
            break;
         case "doggystartMSG4":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_SMALLINSERTS));
            this.playSound(SoundHandler.GIRLS_JENNY_MMM[1]);
            if (controlled) {
               SexMod.CLIENT.resetHornyMeter();
            }
            break;
         case "doggystartMSG5":
            this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.MISC_POUNDING), 0.33F);
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MOAN));
            break;
         case "doggystartDone":
            if (controlled) {
               this.setCurrentAction(Action.DOGGYSLOW);
               SexMod.CLIENT.showHornyMeter();
            }
            break;
         case "doggyslowMSG1":
            this.doggyHard = false;
            this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.MISC_POUNDING), 0.33F);
            if (this.random.nextInt(4) != 0) {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING));
            } else if (this.random.nextBoolean()) {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MMM));
            } else {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MOAN));
            }
            this.addToHornyMeterIfControlled(0.00666);
            break;
         case "doggyslowMSG2":
            this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_LIGHTBREATHING), 0.5F);
            break;
         case "doggyfastMSG1":
            this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.MISC_POUNDING), 0.75F);
            this.addToHornyMeterIfControlled(0.02);
            if (++this.fastMoanCount % 2 != 0) {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_AHH));
            } else if (this.random.nextBoolean()) {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MOAN));
            } else {
               this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING));
            }
            break;
         case "doggyfastReady":
            if (controlled && SexMod.CLIENT.isFastKeyDown()) {
               this.resetAnimationControllerOffset();
               this.doggyHard = true;
            }
            break;
         case "doggyfastDone":
            this.doggyHard = false;
            if (controlled) {
               this.setCurrentAction(Action.DOGGYSLOW);
            }
            break;
         case "doggycumMSG1":
            this.playSoundAtVolume(SoundHandler.MISC_CUMINFLATION[0], 2.0F);
            this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.MISC_POUNDING), 2.0F);
            this.playSound(SoundHandler.randomSound(SoundHandler.GIRLS_JENNY_MOAN));
            if (controlled) {
               SexMod.CLIENT.hideHornyMeter();
            }
            break;
         case "doggycumMSG2":
            this.playSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING[4]);
            break;
         case "doggycumMSG3":
            this.playSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING[5]);
            break;
         case "doggycumMSG4":
            this.playSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING[6]);
            break;
         case "doggycumMSG5":
            this.playSound(SoundHandler.GIRLS_JENNY_HEAVYBREATHING[7]);
            break;

         // ---- paizuri
         case "boobjob_camera":
            if (controlled) {
               PlayerEntity player = SexMod.CLIENT.localPlayer();
               if (player != null) {
                  this.cameraYaw = player.getYaw();
               }
               if (!this.paizuriCameraAligned) {
                  this.paizuriCameraAligned = true;
                  this.positionPlayerRelative(-0.7, -0.6, 0.2, 60.0F, -3.0F);
               }
            }
            break;
         case "paizuri_startDone":
            if (controlled) {
               this.setCurrentAction(Action.PAIZURI_SLOW);
               SexMod.CLIENT.resetHornyMeter();
               SexMod.CLIENT.showHornyMeter();
            }
            break;
         case "paizuriFastMSG1":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_POUNDING));
            this.playSound(SoundHandler.randomSound(this.random.nextBoolean() ? SoundHandler.GIRLS_JENNY_MMM : SoundHandler.GIRLS_JENNY_AHH));
            this.addToHornyMeterIfControlled(0.04);
            break;
         case "paizuriSlowMSG1":
         case "paizuriStartMSG1":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_POUNDING));
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "paizuri_fastDone":
            if (controlled) {
               this.setCurrentAction(Action.PAIZURI_SLOW);
               if (!this.paizuriCameraAligned) {
                  this.paizuriCameraAligned = true;
                  this.positionPlayerRelative(-0.7, -0.6, 0.2, 60.0F, -3.0F);
               }
            }
            break;
         case "paizuri_startStep":
            BlockState below = this.getWorld().getBlockState(this.getBlockPos().down());
            this.playSound(below.getSoundGroup().getStepSound());
            break;
         case "paizuri_cumStart":
            if (controlled) {
               SexMod.CLIENT.hideHornyMeter();
               if (!this.paizuriCameraAligned) {
                  this.positionPlayerRelative(-0.7, -0.6, 0.2, 60.0F, -3.0F);
               }
            }
            break;
         default:
      }
   }
}
