package tracewise.network;

/**
 * F09: one account in the money-flow graph. hop is its distance from the selected account (0 for that account);
 * alerted means it has an alert; examined means the case's latest investigation looked it up.
 */
public record AccountNode(String accountId, int hop, boolean alerted, boolean examined) {
}
