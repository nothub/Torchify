package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Inventory;
import lol.hub.testutils.Item;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The area's stone floor has no block light, so Torchify always finds a spot within reach of the bot.
@ExtendWith(TestServerExtension.class)
class TorchTest {

    private static final int TORCHES = 64;
    private static final Pattern FILLED = Pattern.compile("^Successfully filled (\\d+) block");

    @Test
    void torchifyOffKeepsTheTorches(Area area) {
        Bot bot = botWithTorches(area);

        // Three timer periods at 20 TPS, still one on a server slowed to 7 TPS.
        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == TORCHES);
    }

    @Test
    void torchifyOnPlacesTorches(TestServer server, Area area) {
        Bot bot = botWithTorches(area);
        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.waitUntil(Duration.ofSeconds(10), () -> torches(bot.inventory()), count -> count < TORCHES);
        // Toggled off, the timer places nothing more, so inventory and world can be compared.
        assertTrue(bot.run("torchify"), "torchify command");
        int used = TORCHES - torches(bot.inventory());

        assertEquals(used, removeTorchBlocks(server, area), "torch blocks in the area");
    }

    private static Bot botWithTorches(Area area) {
        Bot bot = area.bot();
        bot.hold("minecraft:torch " + TORCHES);
        assertEquals(TORCHES, torches(bot.inventory()), "torches before the test");
        return bot;
    }

    // Torchify reaches 4.5 blocks from the bot's eyes, all inside the area, and on the open floor a torch
    // stands at dy 1. Replacing every torch with air answers with the count, which no query command gives.
    static int removeTorchBlocks(TestServer server, Area area) {
        String reply = server.command("fill " + area.at(0, -4, 0) + " " + area.at(15, 5, 15) + " air replace minecraft:torch");
        if (reply.startsWith("No blocks were filled")) return 0;
        Matcher filled = FILLED.matcher(reply);
        assertTrue(filled.find(), "fill answered: " + reply);
        return Integer.parseInt(filled.group(1));
    }

    static int torches(Inventory inventory) {
        return inventory.slots().values().stream()
                .filter(item -> item.id().equals("minecraft:torch"))
                .mapToInt(Item::count)
                .sum();
    }
}
