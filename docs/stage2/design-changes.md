# Stage 2 design changes

Every difference between the Stage 1 design (tag `stage1-submitted`) and the implementation,
recorded when it is made.

| # | Date | Original design | Implemented change | Reason | Impact (features, use cases, classes, patterns) |
|---|---|---|---|---|---|
| 1 | 2026-10-20 | Scenario CSV format has a separate cash deposit/withdrawal direction column (Stage 1 report, F01 input) | Cash direction is encoded by the counter account `CASH`: a deposit is CASH to customer, a withdrawal is customer to CASH | The rules already read sender and receiver; a second field saying the same thing could disagree with them | F01 (`ScenarioCsvFormat`), F02 (R1, R3 read `Transaction.isCashDeposit()`); no pattern change |
| 2 | 2026-10-20 | `Account` has `id`, `bankId`, `watchlisted` | `Account` has `id`, `watchlisted` | No feature reads the bank code, and carrying it would add bank fields to every format and to `Transaction` | None beyond `Account` (not on the UMLet class diagram) |
| 3 | 2026-10-20 | Every account in the data is stored and monitored | Parties outside the bank (the `CASH` counter and ids starting `EXT-`) appear in transactions but are not stored as accounts or monitored | A bank monitors its own customers; without this, a foreign sender of a planted wire received its own threshold report. IBM data has no `EXT-` ids, so all its accounts stay monitored | F01 (account creation, import summary), F02 (accounts scanned); `Transaction.isOutsideBank()` added; no pattern change |
| 4 | 2026-10-20 | `WindowedRule.isMatch(window: List<Transaction>)` | `isMatch(account: Account, window: List<Transaction>)` | R2 and R4 must tell money in from money out, which depends on which account is being scanned | F02; `WindowedRule` and all six rules; Template Method unchanged (`evaluate()` still fixes the algorithm). The class diagram is updated in Stage 2 |
| 5 | 2026-10-20 | `RiskScorer.score(match, account)` | `score(match, account, rulesTriggered)` | The score includes how many different rules the account triggered (Stage 1, F02 step 5), which one match cannot know | F02; `RiskScorer`, `MonitoringService` |
| 6 | 2026-10-20 | `AnthropicLLMClient(config: ModelConfig)` | `AnthropicLLMClient(config: ModelConfig, apiKey: String)`; the key is read from `.env` by `EnvFile` and never stored in `ModelConfig` | `ModelConfig` is shown in logs and reports (model name, prices); keeping the key out of it means it cannot leak through them | F03, F10, F11 (LLM access); Adapter pattern unchanged |
