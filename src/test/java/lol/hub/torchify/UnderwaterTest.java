package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Block;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static lol.hub.torchify.PriorityTest.give;
import static lol.hub.torchify.PriorityTest.onlyLightLanded;
import static lol.hub.torchify.PriorityTest.useOne;
import static lol.hub.torchify.SpotTest.awaitLight;
import static lol.hub.torchify.SpotTest.botWithTorches;
import static lol.hub.torchify.SpotTest.sealedBox;
import static lol.hub.torchify.TorchTest.removeTorchBlocks;
import static lol.hub.torchify.TorchTest.torches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TestServerExtension.class)
class UnderwaterTest {

    private static final String LANTERN = "minecraft:lantern";

    @Test
    void lanternStandsUnderwaterBehindWater(TestServer server, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, LANTERN);
        // The ray from the eyes crosses the water of the cells at x 7 and 6 before it reaches the cell.
        Pos cell = area.at(5, 1, 8);
        onlyWaterCandidate(area, cell);

        assertEquals(LANTERN, useOne(area, bot), "item used");
        assertEquals(lantern(false), server.block(cell), "block at " + cell);
        onlyLightLanded(area, LANTERN);
    }

    @Test
    void torchesStayOutOfWater(TestServer server, Area area) {
        Bot bot = botWithTorches(area);
        Pos cell = area.at(5, 1, 8);
        onlyWaterCandidate(area, cell);

        assertTrue(bot.run("torchify"), "torchify command");

        Waiting.holdsFor(Duration.ofSeconds(3), () -> torches(bot.inventory()), count -> count == SpotTest.TORCHES);
        assertEquals(new Block("minecraft:water", Map.of("level", "0")), server.block(cell), "block at " + cell);
        assertEquals(0, removeTorchBlocks(server, area), "torch blocks in the area");
    }

    @Test
    void lanternHangsUnderwaterWhereNoFloorIsInReach(TestServer server, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, LANTERN);
        // Under the box's ceiling at dy 7. The light block below gives no support.
        Pos cell = area.at(8, 6, 8);
        onlyWaterCandidate(area, cell);

        assertEquals(LANTERN, useOne(area, bot), "item used");
        assertEquals(lantern(true), server.block(cell), "block at " + cell);
        onlyLightLanded(area, LANTERN);
    }

    @Test
    void lanternStandsUnderwaterWhereFloorAndCeilingFit(TestServer server, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, LANTERN);
        Pos cell = area.at(6, 1, 8);
        onlyWaterCandidate(area, cell);
        // Above the cell, so a hanging lantern would fit there too.
        area.command("setblock " + area.at(6, 2, 8) + " stone");

        assertEquals(LANTERN, useOne(area, bot), "item used");
        assertEquals(lantern(false), server.block(cell), "block at " + cell);
        onlyLightLanded(area, LANTERN);
    }

    // Waterlogged light blocks fill the reach, the bot's eyes included. They are not water cells, but their water
    // stops a ray that collides with fluids. The one water source left is the only candidate.
    private static void onlyWaterCandidate(Area area, Pos cell) {
        sealedBox(area);
        area.command("fill " + area.at(4, 1, 4) + " " + area.at(11, 6, 11) + " light[level=1,waterlogged=true]");
        area.command("setblock " + cell + " water");
        awaitLight(area, Map.of(cell, 0));
    }

    private static Block lantern(boolean hanging) {
        return new Block(LANTERN, Map.of("hanging", String.valueOf(hanging), "waterlogged", "true"));
    }
}
