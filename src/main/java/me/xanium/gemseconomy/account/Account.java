/*
 * Copyright Xanium Development (c) 2013-2018. All Rights Reserved.
 * Any code contained within this document, and any associated APIs with similar branding
 * are the sole property of Xanium Development. Distribution, reproduction, taking snippets or claiming
 * any contents as your own will break the terms of the license, and void any agreements with you, the third party.
 * Thank you.
 */

package me.xanium.gemseconomy.account;

import me.lucko.helper.Schedulers;
import me.xanium.gemseconomy.GemsEconomy;
import me.xanium.gemseconomy.bungee.UpdateType;
import me.xanium.gemseconomy.currency.Currency;
import me.xanium.gemseconomy.event.GemsPostTransactionEvent;
import me.xanium.gemseconomy.event.GemsPreTransactionEvent;
import me.xanium.gemseconomy.utils.TransactionType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Account {

    private @NonNull final UUID uuid;
    private @MonotonicNonNull String nickname;
    private @NonNull final Map<Currency, Double> balances = new HashMap<>();
    private @NonNull final Map<Currency, Double> accBalances = new HashMap<>(); // TODO record accumulated deposition
    private boolean canReceiveCurrency = true;

    public Account(@NonNull UUID uuid, @Nullable String nickname) {
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public synchronized boolean withdraw(@NonNull Currency currency, double amount) {
        if (hasEnough(currency, amount)) {
            GemsPreTransactionEvent preEvent = new GemsPreTransactionEvent(currency, this, amount, TransactionType.WITHDRAW);
            if (!Schedulers.sync().call(preEvent::callEvent).join())
                return false; // Call event on main thread, then wait it return

            double finalAmount = getBalance(currency) - amount;
            double cappedAmount = Math.min(finalAmount, currency.getMaxBalance());

            // Update balance
            balances.put(currency, cappedAmount);
            // Save it to database
            GemsEconomy.getInstance().getDataStore().saveAccount(this);
            // Sync between servers
            GemsEconomy.getInstance().getUpdateForwarder().sendUpdateMessage(UpdateType.ACCOUNT, getUuid().toString());

            GemsPostTransactionEvent postEvent = new GemsPostTransactionEvent(currency, this, amount, TransactionType.WITHDRAW);
            Schedulers.sync().run(postEvent::callEvent);

            GemsEconomy.getInstance().getEconomyLogger().log("[WITHDRAW] Account: " + getDisplayName() + " were withdrawn: " + currency.format(amount) + " and now has " + currency.format(cappedAmount));
            return true;
        }
        return false;
    }

    public synchronized boolean deposit(@NonNull Currency currency, double amount) {
        if (this.canReceiveCurrency) {
            GemsPreTransactionEvent preEvent = new GemsPreTransactionEvent(currency, this, amount, TransactionType.DEPOSIT);
            if (!Schedulers.sync().call(preEvent::callEvent).join())
                return false; // Call event on main thread, then wait it return

            double finalAmount = getBalance(currency) + amount;
            double cappedAmount = Math.min(finalAmount, currency.getMaxBalance());

            // Update balance
            balances.put(currency, cappedAmount);
            // Accumulate deposited amount
            accBalances.merge(currency, cappedAmount, Double::sum);
            // Save it to database
            GemsEconomy.getInstance().getDataStore().saveAccount(this);
            // Sync between servers
            GemsEconomy.getInstance().getUpdateForwarder().sendUpdateMessage(UpdateType.ACCOUNT, getUuid().toString());

            GemsPostTransactionEvent postEvent = new GemsPostTransactionEvent(currency, this, amount, TransactionType.DEPOSIT);
            Schedulers.sync().run(postEvent::callEvent);

            GemsEconomy.getInstance().getEconomyLogger().log("[DEPOSIT] Account: " + getDisplayName() + " were deposited: " + currency.format(amount) + " and now has " + currency.format(cappedAmount));
            return true;
        }
        return false;
    }

    public synchronized void setBalance(@NonNull Currency currency, double amount) {
        GemsPreTransactionEvent preEvent = new GemsPreTransactionEvent(currency, this, amount, TransactionType.SET);
        if (!Schedulers.sync().call(preEvent::callEvent).join())
            return; // Call event on main thread, then wait it return

        double cappedAmount = Math.min(amount, currency.getMaxBalance());

        // Update balance
        balances.put(currency, cappedAmount);
        // Save it to database
        GemsEconomy.getInstance().getDataStore().saveAccount(this);
        // Sync between servers
        GemsEconomy.getInstance().getUpdateForwarder().sendUpdateMessage(UpdateType.ACCOUNT, getUuid().toString());

        GemsPostTransactionEvent postEvent = new GemsPostTransactionEvent(currency, this, cappedAmount, TransactionType.SET);
        Schedulers.sync().run(postEvent::callEvent);

        GemsEconomy.getInstance().getEconomyLogger().log("[BALANCE SET] Account: " + getDisplayName() + " were set to: " + currency.format(cappedAmount));
    }

    public double getBalance(@NonNull Currency currency) {
        return this.balances.computeIfAbsent(currency, Currency::getDefaultBalance);
    }

    public double getBalance(@NonNull String identifier) {
        for (Currency currency : this.balances.keySet()) {
            if (currency.getSingular().equalsIgnoreCase(identifier) || currency.getPlural().equalsIgnoreCase(identifier))
                return this.balances.get(currency);
        }
        return 0; // Do not edit this
    }

    public @NonNull Map<Currency, Double> getBalances() {
        return balances;
    }

    public @NonNull String getDisplayName() {
        return this.nickname != null ? this.nickname : this.uuid.toString();
    }

    public @MonotonicNonNull String getNickname() {
        return nickname;
    }

    public @NonNull UUID getUuid() {
        return uuid;
    }

    public boolean testOverflow(@NonNull Currency currency, double amount) {
        return this.balances.get(currency) + amount > currency.getMaxBalance();
    }

    public boolean hasEnough(double amount) {
        return hasEnough(GemsEconomy.getInstance().getCurrencyManager().getDefaultCurrency(), amount);
    }

    public boolean hasEnough(@NonNull Currency currency, double amount) {
        return getBalance(currency) >= amount;
    }

    public boolean canReceiveCurrency() {
        return canReceiveCurrency;
    }

    public void setCanReceiveCurrency(boolean canReceiveCurrency) {
        this.canReceiveCurrency = canReceiveCurrency;
    }

    public void setNickname(@NonNull String nickname) {
        this.nickname = nickname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return uuid.equals(account.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

}

