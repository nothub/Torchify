package lol.hub.torchify;

import org.bukkit.GameMode;
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
                        .forEach(Placement::lightOneSpot), 20, 20);
    }
}
