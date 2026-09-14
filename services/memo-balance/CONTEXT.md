# memo-balance

The system of record for accounts written off to memo (RFP §3.13(bis)): detects them, tracks and adjusts their balance, flags exceptions, and holds their supporting documents. Implemented in PR #10 (Tickets 01–08, closing Spec #1).

## Language

**Memo account**:
An account flagged because a Finacle transaction narration matched "written off" (case-insensitive). Tracked from detection through liquidation.
_Avoid_: Written-off account, memo record.

**Imported — Pending Review**:
A Memo account's status immediately after detection, before any adjustment has touched it.

**Liquidated**:
A Memo account's status once its balance reaches exactly zero, whether by an exact payment or an excessive one capped at zero. Distinct from the *loan segment* "Paid-Off" (`account-verification`'s concept, driven by `MemoLiquidated` — see ADR-0006); `MemoStatus.LIQUIDATED` is this context's own internal lifecycle state.
_Avoid_: Paid-off (that's account-verification's term for the loan segment, not this context's status).

**Adjustment**:
A partial payment or an approved write-off applied against a Memo account's balance. Always reduces the balance, never increases it; an amount exceeding the outstanding balance caps at zero rather than going negative.

**Unallocated payment**:
The portion of an adjustment amount that exceeded the outstanding balance and couldn't be applied. Flagged as an exception for Transaction Services, not rejected.

**Balance discrepancy**:
A flagged exception raised when this context's own calculated balance disagrees with Vision's reported balance for the same account.

## Published events

Kafka topics, per ADR-0001. These are the authoritative schemas — the Java records in `event/` are the implementation, this is the contract other contexts should read instead of that source.

**`mbp.memo-balance.balance-adjusted`** (`MemoBalanceAdjustedEvent`) — published on every adjustment:
`accountNumber` (string), `adjustmentType` (`PARTIAL_PAYMENT` | `WRITE_OFF_APPROVED`), `previousBalance` (decimal), `newBalance` (decimal), `occurredAt` (instant).

**`mbp.memo-balance.liquidated`** (`MemoLiquidatedEvent`) — published the instant a balance reaches exactly zero. Consumed by `account-verification` (drives the Owing → Paid-Off transition, ADR-0006) and `clearance-orchestration` (starts the ICAD clearance workflow):
`accountNumber` (string), `liquidatedAt` (instant).

## Consumed events

Both defined by memo-balance itself, as the producing contexts (`integration`) don't exist yet — treat these as the target shape `integration` should produce to, not a memo-balance-owned contract:

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`): `accountNumber`, `customerId`, `branchSol`, `currency`, `postingReference`, `narration`, `balance`, `transferDate`, `source`, `country` (nullable — optional for backward compatibility, see the Ticket 08 NPE fix in PR #10).

**`mbp.integration.vision-balance-synced`** (`VisionBalanceSyncedEvent`): `accountNumber`, `balance`, `syncedAt`.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Document storage**: `DocumentStorage` — `LocalFilesystemDocumentStorage` writes to a local directory, unencrypted, until real Blob storage (ADR-0003) is provisioned.

## Closed seams

- **RBAC**: `JwtRoleConverter` validates real RS256 tokens issued by `shared-platform`'s authentication-service, verified via its JWKS endpoint (ADR-0017) — the dev-only symmetric key this bullet used to describe is gone.
- **Country/GL mapping**: `CountryConfigLookup` is now backed by `HttpCountryConfigLookup`, calling `reference-data-config`'s real `GET /countries/{code}` (ADR-0021) — `StaticCountryConfigLookup` is deleted. A null/unrecognized/unreachable country still falls back to the NG default, same behavior as the old stand-in.
