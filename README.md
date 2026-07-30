# LoveCore

Общее ядро экосистемы Love*: реестр служб, кошелёк, боевое состояние, шина статистики.

## Зачем

Межплагинные связки в экосистеме написаны рефлексией по строковым именам классов и глушат
ошибки пустым `catch`. За один проход аудита нашлись три интеграции, которые **никогда не
работали**, и никто этого не заметил:

| Где | Что звала | Что есть на самом деле |
|---|---|---|
| LoveLeaderboards → LoveHunt | `dev.lovelace.lovehunt.events.BountyCompletedEvent` | `me.lovelace.loveHunt.api.event.BountyClaimEvent` |
| LoveLeaderboards → LoveClans | `dev.lovelace.loveclans.api.LoveClansAPI`, `getId`/`getName`/`getPower` | `me.lovelace.loveclans.api.LoveClansAPI`, `id()`/`name()`/`influence()` |
| LoveHunt → кланы | плагин `Clans` | плагин называется `LoveClans` |

Ядро нужно не ради красоты архитектуры, а чтобы такие расхождения ловил компилятор — и чтобы
связка, которая всё-таки сломалась, говорила об этом в первую же минуту работы сервера.

> **Публикация отстаёт от исходников.** Последняя опубликованная на JitPack сборка
> (`fe15e0144e`, её и тянут все текущие потребители через `provided`-зависимость) несёт
> **другой** `LoveEconomy` — виртуальный счёт (`AccountId`, `balance/withdraw/deposit/transfer`,
> все операции через `CompletableFuture`), а не физические монеты в инвентаре, описанные ниже.
> Раздел «LoveEconomy API» документирует то, что лежит в этом репозитории *сейчас* — писать
> плагин против него можно, но пока новая версия не опубликована на JitPack и потребители не
> обновили `<version>`, реально резолвится старый (`AccountId`-ledger) API. Проверяйте `javap`
> по скачанному jar из `~/.m2`, если сомневаетесь, какая версия у вас на класспасе.

## Два артефакта

```
LoveCore/
├── lovecore-api/     ← тонкий jar: интерфейсы, события, DTO. Без состояния и логики.
│   └── публикуется, плагины зависят в scope provided
└── lovecore-plugin/  ← реализация: ставится на сервер, публикует службы в ServicesManager
```

Правка внутренностей реализации не должна заставлять пересобирать пятнадцать плагинов. И
наоборот — плагин, собранный против `lovecore-api` версии 1.2, обязан работать с ядром 1.5.

## Подключение

```xml
<dependency>
    <groupId>com.github.mvr347.LoveCore</groupId>
    <artifactId>lovecore-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

```yaml
# plugin.yml потребителя
softdepend: [LoveCore]
```

`softdepend`, а не `depend`: плагин обязан подниматься и без ядра.

## Службы

| Служба | Что отвечает | Источник |
|---|---|---|
| `LoveEconomy` | баланс, списание и выдача монет — физических предметов ItemsAdder в инвентаре игрока, с разменом | инвентарь игрока напрямую, без базы |
| `CombatState` | в бою ли игрок, до какого момента и почему | свой учёт PvP + метки боевых плагинов |
| `StatBus` | приём изменений метрик, рассылка `StatChangedEvent` | свой |
| `ProfileOracle` | клан игрока, вражда, соклановцы | LoveClans |
| `TerritoryOracle` | владелец точки, враждебность | LoveClaims |
| `ReputationOracle` | репутация 0..100 и ступень | LoveBehavior |

### LoveEconomy API

Единая система валюты для всей экосистемы. Все деньги — физические предметы (монеты ItemsAdder) в инвентаре игрока, без виртуальных балансов.

**Основные методы:**

```java
LoveEconomy economy = LoveCore.service(LoveEconomy.class).orElseThrow();

// Получить баланс игрока (сумма всех монет)
long balance = economy.balance(player);

// Проверить, достаточно ли монет
boolean has = economy.has(player, 500);

// Выдать монеты (с автоматическим разменом и overflow handling)
economy.give(player, 500);

// Списать монеты
economy.charge(player, 500);

// Проверить, поместится ли сумма в инвентарь
boolean canFit = economy.canFit(player, 500);

// Получить имя валюты
String name = economy.currencyName();  // "Монеты"

