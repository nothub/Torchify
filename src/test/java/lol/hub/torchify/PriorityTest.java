package lol.hub.torchify;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Inventory;
import lol.hub.testutils.Item;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static lol.hub.torchify.SpotTest.awaitLight;
import static lol.hub.torchify.SpotTest.sealedBox;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// One light lifts every cell in reach above the overworld limit 0, so each placement needs its own area and bot.
@ExtendWith(TestServerExtension.class)
class PriorityTest {

    private static final int COUNT = 16;
    private static final Pattern FILLED = Pattern.compile("^Successfully filled (\\d+) block");

    // Each item names its own block, so a placed light is found by the item id.
    private static final List<String> TABLE = List.of(
            "minecraft:waxed_copper_lantern", "minecraft:waxed_exposed_copper_lantern",
            "minecraft:waxed_weathered_copper_lantern", "minecraft:waxed_oxidized_copper_lantern",
            "minecraft:copper_lantern", "minecraft:exposed_copper_lantern",
            "minecraft:weathered_copper_lantern", "minecraft:oxidized_copper_lantern",
            "minecraft:lantern", "minecraft:copper_torch", "minecraft:torch");

    @Test
    void bestGroupGoesFirst(TestServer server) {
        // Highest priority last, so inventory order alone would pick the torch.
        List<String> carried = List.of(
                "minecraft:torch", "minecraft:copper_torch", "minecraft:lantern",
                "minecraft:copper_lantern", "minecraft:waxed_copper_lantern");
        for (int best = carried.size() - 1; best >= 0; best--) {
            try (Area area = server.area()) {
                Bot bot = area.bot();
                List<String> items = carried.subList(0, best + 1);
                for (int slot = 0; slot < items.size(); slot++) give(area, bot, slot, items.get(slot));

                assertEquals(items.get(best), placeOne(area, bot), "light placed with " + items);
            }
        }
    }

    @Test
    void inventoryOrderDecidesWithinAGroup(Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, "minecraft:oxidized_copper_lantern");
        give(area, bot, 1, "minecraft:copper_lantern");

        assertEquals("minecraft:oxidized_copper_lantern", placeOne(area, bot), "light placed");
    }

    @ParameterizedTest
    @FieldSource("TABLE")
    void usedItemLandsAsItsBlock(String item, Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, item);

        assertEquals(item, placeOne(area, bot), "light placed");
    }

    @Test
    void lanternBlockedByTheBotFallsBackToATorch(Area area) {
        Bot bot = botWithLanternAndTorches(area);
        Pos feet = area.at(8, 1, 8);
        // At a block centre the bot's box (x.2 to x.8) overlaps a lantern's (x.3125 to x.6875).
        String reply = area.command("tp " + bot.name() + " " + (feet.x() + 0.5) + " " + feet.y() + " " + (feet.z() + 0.5));
        assertTrue(reply.startsWith("Teleported"), "tp answered: " + reply);
        onlyCandidate(area, feet);

        assertEquals("minecraft:torch", placeOne(area, bot), "light placed");
    }

    @Test
    void lanternFitsBesideTheBotAtABlockCorner(Area area) {
        Bot bot = botWithLanternAndTorches(area);
        // At a block corner the bot's box (x-0.3 to x+0.3) misses the lantern's (x.3125 to x.6875), though it reaches
        // into the cell.
        onlyCandidate(area, area.at(8, 1, 8));

        assertEquals("minecraft:lantern", placeOne(area, bot), "light placed");
    }

    @ParameterizedTest
    @CsvSource({"0b, minecraft:torch", "1b, minecraft:lantern"})
    void armorStandBlocksTheLanternUnlessAMarker(String marker, String light, Area area) {
        Bot bot = botWithLanternAndTorches(area);
        Pos cell = area.at(6, 1, 8);
        onlyCandidate(area, cell);
        // Raised into the lantern's box: a marker's box has no size, so on the floor it would only touch the lantern's.
        String reply = area.command("summon armor_stand " + (cell.x() + 0.5) + " " + (cell.y() + 0.25) + " " + (cell.z() + 0.5)
                + " {NoGravity:1b,Marker:" + marker + "}");
        assertTrue(reply.startsWith("Summoned"), "summon answered: " + reply);

        assertEquals(light, placeOne(area, bot), "light placed");
    }

    private static Bot botWithLanternAndTorches(Area area) {
        Bot bot = area.bot();
        give(area, bot, 0, "minecraft:lantern");
        give(area, bot, 1, "minecraft:torch");
        return bot;
    }

    // Light blocks fill the reach, so the one cleared cell is the only candidate, and any light found in the area
    // stands there.
    static void onlyCandidate(Area area, Pos cell) {
        sealedBox(area);
        area.command("fill " + area.at(4, 1, 4) + " " + area.at(11, 6, 11) + " light[level=1]");
        area.command("setblock " + cell + " air");
        awaitLight(area, Map.of(cell, 0));
    }

    static void give(Area area, Bot bot, int slot, String item) {
        String reply = area.command("item replace entity " + bot.name() + " hotbar." + slot + " with " + item + " " + COUNT);
        assertTrue(reply.startsWith("Replaced 1 slot(s)"), "give " + item + " answered: " + reply);
    }

    private static String placeOne(Area area, Bot bot) {
        String item = useOne(area, bot);
        onlyLightLanded(area, item);
        return item;
    }

    // Toggles Torchify on until one item is used, then off, and returns the one item used.
    static String useOne(Area area, Bot bot) {
        Map<String, Integer> before = counts(bot.inventory());
        int carried = before.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(bot.run("torchify"), "torchify command");
        Waiting.waitUntil(Duration.ofSeconds(10), () -> total(bot.inventory()), count -> count < carried);
        assertTrue(bot.run("torchify"), "torchify command");

        Map<String, Integer> after = counts(bot.inventory());
        List<String> used = before.keySet().stream().filter(id -> after.getOrDefault(id, 0) < before.get(id)).toList();
        assertEquals(1, used.size(), "item kinds used, before " + before + ", after " + after);
        String item = used.getFirst();
        assertEquals(before.get(item) - 1, after.getOrDefault(item, 0), item + " used");
        return item;
    }

    // Removes every light in the area, asserting one block of the item and none of any other.
    static void onlyLightLanded(Area area, String item) {
        for (String light : TABLE) {
            assertEquals(light.equals(item) ? 1 : 0, removeBlocks(area, light), light + " blocks in the area");
        }
    }

    private static Map<String, Integer> counts(Inventory inventory) {
        return inventory.slots().values().stream()
                .collect(Collectors.toMap(Item::id, Item::count, Integer::sum));
    }

    private static int total(Inventory inventory) {
        return inventory.slots().values().stream().mapToInt(Item::count).sum();
    }

    // Reach is 4.5 blocks from the eyes, all inside the area. Replacing the block with air answers with the count,
    // which no query command gives.
    private static int removeBlocks(Area area, String block) {
        String reply = area.command("fill " + area.at(0, -4, 0) + " " + area.at(15, 7, 15) + " air replace " + block);
        if (reply.startsWith("No blocks were filled")) return 0;
        Matcher filled = FILLED.matcher(reply);
        assertTrue(filled.find(), "fill answered: " + reply);
        return Integer.parseInt(filled.group(1));
    }
}
