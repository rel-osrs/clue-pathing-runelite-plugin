package com.shortestclue;

import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.all;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.any;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.emptySlot;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.item;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import org.junit.Test;

public class DwmsStashesTest
{
	private static final int HAT = 1;
	private static final int TOP = 2;
	private static final int RING = 3;
	private static final int RING_IMBUED = 4;
	private static final int BOOTS = 5;

	private static final ItemRequirement[] OUTFIT = {
		item(HAT),
		all(item(TOP), any("Ring", item(RING), item(RING_IMBUED))),
		emptySlot("Nothing on your legs", EquipmentInventorySlot.LEGS),
	};

	private static Item[] items(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		return items;
	}

	private static Map<String, Object> storage(String category, int... ids)
	{
		List<Map<String, Object>> items = new ArrayList<>();
		for (int id : ids)
		{
			items.add(Map.of("id", id, "quantity", 1L));
		}
		return Map.of("category", category, "name", "Catherby", "lastUpdated", -1L, "items", items);
	}

	@Test
	public void aUnitHoldingTheOutfitIsTheClues()
	{
		assertTrue(DwmsStashes.holds(items(HAT, TOP, RING_IMBUED), OUTFIT));
	}

	@Test
	public void aUnitMissingAnItemIsNot()
	{
		assertFalse(DwmsStashes.holds(items(HAT, TOP), OUTFIT));
	}

	@Test
	public void aUnitHoldingAnItemTheClueDoesntNeedIsAnotherUnit()
	{
		assertFalse(DwmsStashes.holds(items(HAT, TOP, RING, BOOTS), OUTFIT));
	}

	@Test
	public void aClueNeedingOnlyEmptySlotsHasNoUnitHoldingItsItems()
	{
		ItemRequirement[] nothing = {emptySlot("Nothing on your head", EquipmentInventorySlot.HEAD)};
		assertFalse(DwmsStashes.holds(items(HAT), nothing));
	}

	@Test
	public void onlyStashStoragesAreRead()
	{
		List<Item[]> stashes = DwmsStashes.stashItems(List.of(
			storage("stash", HAT, TOP),
			storage("carryable", BOOTS),
			storage("stash"),
			Map.of("category", "stash"),
			"not a storage"));
		assertEquals(1, stashes.size());
		assertEquals(List.of(new Item(HAT, 1), new Item(TOP, 1)), List.of(stashes.get(0)));
	}

	@Test
	public void badItemsAreSkipped()
	{
		List<Item[]> stashes = DwmsStashes.stashItems(List.of(Map.of("category", "stash", "items", List.of(
			Map.of("id", HAT, "quantity", 1L),
			Map.of("id", TOP, "quantity", 0L),
			Map.of("id", "x", "quantity", 1L),
			Map.of("id", RING),
			"not an item"))));
		assertEquals(List.of(new Item(HAT, 1)), List.of(stashes.get(0)));
	}

	@Test
	public void noStoragesIsNoStashes()
	{
		assertEquals(List.of(), DwmsStashes.stashItems(null));
	}
}
