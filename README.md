# cloud-itonami-isic-4662

Open Business Blueprint for **ISIC Rev.5 4662**: Wholesale of Metals and
Metal Ores -- metal-order intake, per-jurisdiction counterparty-diligence /
sanctions regulatory verification, conflict-minerals chain-of-custody
provenance verification, bulk-metal/ore dispatch, and invoice settlement
for a wholesaler of base and precious metals and metal ores (copper, tin,
tungsten, gold, iron ore, aluminum, cobalt, and more).

This repository publishes a metal-wholesale actor -- metal-order intake,
per-jurisdiction contract / sanctions regulatory verification, conflict-
minerals provenance verification, bulk-metal/ore dispatch and invoice
settlement -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so a regional metal trader never surrenders
counterparty, credit, sanctions and sourcing-provenance data to a closed
metal-trading / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet, here it is **MetalTradeAdvisor ⊣ Metal
Trading Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:metal-trading-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`), commission-brokerage
(`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`)
and provision-trading (`cloud-itonami-isic-4630`) siblings, this
vertical is SELF-CONTAINED**: there is no `kotoba-lang/metaltrade` to
delegate metal-trading validation to, so the credit-clearance /
contract-on-file / conflict-minerals-provenance / sanctions-screening
checks live as direct entity boolean reads in `metaltrade.governor` (off
dedicated `:credit-cleared?` / `:contract-terms` / `:chain-of-custody-
documented?` / `:conflict-free-smelter-certified?` /
`:sanctions-screened?` facts on the `metal-order` record), rather than
wrapping an external capability library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it has
> **no notion of which jurisdiction's customs / sanctions law is
> official, no license to dispatch real bulk metal/ore to a counterparty
> or settle a real invoice, and no way to know on its own whether the
> counterparty's credit has actually been cleared, whether contract terms
> are actually on file, whether a 3TG/cobalt shipment actually has a
> documented chain of custody back to the mine and a conflict-free-
> certified smelter/refiner, or whether OFAC / equivalent sanctions
> screening has actually been passed**. Letting it dispatch metal or
> settle an invoice directly invites fabricated regulatory citations,
> bulk metal/ore leaving the yard to an uncreditworthy or unscreened
> counterparty, and -- the defining risk of this vertical -- conflict
> minerals (tin, tantalum, tungsten, gold, cobalt) entering a supply
> chain with no genuine chain-of-custody trail, exposing the operator
> and every downstream buyer to real enforcement, reputational and
> human-rights liability. This project seals the MetalTradeAdvisor into a
> single node and wraps it with an independent **Metal Trading
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers metal-order intake through customs / sanctions
regulatory verification, conflict-minerals provenance verification,
bulk-metal/ore dispatch and invoice settlement. It does **not**, by
itself, hold any metal-wholesale licence, export/import authority or
operating authority required to run a metal-wholesale business in a
given jurisdiction, and it does not claim to. It also does not perform
the actual physical yard/warehouse loadout or route optimization itself,
or judge trading-book economics -- freight/route optimization and
trading-book optimization (the blueprint's own `:optimization`
technology) is a follow-up slice, not in this R0. Whoever deploys and
operates a live instance (a qualified trading supervisor / yard operator)
supplies any jurisdiction-specific operating authority, the real
stacker-reclaimer/overhead-crane dispatch integration and the real ERP /
accounts-receivable integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch.

### Actuation

**Dispatching real bulk metal/ore to a counterparty from the wholesale
yard/warehouse and settling a real metal invoice are never autonomous, at
any phase, by construction.** Two independent layers enforce this
(`metaltrade.governor`'s `:delivery/dispatch`/`:invoice/settle`
high-stakes gate and `metaltrade.phase`'s phase table, which never puts
either op in any phase's `:auto` set) -- see `metaltrade.phase`'s
docstring and `test/metaltrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check and
recommend; a human trading supervisor is always the one who actually
dispatches a bulk-metal/ore delivery or settles an invoice. Grounded in
metal-trading doctrine (the same discipline every regulator in
`metaltrade.facts` codifies: a real dispatch and a real invoice
settlement are human sign-off acts) -- a genuine DUAL-actuation shape,
applied SEQUENTIALLY to the SAME metal-order (dispatch first, invoice
settlement later), unlike `retailops`/4711's own `:kind`-distinguished
alternative-action shape.

## The core contract

```
metal-order intake + jurisdiction facts (metaltrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ MetalTradeAdvisor      │ ─────────────▶ │ Metal Trading Governor      │  (independent system)
   │ (sealed)               │  + citations    │ spec-basis · evidence-      │
   └───────────────────────┘                 │ incomplete · credit-         │
          │                 commit ◀┼ uncleared · contract-missing ·│
          │                         │ conflict-minerals-provenance-  │
    record + ledger        escalate ┼ unverified · counterparty-     │
          │              (ALWAYS for│ sanctions-flag-unresolved ·    │
          │       :delivery/        │ already-dispatched ·           │
          │       dispatch/         │ already-invoiced               │
          │       :invoice/         └────────────────────────────┘
          │       settle)
          ▼
      human approval
```

**The MetalTradeAdvisor never dispatches bulk metal/ore to a counterparty
or settles an invoice the Metal Trading Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; an uncleared counterparty
credit; no contract-terms on file; a 3TG/cobalt shipment with no verified
chain of custody AND conflict-free-certified smelter; an unresolved
sanctions-screening flag; a double dispatch/invoice) force **hold** and
*cannot* be approved past; a clean dispatch/invoice proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean provenance-verify + dispatch + invoice lifecycle, plus seven HARD-hold/control cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Conflict minerals: the domain-defining check

