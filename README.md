<p align="center">
  <img src=".github/banner.webp" alt="Aeronautics Gravity" width="640"/>
</p>

<h1 align="center">Aeronautics: Gravity Visualization</h1>

<p align="center">
  A <b>Create Aeronautics</b> add-on for Minecraft 1.21.1 / NeoForge.<br/>
  The jar contains three sub-mods - Gravity Visualization, Simplification Related, and Starlight Logistics. Gravity Visualization focuses on simplifying craft balancing; Simplification Related adds machines that improve the gameplay experience; Starlight Logistics focuses on cross-dimensional package and player teleportation.
</p>

<p align="center">
  English | <a href="README.zh_CN.md">简体中文</a>
</p>

---

## One jar, three mods

~~It started as one thing and then scope creep happened - I just kept wanting to add a bit of everything~~

The suite registers **three mods** from a single jar - each can be enabled/disabled independently, but they share mixins and config:

### 1. Aeronautics: Gravity Visualization (`gravity_visualization`)
**See and tune your center of gravity.** The Spark Wand overlays per-block mass numbers and the center of mass on a contraption, and doubles as Create goggles. Counterweight / counterweight-light blocks let you adjust mass and buoyancy.

### 2. Aeronautics: Simplification Related (`simplification_related`)
**Hassle-free machinery.** The convenient analog transmission turns a 0-15 redstone signal into a fixed RPM; the variable speed portable engine offers 15 speed settings; the sequential feeder is a programmable batch-feeding tape for sequenced assembly lines.

### 3. Aeronautics: Starlight Logistics (`starlight_logistics`)
**Logistics made simple** ~~(of packages, and... people)~~. World anchors let you ship packages between any two points, addressing signs make switching target addresses quick and easy, and activated ender pearls slot right into the package system: when a package containing one is in entity form, it bursts open after 3 seconds, releasing the pearl to teleport the player.

### In-game config

A config screen is attached to all three mods' mod-list buttons. Every feature and recipe group can be toggled on/off independently (all on by default). Disabling a feature removes its crafting recipes and deletes the item the moment a player picks it up (placed blocks keep working; an actionbar message tells the player).

## Dependencies

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | ≥ 21.1.235 |
| Create | ≥ 6.0.10 |
| Sable | ≥ 2.0.0 |
| Simulated | ≥ 1.3.0 |
| Aeronautics | ≥ 1.3.0 |

## Installation

Drop the jar into your client/server `mods/` folder alongside the dependencies above.

## Building

```bash
./gradlew build
```

The output jar will be in `build/libs/`.

## License

Released under the [MIT License](LICENSE).
