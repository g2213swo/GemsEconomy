/*
 * Copyright Xanium Development (c) 2013-2018. All Rights Reserved.
 * Any code contained within this document, and any associated APIs with similar branding
 * are the sole property of Xanium Development. Distribution, reproduction, taking snippets or claiming
 * any contents as your own will break the terms of the license, and void any agreements with you, the third party.
 * Thank you.
 */

package me.xanium.gemseconomy.api;

import me.xanium.gemseconomy.GemsEconomy;
import me.xanium.gemseconomy.account.Account;
import me.xanium.gemseconomy.currency.Currency;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("unused")
public class GemsEconomyAPI {

    public final GemsEconomy plugin = GemsEconomy.getInstance();

    public GemsEconomyAPI() {}

    public @NonNull Account pullAccount(@NonNull UUID uuid) {
        requireNonNull(uuid, "uuid");

        if (plugin.getAccountManager().hasAccount(uuid)) {
            return requireNonNull(plugin.getAccountManager().fetchAccount(uuid));
        }
        plugin.getAccountManager().createAccount(uuid);
        return requireNonNull(plugin.getAccountManager().fetchAccount(uuid));
    }

    /**
     * @param uuid   - the user's unique ID
     * @param amount - the amount of the default Currency
     */
    public void deposit(@NonNull UUID uuid, double amount) {
        requireNonNull(uuid, "uuid");

        Account account = pullAccount(uuid);
        account.deposit(plugin.getCurrencyManager().getDefaultCurrency(), amount);
    }

    /**
     * @param uuid     - the user's unique ID
     * @param amount   - the amount of a Currency, if the Currency is null, the default will be used
     * @param currency - the specified Currency
     */
    public void deposit(@NonNull UUID uuid, double amount, @NonNull Currency currency) {
        requireNonNull(uuid, "uuid");
        requireNonNull(currency, "currency");

        Account account = pullAccount(uuid);
        account.deposit(currency, amount);
    }

    /**
     * @param uuid   - the user's unique ID
     * @param amount - the amount of the default Currency
     */
    public void withdraw(@NonNull UUID uuid, double amount) {
        requireNonNull(uuid, "uuid");

        Account account = pullAccount(uuid);
        account.withdraw(plugin.getCurrencyManager().getDefaultCurrency(), amount);
    }

    /**
     * @param uuid     - the user's unique ID
     * @param amount   - the amount of the currency
     * @param currency - the Currency you withdraw from
     */
    public void withdraw(@NonNull UUID uuid, double amount, @NonNull Currency currency) {
        requireNonNull(uuid, "uuid");
        requireNonNull(uuid, "currency");

        Account account = pullAccount(uuid);
        account.withdraw(currency, amount);
    }

    /**
     * @param uuid - the user's unique ID
     *
     * @return - the default Currency balance of the user
     */
    public double getBalance(@NonNull UUID uuid) {
        requireNonNull(uuid, "uuid");

        Account account = pullAccount(uuid);
        return account.getBalance(plugin.getCurrencyManager().getDefaultCurrency());
    }

    /**
     * @param uuid     - the user's unique ID
     * @param currency - the amount of the default Currency
     *
     * @return - the balance of the specified Currency
     */
    public double getBalance(@NonNull UUID uuid, @NonNull Currency currency) {
        requireNonNull(uuid, "uuid");
        requireNonNull(uuid, "currency");

        Account account = pullAccount(uuid);
        return account.getBalance(currency);
    }

    /**
     * @param name - the Currency name in singular or plural
     *
     * @return - Currency object
     */
    public Currency getCurrency(@NonNull String name) {
        requireNonNull(name, "name");

        if (plugin.getCurrencyManager().getCurrency(name) != null) {
            return plugin.getCurrencyManager().getCurrency(name);
        }
        return null;
    }

}