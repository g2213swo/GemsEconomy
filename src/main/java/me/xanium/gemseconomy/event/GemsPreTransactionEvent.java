package me.xanium.gemseconomy.event;

import me.xanium.gemseconomy.account.Account;
import me.xanium.gemseconomy.currency.Currency;
import me.xanium.gemseconomy.utils.TransactionType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class GemsPreTransactionEvent extends Event implements Cancellable {

    private static final HandlerList handlerList = new HandlerList();
    private final Currency currency;
    private final Account account;
    private final double amount;
    private final TransactionType type;
    private boolean cancelled = false;

    public GemsPreTransactionEvent(Currency currency, Account account, double amount, TransactionType type) {
        this.currency = currency;
        this.account = account;
        this.amount = amount;
        this.type = type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Account getAccount() {
        return account;
    }

    public double getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
