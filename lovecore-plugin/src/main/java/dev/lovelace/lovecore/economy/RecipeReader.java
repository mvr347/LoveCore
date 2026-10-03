package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.economy.PriceModelCore.Ingredient;
import dev.lovelace.lovecore.economy.PriceModelCore.Kind;
import dev.lovelace.lovecore.economy.PriceModelCore.Recipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Читает реестр рецептов сервера и переводит его в граф {@link PriceModelCore}. Только на главном потоке. */
final class RecipeReader {

    private RecipeReader() {
    }

    static List<Recipe> read() {
        List<Recipe> out = new ArrayList<>();
        Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe recipe;
            try {
                recipe = it.next();
            } catch (RuntimeException e) {
                continue; // a broken third-party recipe must not stop the model
            }
            ItemStack result = recipe.getResult();
            if (result == null || result.getType().isAir()) continue;
            String output = result.getType().name();
            int count = Math.max(1, result.getAmount());
            try {
                if (recipe instanceof ShapedRecipe shaped) {
                    Map<Character, Integer> counts = new LinkedHashMap<>();
                    for (String row : shaped.getShape()) {
                        for (char c : row.toCharArray()) {
                            if (c != ' ') counts.merge(c, 1, Integer::sum);
                        }
                    }
                    List<Ingredient> inputs = new ArrayList<>();
                    for (Map.Entry<Character, Integer> e : counts.entrySet()) {
                        List<String> options = optionsOf(shaped.getChoiceMap().get(e.getKey()));
                        if (!options.isEmpty()) inputs.add(new Ingredient(options, e.getValue()));
                    }
                    if (!inputs.isEmpty()) out.add(new Recipe(output, count, inputs, Kind.CRAFT));
                } else if (recipe instanceof ShapelessRecipe shapeless) {
                    Map<List<String>, Integer> grouped = new LinkedHashMap<>();
                    for (RecipeChoice choice : shapeless.getChoiceList()) {
                        List<String> options = optionsOf(choice);
                        if (!options.isEmpty()) grouped.merge(options, 1, Integer::sum);
                    }
                    List<Ingredient> inputs = new ArrayList<>();
                    grouped.forEach((options, n) -> inputs.add(new Ingredient(options, n)));
                    if (!inputs.isEmpty()) out.add(new Recipe(output, count, inputs, Kind.CRAFT));
                } else if (recipe instanceof CookingRecipe<?> cooking) {
                    List<String> options = optionsOf(cooking.getInputChoice());
                    if (!options.isEmpty()) out.add(new Recipe(output, count, List.of(new Ingredient(options, 1)), Kind.SMELT));
                } else if (recipe instanceof StonecuttingRecipe stonecutting) {
                    List<String> options = optionsOf(stonecutting.getInputChoice());
                    if (!options.isEmpty()) out.add(new Recipe(output, count, List.of(new Ingredient(options, 1)), Kind.STONECUT));
                } else if (recipe instanceof SmithingTransformRecipe smithing) {
                    List<Ingredient> inputs = new ArrayList<>();
                    for (RecipeChoice choice : List.of(smithing.getTemplate(), smithing.getBase(), smithing.getAddition())) {
                        List<String> options = optionsOf(choice);
                        if (!options.isEmpty()) inputs.add(new Ingredient(options, 1));
                    }
                    if (!inputs.isEmpty()) out.add(new Recipe(output, count, inputs, Kind.SMITHING));
                }
            } catch (RuntimeException e) {
                // an exotic recipe type: skip it, the item may still be priced through another recipe
            }
        }
        return out;
    }

    private static List<String> optionsOf(RecipeChoice choice) {
        List<String> names = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            for (Material m : materials.getChoices()) names.add(m.name());
        } else if (choice instanceof RecipeChoice.ExactChoice exact) {
            for (ItemStack s : exact.getChoices()) names.add(s.getType().name());
        }
        return names;
    }
}
