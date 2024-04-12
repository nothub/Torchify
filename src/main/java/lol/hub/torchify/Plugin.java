package lol.hub.torchify;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Plugin extends JavaPlugin implements Listener {

    private static void placeTorch(Block center, int radius, int lightLevel) {
        for (int x = -radius; x < radius; x++) {
            for (int y = -radius; y < radius; y++) {
                for (int z = -radius; z < radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    Block above = block.getRelative(BlockFace.UP);
                    if (!above.isEmpty()) continue;
                    if (block.getLightLevel() >= lightLevel) continue;
                    above.setType(Material.TORCH);
                    return;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.isDead()) continue;
                if (player.isSleeping()) continue;
                if (player.getGameMode() == GameMode.ADVENTURE) continue;
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                placeTorch(player.getWorld().getBlockAt(player.getLocation()), 5, 8);
            }
        }, 20, 20);
    }

}
