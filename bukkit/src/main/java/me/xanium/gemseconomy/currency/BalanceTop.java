package me.xanium.gemseconomy.currency;

import com.google.common.collect.Lists;
import me.xanium.gemseconomy.data.TransientBalance;
import me.xanium.gemseconomy.utils.UtilTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a sorted list of balances associated with a {@link Currency}.
 */
public class BalanceTop {

    /**
     * Fallback in case sth wrong.
     */
    public static final BalanceTop EMPTY = new BalanceTop(new ArrayList<>());
    /**
     * Number of entries in a page.
     */
    public static final int ENTRY_PER_PAGE = 10;

    private final List<List<TransientBalance>> partition;
    private final String lastUpdate;

    BalanceTop(List<TransientBalance> results) {
        List<TransientBalance> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(TransientBalance::amount).reversed()); // sort entries
        this.partition = Lists.partition(sorted, ENTRY_PER_PAGE);
        this.lastUpdate = UtilTime.now();
    }

    /**
     * Gets the entries at specific page.
     *
     * @param page page index starting from 0
     *
     * @return the entries at specific page index
     */
    public List<TransientBalance> getResultsAt(int page) {
        return this.partition.get(page);
    }

    /**
     * @return the number of pages this BalanceTop has
     */
    public int getMaxPage() {
        return this.partition.size();
    }

    /**
     * @return the formatted timestamp when last update
     */
    public String getLastUpdate() {
        return this.lastUpdate;
    }
}