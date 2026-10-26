package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class BetterTooltips extends Module {
    private final BooleanValue foodInfo = this.registerValue(new BooleanValue("food-info", this, true));
    private final BooleanValue durability = this.registerValue(new BooleanValue("durability", this, true));
    private final BooleanValue enchantCount = this.registerValue(new BooleanValue("enchant-count", this, true));
    private final BooleanValue componentCount = this.registerValue(new BooleanValue("component-count", this, true));

    public BetterTooltips() {
        super("better-tooltips", ModuleCategory.RENDER);
    }

    public void appendTooltip(ItemStack stack, List<Text> lines) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (this.foodInfo.getValue()) {
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food != null) {
                lines.add(Text.literal("Food: +" + food.nutrition() + ", saturation " + food.saturation()).formatted(Formatting.GRAY));
            }
        }

        if (this.durability.getValue() && stack.isDamageable()) {
            int remaining = stack.getMaxDamage() - stack.getDamage();
            lines.add(Text.literal("Durability: " + remaining + " / " + stack.getMaxDamage()).formatted(Formatting.GRAY));
        }

        if (this.enchantCount.getValue()) {
            ItemEnchantmentsComponent enchants = stack.getEnchantments();
            if (!enchants.isEmpty()) {
                lines.add(Text.literal("Enchantments: " + enchants.getSize()).formatted(Formatting.DARK_AQUA));
            }
        }

        if (this.componentCount.getValue()) {
            lines.add(Text.literal("Components: " + stack.getComponentChanges().size()).formatted(Formatting.DARK_GRAY));
        }
    }
}
