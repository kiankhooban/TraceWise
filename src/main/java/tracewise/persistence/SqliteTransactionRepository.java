package tracewise.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tracewise.domain.Account;
import tracewise.domain.DateRange;
import tracewise.domain.PaymentChannel;
import tracewise.domain.Transaction;

public class SqliteTransactionRepository implements TransactionRepository {

    private static final String COLUMNS = "id, ts, sender, receiver, amount_cad, original_amount, currency, "
            + "channel, counterparty_country, laundering_label";

    private final Database db;

    public SqliteTransactionRepository(Database db) {
        this.db = db;
    }

    /** Stores the transactions and creates any new accounts, all in one database transaction. */
    @Override
    public void saveAll(List<Transaction> transactions) {
        db.inTransaction(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT INTO transactions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Transaction t : transactions) {
                    ps.setString(1, t.id());
                    ps.setLong(2, t.timestamp().toEpochMilli());
                    ps.setString(3, t.senderAccountId());
                    ps.setString(4, t.receiverAccountId());
                    ps.setString(5, t.amountCad().toPlainString());
                    ps.setString(6, t.originalAmount().toPlainString());
                    ps.setString(7, t.currency());
                    ps.setString(8, t.channel().name());
                    ps.setString(9, t.counterpartyCountry());
                    if (t.launderingLabel() == null) {
                        ps.setNull(10, Types.INTEGER);
                    } else {
                        ps.setInt(10, t.launderingLabel() ? 1 : 0);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new DatabaseException(e);
            }
            for (Transaction t : transactions) {
                for (String id : List.of(t.senderAccountId(), t.receiverAccountId())) {
                    if (!Transaction.CASH.equals(id)) {
                        saveAccount(new Account(id, false));
                    }
                }
            }
        });
    }

    @Override
    public boolean exists(String id) {
        return findTransaction(id).isPresent();
    }

    @Override
    public void saveAccount(Account account) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT OR IGNORE INTO accounts (id, watchlisted) VALUES (?, ?)")) {
            ps.setString(1, account.id());
            ps.setInt(2, account.watchlisted() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public List<Account> accounts() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, watchlisted FROM accounts ORDER BY id"); ResultSet rs = ps.executeQuery()) {
            var accounts = new ArrayList<Account>();
            while (rs.next()) {
                accounts.add(new Account(rs.getString(1), rs.getInt(2) == 1));
            }
            return accounts;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public List<Transaction> transactionsFor(String accountId, DateRange range) {
        return query("SELECT " + COLUMNS + " FROM transactions WHERE (sender = ? OR receiver = ?) "
                + "AND ts >= ? AND ts < ? ORDER BY ts, id", accountId, accountId,
                range.from().toEpochMilli(), range.to().toEpochMilli());
    }

    @Override
    public Optional<Transaction> findTransaction(String id) {
        return query("SELECT " + COLUMNS + " FROM transactions WHERE id = ?", id).stream().findFirst();
    }

    private List<Transaction> query(String sql, Object... params) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                var result = new ArrayList<Transaction>();
                while (rs.next()) {
                    int label = rs.getInt(10);
                    Boolean launderingLabel = rs.wasNull() ? null : label == 1;
                    result.add(new Transaction(rs.getString(1), Instant.ofEpochMilli(rs.getLong(2)),
                            rs.getString(3), rs.getString(4), new BigDecimal(rs.getString(5)),
                            new BigDecimal(rs.getString(6)), rs.getString(7),
                            PaymentChannel.valueOf(rs.getString(8)), rs.getString(9), launderingLabel));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }
}
