# Operator Guide

## First Deployment
1. Register traders, yards/warehouses, metal-orders, and trading
   supervisors.
2. Import metal-order, counterparty, credit, sanctions and conflict-
   minerals sourcing history.
3. Seed the per-jurisdiction spec-basis catalog (`metaltrade.facts`) for
   the jurisdictions you actually trade in, citing real official sources
   only. Seed `metaltrade.facts/conflict-minerals-basis` for any
   jurisdiction where a binding conflict-minerals statute applies to
   your own counterparties (Dodd-Frank 1502 for US-nexus deals, EU
   2017/821 for EU-nexus deals) -- every other jurisdiction falls back to
   the OECD Guidance baseline automatically.
4. Confirm which metal types your business trades are in
   `metaltrade.facts/conflict-minerals-metals` (tin, tantalum, tungsten,
   gold, cobalt by default); extend it only with a documented real-world
   citation if your operation treats an additional metal as a conflict
   mineral.
5. Run read-only spec-basis validation per jurisdiction.
6. Configure sanctions / credit escalation and accounts-receivable
   accounts.
7. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record) before any dispatch
- for 3TG/cobalt orders: a genuinely documented chain of custody back to
  the mine of origin AND a conflict-free-certified smelter/refiner
  (e.g. RMI/RMAP-conformant) before any dispatch -- never a paperwork
  formality
- credit-clearance, contract-on-file and conflict-minerals-provenance
  checks before any dispatch; sanctions-screening before any dispatch
  AND any invoice
- sanctions / credit escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Metals and Metal Ores (ISIC 4662,
`cloud-itonami-isic-4662`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a regional metal trader needs to bring a metal-order
(say, a 25-tonne tin sale to a counterparty in Japan) from intake through
provenance verification to a bulk-metal dispatch and an invoice
settlement. Walking through one order, end to end:

1. **Intake.** The trader books the metal-order through `:forms`:
   order-id, metal-type, origin (mine-of-origin or country/district),
   quantity-tonnes, counterparty, price, contract-terms, jurisdiction,
   and the order's own diligence record (credit-cleared?, sanctions-
   screened?, chain-of-custody-documented?, conflict-free-smelter-
   certified?). This creates a metal-order record at `:order/intake`
   status. The MetalTradeAdvisor only normalizes the patch; it does not
   invent the order-id, counterparty, metal-type, origin, or any
   commercial/diligence value.
2. **Verify.** The MetalTradeAdvisor drafts a per-jurisdiction contract /
   sanctions evidence checklist (`:provenance/verify`) from
   `metaltrade.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (credit-clearance record, contract/PO, sanctions-screening
   record). WHEN the order's metal-type is a conflict mineral, it ALSO
   drafts an informational conflict-minerals citation (Dodd-Frank 1502,
   EU 2017/821, or the OECD Guidance baseline, per
   `metaltrade.facts/conflict-minerals-citation`) -- this citation is for
   the human reviewer's benefit only; it does NOT substitute for the
   order's own chain-of-custody/smelter-certification facts, which the
   governor re-verifies independently at dispatch. The
   `:metal-trading-governor` sign-off gate must clear: it checks the
   jurisdiction actually has an official spec-basis on file (never
   invent one). A jurisdiction with no spec-basis is a HARD hold at the
   governor node -- it never even reaches a human. This verification
   always escalates to a human for approval; it is never auto.
3. **Dispatch.** Before bulk metal/ore can leave the yard/warehouse, the
   `:metal-trading-governor` sign-off gate runs the full HARD check set
   against the order's own ground truth: the spec-basis exists, the
   evidence checklist is complete, the counterparty's credit has been
   cleared, contract-terms are on file, -- WHEN the metal-type is a
   conflict mineral -- a documented chain of custody back to the mine
   AND a conflict-free-certified smelter/refiner are BOTH on file, the
   counterparty has passed sanctions screening, and the order has not
   already been dispatched. Any failure is a HARD hold that a human
   cannot override. If every check is clean, the proposal STILL always
   escalates to a human trading supervisor -- a `:delivery/dispatch`
   never auto-commits at any phase. On approval, the dispatch record is
   drafted (`<JURISDICTION>-DISPATCH-000001`) and the order's
   `:dispatched?` flag is set.
4. **Settle.** Once bulk metal/ore has actually been dispatched, the
   invoice is settled (`:invoice/settle`): the money side of the trade,
   custody / financial transfer. The governor re-checks the spec-basis,
   the evidence completeness, the sanctions screening, and that this
   order's invoice has not already been settled. As with the dispatch, a
   clean invoice STILL always escalates to a human trading supervisor --
   `:invoice/settle` never auto-commits. On approval the invoice record
   is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the dispatch
   record, the invoice sign-off, and the invoice record are all appended
   to the `:audit-ledger` -- immutable and exportable, so a counterparty,
   a downstream buyer running its OWN Dodd-Frank 1502 / EU 2017/821 due
   diligence, or a regulatory dispute can be traced back to the exact
   spec-basis citation, evidence checklist, conflict-minerals provenance
   evidence (where applicable), and supervisor sign-off that authorized
   the dispatch and invoice. If something is wrong with the counterparty
   or the sourcing (a credit deterioration, a sanctions hit, a contract
   gap, an unverifiable chain of custody), that gets raised as a flag and
   routed through the escalation gate instead of being silently
   suppressed -- a dispatch for that order then waits on governor sign-
   off of the flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a dispatch started with incomplete evidence, an
uncleared counterparty credit or a contract gap, a 3TG/cobalt dispatch
started with an unverified chain of custody or an uncertified smelter, a
sanctions screening suppressed to force a dispatch through, or an
invoice posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:metal-trading-governor` gate exists is the bundled
demo, which walks one clean metal-order through intake → verify →
dispatch → settle (each dispatch/settle pausing for human approval) and
then exercises every HARD-hold failure mode in isolation, PLUS the
control case that proves the conflict-minerals check is genuinely
metal-type-gated:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a 3TG metal (gold) with neither a documented chain of custody nor a
  conflict-free-certified smelter → HOLD
  (`:conflict-minerals-provenance-unverified`) -- the domain-defining
  check,
- the SAME unverified-provenance facts on a copper order (not a conflict
  mineral) → dispatches CLEANLY, only the ordinary human sign-off gate
  applies -- proving the check is metal-type-gated, not a blanket
  provenance requirement,
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
sanctions-screening), GENUINE conflict-minerals chain-of-custody and
conflict-free-smelter verification for every 3TG/cobalt order (never a
paperwork formality), and human review for every dispatch- and invoice-
affecting action.
