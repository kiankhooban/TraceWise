# Stage 2 design changes

Every difference between the Stage 1 design (tag `stage1-submitted`) and the implementation,
recorded when it is made.

| # | Date | Original design | Implemented change | Reason | Impact (features, use cases, classes, patterns) |
|---|---|---|---|---|---|
| 1 | 2026-10-20 | Scenario CSV format has a separate cash deposit/withdrawal direction column (Stage 1 report, F01 input) | Cash direction is encoded by the counter account `CASH`: a deposit is CASH to customer, a withdrawal is customer to CASH | The rules already read sender and receiver; a second field saying the same thing could disagree with them | F01 (`ScenarioCsvFormat`), F02 (R1, R3 read `Transaction.isCashDeposit()`); no pattern change |
| 2 | 2026-10-20 | `Account` has `id`, `bankId`, `watchlisted` | `Account` has `id`, `watchlisted` | No feature reads the bank code, and carrying it would add bank fields to every format and to `Transaction` | None beyond `Account` (not on the UMLet class diagram) |
| 3 | 2026-10-20 | Every account in the data is stored and monitored | Parties outside the bank (the `CASH` counter and ids starting `EXT-`) appear in transactions but are not stored as accounts or monitored | A bank monitors its own customers; without this, a foreign sender of a planted wire received its own threshold report. IBM data has no `EXT-` ids, so all its accounts stay monitored | F01 (account creation, import summary), F02 (accounts scanned); `Transaction.isOutsideBank()` added; no pattern change |
