/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins.boosts;

import com.google.common.collect.ObjectArrays;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.BoostedLevelChanged;
import net.runelite.api.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@PluginDescriptor(
	name = "Boosts Information"
)
@Slf4j
public class BoostsPlugin extends Plugin
{
	private static final Skill[] COMBAT = new Skill[]
	{
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC
	};
	private static final Skill[] SKILLING = new Skill[]
	{
		Skill.MINING, Skill.AGILITY, Skill.SMITHING, Skill.HERBLORE, Skill.FISHING, Skill.THIEVING,
		Skill.COOKING, Skill.CRAFTING, Skill.FIREMAKING, Skill.FLETCHING, Skill.WOODCUTTING, Skill.RUNECRAFT,
		Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER
	};

	private final int[] lastSkillLevels = new int[Skill.values().length - 1];

	@Getter
	private Instant lastChange;

	@Inject
	private Client client;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private BoostsOverlay boostsOverlay;

	@Inject
	private BoostsConfig config;

	@Inject
	private SkillIconManager skillIconManager;

	@Getter
	private Skill[] shownSkills;

	private StatChangeIndicator statChangeIndicator;

	private BufferedImage overallIcon;

	@Provides
	BoostsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BoostsConfig.class);
	}

	@Override
	public Overlay getOverlay()
	{
		return boostsOverlay;
	}

	@Override
	protected void startUp()
	{
		updateShownSkills(config.enableSkill());
		Arrays.fill(lastSkillLevels, -1);
		overallIcon = skillIconManager.getSkillImage(Skill.OVERALL);
	}

	@Override
	protected void shutDown() throws Exception
	{
		infoBoxManager.removeIf(t -> t instanceof BoostIndicator || t instanceof StatChangeIndicator);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("boosts"))
		{
			return;
		}

		if (event.getKey().equals("displayIndicators") || event.getKey().equals("displayNextChange"))
		{
			addStatChangeIndicator();
			return;
		}

		Skill[] old = shownSkills;
		updateShownSkills(config.enableSkill());

		if (!Arrays.equals(old, shownSkills))
		{
			infoBoxManager.removeIf(t -> t instanceof BoostIndicator
				&& !Arrays.asList(shownSkills).contains(((BoostIndicator) t).getSkill()));
		}
	}

	@Subscribe
	void onBoostedLevelChange(BoostedLevelChanged boostedLevelChanged)
	{
		Skill skill = boostedLevelChanged.getSkill();

		// Ignore changes to hitpoints or prayer
		if (skill == Skill.HITPOINTS || skill == Skill.PRAYER)
		{
			return;
		}

		int skillIdx = skill.ordinal();
		int last = lastSkillLevels[skillIdx];
		int cur = client.getBoostedSkillLevel(skill);

		// Check if stat goes +1 or -2
		if (cur == last + 1 || cur == last - 1)
		{
			log.debug("Skill {} healed", skill);
			lastChange = Instant.now();
			addStatChangeIndicator();
		}
		lastSkillLevels[skillIdx] = cur;
	}

	private void updateShownSkills(boolean showSkillingSkills)
	{
		if (showSkillingSkills)
		{
			shownSkills = ObjectArrays.concat(COMBAT, SKILLING, Skill.class);
		}
		else
		{
			shownSkills = COMBAT;
		}
	}

	public void addStatChangeIndicator()
	{
		infoBoxManager.removeInfoBox(statChangeIndicator);
		statChangeIndicator = null;

		if (lastChange != null
			&& config.displayIndicators()
			&& config.displayNextChange())
		{
			statChangeIndicator = new StatChangeIndicator(getChangeTime(), ChronoUnit.SECONDS, overallIcon, this);
			infoBoxManager.addInfoBox(statChangeIndicator);
		}
	}

	public int getChangeTime()
	{
		return 60 - (int) Duration.between(lastChange, Instant.now()).getSeconds();
	}
}
