package dev.lovelace.lovecore.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Ядро модели цен: из якорей (сырьё, редкий лут, ручные цены) и графа рецептов выводит стоимость всего,
 * что можно скрафтить, переплавить или получить на камнерезе / кузнечном столе. Без Bukkit — чистая
 * логика, тестируется отдельно.
 *
 * <p>Алгоритм — релаксация «минимум по рецептам»: цены якорей фиксированы, цена производного предмета —
 * самая дешёвая из рецептов, у которых все входы уже оценены; повторяется до неизменности. Наценки
 * неотрицательны, поэтому цикл (слиток ↔ блок ↔ самородок) не может обесценить предмет, и результат не
 * зависит от порядка рецептов. Сжатие и распаковка запасов (9 слитков ↔ блок) идут без наценки, поэтому блок
 * стоит ровно девять слитков.</p>
 */
public final class PriceModelCore {

    public enum Kind { CRAFT, SMELT, SMITHING, STONECUT }

    /** Один слот рецепта: подойдёт любой из вариантов, берётся самый дешёвый из оценённых. */
    public record Ingredient(List<String> options, int count) {
        public Ingredient {
            options = List.copyOf(options);
        }
    }

    public record Recipe(String output, int outputCount, List<Ingredient> inputs, Kind kind) {
        public Recipe {
            inputs = List.copyOf(inputs);
        }

        /** Сжатие/распаковка запаса: один вид входа, 4+ штук в один или один в 4+. Без наценки за крафт. */
        boolean isCompression() {
            if (kind != Kind.CRAFT || inputs.size() != 1 || inputs.get(0).options().size() != 1) return false;
            int in = inputs.get(0).count();
            return (in >= 4 && outputCount == 1) || (in == 1 && outputCount >= 4);
        }
    }

    public record RoundStep(double upTo, double step) {
    }

    public static final class Config {
        /** Сырьё и редкий лут: фиксированные цены. */
        public final Map<String, Double> anchors = new HashMap<>();
        /** Из якорей — те, что редкий лут (для разбора). */
        public final Set<String> rare = new HashSet<>();
        /** Ручные цены: перекрывают и якорь, и расчёт. */
        public final Map<String, Long> overrides = new HashMap<>();
        public double defaultMarkupPercent = 15;
        public final Map<String, Double> categoryMarkupPercent = new HashMap<>();
        public final Map<String, Double> itemMarkupPercent = new HashMap<>();
        public Function<String, String> categoryOf = name -> "default";
        public double smeltFuelCost = 4;
        public double globalScale = 1.0;
        public long minPrice = 1;
        public long maxPrice = Long.MAX_VALUE;
        public List<RoundStep> rounding = List.of(new RoundStep(20, 1), new RoundStep(200, 5),
                new RoundStep(2_000, 10), new RoundStep(20_000, 50), new RoundStep(Double.MAX_VALUE, 100));

        double markupPercent(String item) {
            Double byItem = itemMarkupPercent.get(item);
            if (byItem != null) return byItem;
            Double byCategory = categoryMarkupPercent.get(categoryOf.apply(item));
            return byCategory != null ? byCategory : defaultMarkupPercent;
        }
    }

    /** Откуда взялась цена: для разбора. */
    public record Source(String kind, Recipe recipe, double markupPercent, double rawCost) {
    }

    public record Result(Map<String, Long> prices, Map<String, Source> sources, Set<String> unpriced) {
    }

    private PriceModelCore() {
    }

