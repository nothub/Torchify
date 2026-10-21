package lol.hub.torchify;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Lights the furthest spot a player could have lit themselves, where a hostile mob could spawn. */
final class Placement {

    // Best first. Items within a group rank equal, so inventory order decides between them. Each item shares its
    // Material with the block it places.
    private static final List<List<Material>> lightSources = List.of(
            List.of(Material.WAXED_COPPER_LANTERN, Material.WAXED_EXPOSED_COPPER_LANTERN,
                    Material.WAXED_WEATHERED_COPPER_LANTERN, Material.WAXED_OXIDIZED_COPPER_LANTERN),
            List.of(Material.COPPER_LANTERN, Material.EXPOSED_COPPER_LANTERN,
                    Material.WEATHERED_COPPER_LANTERN, Material.OXIDIZED_COPPER_LANTERN),
            List.of(Material.LANTERN),
            List.of(Material.COPPER_TORCH),
            List.of(Material.TORCH));
    // At a spawn light limit this high, no light prevents spawns.
    private static final int maxBlockLight = 15;

    private Placement() {
    }

    /**
     * Places one light source from the player's inventory, the best item that fits somewhere. Returns false when light
     * can't prevent spawns here, the player carries no light source or none fits.
     */
    static boolean lightOneSpot(Player player) {
        int limit = spawnBlockLightLimit(player.getWorld());
        if (limit >= maxBlockLight) return false;

        ItemStack[] contents = player.getInventory().getContents();
        // Created once the player turns out to carry a light source, then shared by every item tried.
        Reach reach = null;
        for (List<Material> group : lightSources) {
            Set<Material> tried = EnumSet.noneOf(Material.class);
            for (ItemStack stack : contents) {
                if (stack == null || !group.contains(stack.getType()) || !tried.add(stack.getType())) continue;

                if (reach == null) reach = new Reach(player, limit);
                Spot spot = reach.furthestSpot(stack.getType());
                if (spot == null) continue;

                Block cell = spot.cell();
                cell.setBlockData(spot.block());
                stack.setAmount(stack.getAmount() - 1);
                cell.getWorld().playSound(cell.getLocation(), Sound.BLOCK_CANDLE_PLACE, 0.33f, 0.8f);
                return true;
            }
        }
        return false;
    }

    private record Spot(Block cell, BlockData block) {
    }

    // In the order a cell tries them, so a lantern stands wherever it could also hang. A torch can't be waterlogged.
    private static List<BlockData> variants(Material item, boolean waterlogged) {
        BlockData standing = item.createBlockData();
        if (!(standing instanceof Lantern)) return waterlogged ? List.of() : List.of(standing);
        return List.of(lantern(item, false, waterlogged), lantern(item, true, waterlogged));
    }

    private static BlockData lantern(Material item, boolean hanging, boolean waterlogged) {
        return item.createBlockData(block -> {
            Lantern lantern = (Lantern) block;
            lantern.setHanging(hanging);
            lantern.setWaterlogged(waterlogged);
        });
    }

    private enum Kind {DARK_AIR, DARK_WATER, NONE}

    /**
     * The cells in a player's reach. Each item scans them for its furthest fit. The facts that don't depend on the
     * item are read on first use and reused by later items: whether a cell is dark air or dark source water, and
     * whether it is in sight. They stay valid only while the world is unchanged, so a Reach lives for one
     * lightOneSpot call.
     */
    private static final class Reach {
        private final World world;
        private final Location eyes;
        private final int limit;
        private final double range;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final Block[] blocks;
        private final Kind[] kinds;
        private final Boolean[] inSight;

