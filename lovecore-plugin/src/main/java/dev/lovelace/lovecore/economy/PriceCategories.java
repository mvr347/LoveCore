package dev.lovelace.lovecore.economy;

/** Категория предмета по имени материала — для наценки за крафт ({@code economy.price-model.craft-markup}). */
public final class PriceCategories {

    private PriceCategories() {
    }

    public static String of(String name) {
        if (endsWithAny(name, "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD") || name.equals("SHEARS")
                || name.equals("FISHING_ROD") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")
                || name.equals("MACE") || name.equals("FLINT_AND_STEEL")) {
            return "tools";
        }
        if (endsWithAny(name, "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS") || name.equals("SHIELD")) {
            return "armor";
        }
        if (name.startsWith("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR") || name.contains("PISTON")
                || name.contains("OBSERVER") || name.contains("HOPPER") || name.contains("DISPENSER")
                || name.contains("DROPPER") || name.endsWith("_RAIL") || name.equals("RAIL") || name.equals("TNT")
                || name.equals("LEVER") || name.contains("DETECTOR") || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_BUTTON") || name.equals("TARGET") || name.equals("TRIPWIRE_HOOK")) {
            return "redstone";
        }
        if (name.contains("BREAD") || name.contains("STEW") || name.contains("COOKIE") || name.contains("_PIE")
                || name.contains("CAKE") || name.contains("SOUP") || name.contains("APPLE") || name.startsWith("COOKED_")
                || name.startsWith("BAKED_") || name.contains("CARROT") || name.contains("MELON")) {
            return "food";
        }
        if (endsWithAny(name, "_BED", "_BANNER", "_CARPET", "_STAINED_GLASS", "_STAINED_GLASS_PANE", "_CONCRETE",
                "_CONCRETE_POWDER", "_TERRACOTTA", "_GLAZED_TERRACOTTA", "_CANDLE", "_DYE", "_SHULKER_BOX")
                || name.equals("CANDLE") || name.equals("PAINTING") || name.equals("ITEM_FRAME")) {
            return "decor";
        }
        if (endsWithAny(name, "_BLOCK", "_PLANKS", "_SLAB", "_STAIRS", "_WALL", "_BRICKS", "_FENCE", "_FENCE_GATE", "_DOOR",
                "_TRAPDOOR", "_WOOL")) {
            return "blocks";
        }
        return "default";
    }

    private static boolean endsWithAny(String name, String... suffixes) {
        for (String s : suffixes) {
            if (name.endsWith(s)) return true;
        }
        return false;
    }
}
