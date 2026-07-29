package dev.lovelace.lovecore.api.economy;

import java.util.concurrent.CompletableFuture;

/**
 * Единый кошелёк экосистемы. Все операции идут через ядро, чтобы деньги были одни и те же
 * у скупщика, в клановой казне и у пивовара.
 */
public interface LoveEconomy {

    /** Текущий баланс. Читается из кэша, безопасно вызывать с главного потока. */
    long balance(AccountId account);

    /**
     * Списывает сумму, если её хватает. Проверка баланса и списание происходят одной
     * операцией в базе — вызывающему не нужно проверять баланс заранее и ловить гонку
     * между проверкой и списанием.
     *
     * @param reason попадает в журнал операций; по нему потом ищут, куда делись деньги
     * @return future с false, если денег не хватило
     */
    CompletableFuture<Boolean> withdraw(AccountId account, long amount, String reason);

    CompletableFuture<Void> deposit(AccountId account, long amount, String reason);

    /** Перевод. Либо оба счёта изменились, либо ни один. */
    CompletableFuture<Boolean> transfer(AccountId from, AccountId to, long amount, String reason);

    /** Название валюты для показа игроку. */
    String currencyName();
}