        Reach(Player player, int limit) {
            this.world = player.getWorld();
            this.eyes = player.getEyeLocation();
            this.limit = limit;
            this.range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();
            this.minX = (int) Math.floor(eyes.getX() - range);
            this.maxX = (int) Math.floor(eyes.getX() + range);
            this.minY = Math.max(world.getMinHeight(), (int) Math.floor(eyes.getY() - range));
            this.maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(eyes.getY() + range));
            this.minZ = (int) Math.floor(eyes.getZ() - range);
            this.maxZ = (int) Math.floor(eyes.getZ() + range);
            int size = (maxX - minX + 1) * Math.max(0, maxY - minY + 1) * (maxZ - minZ + 1);
            this.blocks = new Block[size];
            this.kinds = new Kind[size];
            this.inSight = new Boolean[size];
        }

        Spot furthestSpot(Material item) {
            List<BlockData> airVariants = variants(item, false);
            List<BlockData> waterVariants = variants(item, true);
            Spot best = null;
            // Starts above 0: the ray to a cell centre at the eyes would have no direction.
            double bestDistance = 0;
            int i = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++, i++) {
                        double dx = x + 0.5 - eyes.getX();
                        double dy = y + 0.5 - eyes.getY();
                        double dz = z + 0.5 - eyes.getZ();
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        // Strictly greater, so ties keep the first cell in scan order.
                        if (distance > range || distance <= bestDistance) continue;

                        List<BlockData> variants = switch (kind(i, x, y, z)) {
                            case DARK_AIR -> airVariants;
                            case DARK_WATER -> waterVariants;
                            case NONE -> List.of();
                        };
                        if (variants.isEmpty()) continue;
                        BlockData block = firstFit(blocks[i], variants);
                        if (block == null) continue;
                        if (!inSight(i, dx, dy, dz, distance)) continue;

                        best = new Spot(blocks[i], block);
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        private Kind kind(int i, int x, int y, int z) {
            if (kinds[i] == null) {
                Block cell = world.getBlockAt(x, y, z);
                blocks[i] = cell;
                Kind kind = switch (cell.getType()) {
                    case AIR, CAVE_AIR -> Kind.DARK_AIR;
                    // Only source water waterlogs a block. A waterlogged block is not a water cell: its type is the
                    // block's own.
                    case WATER -> ((Levelled) cell.getBlockData()).getLevel() == 0 ? Kind.DARK_WATER : Kind.NONE;
                    default -> Kind.NONE;
                };
                kinds[i] = kind != Kind.NONE && cell.getLightFromBlocks() > limit ? Kind.NONE : kind;
            }
            return kinds[i];
        }

        // Outline shapes, as the player's own block pick uses.
        private boolean inSight(int i, double dx, double dy, double dz, double distance) {
            if (inSight[i] == null) {
                inSight[i] = world.rayTraceBlocks(eyes, new Vector(dx, dy, dz), distance, FluidCollisionMode.NEVER,
                        false) == null;
            }
            return inSight[i];
        }
    }

    private static BlockData firstFit(Block cell, List<BlockData> variants) {
        for (BlockData block : variants) {
            // canPlace checks survival only.
            if (cell.canPlace(block) && !obstructed(cell, block)) return block;
        }
        return null;
    }

    // Vanilla refuses a block whose collision box overlaps an entity that blocks building.
    private static boolean obstructed(Block cell, BlockData block) {
        for (BoundingBox box : block.getCollisionShape(cell.getLocation()).getBoundingBoxes()) {
            box.shift(cell.getX(), cell.getY(), cell.getZ());
            if (!cell.getWorld().getNearbyEntities(box, Placement::blocksBuilding).isEmpty()) return true;
        }
        return false;
    }

    // Vanilla's Entity.blocksBuilding has no API. These are the entities that set it in 26.3. Spectators never block.
    private static boolean blocksBuilding(Entity entity) {
        if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
        if (entity instanceof ArmorStand stand) return !stand.isMarker();
        return entity instanceof LivingEntity || entity instanceof Boat || entity instanceof Minecart
                || entity instanceof TNTPrimed || entity instanceof FallingBlock || entity instanceof EnderCrystal;
    }

    private static int spawnBlockLightLimit(World world) {
        return ((CraftWorld) world).getHandle().dimensionType().monsterSpawnBlockLightLimit();
    }
}