    public static Result compute(Config cfg, List<Recipe> recipes) {
        Map<String, Double> price = new HashMap<>();
        Map<String, Source> source = new HashMap<>();
        Set<String> fixed = new HashSet<>();

        for (Map.Entry<String, Double> e : cfg.anchors.entrySet()) {
            price.put(e.getKey(), e.getValue());
            source.put(e.getKey(), new Source(cfg.rare.contains(e.getKey()) ? "RARE" : "ANCHOR", null, 0, e.getValue()));
            fixed.add(e.getKey());
        }
        for (Map.Entry<String, Long> e : cfg.overrides.entrySet()) {
            price.put(e.getKey(), e.getValue().doubleValue());
            source.put(e.getKey(), new Source("OVERRIDE", null, 0, e.getValue()));
            fixed.add(e.getKey());
        }

        for (int pass = 0; pass < 64; pass++) {
            boolean changed = false;
            for (Recipe r : recipes) {
                if (fixed.contains(r.output())) continue;
                double cost = 0;
                boolean ok = true;
                for (Ingredient ing : r.inputs()) {
                    double best = Double.POSITIVE_INFINITY;
                    for (String opt : ing.options()) {
                        Double p = price.get(opt);
                        if (p != null && p < best) best = p;
                    }
                    if (Double.isInfinite(best)) {
                        ok = false;
                        break;
                    }
                    cost += best * ing.count();
                }
                if (!ok) continue;
                cost /= Math.max(1, r.outputCount());
                double markup;
                double candidate;
                if (r.kind() == Kind.SMELT) {
                    markup = 0;
                    candidate = cost + cfg.smeltFuelCost / Math.max(1, r.outputCount());
                } else if (r.isCompression() || r.kind() == Kind.STONECUT) {
                    markup = 0;
                    candidate = cost;
                } else {
                    markup = cfg.markupPercent(r.output());
                    candidate = cost * (1 + markup / 100.0);
                }
                Double current = price.get(r.output());
                if (current == null || candidate < current - 1e-9) {
                    price.put(r.output(), candidate);
                    source.put(r.output(), new Source(r.kind().name(), r, markup, cost));
                    changed = true;
                }
            }
            if (!changed) break;
        }

        Map<String, Long> out = new TreeMap<>();
        for (Map.Entry<String, Double> e : price.entrySet()) {
            if (cfg.overrides.containsKey(e.getKey())) {
                out.put(e.getKey(), clamp(cfg, cfg.overrides.get(e.getKey())));
            } else {
                out.put(e.getKey(), clamp(cfg, round(cfg, e.getValue() * cfg.globalScale)));
            }
        }
        Set<String> unpriced = new TreeSet<>();
        for (Recipe r : recipes) {
            if (!out.containsKey(r.output())) unpriced.add(r.output());
        }
        return new Result(out, source, unpriced);
    }

    private static long clamp(Config cfg, long value) {
        return Math.min(cfg.maxPrice, Math.max(cfg.minPrice, value));
    }

    /** Округление «красивыми» шагами по порядку величины ({@code rounding} в конфиге). */
    static long round(Config cfg, double value) {
        double step = 1;
        for (RoundStep s : cfg.rounding) {
            step = s.step();
            if (value <= s.upTo()) break;
        }
        if (step <= 1) return Math.max(1, Math.round(value));
        long stepL = Math.round(step);
        return Math.max(1, Math.round(value / step) * stepL);
    }

    /** Строки разбора цены предмета (до 3 уровней вглубь). */
    public static List<String> explain(Result result, String item, int depth) {
        List<String> lines = new ArrayList<>();
        explainInto(result, item, 0, depth, lines);
        return lines;
    }

    private static void explainInto(Result r, String item, int level, int maxDepth, List<String> lines) {
        String pad = "  ".repeat(level);
        Long p = r.prices().get(item);
        if (p == null) {
            lines.add(pad + item + ": цены нет");
            return;
        }
        Source s = r.sources().get(item);
        if (s == null || s.recipe() == null) {
            lines.add(pad + item + " = " + p + " (" + (s == null ? "?" : s.kind().toLowerCase(Locale.ROOT)) + ")");
            return;
        }
        String extra = s.kind().equals("SMELT") ? ", плавка" : String.format(Locale.ROOT, ", наценка %.0f%%", s.markupPercent());
        lines.add(pad + item + " = " + p + " (" + s.kind().toLowerCase(Locale.ROOT) + ", сырьё "
                + Math.round(s.rawCost()) + extra + ")");
        if (level >= maxDepth) return;
        for (Ingredient ing : s.recipe().inputs()) {
            String cheapest = null;
            double best = Double.POSITIVE_INFINITY;
            for (String opt : ing.options()) {
                Long op = r.prices().get(opt);
                if (op != null && op < best) {
                    best = op;
                    cheapest = opt;
                }
            }
            if (cheapest != null) {
                lines.add(pad + "  " + ing.count() + " x");
                explainInto(r, cheapest, level + 1, maxDepth, lines);
            }
        }
    }
}
