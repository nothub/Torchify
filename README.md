# Torchify

Paper plugin. Light it up!

[Modrinth](https://modrinth.com/mod/torchify)  
[GitHub](https://github.com/nothub/torchify/releases)

---

Places light sources from your inventory so hostile mobs can't spawn within your reach.

`/torchify` (alias `/autotorch`) toggles it for you. It starts off, and the setting survives restarts.

Permission: `torchify.toggle`, granted by default.

Once a second, Torchify places one light on the furthest spot that is:

- at or below the dimension's spawn block light limit, so a hostile mob could spawn there at night
- within your block reach, measured from your eyes
- in your line of sight, so never behind a wall
- a spot the item can be placed on, by vanilla's rules

The furthest spot spreads the lights out, so an area becomes spawn-safe with few items.

### Item Priority

Torchify uses the best item you carry, in this order:

1. Waxed copper lanterns (any oxidation stage)
2. Copper lanterns (any oxidation stage)
3. Lanterns
4. Copper torches
5. Torches

Items within one group rank equal. Inventory order decides between them.

If the best item fits nowhere, Torchify tries the next one.

### Placement

| Item     | Floor | Ceiling (hanging) | Underwater |
|----------|-------|-------------------|------------|
| Lanterns | yes   | yes               | yes        |
| Torches  | yes   | no                | no         |

Where a lantern could either stand or hang, it stands. Underwater means source water, not flowing water.

### Where Torchify Does Nothing

- In the Nether, or any dimension where block light can't stop spawns.
- Where every spot in reach is already lit enough.
- While you are dead, sleeping, or in adventure or spectator mode.
