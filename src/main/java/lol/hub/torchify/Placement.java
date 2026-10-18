package lol.hub.torchify;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Lights the furthest spot a player could have lit themselves, where a hostile mob could spawn. */
final class Placement {

    private static final BlockData torchData = Material.TORCH.createBlockData();
    // At a spawn light limit this high, no light prevents spawns.
    private static final int maxBlockLight = 15;

    private Placement() {
    }

    /**
     * Places one torch from the player's inventory. Returns false when light can't prevent spawns here, the player
     * has no torch or no spot fits.
     */
    static boolean lightOneSpot(Player player) {
        int limit = spawnBlockLightLimit(player.getWorld());
        if (limit >= maxBlockLight) return false;

        Optional<ItemStack> torches = Arrays.stream(player.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(stack -> stack.getType() == Material.TORCH)
                .findFirst();
        if (torches.isEmpty()) return false;

        Block spot = furthestSpot(player, limit);
        if (spot == null) return false;

        spot.setBlockData(torchData);
        torches.get().setAmount(torches.get().getAmount() - 1);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_CANDLE_PLACE, 0.33f, 0.8f);
        return true;
    }

    private static Block furthestSpot(Player player, int limit) {
        World world = player.getWorld();
        Location eyes = player.getEyeLocation();
        Vector eyesVector = eyes.toVector();
        double range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();

        int minY = Math.max(world.getMinHeight(), (int) Math.floor(eyes.getY() - range));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(eyes.getY() + range));
        Block best = null;
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
                    if (!cell.canPlace(torchData)) continue;
                    // Outline shapes, as the player's own block pick uses.
                    if (world.rayTraceBlocks(eyes, toCenter, distance, FluidCollisionMode.NEVER, false) != null) continue;

                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static int spawnBlockLightLimit(World world) {
        return ((CraftWorld) world).getHandle().dimensionType().monsterSpawnBlockLightLimit();
    }
}
