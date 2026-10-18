package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.InDimension;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static lol.hub.torchify.SpotTest.TORCHES;
import static lol.hub.torchify.SpotTest.awaitLight;
import static lol.hub.torchify.SpotTest.botWithTorches;
import static lol.hub.torchify.SpotTest.placeOneTorch;
import static lol.hub.torchify.SpotTest.sealedBox;
import static lol.hub.torchify.TorchTest.torches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// torchify:limit_5 is declared in testutils-dimensions.json: the overworld type with a spawn light limit of 5.
@ExtendWith(TestServerExtension.class)
class DimensionTest {

    @Test
    void netherKeepsTheTorches(TestServer server, @InDimension("minecraft:the_nether") Area area) {
        assertEquals(15, server.dimension("minecraft:the_nether").monsterSpawnBlockLightLimit(), "nether limit");
        Bot bot = botWithTorches(area);

        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == TORCHES);
    }

    @Test
    void cellAtLightThreeGetsATorchUnderLimitFive(TestServer server, @InDimension("torchify:limit_5") Area area) {
        Bot bot = botWithTorches(area);
        Pos cell = onlyCandidateAt(server, area, 3);

        placeOneTorch(bot);

        assertEquals("minecraft:torch", server.block(cell).type(), "block at the candidate cell");
    }

    @Test
    void cellAtLightSixKeepsTheTorchesUnderLimitFive(TestServer server, @InDimension("torchify:limit_5") Area area) {
        Bot bot = botWithTorches(area);
        onlyCandidateAt(server, area, 6);

        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == TORCHES);
    }

    // Light blocks fill the bot's reach and are never candidates, so the one cleared floor cell is the only one.
    // Its neighbours emit one more than the level it gets.
    private static Pos onlyCandidateAt(TestServer server, Area area, int level) {
        // A misspelt override key leaves the overworld's 0, which only the light-3 placement would show.
        assertEquals(5, server.dimension("torchify:limit_5").monsterSpawnBlockLightLimit(), "declared limit");
        sealedBox(area);
        area.command("fill " + area.at(4, 1, 4) + " " + area.at(11, 6, 11) + " light[level=" + (level + 1) + "]");
        Pos cell = area.at(6, 1, 8);
        area.command("setblock " + cell + " air");
        awaitLight(area, Map.of(cell, level));
        return cell;
    }
}
