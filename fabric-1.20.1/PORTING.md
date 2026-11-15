# Fapcraft on Fabric 1.20.1 — port status

Warning: this is an 18+ project (adults only).

This directory is a port of the Forge 1.12.2 mod in the repository root to
**Fabric, Minecraft 1.20.1**. It is a separate Gradle project. The 1.12.2
sources and build are untouched, and both builds read the same models,
animations, textures and sounds from `../src/main/resources`.

**State: the core and Jenny are ported. It builds on GitHub Actions, but it has
never been run.** See [What has been checked](#what-has-been-checked) before relying on it.

## Building

Requirements: JDK 17 or newer, and internet access for Gradle (Fabric, Mojang
and GeckoLib repositories).

```
cd fabric-1.20.1
./gradlew build        # jar in build/libs/
./gradlew runClient    # development client
```

To play, put the jar in `mods/` together with
[Fabric API](https://modrinth.com/mod/fabric-api) and
[GeckoLib 4](https://modrinth.com/mod/geckolib) (4.4.2 or newer, for Fabric 1.20.1).

Versions are set in `gradle.properties`: Minecraft 1.20.1, Yarn `1.20.1+build.10`,
Fabric Loader 0.16.14, Fabric API 0.92.6, GeckoLib 4.4.2.

## What has been checked

The port was written without a local Gradle or Minecraft. The build is checked
on GitHub Actions by `.github/workflows/fabric-build.yml` ("Fabric 1.20.1
build"), which runs `./gradlew build` on every push or pull request that touches
this folder or the shared resources, and uploads the jar as the artifact
`fapcraft-fabric-1.20.1-jar`. It does not publish a release.

| Check | Result |
|---|---|
| All Java sources compile against Minecraft 1.20.1 (Yarn `build.10`), Fabric API 0.92.6 and GeckoLib 4.4.2 | Passes in CI (`./gradlew build`, javac under Loom). |
| `src/main` compiles without `src/client`, and uses no client-only Minecraft class or member | Passes (needed for dedicated servers and for Loom's split source sets). |
| GeckoLib 4.4.2's own loader parses Jenny's two models and her animation file | Passes: 294 and 243 bones, 38 animations. Ellie's files were **not** run through the loader; a script confirmed they use the same format versions and the same set of JSON features as Jenny's. |
| Every sound the code registers exists in `sounds.json` and as an `.ogg` | Passes (243 sounds). This check found one sound that 1.12.2 registered but never shipped (`girls.jenny.hmph.hmph4`). |
| Every shipped asset path is a valid 1.20.1 resource path | Passes. |
| `./gradlew build` (compile, resources, remapped jar) | Passes in CI on Java 21, Gradle 8.14.2. Not run on any other machine. |
| Starting the game, rendering, playing a scene | **Not run where the port is written.** From a user's reports: the game starts, Jenny is drawn, her menu works and her scenes start. Nothing about Ellie has been seen in a game. |

Expect the first play session to turn up problems.
The most likely places:

- **Item placement on the model** (`GirlRenderer.renderItemAtBone`): the angles
  for her sword, her bow and the items handed over during payment are the
  1.12.2 numbers, applied through a different renderer.
- **Player placement during scenes** (`BaseGirlEntity.positionPlayerRelative`
  and the offsets in `JennyEntity.onSoundKeyframe`): same numbers as 1.12.2,
  but the player's eye height and teleport handling changed between versions.
- **The scene player's body** (`ScenePlayerSkins`): the skin is read back from
  the GPU and repainted. If that fails, the plain skin is used and a warning is
  logged.
- **Ellie's bed scenes**: the player is placed where the animation shows their
  body, which is partly inside the bed and the floor (`EllieEntity.startActionFromHandState`).
  1.12.2 switched the player's collision off for that; Fabric cannot do that
  for a player without a mixin, so the player is only made weightless. If the
  view slides or jitters there, this is why.
- **Shading of flat model parts** (`GirlRenderer.renderCube`): changed from
  GeckoLib's default to what the 1.12.2 renderer did. Reasoned from the two
  renderers' source, not seen on screen.
- **Scene timing**: scenes advance on GeckoLib sound keyframes, and GeckoLib 4
  fires them slightly differently from GeckoLib 3 (earlier by the length of
  the blend into the animation).

## What is ported

| Area | 1.20.1 | Notes |
|---|---|---|
| Mod entry point, startup banner, config file | `SexMod`, `ModConfig`, `BuildInfo` | Config is `config/sexmod.json`. The banner is printed unmodified. |
| 18+ confirmation | `client/gui/AgeGateScreen` | An in-game screen instead of a Swing window. See differences below. |
| Girl base class: synced state, action state machine, anchoring, player placement, chat, sounds, animation controllers | `entity/BaseGirlEntity`, `entity/Action` | NBT keys are those of 1.12.2. |
| Companion behaviour: follow, teleport to master, melee and bow, downed instead of dying, healing, go home, set home | `entity/AbstractGirlNpcEntity`, `entity/ai/GirlFollowGoal` | |
| **Jenny**: menu, payment, all of her scenes, undress and dress, bed finding | `entity/JennyEntity`, `entity/GirlBedPlacement` | Every animation the 1.12.2 Jenny code plays. |
| **Ellie**: menu, undress and dress, the hug and the two bed scenes it leads to (cowgirl, missionary), the carry scene, ducking under low ceilings | `entity/EllieEntity`, `entity/GirlBedPlacement` | Every animation the 1.12.2 Ellie code plays. Her head does not follow the player while she sits on the bed. |
| Models and renderers | `client/model/GirlModel`, `client/renderer/GirlRenderer` (shared), `JennyModel`, `JennyRenderer`, `EllieModel`, `EllieRenderer`, `client/renderer/ScenePlayerSkins` | GeckoLib 4. |
| Network protocol | `networking/ModPackets`, `client/ClientPackets` | 12 channels, covering what Jenny and companions use (1.12.2 has 47 packet classes). The server now checks who sent what; see differences. |
| Scene controls and HUD | `client/SceneControls`, `client/gui/HornyMeterHud`, `client/gui/TransitionOverlay` | Sneak = faster, jump with a full meter = finish. |
| Action menu | `client/gui/ActionMenuScreen` | Vanilla buttons instead of the custom-drawn 1.12.2 screen. |
| Horny potion | `potion/HornyPotion` | Mundane potion + poppy. |
| Sounds | `util/SoundHandler`, `tools/gen_sounds.py` | |
| Translations | `tools/convert_lang.py` | All 15 languages, converted to JSON. |
| Loot tables and spawn eggs | `data/sexmod/loot_tables/entities/{jenny,ellie}.json`, `item/ModItems` | |

The girls are placed with their spawn eggs (Spawn Eggs creative tab) or
`/summon sexmod:jenny`, `/summon sexmod:ellie`.

Ellie's bed scenes start as in 1.12.2: throw a splash horny potion at her
while you stand within 10 blocks, and have a bed within 16 blocks whose foot
or head end has a free block in front of it.

## What is not ported

The 1.12.2 mod is 378 Java files (about 69,700 lines). This port is 35 files
(about 6,100 lines). Not ported yet:

- **Characters**: Bia, Luna, Allie, the slime girl, the bee, the goblins,
  the kobolds and their tribe system, Galath. Each needs its entity, model,
  renderer and scenes, the way Jenny and Ellie have them.
- **Player-as-girl mode** (`AbstractPlayerGirlEntity` and the model/renderer
  classes for it).
- **The reskin system and the Girl Wand** (custom model loader and its GUI).
- **Equipment screen**: companions have their 1.12.2 inventory (iron sword and
  bow by default) and it is saved, but there is no screen to change it, and
  the armour pieces of the dressed model are never shown.
- **World generation**: the girls' houses (`structures/*.nbt`) and natural
  spawning. `shouldGenBuildings` in the config is read but does nothing.
- **Items**: staffs, lamps, coins, eggs and the other 1.12.2 items. They belong
  to the characters and systems above.
- **Camera bones** (`girlCam`, `boyCam`): the camera is placed by moving the
  player, as most of 1.12.2 does, not by following a bone.
- **Villagers and the horny potion**: the 1.12.2 effect on villagers.
- **Riding along** on the master's mount, and sweeping-edge damage, in the
  companion AI.

### Deliberately left out

**Manglelie is not part of this port and is not planned to be.** The 1.12.2
documentation describes her as the small imp girl bound to a Galath as her
"daughter", and her scenes are sexual ones together with Galath and the
player. I will not port sexual content built around a child-like character,
so her entity, her five actions (`HUG_MANG`, `RIDE_MOMMY_HEAD`,
`THREESOME_SLOW`, `THREESOME_FAST`, `THREESOME_CUM`), her assets and her
translation keys are excluded. If Galath is ported later, she would be ported
without that part.

## Differences from 1.12.2

Behaviour that was changed on purpose:

- **The server checks packets.** In 1.12.2 the server acted on whatever a
  client sent, so any client could teleport any player or change any girl.
  Now a client can only act on a girl in its own world within 64 blocks; while
  a scene runs only that scene's player can change the girl; only a
  companion's master can send her home, move her home or release her; and a
  client can only teleport itself, during its own scene, by at most 12 blocks.
  Still trusted, as before: the client reports when an animation has ended,
  and the price of an action is removed by a separate packet, so a modified
  client can skip paying.
- **One client drives a scene.** Animation keyframes fire on every client that
  sees the girl. In 1.12.2 each of them reported the state changes; now only
  the scene's player does (outside a scene: the client of the nearest player).
- **Waiting on the bed.** In 1.12.2 any player who walked up to a waiting
  Jenny or Ellie was pulled into the scene. Now only the player who asked for
  it is, and she gives up after one minute.
- **Finding a bed.** The 1.12.2 search never tested the block the girl stands
  in or the blocks right next to it, and reached about 10 blocks. A scene
  that started with the player facing the bed from close by put the girl onto
  the bed, where she then reported "no bed in sight...". The search now
  covers every block within 16 blocks horizontally and 3 vertically, nearest
  first, and skips beds that have no usable side instead of stopping at the
  first one. A side also counts as free if it holds carpet, a plant, a torch
  or a snow layer (1.12.2: air only).
- **Shading of flat cubes.** About four in ten cubes of the models are flat
  (hair, lashes, cloth) and are drawn from both sides. GeckoLib 4 corrects
  their normals after the camera rotation is applied, so their brightness
  changed as the view turned. `GirlRenderer` corrects them in the model's own
  space, as the 1.12.2 renderer did, and brings normals back to unit length.
- **Ellie.** She gives up if nobody answers the menu after the hug, if she
  cannot reach the bed within 30 seconds, or if nobody comes to the bed. The
  player is lifted for the second half of her carry intro; the 1.12.2 source
  contains that code but never calls it. Her kissing sounds are her own set
  (1.12.2 played Allie's).
- **A scene ends when its player leaves.** If the scene's player logs out or
  changes dimension, the girl is released.
- **Being stuck is recoverable.** The movement lock releases itself after 5
  seconds without a girl bound to the player. Attack and pearl animations end
  on the server if no client reports their end. A girl who is in a scene
  cannot be pulled into another one by a second player.
- **18+ confirmation.** Answering "below 18" closes the game. 1.12.2 also
  deleted the mod's jar; this port does not delete files. "Don't ask again"
  uses the same marker file (`sexmod/dontAskAgain` in the game directory).
- **The scene player is hidden with the invisibility flag** while the girl's
  model draws their body. Worn armour and held items of an invisible player
  are still drawn by Minecraft.
- **Ids.** The effect `sexmod:horny potion` is now `sexmod:horny_potion`
  (ids cannot contain spaces). Translation keys follow the 1.20.1 scheme
  (`entity.sexmod.jenny`, `effect.sexmod.horny_potion`).
- **Language files.** `en_id.lang` and `ne_be.lang` are not Minecraft language
  codes, so 1.12.2 never offered them. They are shipped as `id_id` (Indonesian)
  and `nl_nl` (Dutch).
- **The jar only contains what the port uses** (about 7 MB of shared assets
  instead of 63 MB).

## Layout

```
fabric-1.20.1/
  build.gradle, gradle.properties, settings.gradle, gradlew
  src/main/java       common code (runs on client and server)
  src/client/java     client-only code; common code reaches it through SexMod.CLIENT
  src/main/resources  fabric.mod.json and everything whose format changed since 1.12.2
  tools/              generators for lang/*.json and sounds.json
```

Common code never imports a client class. What it needs from the client
(the local player, the HUD, the menu, sending packets) goes through the
`ClientBridge` interface; `SexMod.CLIENT` is a do-nothing implementation on a
dedicated server and `ClientBridgeImpl` on a client.

### Porting another character

`EllieEntity` is the smaller of the two examples.

1. Add its entity (extend `BaseGirlEntity` or `AbstractGirlNpcEntity`),
   following `JennyEntity` or `EllieEntity`: menu, `doAction`,
   `animationPredicate`, `onSoundKeyframe`. Register it in `SexModEntities`.
2. Add a model (a `GirlModel` subclass naming its files) and a renderer (a
   `GirlRenderer` subclass with its weapon angles) under `src/client`, and
   register the renderer in `SexModClient`.
3. Declare its sounds in `SoundHandler` and run `python3 tools/gen_sounds.py`.
4. Add its asset directories to the `include` list in `build.gradle`.
5. Add its spawn egg in `ModItems`, the egg's model under
   `assets/sexmod/models/item/`, the egg's name to `ADDED` in
   `tools/convert_lang.py` (then run it), and its loot table.
6. Add packets it needs to `ModPackets`, with sender checks like the existing ones.

Sources are kept free of records, pattern-matching `instanceof`, switch
expressions and text blocks, so they can also be checked with a Java 11 level
compiler. The Gradle build targets Java 17.

## Credits and licence

GPLv3, with the additional terms in [`../LICENSE`](../LICENSE) and the root
[`README.md`](../README.md).

- Reverse engineering, remapping and deobfuscation of the 1.12.2 mod, on which
  this port is based: **ReverseEngineeringEnthusiasts**
  (https://github.com/ReverseEngineeringEnthusiasts/Jenny-Mod-Fapcraft).
- Original mod: Trolmastercard, Anonymous dev team, RealCrystalNight. Voice
  actors, translators and asset credits are listed in `../src/main/resources/mcmod.info`.

The startup banner (`banner.txt`) is shared with the 1.12.2 build and printed
unmodified at startup, as the additional terms require.
