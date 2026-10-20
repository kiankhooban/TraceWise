package tracewise.actions;

import java.util.Optional;
import tracewise.domain.Transaction;

/** Watchlisted accounts score higher when monitoring runs again on new data. */
public record AddToWatchlistCommand(String accountId) implements ActionCommand {

    @Override
    public void validate(CommandContext ctx) {
        if (Transaction.isOutsideBank(accountId)) {
            throw new IllegalArgumentException(accountId + " is outside the bank and cannot be watchlisted");
        }
        ctx.accounts().findAccount(accountId).orElseThrow(() -> new IllegalArgumentException("No account " + accountId));
    }

    @Override
    public void execute(CommandContext ctx) {
        ctx.accounts().setWatchlisted(accountId, true);
    }

    @Override
    public String describe() {
        return "Add account " + accountId + " to the watchlist";
    }

    @Override
    public Optional<String> targetCaseId() {
        return Optional.empty();
    }

    @Override
    public String target() {
        return accountId;
    }
}
