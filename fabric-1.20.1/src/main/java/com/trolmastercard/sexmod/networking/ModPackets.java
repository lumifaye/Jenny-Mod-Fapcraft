package com.trolmastercard.sexmod.networking;

import com.trolmastercard.sexmod.SexMod;
import com.trolmastercard.sexmod.entity.Action;
import com.trolmastercard.sexmod.entity.BaseGirlEntity;
import com.trolmastercard.sexmod.entity.JennyEntity;
import com.trolmastercard.sexmod.entity.api.IBeddableSexGirl;
import com.trolmastercard.sexmod.entity.api.IEllie;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The mod's network protocol on Fabric networking. One class replaces the
 * 1.12.2 {@code PacketHandler} and its per-packet classes; each channel names
 * the 1.12.2 packet it stands for.
 * <p>
 * Client-to-server senders go through {@link SexMod#CLIENT}, so this class
 * stays free of client imports. The client-side receivers for the
 * server-to-client channels are registered in {@code ClientPackets}.
 * <p>
 * <b>Trust.</b> The 1.12.2 handlers acted on whatever a client sent: any client
 * could teleport any player or rewrite any girl's state. Here every handler
 * checks the sender:
 * <ul>
 * <li>packets about a girl are ignored unless the sender is in her world and
 * within {@link #MAX_INTERACT_DISTANCE} blocks;</li>
 * <li>while a scene runs, only its own player can change the girl's state;</li>
 * <li>only a companion's master can send her home, move her home or release her;</li>
 * <li>a client can only teleport itself, only during its own scene, and only
 * within {@link #MAX_SCENE_TELEPORT} blocks;</li>
 * <li>a client can only bind itself as the scene player.</li>
 * </ul>
 * What is still trusted, as in 1.12.2: scenes advance when the client reports
 * the end of an animation, and the price of an action is taken by a separate
 * {@code remove_items} packet, so a modified client can skip paying.
 */
public final class ModPackets {
   /** C2S, 1.12.2 {@code ChangeDataParameterPacket}: girl id, key, value. */
   public static final Identifier CHANGE_DATA_PARAMETER = SexMod.id("change_data_parameter");
   /** C2S, 1.12.2 {@code KoboldStatePacket} (despite the name, the shared "start a scene" packet). */
   public static final Identifier START_SCENE = SexMod.id("start_scene");
   /** C2S, 1.12.2 {@code ResetGirlPacket}: girl id, player-only flag. */
   public static final Identifier RESET_GIRL = SexMod.id("reset_girl");
   /** Both ways, 1.12.2 {@code ResetControllerPacket}: restart the action animation for other observers. */
   public static final Identifier RESET_CONTROLLER = SexMod.id("reset_controller");
   /** C2S, 1.12.2 {@code TeleportPlayerPacket}: scene camera placement of the sender. */
   public static final Identifier TELEPORT_PLAYER = SexMod.id("teleport_player");
   /** C2S, 1.12.2 {@code SendGirlToSexPacket}: walk to the nearest bed. */
   public static final Identifier SEND_GIRL_TO_SEX = SexMod.id("send_girl_to_sex");
   /** C2S, 1.12.2 {@code SetPlayerForGirlPacket}: the sender joins the girl waiting on the bed. */
   public static final Identifier SET_PLAYER_FOR_GIRL = SexMod.id("set_player_for_girl");
   /** C2S, 1.12.2 {@code SendCompanionHomePacket}. */
   public static final Identifier SEND_COMPANION_HOME = SexMod.id("send_companion_home");
   /** C2S, 1.12.2 {@code SetNewHomePacket}. */
   public static final Identifier SET_NEW_HOME = SexMod.id("set_new_home");
   /** Both ways, 1.12.2 {@code SendChatMessagePacket}: a scene chat line. */
   public static final Identifier SEND_CHAT_MESSAGE = SexMod.id("send_chat_message");
   /** C2S, 1.12.2 {@code RemoveItemsPacket}: pay for a menu action. */
   public static final Identifier REMOVE_ITEMS = SexMod.id("remove_items");
   /** S2C, 1.12.2 {@code SetPlayerMovementPacket}: {@code true} = the player may move again. */
   public static final Identifier SET_PLAYER_MOVEMENT = SexMod.id("set_player_movement");

   public static final double MAX_INTERACT_DISTANCE = 64.0;
   public static final double MAX_SCENE_TELEPORT = 12.0;
   private static final double CHAT_RANGE = 40.0;
   private static final int MAX_CHAT_LENGTH = 256;

   private ModPackets() {
   }

   // ------------------------------------------------------- client senders

   public static void sendChangeDataParameter(UUID girlId, String key, String value) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      buf.writeString(key);
      buf.writeString(value == null ? "null" : value);
      SexMod.CLIENT.sendToServer(CHANGE_DATA_PARAMETER, buf);
   }

   public static void sendStartScene(UUID girlId, boolean walkToPlayer, boolean anchorInFront) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      buf.writeBoolean(walkToPlayer);
      buf.writeBoolean(anchorInFront);
      SexMod.CLIENT.sendToServer(START_SCENE, buf);
   }

   public static void sendResetGirl(UUID girlId, boolean playerOnly) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      buf.writeBoolean(playerOnly);
      SexMod.CLIENT.sendToServer(RESET_GIRL, buf);
   }

   public static void sendResetController(UUID girlId) {
      SexMod.CLIENT.sendToServer(RESET_CONTROLLER, girlBuf(girlId));
   }

   public static void sendTeleportPlayer(Vec3d pos, float yaw, float pitch) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeDouble(pos.x);
      buf.writeDouble(pos.y);
      buf.writeDouble(pos.z);
      buf.writeFloat(yaw);
      buf.writeFloat(pitch);
      SexMod.CLIENT.sendToServer(TELEPORT_PLAYER, buf);
   }

   public static void sendGirlToSex(UUID girlId) {
      SexMod.CLIENT.sendToServer(SEND_GIRL_TO_SEX, girlBuf(girlId));
   }

   public static void sendSetPlayerForGirl(UUID girlId) {
      SexMod.CLIENT.sendToServer(SET_PLAYER_FOR_GIRL, girlBuf(girlId));
   }

   public static void sendCompanionHome(UUID girlId) {
      SexMod.CLIENT.sendToServer(SEND_COMPANION_HOME, girlBuf(girlId));
   }

   public static void sendSetNewHome(UUID girlId, Vec3d home) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      buf.writeDouble(home.x);
      buf.writeDouble(home.y);
      buf.writeDouble(home.z);
      SexMod.CLIENT.sendToServer(SET_NEW_HOME, buf);
   }

   public static void sendChatMessage(UUID girlId, String line) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      buf.writeString(line);
      SexMod.CLIENT.sendToServer(SEND_CHAT_MESSAGE, buf);
   }

   public static void sendRemoveItems(ItemStack cost) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeItemStack(cost);
      SexMod.CLIENT.sendToServer(REMOVE_ITEMS, buf);
   }

   private static PacketByteBuf girlBuf(UUID girlId) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeUuid(girlId);
      return buf;
   }

   // ------------------------------------------------------- server senders

   /** Sends a chat line to every player within 40 blocks of the girl. */
   public static void broadcastChat(BaseGirlEntity girl, String line) {
      if (!(girl.getWorld() instanceof ServerWorld)) {
         return;
      }
      for (ServerPlayerEntity player : ((ServerWorld)girl.getWorld()).getPlayers()) {
         if (player.squaredDistanceTo(girl) <= CHAT_RANGE * CHAT_RANGE) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(line);
            ServerPlayNetworking.send(player, SEND_CHAT_MESSAGE, buf);
         }
      }
   }

   public static void sendPlayerMovement(ServerPlayerEntity player, boolean free) {
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeBoolean(free);
      ServerPlayNetworking.send(player, SET_PLAYER_MOVEMENT, buf);
   }

   // ------------------------------------------------------ server handlers

   public static void registerServerReceivers() {
      ServerPlayNetworking.registerGlobalReceiver(CHANGE_DATA_PARAMETER, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         String key = buf.readString(64);
         String value = buf.readString(256);
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            // While a scene runs, only its own player drives the girl.
            if (girl != null && isScenePlayerOrNoScene(girl, player)) {
               changeDataParameter(girl, player, key, value);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(START_SCENE, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         boolean walkToPlayer = buf.readBoolean();
         boolean anchorInFront = buf.readBoolean();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            // A girl who is in a scene with someone else cannot be taken over.
            if (girl != null && isScenePlayerOrNoScene(girl, player)) {
               startScene(girl, player.getUuid(), walkToPlayer, anchorInFront);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(RESET_GIRL, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         boolean playerOnly = buf.readBoolean();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            // Only the scene's own player may end it; an unbound girl can be reset by anyone nearby.
            if (girl == null || !isScenePlayerOrNoScene(girl, player)) {
               return;
            }
            PlayerEntity scenePlayer = girl.getInteractionPlayer();
            if (scenePlayer instanceof ServerPlayerEntity) {
               resetPlayer((ServerPlayerEntity)scenePlayer);
            }
            if (!playerOnly) {
               resetGirl(girl);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(RESET_CONTROLLER, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            if (girl == null) {
               return;
            }
            for (ServerPlayerEntity other : ((ServerWorld)girl.getWorld()).getPlayers()) {
               if (other != player && other.squaredDistanceTo(girl) < 100.0 * 100.0) {
                  ServerPlayNetworking.send(other, RESET_CONTROLLER, girlBuf(girlId));
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TELEPORT_PLAYER, (server, player, handler, buf, sender) -> {
         Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
         float yaw = buf.readFloat();
         float pitch = buf.readFloat();
         server.execute(() -> {
            // Scene camera placement only: the sender must be in a scene and stay close to where they are.
            if (BaseGirlEntity.getActiveScene(player.getUuid(), false) == null) {
               return;
            }
            if (!Double.isFinite(pos.x + pos.y + pos.z) || player.getPos().squaredDistanceTo(pos) > MAX_SCENE_TELEPORT * MAX_SCENE_TELEPORT) {
               return;
            }
            teleportPlayer(player, pos, yaw, pitch);
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SEND_GIRL_TO_SEX, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            if (girl instanceof IBeddableSexGirl) {
               ((IBeddableSexGirl)girl).goToSexBed();
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SET_PLAYER_FOR_GIRL, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            if (girl == null || girl.getInteractionPlayerUUID() != null) {
               return;
            }
            if (girl instanceof JennyEntity) {
               ((JennyEntity)girl).snapInPending = true;
            }
            girl.setInteractionPlayerUUID(player.getUuid());
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SEND_COMPANION_HOME, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            if (girl != null && isMasterOrUnowned(girl, player)) {
               companionGoHome(girl);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SET_NEW_HOME, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         Vec3d home = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            if (girl != null && isMasterOrUnowned(girl, player) && Double.isFinite(home.x + home.y + home.z) && girl.getPos().squaredDistanceTo(home) < 16.0) {
               girl.homePos = new Vec3d(home.x, Math.floor(home.y), home.z);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SEND_CHAT_MESSAGE, (server, player, handler, buf, sender) -> {
         UUID girlId = buf.readUuid();
         String line = buf.readString(MAX_CHAT_LENGTH);
         server.execute(() -> {
            BaseGirlEntity girl = girlNear(player, girlId);
            // Only the scene's own player relays scene lines.
            if (girl != null && player.getUuid().equals(girl.getInteractionPlayerUUID())) {
               broadcastChat(girl, line);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(REMOVE_ITEMS, (server, player, handler, buf, sender) -> {
         ItemStack cost = buf.readItemStack();
         server.execute(() -> removeItems(player, cost));
      });
   }

   /** The girl with this id, if the sender is allowed to act on her. */
   private static BaseGirlEntity girlNear(ServerPlayerEntity player, UUID girlId) {
      BaseGirlEntity girl = BaseGirlEntity.getServerGirlEntity(girlId);
      if (girl == null || girl.getWorld() != player.getWorld()) {
         return null;
      }
      return girl.squaredDistanceTo(player) <= MAX_INTERACT_DISTANCE * MAX_INTERACT_DISTANCE ? girl : null;
   }

   private static boolean isScenePlayerOrNoScene(BaseGirlEntity girl, ServerPlayerEntity player) {
      UUID scenePlayer = girl.getInteractionPlayerUUID();
      return scenePlayer == null || scenePlayer.equals(player.getUuid());
   }

   private static boolean isMasterOrUnowned(BaseGirlEntity girl, ServerPlayerEntity player) {
      return !girl.hasMaster() || player.getUuid().equals(girl.getMasterUUID());
   }

   private static void changeDataParameter(BaseGirlEntity girl, ServerPlayerEntity sender, String key, String value) {
      try {
         switch (key) {
            case "currentModel":
               girl.getDataTracker().set(BaseGirlEntity.OUTFIT_INDEX, MathHelper.clamp(Integer.parseInt(value), 0, 1));
               break;
            case "currentAction":
               girl.setCurrentAction(Action.byName(value));
               break;
            case "animationFollowUp":
               girl.getDataTracker().set(BaseGirlEntity.GIRL_HAND_STATES, value);
               break;
            case "playerSheHasSexWith":
               if ("null".equals(value)) {
                  girl.setInteractionPlayerUUID(null);
               } else if (sender.getUuid().equals(UUID.fromString(value))) {
                  girl.setInteractionPlayerUUID(sender.getUuid());
               }
               break;
            case "targetPos":
               String[] parts = value.split("f");
               Vec3d target = new Vec3d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
               if (Double.isFinite(target.x + target.y + target.z) && target.squaredDistanceTo(girl.getPos()) <= MAX_INTERACT_DISTANCE * MAX_INTERACT_DISTANCE) {
                  girl.setTargetPosition(target);
               }
               break;
            case "master":
               // A client can make an unowned girl follow itself, or release its own girl. Nothing else.
               if (isMasterOrUnowned(girl, sender) && (value.isEmpty() || value.equals(sender.getUuid().toString()))) {
                  girl.getDataTracker().set(BaseGirlEntity.MASTER, value);
               }
               break;
            case "walk speed":
               girl.setWalkSpeed(BaseGirlEntity.BaseGirlEntityState.valueOf(value));
               break;
            case "shouldbeattargetpos":
               girl.getDataTracker().set(BaseGirlEntity.IS_ANCHORED, Boolean.parseBoolean(value));
               break;
            default:
               SexMod.LOGGER.warn("unknown data parameter '{}' @ChangeDataParameter", key);
         }
      } catch (RuntimeException e) {
         SexMod.LOGGER.warn("received an invalid message @ChangeDataParameter ({}={}): {}", key, value, e.toString());
      }
   }

   /**
    * Scene entry (1.12.2 {@code KoboldStatePacket.Handler.sendState}): stops the
    * girl, binds the player, moves the player in front of her and, for girls
    * that walk up to the player, starts that walk.
    */
   public static void startScene(BaseGirlEntity girl, UUID playerUuid, boolean walkToPlayer, boolean anchorInFront) {
      girl.removeWanderGoals();
      girl.getNavigation().stop();
      girl.setVelocity(Vec3d.ZERO);
      if (girl.getInteractionPlayerUUID() == null) {
         girl.setInteractionPlayerUUID(playerUuid);
      }
      if (anchorInFront) {
         girl.setTargetPosition(girl.getFrontOffsetVector());
      }
      girl.snapPlayerToPosition();
      PlayerEntity player = girl.getInteractionPlayer();
      if (player instanceof ServerPlayerEntity && player.getUuid().equals(playerUuid)) {
         enterScene((ServerPlayerEntity)player);
      }
      if (walkToPlayer && girl instanceof IEllie) {
         ((IEllie)girl).setDismounted();
      }
   }

   /**
    * Puts a player into scene state: input locked, and their own model hidden,
    * because the girl's model draws the player's body for the scene.
    * {@link #resetPlayer} undoes both.
    */
   public static void enterScene(ServerPlayerEntity player) {
      player.setInvisible(true);
      sendPlayerMovement(player, false);
   }

   /** Full scene-end reset of the girl: goals back, anchor and player binding cleared, moved out of blocks. */
   public static void resetGirl(BaseGirlEntity girl) {
      girl.reinitTasks();
      girl.setAnchored(false);
      girl.setInteractionPlayerUUID(null);
      girl.cameraOriginPos = null;
      girl.setNoGravity(false);
      girl.noClip = false;
      Vec3d pos = firstFreePosition(girl.getWorld(), girl.getPos());
      girl.requestTeleport(pos.x, pos.y, pos.z);
   }

   /** Gives a scene player their normal state back and unlocks their input. */
   public static void resetPlayer(ServerPlayerEntity player) {
      Vec3d pos = firstFreePosition(player.getWorld(), player.getPos());
      player.requestTeleport(pos.x, pos.y, pos.z);
      player.setInvisible(false);
      player.noClip = false;
      player.setNoGravity(false);
      if (!player.isCreative() && !player.isSpectator() && player.getAbilities().flying) {
         player.getAbilities().flying = false;
         player.sendAbilitiesUpdate();
      }
      sendPlayerMovement(player, true);
   }

   private static Vec3d firstFreePosition(World world, Vec3d pos) {
      int guard = 0;
      while (!world.getBlockState(BlockPos.ofFloored(pos)).isAir() && guard++ < 16) {
         pos = pos.add(0.0, 1.0, 0.0);
      }
      return pos;
   }

   public static void teleportPlayer(ServerPlayerEntity player, Vec3d pos, float yaw, float pitch) {
      yaw = MathHelper.wrapDegrees(yaw);
      pitch = MathHelper.wrapDegrees(pitch);
      player.setVelocity(Vec3d.ZERO);
      player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, pitch);
      player.setHeadYaw(yaw);
   }

   /**
    * Server side "go home": the first call starts the pearl-throw animation,
    * the second (sent by the animation's {@code pearl} keyframe, or by the
    * server's own timeout if no client reports it) teleports her home.
    */
   public static void companionGoHome(BaseGirlEntity girl) {
      if (girl.getCurrentAction() != Action.THROW_PEARL) {
         girl.setCurrentAction(Action.THROW_PEARL);
         girl.setYawRotation((float)(Math.atan2(girl.getZ() - girl.homePos.z, girl.getX() - girl.homePos.x) * (180.0 / Math.PI)) + 90.0F);
         girl.setTargetPosition(girl.getPos());
         girl.getDataTracker().set(BaseGirlEntity.IS_ANCHORED, true);
         girl.pearlThrown = false;
      } else if (!girl.pearlThrown) {
         girl.pearlThrown = true;
         if (girl.getWorld() instanceof ServerWorld) {
            ((ServerWorld)girl.getWorld()).spawnParticles(ParticleTypes.PORTAL, girl.getX(), girl.getY() + 1.0, girl.getZ(), 64, 0.2, 0.6, 0.2, 0.5);
         }
         girl.getNavigation().stop();
         girl.requestTeleport(girl.homePos.x, girl.homePos.y, girl.homePos.z);
         girl.setCurrentAction(Action.NULL);
         girl.getDataTracker().set(BaseGirlEntity.IS_ANCHORED, false);
         girl.goHome();
         girl.pearlThrown = false;
      }
   }

   private static void removeItems(ServerPlayerEntity player, ItemStack cost) {
      if (cost == null || cost.isEmpty() || player.isCreative()) {
         return;
      }
      for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
         ItemStack stack = player.getInventory().main.get(slot);
         if (stack.isOf(cost.getItem()) && stack.getCount() >= cost.getCount()) {
            stack.decrement(cost.getCount());
            return;
         }
      }
   }
}
