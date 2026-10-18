package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static lol.hub.torchify.TorchTest.removeTorchBlocks;
import static lol.hub.torchify.TorchTest.torches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The bot stands at (8,1,8), so its eyes sit at (8, 2.62, 8). Survival reach is 4.5 blocks from the eyes,
// which covers air cells x/z 4 to 11 and dy 1 to 6. In the overworld the spawn light limit is 0, so one
// torch lights every cell in reach above it and Torchify places exactly one.
@ExtendWith(TestServerExtension.class)
class SpotTest {

    private static final int TORCHES = 64;

    @Test
    void torchLandsOnTheFurthestDarkFloorCellInReach(TestServer server, Area area) {
        Bot bot = botWithTorches(area);
        // A floor cell centre lies 1.12 below the eyes. The furthest ones in reach are 3.5 and 2.5 off the
        // eyes horizontally: 3.5² + 2.5² + 1.12² = 19.75 ≤ 4.5² = 20.25. The next ring out is beyond reach.
        List<Pos> furthest = List.of(
                area.at(4, 1, 5), area.at(4, 1, 10), area.at(11, 1, 5), area.at(11, 1, 10),
                area.at(5, 1, 4), area.at(10, 1, 4), area.at(5, 1, 11), area.at(10, 1, 11));

        placeOneTorch(bot);

        List<Pos> placed = furthest.stream().filter(pos -> server.block(pos).type().equals("minecraft:torch")).toList();
        assertEquals(1, placed.size(), "torches on the furthest floor cells");
        assertEquals(1, removeTorchBlocks(server, area), "torch blocks in the area");
    }

    @Test
    void torchStaysOnTheBotsSideOfAWall(TestServer server, Area area) {
        Bot bot = botWithTorches(area);
        sealedBox(area);
        // Light blocks fill the bot's side, so only the cleared cell there is a candidate. They don't stop the
        // line-of-sight ray. The wall reaches the ceiling, so its top is out of reach.
        area.command("fill " + area.at(4, 1, 4) + " " + area.at(9, 6, 11) + " light[level=1]");
        area.command("fill " + area.at(10, 1, 4) + " " + area.at(10, 6, 11) + " stone");
        Pos near = area.at(6, 1, 8);
        area.command("setblock " + near + " air");
        // Behind the wall, (11,1,8) is 3.71 from the eyes against 1.94 for the near cell, so without the
        // line-of-sight check the torch lands behind the wall.
        awaitLight(area, Map.of(near, 0, area.at(11, 1, 8), 0));

        placeOneTorch(bot);

        assertEquals("minecraft:torch", server.block(near).type(), "block at the near cell");
        assertEquals(1, removeTorchBlocks(server, area), "torch blocks in the area");
    }

    @Test
    void litSurroundingsKeepTheTorches(Area area) {
        Bot bot = botWithTorches(area);
        sealedBox(area);
        // Light falls by 1 per cell, so the box corner (4,1,4), 11 steps away and further than any cell in
        // reach, gets 4.
        area.command("setblock " + area.at(8, 4, 8) + " light[level=15]");
        awaitLight(area, Map.of(area.at(4, 1, 4), 4, area.at(11, 1, 11), 6));

        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == TORCHES);
    }

    private static Bot botWithTorches(Area area) {
        Bot bot = area.bot();
        bot.hold("minecraft:torch " + TORCHES);
        assertEquals(TORCHES, torches(bot.inventory()), "torches before the test");
        return bot;
    }

    // Toggled off after the first torch, so the world can be read without the timer racing it.
    private static void placeOneTorch(Bot bot) {
        assertTrue(bot.run("torchify"), "torchify command");
        Waiting.waitUntil(Duration.ofSeconds(10), () -> torches(bot.inventory()), count -> count < TORCHES);
        assertTrue(bot.run("torchify"), "torchify command");
        assertEquals(TORCHES - 1, torches(bot.inventory()), "torches after the first placement");
    }

    // A stone box around the bot's reach, with air inside. Sky light inside settles to 0, so vanilla's light
    // predicate, which reads the brighter of sky and block light, reads block light.
    private static void sealedBox(Area area) {
        String reply = area.command("fill " + area.at(3, 0, 3) + " " + area.at(12, 7, 12) + " stone hollow");
        assertTrue(reply.startsWith("Successfully filled"), "box fill answered: " + reply);
    }

    // Paper computes light off the main thread, so a fixture is used only once every listed cell reads its level.
    // A second condition in the chain, like `if loaded`, would turn a failed check into an empty reply.
    private static void awaitLight(Area area, Map<Pos, Integer> levels) {
        Waiting.waitUntil(Duration.ofSeconds(10), () -> levels.entrySet().stream()
                .filter(cell -> !lightIs(area, cell.getKey(), cell.getValue()))
                .map(Map.Entry::getKey)
                .toList(), List::isEmpty);
    }

    private static boolean lightIs(Area area, Pos cell, int level) {
        String reply = area.command("execute positioned " + cell
                + " if predicate {type:\"minecraft:location_check\",predicate:{light:{light:" + level + "}}}").strip();
        if (!reply.equals("Test passed") && !reply.equals("Test failed")) {
            throw new IllegalStateException("light check at " + cell + " answered: " + reply);
        }
        return reply.equals("Test passed");
    }
}
