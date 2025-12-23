
package dev.shared.do_gamer.utils;

import java.util.Locale;

import eu.darkbot.api.game.items.ItemCategory;
import eu.darkbot.api.game.items.SelectableItem;

// Custom ability enum to support both original and custom abilities.
public enum CustomAbility implements SelectableItem {
    SOLARIS_INC(Ability.SOLARIS_INC),
    SOLARIS_PLUS_INCINERATE_PLUS(Ability.SOLARIS_PLUS_INCINERATE_PLUS),
    PALADIN_RIPPER(null);

    public final Ability baseAbility;

    CustomAbility(Ability baseAbility) {
        this.baseAbility = baseAbility;
    }

    @Override
    public String getId() {
        if (this.baseAbility != null) {
            return this.baseAbility.getId();
        }
        // Fallback for custom abilities
        return "ability_" + this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ItemCategory getCategory() {
        return ItemCategory.SHIP_ABILITIES;
    }
}
