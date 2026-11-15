package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.entity.api.IEllie;
import com.trolmastercard.sexmod.networking.ModPackets;
import com.trolmastercard.sexmod.potion.HornyPotion;
import com.trolmastercard.sexmod.util.SoundHandler;
import com.trolmastercard.sexmod.util.VectorMath;
import java.util.UUID;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Ellie. She has two kinds of scene.
 * <p>
 * <b>Bed scenes</b> start when she is under the horny effect (a splash potion)
 * with a player within 10 blocks. The server walks her through it with the
 * tick counters below, in this order:
 * dash to the player, hug, the player picks cowgirl or missionary in the menu,
 * she walks to the end of the nearest bed and sits down, and the scene starts
 * when that player steps up to her. From there the sound keyframes of
 * {@code ellie.animation.json} drive it, as for Jenny.
 * <p>
 * <b>The carry scene</b> ("Face fuck" in her menu while she is dressed) starts
 * where she stands, through {@link #setDismounted()}.
 * <p>
 * Differences from 1.12.2: only the player she hugged can start the bed scene
 * (there, whoever came close first); she gives up if nobody answers the menu,
 * if she cannot reach the bed, or if nobody comes to the bed; and the player
 * is lifted for the second half of the carry intro, which the 1.12.2 source
 * has the code for but never calls.
 */
public class EllieEntity extends AbstractGirlNpcEntity implements IEllie {
   private static final int DASH_TICKS = 16;
   private static final int HUG_TICKS = 150;
   private static final int HUG_SELECTED_TICKS = 79;
   private static final int SIT_DOWN_TICKS = 109;
   private static final int SCENE_START_DELAY_TICKS = 20;
   private static final int CARRY_LIFT_TICKS = 110;
   /** How long she waits for a menu choice, and for the player to come to the bed. */
   private static final int WAIT_TIMEOUT_TICKS = 1200;
   private static final int BED_WALK_TIMEOUT_TICKS = 600;

   // Server side countdowns; -1 = not running.
   private int dashTicks = -1;
   private int hugTicks = -1;
   private int hugSelectedTicks = -1;
   private int sitDownTicks = -1;
   private int sceneStartTicks = -1;
   private int carryIntroTicks = -1;
   private boolean walkingToBed;
   private int bedWalkTicks;
   private GirlBedPlacement.Spot bedSpot;
   private boolean loadCleanupDone;

   // Client side.
   private Action lastClientAction = Action.NULL;
   private boolean bedTransitionShown;
   private int carryVariant = 1;
   private boolean skipNextFastMoan;

   public EllieEntity(EntityType<? extends EllieEntity> type, World world) {
      super(type, world);
   }

   @Override
   public String getDisplayNameText() {
      return "Ellie";
   }

   @Override
   protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
      return 1.9F;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundHandler.randomSound(SoundHandler.GIRLS_ELLIE_SIGH);
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   /** She is taller than two blocks and ducks where the ceiling is lower. */
   private boolean isUnderLowCeiling() {
      return !this.getWorld().getBlockState(this.getBlockPos().up(2)).isAir();
   }

   // ------------------------------------------------------------ server tick

   @Override
   protected void mobTick() {
      super.mobTick();
      if (!this.loadCleanupDone) {
         // Scene stages are not saved; one that was cut off by a save must not leave her floating.
         this.loadCleanupDone = true;
         this.noClip = false;
         this.setNoGravity(false);
      }
      this.tickHornyPotion();
      this.tickDash();
      this.tickHug();
      this.tickHugSelected();
      this.tickWalkToBed();
      this.tickSitDown();
      this.tickBedWait();
      this.tickSceneStart();
      this.tickCarryIntro();
   }

   private ServerPlayerEntity getScenePlayer() {
      PlayerEntity player = this.getInteractionPlayer();
      return player instanceof ServerPlayerEntity ? (ServerPlayerEntity)player : null;
   }

   /** Ends whatever stage the scene is in and frees her and the player. */
   private void abortScene() {
      ServerPlayerEntity player = this.getScenePlayer();
      if (player != null) {
         ModPackets.resetPlayer(player);
      }
      this.setCurrentAction(Action.NULL);
      this.dataTracker.set(GIRL_HAND_STATES, "");
      ModPackets.resetGirl(this);
   }

   private void tickHornyPotion() {
      if (!this.hasStatusEffect(HornyPotion.HORNY) || this.getCurrentAction() != Action.NULL || this.getInteractionPlayerUUID() != null) {
         return;
      }
      PlayerEntity player = this.getWorld().getClosestPlayer(this, 10.0);
      if (!(player instanceof ServerPlayerEntity) || player.isSpectator() || BaseGirlEntity.getActiveScene(player.getUuid(), false) != null) {
         return;
      }
      this.removeStatusEffect(HornyPotion.HORNY);
      this.setInteractionPlayerUUID(player.getUuid());
      this.setYawRotation((float)(Math.atan2(this.getZ() - player.getZ(), this.getX() - player.getX()) * (180.0 / Math.PI)));
      this.setTargetPosition(this.getPos());
      this.dataTracker.set(IS_ANCHORED, true);
      this.setCurrentAction(Action.DASH);
      this.dashTicks = DASH_TICKS;
      this.setNoGravity(true);
      this.noClip = true;
      this.removeWanderGoals();
      this.getNavigation().stop();
      ModPackets.sendPlayerMovement((ServerPlayerEntity)player, false);
   }

   /** End of the dash: she is behind the player and hugs them. */
   private void tickDash() {
      if (this.dashTicks < 0 || --this.dashTicks > 0) {
         return;
      }
      this.dashTicks = -1;
      ServerPlayerEntity player = this.getScenePlayer();
      if (player == null) {
         this.abortScene();
         return;
      }
      this.setTargetPosition(player.getPos().add(VectorMath.rotateByYaw(new Vec3d(0.0, 0.0, -0.5), player.getYaw())));
      this.setYawRotation(player.getYaw());
      this.setCurrentAction(Action.HUG);
      this.hugTicks = HUG_TICKS;
      ModPackets.enterScene(player);
   }

   private void tickHug() {
      if (this.hugTicks >= 0 && --this.hugTicks <= 0) {
         this.hugTicks = -1;
         if (this.getCurrentAction() == Action.HUG) {
            this.setCurrentAction(Action.HUGIDLE);
         }
      }
      // The menu was closed without a choice, or the player is gone.
      if (this.getCurrentAction() == Action.HUGIDLE && this.getActionTicks() > WAIT_TIMEOUT_TICKS) {
         this.abortScene();
      }
   }

   /** End of the "follow me" animation: let the player go and look for a bed. */
   private void tickHugSelected() {
      if (this.hugSelectedTicks < 0 || --this.hugSelectedTicks > 0) {
         return;
      }
      this.hugSelectedTicks = -1;
      if (this.getCurrentAction() != Action.HUGSELECTED) {
         return;
      }
      this.bedSpot = GirlBedPlacement.findBedEnd(this);
      if (this.bedSpot == null) {
         this.sendGirlChatMessage("no bed in sight...");
         this.getWorld().playSound(null, this.getBlockPos(), SoundHandler.GIRLS_ELLIE_SIGH[0], this.getSoundCategory(), 6.0F, 1.0F);
         this.abortScene();
         return;
      }
      ServerPlayerEntity player = this.getScenePlayer();
      if (player != null) {
         ModPackets.resetPlayer(player);
      }
      this.dataTracker.set(IS_ANCHORED, false);
      this.setCurrentAction(Action.NULL);
      this.noClip = false;
      this.setNoGravity(false);
      this.walkingToBed = true;
      this.bedWalkTicks = 0;
   }

   private void tickWalkToBed() {
      if (!this.walkingToBed) {
         return;
      }
      Vec3d spot = this.bedSpot.pos;
      if (spot.distanceTo(this.getPos()) > 1.0) {
         if (this.bedWalkTicks > BED_WALK_TIMEOUT_TICKS) {
            this.abortScene();
            return;
         }
         // Start walking, and re-path now and then in case she got stuck on a door or a corner.
         if (this.bedWalkTicks++ % 40 == 0) {
            this.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 0.35);
         }
         return;
      }
      this.walkingToBed = false;
      this.getNavigation().stop();
      this.setVelocity(Vec3d.ZERO);
      this.setTargetPosition(spot);
      this.setYawRotation(this.bedSpot.yaw);
      this.setCurrentAction(Action.SITDOWN);
      this.dataTracker.set(IS_ANCHORED, true);
      this.sitDownTicks = SIT_DOWN_TICKS;
      this.noClip = true;
      this.setNoGravity(true);
      this.bedSpot = null;
   }

   private void tickSitDown() {
      if (this.sitDownTicks >= 0 && --this.sitDownTicks <= 0) {
         this.sitDownTicks = -1;
         if (this.getCurrentAction() == Action.SITDOWN) {
            this.setCurrentAction(Action.SITDOWNIDLE);
         }
      }
   }

   /** Sitting on the bed: the scene starts shortly after her player steps up to her. */
   private void tickBedWait() {
      if (this.getCurrentAction() != Action.SITDOWNIDLE || this.sceneStartTicks >= 0) {
         return;
      }
      if (this.getActionTicks() > WAIT_TIMEOUT_TICKS) {
         this.abortScene();
         return;
      }
      PlayerEntity player = this.getInteractionPlayerUUID() != null ? this.getInteractionPlayer() : this.getWorld().getClosestPlayer(this, 10.0);
      if (player instanceof ServerPlayerEntity && this.distanceTo(player) <= 1.5F) {
         this.setInteractionPlayerUUID(player.getUuid());
         this.sceneStartTicks = SCENE_START_DELAY_TICKS;
      }
   }

   private void tickSceneStart() {
      if (this.sceneStartTicks >= 0 && --this.sceneStartTicks <= 0) {
         this.sceneStartTicks = -1;
         if (this.getCurrentAction() == Action.SITDOWNIDLE) {
            this.startActionFromHandState();
         }
      }
   }

   /**
    * Half way through the carry intro she has lifted the player; from then on
    * the player's eyes are where the carried body's head is.
    */
   private void tickCarryIntro() {
      if (this.carryIntroTicks < 0 || ++this.carryIntroTicks < CARRY_LIFT_TICKS) {
         return;
      }
      this.carryIntroTicks = -1;
      ServerPlayerEntity player = this.getScenePlayer();
      if (player == null || this.getCurrentAction() != Action.CARRY_INTRO) {
         return;
      }
      float yaw = this.getYawRotation();
      Vec3d pos = this.getTargetPosition().add(VectorMath.rotateByYaw(new Vec3d(0.0, 2.5625 - player.getStandingEyeHeight(), -0.3125), 180.0F + yaw));
      player.setNoGravity(true);
      ModPackets.teleportPlayer(player, pos, yaw + 180.0F, player.getPitch());
   }

   /** Server side: the bed scene picked during the hug begins. */
   @Override
   protected void startActionFromHandState() {
      if (this.getWorld().isClient) {
         return;
      }
      String scene = this.dataTracker.get(GIRL_HAND_STATES);
      boolean missionary = "Missionary".equals(scene);
      if (!missionary && !"cowgirl".equals(scene)) {
         return;
      }
      ServerPlayerEntity player = this.getScenePlayer();
      if (player == null) {
         this.abortScene();
         return;
      }
      this.dataTracker.set(GIRL_HAND_STATES, "");
      this.dataTracker.set(OUTFIT_INDEX, 0);
      this.setCurrentAction(missionary ? Action.MISSIONARY_START : Action.COWGIRLSTART);
      // The player lies where the animation shows their body, which is partly
      // inside the bed, so they must not fall or be pushed out during the scene.
      float yaw = missionary ? this.getYawRotation() : this.getYawRotation() + 180.0F;
      Vec3d offset = missionary ? new Vec3d(0.0, 0.0, 0.1) : new Vec3d(0.0, 1.0 - player.getStandingEyeHeight(), -1.8125);
      Vec3d pos = this.getTargetPosition().add(VectorMath.rotateByYaw(offset, yaw));
      player.setNoGravity(true);
      ModPackets.teleportPlayer(player, pos, yaw, player.getPitch());
      ModPackets.enterScene(player);
   }

   /** Server side: start of the carry scene, called once the player is bound and placed in front of her. */
   @Override
   public void setDismounted() {
      ServerPlayerEntity player = this.getScenePlayer();
      if (player == null) {
         this.abortScene();
         return;
      }
      this.setYawRotation(player.getYaw() - 180.0F);
      this.setCurrentAction(Action.CARRY_INTRO);
      this.dataTracker.set(IS_ANCHORED, true);
   }

   @Override
   public void reinitTasks() {
      this.dashTicks = -1;
      this.hugTicks = -1;
      this.hugSelectedTicks = -1;
      this.sitDownTicks = -1;
      this.sceneStartTicks = -1;
      this.carryIntroTicks = -1;
      this.walkingToBed = false;
      this.bedSpot = null;
      this.resetWanderGoals();
   }

   @Override
   public void setCurrentAction(Action action) {
      Action current = this.getCurrentAction();
      // A finish cannot fall back into the loop it came from (late packets from the fast/slow toggle).
      if (current == Action.MISSIONARY_CUM && (action == Action.MISSIONARY_FAST || action == Action.MISSIONARY_SLOW)) {
         return;
      }
      if (current == Action.COWGIRLCUM && (action == Action.COWGIRLSLOW || action == Action.COWGIRLFAST)) {
         return;
      }
      if (current == Action.CARRY_CUM && (action == Action.CARRY_SLOW || action == Action.CARRY_FAST)) {
         return;
      }
      if (!this.getWorld().isClient) {
         if (action == Action.HUGSELECTED) {
            // Only out of the hug, so a stray packet cannot start the walk to the bed.
            if (current != Action.HUGIDLE && current != Action.HUG) {
               return;
            }
            this.hugTicks = -1;
            this.hugSelectedTicks = HUG_SELECTED_TICKS;
         } else if (action == Action.CARRY_INTRO) {
            this.carryIntroTicks = 0;
         }
      }
      super.setCurrentAction(action);
   }

   // ------------------------------------------------------------ client tick

   @Override
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient) {
         return;
      }
      Action action = this.getCurrentAction();
      boolean controlled = this.isControlledByLocalPlayer();
      if (action != this.lastClientAction) {
         this.lastClientAction = action;
         this.bedTransitionShown = false;
         if (action == Action.CARRY_SLOW && controlled) {
            SexMod.CLIENT.showHornyMeter();
         }
      }
      // The screen fades to black while the player gets onto the bed.
      if (action == Action.SITDOWNIDLE && controlled && !this.bedTransitionShown) {
         PlayerEntity player = SexMod.CLIENT.localPlayer();
         if (player != null && this.distanceTo(player) <= 1.5F) {
            this.bedTransitionShown = true;
            SexMod.CLIENT.playBlackScreenTransition();
         }
      }
   }

   // ------------------------------------------------------------ interaction

   @Override
   protected ActionResult interactMob(PlayerEntity player, Hand hand) {
      if (hand != Hand.MAIN_HAND) {
         return ActionResult.PASS;
      }
      if (this.getWorld().isClient && !this.openInteractionMenu(player)) {
         this.sendChatMessage(SexMod.CLIENT.translate("ellie.dialogue.busy"));
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
      SexMod.CLIENT.openActionMenu(this, new String[]{this.getOutfitIndex() == 0 ? "action.names.dressup" : "Face fuck"}, null);
      return true;
   }

   /** Client side: the menu shown at the end of the hug. */
   private void openBedSceneMenu() {
      SexMod.CLIENT.openActionMenu(this, new String[]{"action.names.cowgirl", "action.names.missionary"}, null);
   }

   @Override
   public void doAction(String action, UUID playerUuid) {
      super.doAction(action, playerUuid);
      switch (action) {
         case "action.names.missionary":
            this.changeDataParameterFromClient("animationFollowUp", "Missionary");
            this.setCurrentAction(Action.HUGSELECTED);
            break;
         case "action.names.cowgirl":
            this.changeDataParameterFromClient("animationFollowUp", "cowgirl");
            this.setCurrentAction(Action.HUGSELECTED);
            break;
         case "action.names.dressup":
         case "action.names.strip":
            this.changeDataParameterFromClient("animationFollowUp", "");
            this.setCurrentAction(Action.STRIP);
            break;
         case "Face fuck":
            this.triggerActionSync(true, true, playerUuid);
            SexMod.CLIENT.setMovementLocked(true);
            break;
         default:
      }
   }

   @Override
   public void onArriveHome() {
      this.sendChatMessage("Okay, I will be residing here then..");
      this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_HUH[0], 6.0F);
   }

   @Override
   public void goHome() {
      super.goHome();
      this.sendChatMessage("stay safe darling~");
      this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_SIGH[1], 6.0F);
   }

   // ----------------------------------------------------------- action flow

   @Override
   protected Action getCumAction(Action action) {
      if (action == Action.COWGIRLFAST || action == Action.COWGIRLSLOW) {
         return Action.COWGIRLCUM;
      }
      if (action == Action.MISSIONARY_FAST || action == Action.MISSIONARY_SLOW) {
         return Action.MISSIONARY_CUM;
      }
      return action == Action.CARRY_SLOW || action == Action.CARRY_FAST ? Action.CARRY_CUM : null;
   }

   @Override
   protected Action getNextAction(Action action) {
      if (action == Action.COWGIRLSLOW) {
         return Action.COWGIRLFAST;
      }
      if (action == Action.MISSIONARY_SLOW) {
         return Action.MISSIONARY_FAST;
      }
      return action == Action.CARRY_SLOW ? Action.CARRY_FAST : null;
   }

   // -------------------------------------------------------------- animation

   @Override
   protected PlayState animationPredicate(AnimationState<BaseGirlEntity> state) {
      Action action = this.getCurrentAction();
      switch (state.getController().getName()) {
         case "eyes":
            this.createAnimation(action == Action.NULL ? "animation.ellie.eyes" : "animation.ellie.null", true, state);
            break;
         case "movement":
            if (action != Action.NULL) {
               this.createAnimation("animation.ellie.null", true, state);
            } else if (this.hasVehicle()) {
               this.createAnimation("animation.ellie.sit", true, state);
            } else if (!this.isMovingHorizontally()) {
               this.createAnimation(this.isUnderLowCeiling() ? "animation.ellie.crouchidle" : "animation.ellie.idle", true, state);
            } else if (this.isUnderLowCeiling()) {
               this.createAnimation("animation.ellie.crouchwalk", true, state);
            } else {
               switch (this.getWalkType()) {
                  case RUN:
                     this.createAnimation("animation.ellie.run", true, state);
                     break;
                  case FAST_WALK:
                     this.createAnimation("animation.ellie.fastwalk", true, state);
                     break;
                  case WALK:
                  default:
                     this.createAnimation("animation.ellie.walk", true, state);
               }
            }
            break;
         case "action":
            String animation = this.actionAnimation(action);
            if (animation != null) {
               this.createAnimation("animation.ellie." + animation, isLoopingAction(action), state);
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
         case DASH:
            return "dash";
         case HUG:
            return "hug";
         case HUGIDLE:
            return "hugidle";
         case HUGSELECTED:
            return "hugselected";
         case SITDOWN:
            return "sitdown";
         case SITDOWNIDLE:
            return "sitdownidle";
         case COWGIRLSTART:
            return "cowgirlstart";
         case COWGIRLSLOW:
            return "cowgirlslow2";
         case COWGIRLFAST:
            return "cowgirlfast";
         case COWGIRLCUM:
            return "cowgirlcum";
         case MISSIONARY_START:
            return "missionary_start";
         case MISSIONARY_SLOW:
            return "missionary_slow";
         case MISSIONARY_FAST:
            return "missionary_fast";
         case MISSIONARY_CUM:
            return "missionary_cum";
         case CARRY_INTRO:
            return "carry_intro";
         case CARRY_SLOW:
            return "carry_slow" + this.carryVariant;
         case CARRY_FAST:
            return "carry_fast";
         case CARRY_CUM:
            return "carry_cum";
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
         default:
            return null;
      }
   }

   /** The 1.12.2 loop flags; the other actions hold their last frame until a keyframe or a timer moves on. */
   private static boolean isLoopingAction(Action action) {
      return Action.isAny(
         action,
         Action.NULL,
         Action.HUGIDLE,
         Action.SITDOWNIDLE,
         Action.COWGIRLSLOW,
         Action.COWGIRLFAST,
         Action.COWGIRLCUM,
         Action.MISSIONARY_SLOW,
         Action.MISSIONARY_FAST,
         Action.CARRY_SLOW,
         Action.CARRY_FAST,
         Action.CARRY_CUM,
         Action.RIDE,
         Action.SIT,
         Action.DOWNED
      );
   }

   /** See {@code JennyEntity#isDrivingClient()}. */
   private boolean isDrivingClient() {
      if (!this.getWorld().isClient) {
         return false;
      }
      return this.getInteractionPlayerUUID() != null ? this.isControlledByLocalPlayer() : this.isLocalPlayerNearby();
   }

   private void say(String key) {
      this.sendGirlChatMessage(SexMod.CLIENT.translate(key));
   }

   private void pound() {
      this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.MISC_POUNDING), 0.75F);
   }

   private void ahh() {
      this.playSoundAtVolume(SoundHandler.randomSound(SoundHandler.GIRLS_ELLIE_AHH), 6.0F);
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
               this.resetCameraAndPhysics();
            }
            break;

         // ---- hug
         case "hugMSG2":
            this.sendGirlChatMessage("Hmm...");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_HMPH[3], 6.0F);
            break;
         case "hugMSG3":
            this.sendGirlChatMessage("Hey!");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_HUH[1], 1.0F);
            break;
         case "hugMSG4":
            this.say("ellie.dialogue.mommyhorny");
            this.playRandomSoundAtVolume(SoundHandler.GIRLS_ELLIE_MOMMYHORNY, 0.5F);
            break;
         case "hugMSG5":
            this.say("ellie.dialogue.whattodo");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_HUH[1], 6.0F);
            break;
         case "hugDone":
            if (controlled) {
               this.openBedSceneMenu();
            }
            break;
         case "hugselectedMSG1":
            this.say("ellie.dialogue.iknow");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_HMPH[3], 6.0F);
            break;
         case "hugselectedMSG2":
            this.say("ellie.dialogue.followmedarling");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_GIGGLE[3], 6.0F);
            break;
         case "sitdownMSG1":
            this.playRandomSoundAtVolume(SoundHandler.GIRLS_ELLIE_COMETOMOMMY, 0.5F);
            this.say("ellie.dialogue.cometomommy");
            break;

         // ---- cowgirl
         case "cowgirlStartMSG0":
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_GIGGLE[4], 6.0F);
            break;
         case "cowgirlStartMSG1":
            if (controlled) {
               this.sendChatMessage(SexMod.CLIENT.translate("ellie.dialogue.like"));
               SexMod.CLIENT.resetHornyMeter();
            }
            break;
         case "cowgirlStartMSG2":
            this.ahh();
            this.pound();
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "cowgirlStartDone":
            if (controlled) {
               this.setCurrentAction(Action.COWGIRLSLOW);
               SexMod.CLIENT.showHornyMeter();
            }
            break;
         case "cowgirlfastMSG1":
            if (this.skipNextFastMoan) {
               this.skipNextFastMoan = false;
            } else {
               this.ahh();
            }
            this.pound();
            this.addToHornyMeterIfControlled(0.04);
            break;
         case "cowgirlfastDone":
            if (controlled && !SexMod.CLIENT.isFastKeyDown()) {
               this.setCurrentAction(Action.COWGIRLSLOW);
            }
            break;
         case "cowgirlfastdomMSG1":
            this.pound();
            this.addToHornyMeterIfControlled(0.2);
            break;
         case "cowgirlcumMSG1":
            this.ahh();
            this.pound();
            break;
         case "cowgirlcumMSG2":
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_MOAN[5], 3.0F);
            this.pound();
            break;
         case "cowgirlcumMSG3":
            this.pound();
            break;
         case "cowgirlcumMSG4":
            if (controlled) {
               SexMod.CLIENT.hideHornyMeter();
            }
            break;
         case "cowgirlcumMSG5":
         case "missionary_cumMSG2":
            this.playRandomSoundAtVolume(SoundHandler.GIRLS_ELLIE_GOODBOY, 0.5F);
            if (controlled) {
               this.sendChatMessage(SexMod.CLIENT.translate("ellie.dialogue.goodboy"));
            }
            break;
         case "cowgirlcumMSG6":
         case "blackScreen":
            if (controlled) {
               SexMod.CLIENT.playBlackScreenTransition();
            }
            break;
         case "missionary_cumDone":
         case "cowgirlcumDone":
         case "carry_cumDone":
            if (controlled) {
               SexMod.CLIENT.resetHornyMeter();
               SexMod.CLIENT.hideHornyMeter();
               this.resetCameraAndPhysics();
            }
            break;

         // ---- missionary
         case "openSexUi":
            if (controlled) {
               SexMod.CLIENT.showHornyMeter();
            }
            break;
         case "missionary_slowMSG1":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_POUNDING));
            // One thrust in four gets a moan, the others a gasp.
            this.playSoundAtVolume(SoundHandler.randomSound(this.random.nextInt(4) == 0 ? SoundHandler.GIRLS_ELLIE_MOAN : SoundHandler.GIRLS_ELLIE_AHH), 6.0F);
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "missionary_fastMSG1":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_POUNDING));
            // The other way round when fast.
            this.playSoundAtVolume(SoundHandler.randomSound(this.random.nextInt(4) == 0 ? SoundHandler.GIRLS_ELLIE_AHH : SoundHandler.GIRLS_ELLIE_MOAN), 6.0F);
            this.addToHornyMeterIfControlled(0.05);
            break;
         case "missionary_startDone":
            if (controlled) {
               this.setCurrentAction(Action.MISSIONARY_SLOW);
               SexMod.CLIENT.showHornyMeter();
            }
            break;
         case "missionary_fastDone":
            if (controlled && !SexMod.CLIENT.isFastKeyDown()) {
               this.setCurrentAction(Action.MISSIONARY_SLOW);
            }
            break;
         case "bedRustle":
            this.playSound(SoundHandler.randomSound(SoundHandler.MISC_POUNDING));
            this.playSound(SoundHandler.MISC_BEDRUSTLE[0]);
            break;
         case "bedRustle1":
            this.playSound(SoundHandler.MISC_BEDRUSTLE[1]);
            break;
         case "missionary_cumMSG1":
            this.ahh();
            break;

         // ---- carry
         case "carry_introMSG1":
            this.sendChatMessage("I'm hungry..");
            this.playRandomSoundAtVolume(SoundHandler.GIRLS_ELLIE_HMPH, 6.0F);
            break;
         case "carry_introMSG2":
            this.sendChatMessage("heh~");
            this.playSoundAtVolume(SoundHandler.GIRLS_ELLIE_GIGGLE[3], 6.0F);
            break;
         case "lipsound":
            this.playRandomSound(SoundHandler.GIRLS_ELLIE_LIPSOUND);
            this.addToHornyMeterIfControlled(0.02);
            break;
         case "cum":
            this.playRandomSoundAtVolume(SoundHandler.MISC_INSERTS, 6.0F);
            this.playRandomSound(SoundHandler.MISC_POUNDING);
            break;
         case "pound":
            this.playRandomSound(SoundHandler.MISC_POUNDING);
            this.addToHornyMeterIfControlled(0.04);
            break;
         case "carry_slowDone":
            // Each pass of the slow loop plays a different one of its four variations.
            int previous = this.carryVariant;
            do {
               this.carryVariant = this.random.nextInt(4) + 1;
            } while (this.carryVariant == previous);
            break;
         case "carry_fastDone":
            if (controlled && !SexMod.CLIENT.isFastKeyDown()) {
               this.setCurrentAction(Action.CARRY_SLOW);
            }
            break;
         default:
      }
   }
}
