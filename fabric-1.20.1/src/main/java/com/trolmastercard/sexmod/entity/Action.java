package com.trolmastercard.sexmod.entity;

/**
 * The scene/action state machine shared by every girl.
 * <p>
 * A girl's current state is stored in the {@link BaseGirlEntity#CUR_ACTION}
 * tracked-data entry as this enum's {@code name()}. Transitions go through
 * {@link BaseGirlEntity#setCurrentAction(Action)}: on the client that sends a
 * {@code change_data_parameter} packet, the server writes the tracked data,
 * and the data tracker syncs it back to every client.
 * <p>
 * Each constant carries:
 * <ul>
 *   <li>{@link #transitionTick}: GeckoLib transition length (ticks) when the
 *       action controller switches into this action's animation.</li>
 *   <li>{@link #hasPlayer}: whether the interaction player is part of the
 *       scene (the model's {@code steve} body is shown, the real player hidden).</li>
 *   <li>{@link #autoBlink}: whether the eyes controller blinks during it.</li>
 *   <li>{@link #maxGirlPitch}/{@link #minGirlPitch}, {@link #flipGirlYaw},
 *       {@link #useBoyCam}, {@link #hideNameTag}: camera and name tag flags.</li>
 *   <li>{@link #length}/{@link #followUp}: optional timed auto-transition,
 *       applied on the server by {@link BaseGirlEntity#tickFollowUpTransitions()}.</li>
 * </ul>
 * Scenes otherwise advance through sound keyframes in the animation JSON
 * ({@code xxxDone}, {@code xxxMSG1..N}), handled by each girl's sound listener,
 * and through player input (sneak = faster, jump with a full meter = finish).
 * <p>
 * The constant list matches the 1.12.2 mod, minus the five Manglelie-only
 * actions, which are not part of this port (see PORTING.md). Do not give a
 * sound-driven action a {@code followUp}: it would fire after {@code length}
 * ticks and override the sound-listener transition.
 */
