# TraceWise GUI design (agreed with Kian, 2026-11-02)

The look of the JavaFX GUI for period 3. Pages, tabs and their structure stay as they are (Stage 1 says only
"JavaFX desktop application covering all major functionality"); this is presentation. Previews:
https://claude.ai/artifact/XHJTtn37TDv1NpKHN6DL5A (private to Kian). Research behind it: three research briefs on
2026-11-02 (Bloomberg Terminal design, AML investigation tools, dense data UIs and JavaFX) and one on fonts.

## Principles and where they come from

| Rule | Source |
|---|---|
| Function over form; colour is scarce and always means a state | Bloomberg UX team ("function over form", "we never design for design's sake"), digitalcontentnext.org, 2017 |
| Colour never carries meaning alone: every state is also a text code (`HIGH 60`, `VER 6/6`, `AGENT`) | Bloomberg's colour-vision work (~20,000 users with CVD), techatbloomberg.com, 2021 |
| Panels with hairline borders and a title strip with a short code (ALRT, CASE, APRV, ADMN, ASK, LOG); square corners; no cards, pills or shadows | Bloomberg panels and Launchpad components |
| Keyboard first: number keys for screens and tabs, F-keys for actions, a key legend in the status bar, a command bar (Ctrl+K) | Bloomberg command line and function keys |
| Not Bloomberg's amber, `GO` or name: the accent is our own signal cyan | Bloomberg protects black and amber as brand |
| Dense rows: 26 px, header the same height | IBM Carbon data tables (xs 24, sm 32) |
| Every id, amount, time and code in the mono face, right-aligned for amounts; prose in the sans at 13.5 px, line spacing about 1.6, narrower measure, softer tone | Table-design guidance; JavaFX cannot enable tabular figures |
| Case fact header (trigger, subject, window, net activity, risk, finding) and a decision bar | Hummingbird case overview |
| Age column in the alert queue | Oracle Behavior Detection alert list |
| Agent steps as a timestamped log | Unit21 "each finding shows its work" |
| Agent proposes, human decides, shown and logged as `AGENT` / `HUMAN` | Lucinity audit split; four-eyes review |

## Fonts

Source Sans 3 (labels, prose) and JetBrains Mono (ids, amounts, times, codes), both SIL OFL 1.1, bundled as static
TTFs under `gui/src/main/resources/tracewise/fonts/` with their licences. Chosen by measurement: Source Sans 3 has
tabular digits by default and is the narrowest of the candidates; Inter and Geist have proportional digits by
default, which JavaFX cannot switch off. JavaFX exposes each static weight as its own family, so CSS names the
family per weight (for example `"Source Sans 3 Semibold"`, checked with `Font.getFamilies()`).

## Colours (looked-up colours on `.root`; the day theme overrides the same names)

| Name | Dark | Day | Use |
|---|---|---|---|
| `-tw-bg` | #121417 | #F7F8FA | window |
| `-tw-chrome` | #0D0F11 | #EDF0F3 | top bar, status bar |
| `-tw-panel` | #181B1F | #FFFFFF | title strips, selected tab |
| `-tw-line` | #2A2F36 | #D5DBE2 | hairlines |
| `-tw-faint` | #1E2227 | #E6EAEF | row dividers |
| `-tw-strong` | #3A414B | #9AA4B1 | control borders |
| `-tw-text` | #E8EAED | #14171C | primary text |
| `-tw-soft` | #C9CED6 | #2F3742 | prose, secondary values |
| `-tw-muted` | #9BA3AE | #5A6472 | labels, headers |
| `-tw-accent` | #3DD6D0 | #0B7470 | focus, selection, primary action |
| `-tw-on-accent` | #0D0F11 | #FFFFFF | text on the accent |
| `-tw-red` | #FF7A59 | #B3361B | HIGH risk, ESCALATE |
| `-tw-amber` | #F2B33D | #8A5A00 | MED risk, waiting, INCONCLUSIVE |
| `-tw-green` | #4CC38A | #1F7A4A | verified, ok |
| `-tw-selected` | #1B2A2E | #E3F2F1 | selected row |

Risk levels: HIGH at 60 and over, MED 45 to 59, LOW under 45 (display only; the score itself is F02's).

## Additions beyond restyling (design change 50)

Keyboard shortcuts and the key legend, the command bar (navigates to a screen or opens a case or alert by id; no
new logic), the age column, and the dark/day theme switch. Approving from the Case screen is not added: F06's
approval queue stays the one place to decide.
