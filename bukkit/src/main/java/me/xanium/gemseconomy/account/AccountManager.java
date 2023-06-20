/*
 * Copyright Xanium Development (c) 2013-2018. All Rights Reserved.
 * Any code contained within this document, and any associated APIs with similar branding
 * are the sole property of Xanium Development. Distribution, reproduction, taking snippets or claiming
 * any contents as your own will break the terms of the license, and void any agreements with you, the third party.
 * Thank you.
 */

package me.xanium.gemseconomy.account;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import me.lucko.helper.profiles.OfflineModeProfiles;
import me.lucko.helper.scheduler.HelperExecutors;
import me.xanium.gemseconomy.GemsEconomy;
import me.xanium.gemseconomy.data.DataStorage;
import me.xanium.gemseconomy.message.Action;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class AccountManager {

    private final @NonNull GemsEconomy plugin;
    private final @NonNull LoadingCache<UUID, Optional<Account>> caches; // accounts loaded in memory

    public AccountManager(@NonNull GemsEconomy plugin) {
        this.plugin = plugin;
        this.caches = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.of(10, ChronoUnit.MINUTES))
            .build(CacheLoader.asyncReloading(new CacheLoader<>() {
                @Override public @NonNull Optional<Account> load(final @NonNull UUID key) {
                    return Optional.ofNullable(plugin.getDataStore().loadAccount(key));
                }

                @Override public @NonNull ListenableFuture<Optional<Account>> reload(final @NonNull UUID key, final @NonNull Optional<Account> oldValue) {
                    return oldValue
                        .map(account -> plugin.getDataStore().updateAccount(account) /* Note that it doesn't change reference */)
                        .map(value -> Futures.immediateFuture(Optional.of(value)))
                        .orElseGet(() -> Futures.immediateFuture(oldValue));
                }
            }, HelperExecutors.asyncHelper()));
    }

    /**
     * Creates, saves, and caches an Account.
     * <p>
     * If the Account with specific UUID is already loaded or exists in database, this method will do nothing.
     *
     * @param uuid the uuid of the new Account
     */
    public void createAccount(@NonNull UUID uuid) {
        if (hasAccount(uuid))
            return;

        Account account = new PlayerAccount(uuid, null);

        // Set default balances
        plugin.getCurrencyManager().getCurrencies().forEach(currency ->
            account.setBalance(currency, currency.getDefaultBalance())
        );

        cacheAccount(account);
        plugin.getDataStore().createAccount(account);
        plugin.getMessenger().sendMessage(Action.UPDATE_ACCOUNT, account.getUuid());
    }

    /**
     * Creates, saves, and caches an Account.
     * <p>
     * If the Account with specific player is already loaded or exists in database, this method will do nothing.
     *
     * @param player the player who owns the new Account
     */
    public void createAccount(@NonNull OfflinePlayer player) {
        createAccount(player.getUniqueId());
    }

    /**
     * Creates, saves, and caches an Account.
     * <p>
     * If the Account with specific nickname is already loaded or exists in database, this method will do nothing.
     * <p>
     * This method will try the best to create a new Account with an uuid being generated by the <b>Mojang offline
     * method</b>. That is, the uuid of the created Account <b>WILL NOT</b> be the Mojang online version even if the
     * nickname does map to an online Minecraft account. The existence of this method is for the compatibility with
     * other plugins which need to create economy Accounts with the plain string name being the Account identifier.
     *
     * @param nickname the nickname of the new Account
     *
     * @see OfflineModeProfiles
     */
    public void createAccount(@NonNull String nickname) {
        if (hasAccount(nickname))
            return;

        Account account = new PlayerAccount(
            // Get the UUID of the name by using the Mojang offline player method
            // so that we can ensure same nicknames always point to the same UUID.
            OfflineModeProfiles.getUniqueId(nickname),
            // The nickname must be stored for this account
            // because it is the identifier of the account!
            nickname
        );

        // Set default balances
        plugin.getCurrencyManager().getCurrencies().forEach(currency ->
            account.setBalance(currency, currency.getDefaultBalance())
        );

        cacheAccount(account);
        plugin.getDataStore().createAccount(account);
        plugin.getMessenger().sendMessage(Action.UPDATE_ACCOUNT, account.getUuid());
    }

    /**
     * Deletes specific Account from both cache and database.
     *
     * @param uuid the uuid of specific Account
     */
    public void deleteAccount(@NonNull UUID uuid) {
        caches.invalidate(uuid); // Delete from memory
        plugin.getDataStore().deleteAccount(uuid); // Delete from database
    }

    /**
     * Deletes specific Account from both cache and database.
     *
     * @param player the owner of specific Account
     */
    public void deleteAccount(@NonNull OfflinePlayer player) {
        deleteAccount(player.getUniqueId());
    }

    /**
     * @see #hasAccount(UUID)
     */
    public boolean hasAccount(@NonNull OfflinePlayer player) {
        return hasAccount(player.getUniqueId());
    }

    /**
     * Checks whether the Account with given uuid exists.
     * <p>
     * This method is equivalent to simply call:
     *
     * <pre>{@code fetchAccount(uuid) != null}</pre>
     *
     * @param uuid the uuid of the Account
     *
     * @return true if the Account with given uuid exists; otherwise false
     */
    public boolean hasAccount(@NonNull UUID uuid) {
        return fetchAccount(uuid) != null;
    }

    /**
     * Checks whether the Account with given name exists.
     * <p>
     * This method is equivalent to the call:
     *
     * <pre>{@code fetchAccount(name) != null}</pre>
     *
     * @param name the name of the Account
     *
     * @return true if the Account with given name exists; otherwise false
     */
    public boolean hasAccount(@NonNull String name) {
        return fetchAccount(name) != null;
    }

    /**
     * @see #fetchAccount(UUID)
     */
    public @Nullable Account fetchAccount(@NonNull OfflinePlayer player) {
        return fetchAccount(player.getUniqueId());
    }

    /**
     * Fetch an Account with specific uuid.
     * <p>
     * This will first get the Account from caches, followed by database. If neither is found, it will return null.
     * <p>
     * This will also cache the fetched Account if it exists in database.
     *
     * @param uuid the uuid of the Account to fetch for
     *
     * @return an Account with given uuid
     */
    public @Nullable Account fetchAccount(@NonNull UUID uuid) {
        return caches.getUnchecked(uuid).orElse(null);
    }

    /**
     * Fetch an Account with specific name.
     * <p>
     * This will first get the Account from caches, followed by database. If neither is found, it will return null.
     * <p>
     * This will also cache the fetched Account if it exists in database.
     *
     * @param name the name of the Account to fetch for
     *
     * @return an Account with given name
     */
    public @Nullable Account fetchAccount(@NonNull String name) {
        for (final Optional<Account> account : caches.asMap().values()) {
            if (account.isPresent() && name.equalsIgnoreCase(account.get().getNickname())) {
                return account.get();
            }
        }
        @Nullable Account account = plugin.getDataStore().loadAccount(name);
        if (account == null) {
            return null;
        } else {
            cacheAccount(account);
            return account;
        }
    }

    /**
     * Caches an Account.
     * <p>
     * If the Account is already cached (regardless whether it's empty optional or not), this method will override the
     * original object.
     *
     * @param account the Account to be loaded into memory
     */
    public void cacheAccount(@NonNull Account account) {
        caches.put(account.getUuid(), Optional.of(account));
    }

    /**
     * Checks if specific Account is currently cached.
     *
     * @param uuid the uuid of specific Account
     *
     * @return true if the Account is cached; false otherwise
     */
    public boolean cached(@NonNull UUID uuid) {
        return caches.asMap().containsKey(uuid);
    }

    /**
     * Refreshes specific Account from database.
     *
     * @param uuid the uuid of the Account
     */
    public void refreshAccount(@NonNull UUID uuid) {
        // TODO According to javadoc,
        //  if the Account is being read by another thread this method will basically do nothing.
        //  This would be an issue because the Account may not sync with the database.
        //  Link: https://github.com/google/guava/wiki/CachesExplained
        caches.refresh(uuid);
    }

    /**
     * Discards specific Account object from memory.
     *
     * @param uuid the uuid of the Account
     */
    public void flushAccount(@NonNull UUID uuid) {
        caches.invalidate(uuid);
    }

    /**
     * Discards all Account objects from memory.
     */
    public void flushAccounts() {
        caches.invalidateAll();
    }

    /**
     * Returns a view of all the Accounts that are currently loaded in memory.
     *
     * @return a view of all the Accounts loaded in memory
     */
    public @NonNull Collection<Account> getCachedAccounts() {
        return caches.asMap().values().stream().filter(Optional::isPresent).map(Optional::get).toList();
    }

    /**
     * It's simply a wrapper of {@link DataStorage#getOfflineAccounts()}.
     */
    public @NonNull Collection<Account> getOfflineAccounts() {
        return plugin.getDataStore().getOfflineAccounts();
    }

    @Deprecated
    public @Nullable Account getAccount(@NonNull Player player) {
        return fetchAccount(player.getUniqueId());
    }

    @Deprecated
    public @Nullable Account getAccount(@NonNull OfflinePlayer player) {
        return fetchAccount(player.getUniqueId());
    }

    @Deprecated
    public @Nullable Account getAccount(@NonNull UUID uuid) {
        return fetchAccount(uuid);
    }

    @Deprecated
    public @Nullable Account getAccount(@NonNull String name) {
        return fetchAccount(name);
    }

}

