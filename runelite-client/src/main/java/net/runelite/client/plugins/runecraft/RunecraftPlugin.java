/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.runecraft;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.queries.InventoryItemQuery;
import net.runelite.api.queries.NPCQuery;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.QueryRunner;

@PluginDescriptor(
	name = "Runecraft"
)
public class RunecraftPlugin extends Plugin
{
	private static Pattern bindNeckString = Pattern.compile("You have ([0-9]+) charges left before your Binding necklace disintegrates.");

	@Getter(AccessLevel.PACKAGE)
	private final Set<DecorativeObject> abyssObjects = new HashSet<>();

	@Getter(AccessLevel.PACKAGE)
	private boolean degradedPouchInInventory;

	@Getter(AccessLevel.PACKAGE)
	private NPC darkMage;

	@Inject
	private RunecraftOverlay overlay;

	@Inject
	private BindNeckOverlay bindNeckOverlay;

	@Inject
	private AbyssOverlay abyssOverlay;

	@Inject
	private QueryRunner queryRunner;

	@Inject
	private RunecraftConfig config;

	@Provides
	RunecraftConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RunecraftConfig.class);
	}

	@Override
	public Collection<Overlay> getOverlays()
	{
		return Arrays.asList(overlay, bindNeckOverlay, abyssOverlay);
	}

	@Override
	protected void startUp() throws Exception
	{
		abyssOverlay.updateConfig();
	}

	@Override
	protected void shutDown() throws Exception
	{
		abyssObjects.clear();
		darkMage = null;
		degradedPouchInInventory = false;
	}

	@Subscribe
	public void updateConfig(ConfigChanged event)
	{
		abyssOverlay.updateConfig();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SERVER || !config.showBindNeck())
		{
			return;
		}

		Matcher match = bindNeckString.matcher(event.getMessage());
		if (match.find())
		{
			bindNeckOverlay.bindingCharges = Integer.parseInt(match.group(1));
			return;
		}

		if (event.getMessage().contains("You bind the temple's power"))
		{
			if (event.getMessage().contains("mud")
				|| event.getMessage().contains("lava")
				|| event.getMessage().contains("steam")
				|| event.getMessage().contains("dust")
				|| event.getMessage().contains("smoke")
				|| event.getMessage().contains("mist"))
			{
				bindNeckOverlay.bindingCharges -= 1;
				return;
			}
		}

		if (event.getMessage().contains("Your Binding necklace has disintegrated."))
		{
			//set it to 17 because this message is triggered first before the above chat event
			bindNeckOverlay.bindingCharges = 17;
		}
	}

	@Subscribe
	public void onDecorativeObjectSpawn(DecorativeObjectSpawned event)
	{
		DecorativeObject decorativeObject = event.getDecorativeObject();
		if (AbyssRifts.getRift(decorativeObject.getId()) != null)
		{
			abyssObjects.add(decorativeObject);
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		DecorativeObject decorativeObject = event.getDecorativeObject();
		abyssObjects.remove(decorativeObject);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			abyssObjects.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		darkMage = null;

		if (!config.hightlightDarkMage())
		{
			return;
		}

		Query inventoryQuery = new InventoryItemQuery(InventoryID.INVENTORY).idEquals(
			ItemID.MEDIUM_POUCH_5511,
			ItemID.LARGE_POUCH_5513,
			ItemID.GIANT_POUCH_5515
		);

		Item[] items = queryRunner.runQuery(inventoryQuery);
		degradedPouchInInventory = items != null && items.length > 0;

		if (degradedPouchInInventory)
		{
			Query darkMageQuery = new NPCQuery().idEquals(NpcID.DARK_MAGE);
			NPC[] result = queryRunner.runQuery(darkMageQuery);
			darkMage = result.length >= 1 ? result[0] : null;
		}
	}
}
