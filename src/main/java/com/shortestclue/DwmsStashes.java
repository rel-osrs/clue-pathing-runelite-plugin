package com.shortestclue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import net.runelite.client.plugins.cluescrolls.clues.item.SlotLimitationRequirement;

/**
 * Which STASH units hold their items, as the "Dude, Where's My Stuff?" plugin tracks them. It's asked with its
 * "storages-request" plugin message and answers with a "storages-response" holding every non-empty storage it
 * tracks; the STASH units are those of category "stash". Without that plugin no unit is known to be filled.
 *
 * <p>The answer names a STASH unit only by its location ("Catherby" is four units), so a unit is matched to an emote
 * clue by what it holds instead: every item meets one of the clue's requirements, and together they meet them all.
 * Client thread.
 */
public class DwmsStashes
{
	static final String NAMESPACE = "dudewheresmystuff";
	static final String REQUEST = "storages-request";
	static final String RESPONSE = "storages-response";
	static final String SOURCE = "Shortest Clue";
	static final String STASH_CATEGORY = "stash";

	/**
	 * Game ticks between requests while an emote clue is open, since the plugin doesn't say when a storage changes.
	 */
	private static final int REQUEST_TICKS = 5;

	/**
	 * Game ticks a new emote clue's path waits for the first answer. The answer comes within a client frame or two.
	 */
	private static final int ANSWER_TICKS = 2;

	private final Client client;
	private final EventBus eventBus;

	/**
	 * What each non-empty STASH unit holds, from the latest answer.
	 */
	private List<Item[]> stashes = List.of();

	/**
	 * Whether the plugin answered since this one started, so it's installed and its answers are worth waiting for.
	 */
	private boolean answered;

	private EmoteClue askedClue;
	private int requestTick;

	/**
	 * Whether the request sent for a new emote clue is still waiting for its answer.
	 */
	private boolean waitingForClue;

	@Inject
	DwmsStashes(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void reset()
	{
		stashes = List.of();
		answered = false;
		askedClue = null;
		waitingForClue = false;
	}

	/**
	 * Asks the plugin for its storages. It answers on the client thread, if it's installed and on.
	 */
	public void request()
	{
		requestTick = client.getTickCount();
		Map<String, Object> data = new HashMap<>();
		data.put("source", SOURCE);
		eventBus.post(new PluginMessage(NAMESPACE, REQUEST, data));
	}

	/**
	 * Keeps the answer fresh while an emote clue is open: asks at once for a new clue, then every few ticks.
	 *
	 * @return true while a new clue's path should wait for the answer, so it doesn't visit a bank first for items
	 * that wait in the STASH unit
	 */
	public boolean waitForAnswer(EmoteClue clue)
	{
		if (clue != askedClue)
		{
			askedClue = clue;
			waitingForClue = true;
			request();
		}
		else if (client.getTickCount() - requestTick >= REQUEST_TICKS)
		{
			request();
		}
		return answered && waitingForClue && client.getTickCount() - requestTick < ANSWER_TICKS;
	}

	/**
	 * True if the clue's STASH unit is known to hold its items.
	 */
	public boolean isFilled(EmoteClue clue)
	{
		if (clue.getStashUnit() == null)
		{
			return false;
		}
		ItemRequirement[] requirements = clue.getItemRequirements();
		for (Item[] stash : stashes)
		{
			if (holds(stash, requirements))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onPluginMessage(final PluginMessage event)
	{
		Map<String, Object> data = event.getData();
		if (!NAMESPACE.equals(event.getNamespace()) || !RESPONSE.equals(event.getName()) || data == null
			|| !SOURCE.equals(data.get("target")))
		{
			return;
		}

		stashes = stashItems(data.get("storages"));
		answered = true;
		waitingForClue = false;
	}

	/**
	 * What each STASH unit in a "storages-response" holds, leaving out the plugin's other storages.
	 *
	 * @param storages the answer's "storages": maps with a "category" and "items", each item a map with an "id" and a
	 *                 "quantity"
	 */
	static List<Item[]> stashItems(Object storages)
	{
		List<Item[]> stashes = new ArrayList<>();
		if (!(storages instanceof Collection))
		{
			return stashes;
		}

		for (Object storage : (Collection<?>) storages)
		{
			if (!(storage instanceof Map) || !STASH_CATEGORY.equals(((Map<?, ?>) storage).get("category")))
			{
				continue;
			}
			Object items = ((Map<?, ?>) storage).get("items");
			if (!(items instanceof Collection))
			{
				continue;
			}

			List<Item> stash = new ArrayList<>();
			for (Object item : (Collection<?>) items)
			{
				if (!(item instanceof Map))
				{
					continue;
				}
				Object id = ((Map<?, ?>) item).get("id");
				Object quantity = ((Map<?, ?>) item).get("quantity");
				if (id instanceof Number && quantity instanceof Number && ((Number) id).intValue() > 0
					&& ((Number) quantity).longValue() > 0)
				{
					stash.add(new Item(((Number) id).intValue(),
						(int) Math.min(Integer.MAX_VALUE, ((Number) quantity).longValue())));
				}
			}
			if (!stash.isEmpty())
			{
				stashes.add(stash.toArray(new Item[0]));
			}
		}
		return stashes;
	}

	/**
	 * Whether a STASH unit holding these items is the one for a clue with these requirements: every item meets one of
	 * them, and together the items meet them all. A requirement about an empty slot is about what's worn, so it's
	 * left out.
	 */
	static boolean holds(Item[] stash, ItemRequirement[] requirements)
	{
		boolean anyItems = false;
		for (ItemRequirement requirement : requirements)
		{
			if (requirement instanceof SlotLimitationRequirement)
			{
				continue;
			}
			if (!requirement.fulfilledBy(stash))
			{
				return false;
			}
			anyItems = true;
		}
		if (!anyItems)
		{
			return false;
		}

		for (Item item : stash)
		{
			boolean needed = false;
			for (ItemRequirement requirement : requirements)
			{
				if (requirement.fulfilledBy(item.getId()))
				{
					needed = true;
					break;
				}
			}
			if (!needed)
			{
				return false;
			}
		}
		return true;
	}
}