public enum Action {
   NULL(0, false, true),
   STARTBLOWJOB(2, true, false),
   SUCKBLOWJOB(2, true, false),
   SUCKBLOWJOB_BLINK(2, true, true),
   CUMBLOWJOB(0, true, false),
   THRUSTBLOWJOB(2, true, false),
   PAYMENT(5, true, false),
   STARTDOGGY(2, false, false),
   WAITDOGGY(0, false, true),
   DOGGYSTART(0, true, false),
   DOGGYSLOW(2, true, false),
   DOGGYFAST(2, true, false),
   DOGGYCUM(2, true, false),
   STRIP(5, false, false),
   DASH(2, false, false),
   HUG(2, true, false),
   HUGIDLE(0, true, true),
   HUGSELECTED(0, true, false),
   UNDRESS(2, false, true),
   DRESS(2, false, true),
   SITDOWN(2, false, false, 60.0F, -90.0F, true),
   SITDOWNIDLE(0, false, true, 60.0F, -60.0F, true),
   COWGIRLSTART(0, true, false, 60.0F, -60.0F, false),
   COWGIRLSLOW(10, true, false, 60.0F, -60.0F, false),
   COWGIRLFAST(10, true, false, 60.0F, -60.0F, false),
   COWGIRLCUM(2, true, false, 60.0F, -60.0F, false),
   ATTACK(0, false, true),
   BOW(2, false, true),
   RIDE(0, false, true),
   SIT(0, false, true),
   THROW_PEARL(0, false, false),
   DOWNED(7, false, true),
   PAIZURI_START(0, true, false, -56.0F, -90.0F, false, true),
   PAIZURI_IDLE(0, true, false, -56.0F, -90.0F, false, true),
   PAIZURI_SLOW(0, true, true, -56.0F, -90.0F, false, true),
   PAIZURI_FAST(0, true, false, -56.0F, -90.0F, false, true),
   PAIZURI_FAST_CONTINUES(0, true, false, -56.0F, -90.0F, false, true),
   PAIZURI_CUM(0, true, false, -56.0F, -90.0F, false, true),
   MISSIONARY_START(0, true, false, 30.0F, -90.0F, true),
   MISSIONARY_SLOW(2, true, false, 30.0F, -90.0F, true),
   MISSIONARY_FAST(2, true, false, 30.0F, -90.0F, true),
   MISSIONARY_CUM(2, true, false, 30.0F, -90.0F, true),
   TALK_HORNY(5, true, false),
   TALK_IDLE(0, true, true),
   TALK_RESPONSE(2, true, false),
   ANAL_PREPARE(5, false, false),
   ANAL_WAIT(0, false, true),
   ANAL_START(0, true, false),
   ANAL_SLOW(2, true, true),
   ANAL_FAST(0, true, false),
   ANAL_CUM(2, true, false),
   KOBOLD_ANAL_START(0, true, false, false, 4.0F, -80.0F, true),
   KOBOLD_ANAL_SLOW(0, true, true, false, 4.0F, -80.0F, true),
   KOBOLD_ANAL_FAST(0, true, false, false, 4.0F, -80.0F, true),
   KOBOLD_ANAL_CUM(2, true, false, false, 4.0F, -80.0F, true),
   SUMMON(0, false, false, false, true),
   SUMMON_WAIT(0, false, true, false, true),
   HEAD_PAT(0, true, false),
   ALLIE_PREPARE_FIRST_TIME(0, false, false, 40.0F, -40.0F, false),
   DEEPTHROAT_START(0, true, false, true, 40.0F, -40.0F, false),
   DEEPTHROAT_SLOW(2, true, false, true, 40.0F, -40.0F, false),
   DEEPTHROAT_FAST(2, true, false, true, 40.0F, -40.0F, false),
   DEEPTHROAT_CUM(2, true, false, true, 40.0F, -40.0F, false),
   ALLIE_PREPARE_NORMAL(2, false, false, 40.0F, -40.0F, false),
   SUMMON_NORMAL(0, false, false),
   SUMMON_SAND(0, false, false),
   SUMMON_NORMAL_WAIT(2, false, true),
   RICH_FIRST_TIME(0, false, false),
   RICH_NORMAL(0, false, false),
   CITIZEN_START(0, true, false, 10.0F, -90.0F, false),
   CITIZEN_SLOW(0, true, false, 10.0F, -90.0F, false),
   CITIZEN_FAST(0, true, false, 10.0F, -90.0F, false),
   CITIZEN_CUM(2, true, false, 10.0F, -90.0F, false),
   FISHING_START(5, false, false),
   FISHING_IDLE(0, false, true),
   FISHING_EAT(0, false, false),
   FISHING_THROW_AWAY(0, false, false),
   TOUCH_BOOBS_INTRO(0, true, false),
   TOUCH_BOOBS_SLOW(2, true, false),
   TOUCH_BOOBS_FAST(2, true, false),
   TOUCH_BOOBS_CUM(2, true, false),
   WAIT_CAT(0, false, false, 30.0F, -90.0F, true),
   COWGIRL_SITTING_INTRO(0, true, false),
   COWGIRL_SITTING_SLOW(5, true, false),
   COWGIRL_SITTING_FAST(5, true, false),
   COWGIRL_SITTING_CUM(5, true, false),
   MINE(0, false, false),
   SLEEP(5, false, false),
   MATING_PRESS_START(0, true, false, false, -50.0F, -90.0F, false),
   MATING_PRESS_SOFT(0, true, false, -50.0F, -90.0F, false),
   MATING_PRESS_HARD(0, true, false, -50.0F, -90.0F, false),
   MATING_PRESS_CUM(2, true, false, -30.0F, -90.0F, false),
   SHOULDER_IDLE(0, false, true, false, true),
   PICK_UP(0, true, false, 10.0F, -90.0F, true, true),
   RUN(5, false, true),
   CATCH(0, true, false),
   CATCH_BJ(0, true, false),
   CATCH_BJ_IDLE(0, true, false),
   START_THROWING(0, true, true),
   THROWN(0, false, true),
   JUMP_0(0, true, false),
   JUMP_1(0, false, false),
   JUMP_2(0, false, false),
   BREEDING_INTRO_0(0, true, false),
   BREEDING_INTRO_1(0, false, false),
   BREEDING_INTRO_2(0, false, false),
   BREEDING_SLOW_0(0, true, false),
   BREEDING_1(0, false, false),
   BREEDING_SLOW_2(5, false, false),
   BREEDING_FAST_0(0, true, false),
   BREEDING_FAST_2(5, false, false),
   BREEDING_CUM_0(0, true, false),
   BREEDING_CUM_1(0, false, false),
   BREEDING_CUM_2(0, false, false),
   AWAIT_PICK_UP(0, false, true),
   VANISH(0, false, true),
   STAND_UP(0, false, false),
   NELSON_INTRO(0, true, false, 30.0F, -20.0F, false, true),
   NELSON_SLOW(0, true, false, 30.0F, -20.0F, false, true),
   NELSON_FAST(0, true, false, 30.0F, -20.0F, false, true),
   NELSON_CUM(0, true, false, 30.0F, -20.0F, false, true),
   CARRY_SLOW(0, true, false, true, true),
   CARRY_FAST(0, true, false, true, true),
   CARRY_CUM(0, true, false, true, true),
   CARRY_INTRO(0, true, false, true, true, 191, CARRY_SLOW),
   PRONE_DOGGY_INTRO(0, true, false, true, true),
   PRONE_DOGGY_SOFT(0, true, false, true, true),
   PRONE_DOGGY_HARD(0, true, false, true, true, 34, PRONE_DOGGY_SOFT),
   PRONE_DOGGY_INSERT(2, true, false, true, true, 42, PRONE_DOGGY_SOFT),
   PRONE_DOGGY_CUM(0, true, false, true, true),
   REVERSE_COWGIRL_SLOW(0, true, false, true, 30.0F, -90.0F, true),
   REVERSE_COWGIRL_FAST_START(0, true, false, true, 34, REVERSE_COWGIRL_SLOW, 30.0F, -90.0F, true),
   REVERSE_COWGIRL_FAST_CONTINUES(0, true, false, true, 39, REVERSE_COWGIRL_SLOW, 30.0F, -90.0F, true),
   REVERSE_COWGIRL_CUM(0, true, false, true, 30.0F, -90.0F, true),
   REVERSE_COWGIRL_START(0, true, false, true, 88, REVERSE_COWGIRL_SLOW, 30.0F, -90.0F, true),
   WAVE_IDLE(0, false, false, false, true),
   WAVE(0, false, false, true, false, 71, WAVE_IDLE),
   FLY(0, false, true),
   SUMMON_SKELETON(0, false, false),
   ATTACK_SWORD(0, false, false),
   KNOCK_OUT_FLY(5, false, false),
   KNOCK_OUT_GROUND(3, false, false),
   KNOCK_OUT_STAND_UP(0, false, false),
   RAPE_PREPARE(0, false, false),
   RAPE_CHARGE(0, false, false),
   RAPE_ON_GOING(0, true, false, true, 60.0F, -30.0F, false),
   RAPE_INTRO(0, true, false, false, true, 46, RAPE_ON_GOING),
   RAPE_CUM_IDLE(0, true, false, true),
   RAPE_CUM(0, true, false, true, 34, RAPE_CUM_IDLE, 60.0F, -30.0F, false),
   CORRUPT_SLOW(0, true, false, -30.0F, -90.0F, false),
   CORRUPT_FAST(0, true, false, -30.0F, -90.0F, false),
   CORRUPT_CUM(0, true, false, false, -30.0F, -90.0F, false),
   CORRUPT_INTRO(0, true, false, true, 29, CORRUPT_SLOW),
   CONTROLLED_FLIGHT(0, true, true, true, true),
   BOOST(3, true, false, true, true, 43, CONTROLLED_FLIGHT),
   GALATH_SUMMON(0, false, false, false, true, 15, NULL),
   GALATH_DE_SUMMON(0, false, false, false, true),
   GIVE_COIN(0, true, false, true, true, 140, NULL),
   MASTERBATE(0, false, false),
   PUSSY_LICKING(0, false, true, false),
   MASTERBATE_SITTING(0, false, true, false),
   MASTERBATE_SITTING_CUM(0, false, false, false),
   MORNING_BLOWJOB_SLOW(0, true, true, true),
   MORNING_BLOWJOB_FAST(0, true, true, true),
   MORNING_BLOWJOB_CUM(0, true, false, true);

