package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Inventory;
import lol.hub.testutils.Item;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The area's stone floor has no block light, so Torchify always finds a spot within reach of the bot.
@ExtendWith(TestServerExtension.class)
class TorchTest {

    private static final int TORCHES = 64;

    @Test
    void torchifyOffKeepsTheTorches(Area area) {
        Bot bot = botWithTorches(area);

        // Three timer periods at 20 TPS, still one on a server slowed to 7 TPS.
        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == TORCHES);
    }

    @Test
    void torchifyOnPlacesTorches(Area area) {
        Bot bot = botWithTorches(area);
        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.waitUntil(Duration.ofSeconds(10), () -> torches(bot.inventory()), count -> count < TORCHES);
    }

    private static Bot botWithTorches(Area area) {
        Bot bot = area.bot();
        bot.hold("minecraft:torch " + TORCHES);
        assertEquals(TORCHES, torches(bot.inventory()), "torches before the test");
        return bot;
    }

    private static int torches(Inventory inventory) {
        return inventory.slots().values().stream()
                .filter(item -> item.id().equals("minecraft:torch"))
                .mapToInt(Item::count)
                .sum();
    }
}
