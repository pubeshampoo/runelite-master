/*
 * Copyright (c) 2018, Kruithne <kruithne@gmail.com>
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
package net.runelite.client.plugins.skillcalculator;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.skillcalculator.beans.SkillData;
import net.runelite.client.plugins.skillcalculator.beans.SkillDataBonus;
import net.runelite.client.plugins.skillcalculator.beans.SkillDataEntry;

class SkillCalculator extends JPanel
{
	private Client client;
	private SkillData skillData;
	private List<UIActionSlot> uiActionSlots = new ArrayList<>();
	private UICalculatorInputArea uiInput;

	private CacheSkillData cacheSkillData = new CacheSkillData();

	static SpriteManager spriteManager;
	static ItemManager itemManager;

	private UICombinedActionSlot combinedActionSlot = new UICombinedActionSlot();
	private ArrayList<UIActionSlot> combinedActionSlots = new ArrayList<>();

	private int currentLevel = 1;
	private int currentXP = Experience.getXpForLevel(currentLevel);
	private int targetLevel = currentLevel + 1;
	private int targetXP = Experience.getXpForLevel(targetLevel);
	private float xpFactor = 1.0f;

	private static int MAX_XP = Experience.getXpForLevel(Experience.MAX_VIRT_LEVEL);

	private static DecimalFormat XP_FORMAT = new DecimalFormat("#.#");

	SkillCalculator(Client client, UICalculatorInputArea uiInput)
	{
		this.client = client;
		this.uiInput = uiInput;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createLineBorder(getBackground().brighter()));

		// Register listeners on the input fields..
		uiInput.uiFieldCurrentLevel.addActionListener(e -> onFieldCurrentLevelUpdated());
		uiInput.uiFieldCurrentXP.addActionListener(e -> onFieldCurrentXPUpdated());
		uiInput.uiFieldTargetLevel.addActionListener(e -> onFieldTargetLevelUpdated());
		uiInput.uiFieldTargetXP.addActionListener(e -> onFieldTargetXPUpdated());
	}

	void openCalculator(CalculatorType calculatorType)
	{
		// Load the skill data.
		skillData = cacheSkillData.getSkillData(calculatorType.getDataFile());

		// Reset the XP factor, removing bonuses.
		xpFactor = 1.0f;

		// Update internal skill/XP values.
		currentXP = client.getSkillExperience(calculatorType.getSkill());
		currentLevel = Experience.getLevelForXp(currentXP);
		targetLevel = enforceSkillBounds(currentLevel + 1);
		targetXP = Experience.getXpForLevel(targetLevel);

		// Remove all components (action slots) from this panel.
		removeAll();

		// Add in checkboxes for available skill bonuses.
		renderBonusOptions();

		// Add the combined action slot.
		add(combinedActionSlot);

		// Create action slots for the skill actions.
		renderActionSlots();

		// Update the input fields.
		updateInputFields();
	}

	private void updateCombinedAction()
	{
		int size = combinedActionSlots.size();
		if (size > 1)
		{
			combinedActionSlot.setTitle(size + " actions selected");
		}
		else if (size == 1)
		{
			combinedActionSlot.setTitle("1 action selected");
		}
		else
		{
			combinedActionSlot.setTitle("No action selected");
			combinedActionSlot.setText("Shift-click to select multiple");
			return;
		}

		int actionCount = 0;
		int neededXP = targetXP - currentXP;
		double xp = 0;

		for (UIActionSlot slot : combinedActionSlots)
			xp += slot.value;

		if (neededXP > 0)
			actionCount = (int) Math.ceil(neededXP / xp);

		combinedActionSlot.setText(formatXPActionString(xp, actionCount));
	}

	private void clearCombinedSlots()
	{
		for (UIActionSlot slot : combinedActionSlots)
			slot.setSelected(false);

		combinedActionSlots.clear();
	}

	private void renderBonusOptions()
	{
		if (skillData.bonuses != null)
		{
			for (SkillDataBonus bonus : skillData.bonuses)
			{
				JPanel uiOption = new JPanel(new BorderLayout());
				JLabel uiLabel = new JLabel(bonus.name);
				JCheckBox uiCheckbox = new JCheckBox();

				// Adding an empty 8-pixel border on the left gives us nice padding.
				uiOption.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

				// Adjust XP bonus depending on check-state of the boxes.
				uiCheckbox.addActionListener(e -> adjustXPBonus(uiCheckbox.isSelected(), bonus.value));

				uiOption.add(uiLabel, BorderLayout.WEST);
				uiOption.add(uiCheckbox, BorderLayout.EAST);
				add(uiOption);
			}
		}
	}

	private void renderActionSlots()
	{
		// Wipe the list of references to the slot components.
		uiActionSlots.clear();

		// Create new components for the action slots.
		for (SkillDataEntry action : skillData.actions)
		{
			UIActionSlot slot = new UIActionSlot(action);
			uiActionSlots.add(slot); // Keep our own reference.
			add(slot); // Add component to the panel.

			slot.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (!e.isShiftDown())
						clearCombinedSlots();

					if (slot.isSelected)
						combinedActionSlots.remove(slot);
					else
						combinedActionSlots.add(slot);

					slot.setSelected(!slot.isSelected);
					updateCombinedAction();
				}
			});
		}

		// Refresh the rendering of this panel.
		revalidate();
		repaint();
	}

	private void calculate()
	{
		for (UIActionSlot slot : uiActionSlots)
		{
			int actionCount = 0;
			int neededXP = targetXP - currentXP;
			double xp = slot.action.xp * xpFactor;

			if (neededXP > 0)
				actionCount = (int) Math.ceil(neededXP / xp);

			slot.setText(formatXPActionString(xp, actionCount));
			slot.setAvailable(currentLevel >= slot.action.level);
			slot.value = xp;
		}
	}

	private String formatXPActionString(double xp, int actionCount)
	{
		return XP_FORMAT.format(xp) + "xp - " + NumberFormat.getIntegerInstance().format(actionCount) + (actionCount > 1 ? " actions" : " action");
	}

	private void updateInputFields()
	{
		if (targetXP < currentXP)
		{
			targetLevel = enforceSkillBounds(currentLevel + 1);
			targetXP = Experience.getXpForLevel(targetLevel);
		}

		uiInput.setCurrentLevelInput(currentLevel);
		uiInput.setCurrentXPInput(currentXP);
		uiInput.setTargetLevelInput(targetLevel);
		uiInput.setTargetXPInput(targetXP);
		calculate();
	}

	private void adjustXPBonus(boolean addBonus, float value)
	{
		xpFactor += addBonus ? value : -value;
		calculate();
	}

	private void onFieldCurrentLevelUpdated()
	{
		currentLevel = enforceSkillBounds(uiInput.getCurrentLevelInput());
		currentXP = Experience.getXpForLevel(currentLevel);
		updateInputFields();
	}

	private void onFieldCurrentXPUpdated()
	{
		currentXP = enforceXPBounds(uiInput.getCurrentXPInput());
		currentLevel = Experience.getLevelForXp(currentXP);
		updateInputFields();
	}

	private void onFieldTargetLevelUpdated()
	{
		targetLevel = enforceSkillBounds(uiInput.getTargetLevelInput());
		targetXP = Experience.getXpForLevel(targetLevel);
		updateInputFields();
	}

	private void onFieldTargetXPUpdated()
	{
		targetXP = enforceXPBounds(uiInput.getTargetXPInput());
		targetLevel = Experience.getLevelForXp(targetXP);
		updateInputFields();
	}

	private static int enforceSkillBounds(int input)
	{
		return Math.min(Experience.MAX_VIRT_LEVEL, Math.max(1, input));
	}

	private static int enforceXPBounds(int input)
	{
		return Math.min(MAX_XP, Math.max(0, input));
	}
}