package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Block;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static lol.hub.torchify.PriorityTest.give;
import static lol.hub.torchify.PriorityTest.onlyCandidate;
import static lol.hub.torchify.PriorityTest.onlyLightLanded;
import static lol.hub.torchify.PriorityTest.useOne;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(TestServerExtension.class)
class HangingTest {

    private static final String LANTERN = "minecraft:lantern";

    @Test
    void lanternHangsWhereNoFloorIsInReach(TestServer server, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, LANTERN);
        // 3.94 from the eyes, under the box's ceiling at dy 7. The light block below gives no support.
        Pos cell = area.at(8, 6, 8);
        onlyCandidate(area, cell);

        assertEquals(LANTERN, useOne(area, bot), "item used");
        assertEquals(lantern(true), server.block(cell), "block at " + cell);
        onlyLightLanded(area, LANTERN);
    }

    @Test
    void lanternStandsWhereFloorAndCeilingFit(TestServer server, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, LANTERN);
        Pos cell = area.at(6, 1, 8);
        onlyCandidate(area, cell);
        // Above the cell, so a hanging lantern would fit there too.
        area.command("setblock " + area.at(6, 2, 8) + " stone");

        assertEquals(LANTERN, useOne(area, bot), "item used");
        assertEquals(lantern(false), server.block(cell), "block at " + cell);
        onlyLightLanded(area, LANTERN);
    }

    private static Block lantern(boolean hanging) {
        return new Block(LANTERN, Map.of("hanging", String.valueOf(hanging), "waterlogged", "false"));
    }
}