Unlike every other check in this actor (and every check in the fuel-
wholesale/general-trading/commission-brokerage/agri-wholesale/provision-
trading siblings), `conflict-minerals-provenance-unverified` is gated on
**metal type**, not on jurisdiction or counterparty. When `:metal-type`
is tin, tantalum, tungsten, gold (the "3TG" minerals named by Dodd-Frank
Section 1502 and EU Regulation 2017/821) or cobalt (this actor's own
policy extension beyond strict 3TG, following the OECD Guidance's
metal-agnostic framing -- see `docs/business-model.md`), `:delivery/
dispatch` HARD-holds unless BOTH `:chain-of-custody-documented?` (a
documented trail back to the mine of origin) AND `:conflict-free-
smelter-certified?` (the smelter/refiner is on a recognized conflict-
free list, e.g. RMI/RMAP-conformant) are true. This check applies
**unconditionally across every jurisdiction** -- see
`metaltrade.governor`'s namespace docstring and `docs/adr/
0001-architecture.md` Decision 4 for the full reasoning. It is a NO-OP
for every other metal type (copper, iron ore, aluminum, nickel, zinc,
lead, ...); `test/metaltrade/governor_contract_test.clj`'s
`conflict-minerals-check-is-a-no-op-for-non-conflict-metals` proves this
directly with a copper order carrying the SAME unverified-provenance
facts as a HELD gold order.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous stacker-
reclaimer/conveyor robot performs the physical bulk-ore reclaim at the
wholesale yard, and an autonomous overhead-crane/forklift robot performs
the physical ingot/coil/cathode pick at the wholesale warehouse, under
the actor, gated by the independent **Metal Trading Governor**. The
governor never dispatches hardware itself: a dispatch-clearing action
must have cleared the same sign-off a human trading supervisor would
need. This restates the fleet-wide robotics premise three ways
(ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robot that performs the physical act, and the Metal Trading
Governor is the independent gate that robot's command must pass -- a
robot may reclaim bulk ore or stage a metal ingot pallet, but only after
the governor and a human supervisor both agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Metal Trading Governor, dispatch/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4662`). Like its wholesale-trading siblings, this vertical is NOT backed
by a separate bespoke domain capability lib: the metal-trading checks
(credit-clearance, contract-on-file, conflict-minerals provenance,
sanctions-screening) are direct entity boolean reads in
`metaltrade.governor`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/metaltrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/metaltrade/registry.cljc` | Dispatch/invoice draft records (record construction only -- the Metal Trading Governor's checks are direct entity booleans, so there are no pure range-check functions to host here) |
| `src/metaltrade/facts.cljc` | Per-jurisdiction customs/sanctions catalog with an official spec-basis citation per entry, PLUS a separate conflict-minerals-basis catalog (Dodd-Frank 1502 for USA, EU 2017/821 for DEU/EU, OECD Guidance elsewhere) and the 3TG+cobalt `conflict-minerals-metals` set, honest coverage reporting |
| `src/metaltrade/metaltradeadvisor.cljc` | **MetalTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/provenance-verification/dispatch/invoice proposals |
| `src/metaltrade/governor.cljc` | **Metal Trading Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · conflict-minerals-provenance-unverified · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/metaltrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/metaltrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/metaltrade/sim.cljc` | demo driver |
| `test/metaltrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers metal-order intake through customs / sanctions
regulatory verification, conflict-minerals provenance verification,
bulk-metal/ore dispatch and invoice settlement -- the core governed
lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Metal-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:provenance/verify`) | Real weighbridge/warehouse-management/ERP integration, freight routing and trading-book economics |
| Bulk-metal/ore dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, verified conflict-minerals provenance (where the metal type applies), a passed sanctions screen and no double-dispatch (`:delivery/dispatch`) | |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a metal-purity/
assay-certificate check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern this
repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`metaltrade.facts/coverage` reports how many requested jurisdictions
actually have an official GENERAL spec-basis in `metaltrade.facts/
catalog` -- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194
jurisdictions worldwide. The SEPARATE `metaltrade.facts/conflict-
minerals-basis` catalog seeds a BINDING conflict-minerals statute for
only 2 of those 4 (USA: Dodd-Frank Section 1502; DEU, representing the
EU: Regulation (EU) 2017/821) -- every other jurisdiction falls back to
the OECD Guidance as a non-statutory operational baseline. This is a
starting catalog to prove the governor contract end-to-end, not a claim
of global coverage. I do not have live web access; the legal-basis and
owner-authority citations above are drawn from training-time knowledge
with reasonable confidence for the OFAC/CBP/HMRC/German-customs general
regime and for Dodd-Frank Section 1502 and EU Regulation 2017/821
themselves, but the specific German national competent authority
(BAFA) attribution for EU 2017/821 enforcement should be independently
verified before this catalog is relied on operationally -- see `docs/
business-model.md`'s "Jurisdiction coverage (honest)" section for the
full picture. Adding a jurisdiction is additive: one map entry in
`metaltrade.facts/catalog` (and, where a binding statute genuinely
exists, one entry in `conflict-minerals-basis`), citing a real official
source -- never fabricate a jurisdiction's requirements to make coverage
look bigger.

## Maturity

`:implemented` -- `MetalTradeAdvisor` + `Metal Trading Governor` run as
real, tested code (see `Run` above), following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own direct-entity-boolean
metal-trading checks -- including the fleet's first metal-type-gated
(rather than jurisdiction-or-kind-gated) domain-defining check. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
