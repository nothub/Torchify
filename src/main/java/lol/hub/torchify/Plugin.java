package lol.hub.torchify;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@SuppressWarnings("unused")
public final class Plugin extends JavaPlugin implements Listener {

    private static final BlockData torchData = Material.TORCH.createBlockData();

    private static final Path configActives = Path.of("plugins", "torchify", "active.txt");

    private final Set<UUID> actives = ConcurrentHashMap.newKeySet();

    private final CommandExecutor toggle = (sender, command, label, args) -> {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is not available for non-players.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        var message = text("Torching ");
        if (actives.contains(uuid)) {
            actives.remove(uuid);
            message = message.append(text("disabled").color(RED));
        } else {
            actives.add(uuid);
            message = message.append(text("enabled").color(GREEN));
        }
        player.sendMessage(message);

        saveActives();

        return true;
    };


    private void loadActives() {
        actives.clear();
        try {
            if (configActives.toFile().exists()) {
                var lines = Files.readAllLines(configActives);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    actives.add(UUID.fromString(line));
                }
            }
        } catch (Exception ex) {
            getLogger().warning(ex.toString());
        }
    }

    private void saveActives() {
        configActives.getParent().toFile().mkdirs();
        try {
            Files.writeString(configActives, actives.stream()
                            .map(UUID::toString)
                            .collect(Collectors.joining(System.lineSeparator())),
                    WRITE, CREATE, TRUNCATE_EXISTING);
        } catch (Exception ex) {
            getLogger().warning(ex.toString());
        }
    }

    private static boolean placeTorch(Block center, int radius) {
        for (int x = -radius; x < radius; x++) {
            for (int y = -radius; y < radius; y++) {
                for (int z = -radius; z < radius; z++) {

                    // the block below the torch
                    Block block = center.getRelative(x, y, z);
                    if (!block.isSolid()) continue;

                    // the block the torch will be placed inside of
                    Block above = block.getRelative(BlockFace.UP);

                    // this check is required, otherwise torches will be placed
                    // in unsuitable locations like underwater
                    if (!above.isEmpty()) continue;

                    // this also excludes blocks like slabs, stairs
                    // or e.g. the top of a cactus
                    if (!above.canPlace(torchData)) continue;

                    // ignore blocks that already have a sufficient light level
                    if ((int) above.getLightFromBlocks() >= 8) continue;

                    // place torch and tick block below the torch
                    above.setType(Material.TORCH);
                    block.tick();

                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
        PluginCommand cmd = getCommand("torchify");
        cmd.setExecutor(toggle);
        cmd.setTabCompleter((sender, command, label, args) -> Collections.emptyList());
        loadActives();
        getServer().getScheduler().runTaskTimer(this, () ->
                getServer().getOnlinePlayers().stream()
                        .filter(player -> actives.contains(player.getUniqueId()))
                        .filter(player -> !player.isDead())
                        .filter(player -> !player.isSleeping())
                        .filter(player -> player.getGameMode() != GameMode.ADVENTURE)
                        .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                        .forEach(player -> Arrays.stream(player.getInventory().getContents())
                                .filter(Objects::nonNull)
                                .filter(stack -> !stack.isEmpty())
                                .filter(stack -> stack.getType() == Material.TORCH)
                                .findFirst()
                                .ifPresent(stack -> {
                                    Block block = player.getWorld().getBlockAt(player.getLocation());
                                    if (placeTorch(block, 5)) {
                                        stack.setAmount(stack.getAmount() - 1);
                                        player.getWorld().playSound(block.getLocation(), Sound.BLOCK_CANDLE_PLACE, 0.33f, 0.8f);
                                    }
                                })
                        ), 20, 20);
    }
}
