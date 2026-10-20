# Stage 2 design changes

Every difference between the Stage 1 design (tag `stage1-submitted`) and the implementation,
recorded when it is made.

| # | Date | Original design | Implemented change | Reason | Impact (features, use cases, classes, patterns) |
|---|---|---|---|---|---|
| 1 | 2026-10-20 | Scenario CSV format has a separate cash deposit/withdrawal direction column (Stage 1 report, F01 input) | Cash direction is encoded by the counter account `CASH`: a deposit is CASH to customer, a withdrawal is customer to CASH | The rules already read sender and receiver; a second field saying the same thing could disagree with them | F01 (`ScenarioCsvFormat`), F02 (R1, R3 read `Transaction.isCashDeposit()`); no pattern change |
