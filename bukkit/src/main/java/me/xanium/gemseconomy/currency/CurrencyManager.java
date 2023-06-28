package me.xanium.gemseconomy.currency;

import com.google.common.collect.ImmutableList;
import me.xanium.gemseconomy.GemsEconomyPlugin;
import me.xanium.gemseconomy.api.Currency;
import me.xanium.gemseconomy.message.Action;
import me.xanium.gemseconomy.message.Messenger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DefaultQualifier(NonNull.class)
public class CurrencyManager {

    private final GemsEconomyPlugin plugin;
    private final Map<UUID, Currency> currencies;

    public CurrencyManager(GemsEconomyPlugin plugin) {
        this.plugin = plugin;
        currencies = new ConcurrentHashMap<>();
    }

    /* ---------------- Getters ---------------- */


    public boolean hasCurrency(String name) {
        return getCurrency(name) != null;
    }

    public @Nullable Currency getCurrency(UUID uuid) {
        return currencies.get(uuid);
    }

    public @Nullable Currency getCurrency(String name) {
        for (Currency currency : currencies.values()) {
            if (currency.getName().equalsIgnoreCase(name)) {
                return currency;
            }
        }
        return null;
    }

    public Currency getDefaultCurrency() {
        for (Currency currency : currencies.values()) {
            if (currency.isDefaultCurrency())
                return currency;
        }
        throw new IllegalStateException("No default currency is provided");
    }

    public List<Currency> getLoadedCurrencies() {
        return ImmutableList.copyOf(currencies.values());
    }

    /* ---------------- Setters ---------------- */

    /**
     * Creates a new Currency and saves it to database.
     *
     * @param name the name of the new Currency
     * @return the new Currency, or <code>null</code> if already existed
     */
    public @Nullable Currency createCurrency(String name) {
        if (hasCurrency(name)) {
            return null;
        }

        Currency currency = new ServerCurrency(UUID.randomUUID(), name);
        currency.setExchangeRate(1D);

        if (currencies.size() == 0) {
            currency.setDefaultCurrency(true);
        }

        addCurrency(currency);

        plugin.getDataStore().saveCurrency(currency);
        plugin.getMessenger().sendMessage(Action.CREATE_CURRENCY, currency.getUuid());

        return currency;
    }

    /**
     * Adds given Currency object to this manager.
     * <p>
     * This method will do nothing if this manager already contains specific Currency.
     *
     * @param currency a Currency object
     */
    public void addCurrency(Currency currency) {
        currencies.putIfAbsent(currency.getUuid(), currency);
    }

    /**
     * Saves specific currency to database.
     *
     * @param currency a Currency
     */
    public void saveCurrency(Currency currency) {
        plugin.getDataStore().saveCurrency(currency);
        plugin.getMessenger().sendMessage(Action.UPDATE_CURRENCY, currency.getUuid());
    }

    /**
     * Updates specific Currency in this manager so that it syncs with database.
     * <p>
     * This method is specifically used by {@link Messenger}.
     *
     * @param uuid   the uuid of specific Currency
     * @param create if true, it will create specific currency if not existing in this manager; otherwise false
     */
    public void updateCurrency(UUID uuid, boolean create) {
        @Nullable Currency newCurrency = plugin.getDataStore().loadCurrency(uuid);
        @Nullable Currency oldCurrency = getCurrency(uuid);
        if (newCurrency != null) { // Only update it if the new currency is actually loaded
            if (oldCurrency != null) { // This manager has specific Currency, but not synced with database
                oldCurrency.update(newCurrency);
            } else if (create) { // This manager doesn't have specific Currency - just create it
                addCurrency(newCurrency);
            }
        }
    }

    /**
     * Removes specified Currency from this manager, all Accounts, and database.
     *
     * @param currency the Currency to remove
     */
    public void removeCurrency(Currency currency) {
        // Remove this currency from all accounts
        GemsEconomyPlugin.getInstance()
            .getAccountManager()
            .getOfflineAccounts()
            .forEach(account -> {
                account.getBalances().remove(currency);
                plugin.getDataStore().saveAccount(account);
                plugin.getMessenger().sendMessage(Action.UPDATE_ACCOUNT, account.getUuid());
            });

        // Remove this currency from this manager
        currencies.remove(currency.getUuid());

        // Remove this currency from data storage
        plugin.getDataStore().deleteCurrency(currency);
        plugin.getMessenger().sendMessage(Action.DELETE_CURRENCY, currency.getUuid());

        // Flush accounts in cache
        plugin.getAccountManager().flushAccounts();
    }

    /**
     * The same as {@link #removeCurrency(Currency)} but it accepts a UUID.
     * <p>
     * If the UUID does not map to a Currency in this manager, this method will do nothing.
     */
    public void removeCurrency(UUID uuid) {
        Currency currency = currencies.get(uuid);
        if (currency != null)
            removeCurrency(currency);
    }

    /**
     * Sets the balances of specific Currency to default value for <b>ALL</b> Accounts.
     *
     * @param currency the Currency to clear balance
     */
    public void clearBalance(Currency currency) {
        plugin.getAccountManager().getOfflineAccounts().forEach(account -> {
            account.getBalances().compute(currency, (c, d) -> c.getDefaultBalance());
            plugin.getDataStore().saveAccount(account);
            plugin.getMessenger().sendMessage(Action.UPDATE_ACCOUNT, account.getUuid());
        });

        // Flush accounts in cache
        plugin.getAccountManager().flushAccounts();
    }

}
