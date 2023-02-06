package me.xanium.gemseconomy.command.command;

import cloud.commandframework.Command;
import cloud.commandframework.bukkit.arguments.selector.MultiplePlayerSelector;
import cloud.commandframework.bukkit.parsers.selector.MultiplePlayerSelectorArgument;
import me.lucko.helper.Schedulers;
import me.lucko.helper.utils.annotation.NonnullByDefault;
import me.xanium.gemseconomy.GemsEconomy;
import me.xanium.gemseconomy.account.Account;
import me.xanium.gemseconomy.command.AbstractCommand;
import me.xanium.gemseconomy.command.CommandManager;
import me.xanium.gemseconomy.command.argument.AmountArgument;
import me.xanium.gemseconomy.command.argument.CurrencyArgument;
import me.xanium.gemseconomy.currency.Currency;
import me.xanium.gemseconomy.event.GemsPayEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static me.xanium.gemseconomy.GemsMessages.*;

public class PayCommand extends AbstractCommand {

    public PayCommand(GemsEconomy plugin, CommandManager manager) {
        super(plugin, manager);
    }

    public void register() {
        Command<CommandSender> pay = manager.commandBuilder("pay")
            .permission("gemseconomy.command.pay")
            .argument(MultiplePlayerSelectorArgument.of("player"))
            .argument(AmountArgument.of("amount"))
            .argument(CurrencyArgument.optional("currency"))
            .senderType(Player.class)
            .handler(context -> {
                Player sender = (Player) context.getSender();
                MultiplePlayerSelector selector = context.get("player");
                double amount = context.get("amount");
                Currency currency = context.getOrDefault("currency", GemsEconomy.getInstance().getCurrencyManager().getDefaultCurrency());
                if (selector.getPlayers().size() > 0) {
                    selector.getPlayers().forEach(p -> pay(sender, p, amount, currency));
                } else {
                    GemsEconomy.lang().sendComponent(sender, "err_player_is_null");
                }
            })
            .build();

        manager.register(List.of(pay));
    }

    @NonnullByDefault
    private void pay(Player sender, Player targetPlayer, double amount, Currency currency) {
        if (!sender.hasPermission("gemseconomy.command.pay." + currency.getPlural().toLowerCase()) &&
            !sender.hasPermission("gemseconomy.command.pay." + currency.getSingular().toLowerCase())) {
            GemsEconomy.lang().sendComponent(sender, GemsEconomy.lang()
                .component(sender, "msg_pay_no_permission")
                .replaceText(CURRENCY_REPLACEMENT.apply(currency.getDisplayName()))
            );
            return;
        }

        if (!currency.isPayable()) {
            GemsEconomy.lang().sendComponent(sender, GemsEconomy.lang()
                .component(sender, "msg_currency_is_not_payable")
                .replaceText(CURRENCY_REPLACEMENT.apply(currency.getDisplayName()))
            );
            return;
        }

        // Check target account
        Account targetAccount = GemsEconomy.getInstance().getAccountManager().fetchAccount(targetPlayer);
        if (targetAccount == null) {
            GemsEconomy.lang().sendComponent(sender, "err_player_is_null");
            return;
        }

        // Check if sender account missing
        Account myselfAccount = GemsEconomy.getInstance().getAccountManager().fetchAccount(sender);
        if (myselfAccount == null) {
            GemsEconomy.lang().sendComponent(sender, "err_account_missing");
            return;
        }

        // Check self pay
        if (targetAccount.getUuid().equals(myselfAccount.getUuid())) {
            GemsEconomy.lang().sendComponent(sender, "err_cannot_pay_yourself");
            return;
        }

        // Check target receivable
        if (!targetAccount.canReceiveCurrency()) {
            GemsEconomy.lang().sendComponent(sender, "err_cannot_receive_money", "account", targetAccount.getNickname());
            return;
        }

        // Check insufficient funds
        if (!myselfAccount.hasEnough(currency, amount)) {
            GemsEconomy.lang().sendComponent(sender, GemsEconomy.lang()
                .component(sender, "err_insufficient_funds")
                .replaceText(CURRENCY_REPLACEMENT.apply(currency.getDisplayName()))
            );
            return;
        }

        // Check target balance overflow
        if (targetAccount.testOverflow(currency, amount)) {
            GemsEconomy.lang().sendComponent(sender, GemsEconomy.lang()
                .component(sender, "msg_currency_overflow")
                .replaceText(ACCOUNT_REPLACEMENT.apply(targetAccount.getNickname()))
                .replaceText(CURRENCY_REPLACEMENT.apply(currency.getDisplayName()))
            );
            return;
        }

        GemsPayEvent event = new GemsPayEvent(currency, myselfAccount, targetAccount, amount);
        if (!Schedulers.sync().call(event::callEvent).join()) return;

        myselfAccount.withdraw(currency, amount);
        targetAccount.deposit(currency, amount);

        GemsEconomy.getInstance().getEconomyLogger().log("[PAYMENT] " + myselfAccount.getDisplayName() +
            " (New bal: " + currency.format(myselfAccount.getBalance(currency)) + ") -> paid " + targetAccount.getDisplayName() +
            " (New bal: " + currency.format(targetAccount.getBalance(currency)) + ") - An amount of " + currency.format(amount)
        );

        GemsEconomy.lang().sendComponent(targetPlayer, GemsEconomy.lang()
            .component(targetPlayer, "msg_received_currency")
            .replaceText(ACCOUNT_REPLACEMENT.apply(myselfAccount.getNickname()))
            .replaceText(AMOUNT_REPLACEMENT.apply(currency, amount))
        );
        GemsEconomy.lang().sendComponent(sender, GemsEconomy.lang()
            .component(sender, "msg_paid_currency")
            .replaceText(ACCOUNT_REPLACEMENT.apply(targetAccount.getNickname()))
            .replaceText(AMOUNT_REPLACEMENT.apply(currency, amount))
        );
    }

}
