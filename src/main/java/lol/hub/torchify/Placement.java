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
        for (List<Material> group : lightSources) {
            Set<Material> tried = EnumSet.noneOf(Material.class);
            for (ItemStack stack : contents) {
                if (stack == null || !group.contains(stack.getType()) || !tried.add(stack.getType())) continue;

                Spot spot = furthestSpot(player, limit, variants(stack.getType()));
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

    // In the order a cell tries them, so a lantern stands wherever it could also hang.
    private static List<BlockData> variants(Material item) {
        BlockData standing = item.createBlockData();
        if (!(standing instanceof Lantern)) return List.of(standing);
        return List.of(standing, item.createBlockData(block -> ((Lantern) block).setHanging(true)));
    }

    private static Spot furthestSpot(Player player, int limit, List<BlockData> variants) {
        World world = player.getWorld();
        Location eyes = player.getEyeLocation();
        Vector eyesVector = eyes.toVector();
        double range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();

        int minY = Math.max(world.getMinHeight(), (int) Math.floor(eyes.getY() - range));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(eyes.getY() + range));
        Spot best = null;
        // Starts above 0: the ray to a cell centre at the eyes would have no direction.
        double bestDistance = 0;
        for (int x = (int) Math.floor(eyes.getX() - range); x <= (int) Math.floor(eyes.getX() + range); x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = (int) Math.floor(eyes.getZ() - range); z <= (int) Math.floor(eyes.getZ() + range); z++) {
                    Vector toCenter = new Vector(x + 0.5, y + 0.5, z + 0.5).subtract(eyesVector);
                    double distance = toCenter.length();
                    // Strictly greater, so ties keep the first cell in scan order.
                    if (distance > range || distance <= bestDistance) continue;

                    Block cell = world.getBlockAt(x, y, z);
                    Material type = cell.getType();
                    if (type != Material.AIR && type != Material.CAVE_AIR) continue;
                    if (cell.getLightFromBlocks() > limit) continue;
                    BlockData block = firstFit(cell, variants);
                    if (block == null) continue;
                    // Outline shapes, as the player's own block pick uses.
                    if (world.rayTraceBlocks(eyes, toCenter, distance, FluidCollisionMode.NEVER, false) != null) continue;

                    best = new Spot(cell, block);
                    bestDistance = distance;
                }
            }
        }
        return best;
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