   /** GeckoLib transition length (ticks) when switching into this action. */
   public final int transitionTick;

   /** Whether the interaction player participates in this action. */
   public final boolean hasPlayer;

   /** Whether the eyes controller plays the blink animation during this action. */
   public final boolean autoBlink;

   /** Maximum camera pitch allowed while this action runs. */
   public final float maxGirlPitch;

   /** Minimum camera pitch allowed while this action runs. */
   public final float minGirlPitch;

   /** Whether the scene camera is flipped 180 degrees. */
   public final boolean flipGirlYaw;

   /** Ticks this action plays before {@link #followUp} fires (server side); 0 for sound-driven actions. */
   public int length;

   /** Auto-transition target after {@link #length} ticks, or {@code null}. */
   public Action followUp = null;

   /** Whether the scene camera follows the player instead of the girl. */
   public boolean useBoyCam;

   /** Whether the name tag is hidden while this action is active. */
   public boolean hideNameTag;

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink) {
      this.transitionTick = transitionTick;
      this.hasPlayer = hasPlayer;
      this.autoBlink = autoBlink;
      this.maxGirlPitch = 30.0F;
      this.minGirlPitch = -90.0F;
      this.flipGirlYaw = false;
      this.useBoyCam = false;
      this.hideNameTag = false;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean useBoyCam) {
      this(transitionTick, hasPlayer, autoBlink);
      this.useBoyCam = useBoyCam;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean useBoyCam, boolean hideNameTag) {
      this(transitionTick, hasPlayer, autoBlink);
      this.useBoyCam = useBoyCam;
      this.hideNameTag = hideNameTag;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, float maxGirlPitch, float minGirlPitch, boolean flipGirlYaw) {
      this.transitionTick = transitionTick;
      this.hasPlayer = hasPlayer;
      this.autoBlink = autoBlink;
      this.maxGirlPitch = maxGirlPitch;
      this.minGirlPitch = minGirlPitch;
      this.flipGirlYaw = flipGirlYaw;
      this.useBoyCam = false;
      this.hideNameTag = false;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, float maxGirlPitch, float minGirlPitch, boolean flipGirlYaw, boolean hideNameTag) {
      this.transitionTick = transitionTick;
      this.hasPlayer = hasPlayer;
      this.autoBlink = autoBlink;
      this.maxGirlPitch = maxGirlPitch;
      this.minGirlPitch = minGirlPitch;
      this.flipGirlYaw = flipGirlYaw;
      this.useBoyCam = false;
      this.hideNameTag = hideNameTag;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean useBoyCam, float maxGirlPitch, float minGirlPitch, boolean flipGirlYaw) {
      this.transitionTick = transitionTick;
      this.hasPlayer = hasPlayer;
      this.autoBlink = autoBlink;
      this.maxGirlPitch = maxGirlPitch;
      this.minGirlPitch = minGirlPitch;
      this.flipGirlYaw = flipGirlYaw;
      this.hideNameTag = false;
      this.useBoyCam = useBoyCam;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, int length, Action followUp) {
      this(transitionTick, hasPlayer, autoBlink);
      this.length = length;
      this.followUp = followUp;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean useBoyCam, int length, Action followUp) {
      this(transitionTick, hasPlayer, autoBlink);
      this.length = length;
      this.followUp = followUp;
      this.useBoyCam = useBoyCam;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean useBoyCam, int length, Action followUp, float minGirlPitch, float maxGirlPitch, boolean flipGirlYaw) {
      this.transitionTick = transitionTick;
      this.hasPlayer = hasPlayer;
      this.autoBlink = autoBlink;
      this.length = length;
      this.followUp = followUp;
      this.useBoyCam = useBoyCam;
      this.minGirlPitch = minGirlPitch;
      this.maxGirlPitch = maxGirlPitch;
      this.flipGirlYaw = flipGirlYaw;
   }

   Action(int transitionTick, boolean hasPlayer, boolean autoBlink, boolean hideNameTag, boolean useBoyCam, int length, Action followUp) {
      this(transitionTick, hasPlayer, autoBlink);
      this.length = length;
      this.followUp = followUp;
      this.useBoyCam = useBoyCam;
      this.hideNameTag = hideNameTag;
   }

   public static boolean isAny(Action candidate, Action... actions) {
      for (Action action : actions) {
         if (candidate == action) {
            return true;
         }
      }
      return false;
   }

   /** Parses a synced action name; unknown names (older or newer mod versions) fall back to {@link #NULL}. */
   public static Action byName(String name) {
      if (name != null) {
         try {
            return valueOf(name);
         } catch (IllegalArgumentException ignored) {
            // fall through
         }
      }
      return NULL;
   }
}
