# Emergency Override — finalized product policy

> Status: **final product requirement**. Supersedes the 2026-10-29 investigative
> record that incorrectly preserved a three-per-month allowance.

Emergency Override is unlimited. It pauses protection for the selected prayer
only and requires one of the supported reasons. Every confirmation appends an
independent event to the local Room database.

An override is not prayer verification. It must not create prayer completion,
increase prayer statistics or streaks, or unlock prayer-completion achievements.

Override events remain under the local device owner and are excluded from all
cloud synchronization and account-adoption paths.

## Regression history

The original `MAX_OVERRIDES_PER_MONTH = 3` implementation survived removal of
the separate weekly mercy/free-pass feature. Commit `beed380` then strengthened
and exposed that obsolete monthly contract in the repository, dialog, and tests.
Commit `9aad9d7` documented the allowance as intentional. That mistaken policy
record is why the old counter remained active and later appeared to have
returned.

Room migration 12→13 converts the monthly aggregate table into append-only
events. Exact legacy prayer/date/timestamp details are recovered from historical
prayer rows; aggregate uses without recoverable prayer detail are retained as
`LEGACY_UNKNOWN` events rather than deleted.