// Получить список номиналов
List<Denomination> denoms = economy.denominations();
```

**Номиналы** (из конфига `economy.denominations`):

```yaml
economy:
  denominations:
    copper:      { item-id: 'itemsadder:copper_coin', value: 1 }
    iron:        { item-id: 'itemsadder:iron_coin', value: 10 }
    gold:        { item-id: 'itemsadder:gold_coin', value: 50 }
    diamond:     { item-id: 'itemsadder:diamond_coin', value: 100 }
    netherite:   { item-id: 'itemsadder:netherite_coin', value: 1000 }
```

Система автоматически дробит крупные номиналы и собирает мелкие: давая 250 монет, выдаст 2 золотых (100) + 5 железных (50).

**Важно:**
- `give()` и `charge()` округляют `double` до `long` (целые монеты)
- Переполнение инвентаря → монеты падают на землю
- Недостаток монет → отказ в операции (`charge()` ничего не делает)
- Все операции синхронны и работают только с онлайн-игроками

Первые три работают всегда. Три оракула поднимаются, только если найден соответствующий
сосед, и на старте пишут в лог, нашёлся он или нет:

```
[LoveCore] Интеграция кланы (LoveClans): подключена.
[LoveCore] Интеграция территория (LoveClaims): сосед не найден, функция отключена. Причина: нет метода ...
[LoveCore] LoveCore готов, служб зарегистрировано: 6 (StatBus, LoveEconomy, CombatState, ...)
```

То же покажет команда `/lovecore`.

## Обращение к ядру

Единственный способ — `LoveCore.service(...)`. Он возвращает `Optional`, и это намеренно:
плагин обязан работать и без ядра.

```java
public boolean isInCombat(Player player) {
    if (!config.isBlockInCombat() || player.hasPermission(BYPASS_COMBAT)) {
        return false;
    }
    return LoveCore.service(CombatState.class)
            .map(state -> state.inCombat(player.getUniqueId()))
            // Ядра нет — падаем на прежнюю проверку метки, а не отключаем защиту молча.
            .orElseGet(() -> hasCombatMetadata(player));
}
```

Кэшировать результат в поле не нужно и вредно: сосед может зарегистрировать более точную
реализацию оракула позже, и закэшированная ссылка её не увидит.

## Метрики

Имена метрик — константы в `Metrics`, чтобы «kills» и «kill» не разъехались по плагинам.
Источник сообщает о событии сам:

```java
LoveCore.service(StatBus.class).ifPresent(bus -> {
    bus.record(hunter.getUniqueId(), Metrics.BOUNTIES_COMPLETED, 1);
    bus.set(hunter.getUniqueId(), Metrics.HUNTER_RATING, rating.rating());
});
```

Изменения склеиваются в окне (`stats.flush-interval-ticks`): прибавки складываются, точные
значения затирают предыдущие — слушатель получает один итог вместо сотни промежуточных.
`StatChangedEvent` всегда приходит на главном потоке, даже если шину дёрнули из асинхронной
задачи.

`CONTRACTS_POSTED`/`CONTRACTS_FULFILLED` добавлены для player-to-player контрактов LoveContracts
(`/pcontract`) — пока не опубликованы на JitPack, поэтому LoveContracts временно шлёт те же
строки литералом (`"contracts.player.posted"` / `"contracts.player.fulfilled"`), не через
константу.

## Что в ядро не кладётся

Правила варки, механика войн, GUI, конфиги плагинов. Иначе «единое ядро» превратится в
монолит, ради правки в котором придётся трогать всё сразу. В ядро идёт то, о чём спрашивают
минимум двое, и что не является правилами конкретной игры.

## Про рефлексию внутри

Три оракула собраны рефлексией — соседи ещё не знают про `lovecore-api`. Разница с тем, что
было, не в способе, а в месте: связка одна, лежит в `integration/Neighbour`, и вслух говорит,
что именно у соседа не нашлось. Когда сосед научится реализовывать контракт сам, он
зарегистрируется с приоритетом выше `Normal` и вытеснит здешнюю реализацию без правок в ядре;
после этого рефлексия отсюда удаляется.

## Сборка

```
mvn package
```

Java 21, Paper 1.21.11. Сборка на пуш настроена в `.github/workflows/build.yml`, там же —
разбор ресурсных YAML: сломанный `plugin.yml` компиляции не мешает, плагин собирается и не
поднимается на сервере.
