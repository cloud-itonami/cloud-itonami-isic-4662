# ADR-0001: MetalTradeAdvisor ⊣ Metal Trading Governor architecture

## Status

Accepted. `cloud-itonami-isic-4662` published directly as `:implemented`
in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4662` publishes an OSS business blueprint for
wholesale of metals and metal ores (metal-order intake, per-jurisdiction
contract / sanctions regulatory verification, conflict-minerals
chain-of-custody provenance verification, dispatch, and invoice
settlement). Like every prior actor in this fleet, the blueprint alone
is not an implementation: this ADR records the governed-actor
architecture that establishes it as real, tested code, following the
same langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established by `cloud-itonami-isic-6511` (life insurance) and
applied across many prior siblings, most directly the PRINCIPAL
wholesale-trading siblings: `cloud-itonami-isic-4671` (fuel wholesale,
single-commodity excise/sanctions focus), `cloud-itonami-isic-4690`
(general/diversified wholesale trading, multi-commodity export-control/
sanctions focus), `cloud-itonami-isic-4620` (agri-wholesale, RAW
agricultural inputs and live animals, biosecurity focus, the fleet's
first kind-gated certificate split), and `cloud-itonami-isic-4630`
(provision trading, PROCESSED food/beverage/tobacco, the fleet's first
three-way category-gated split). `cloud-itonami-isic-4610` (commission
brokerage, AGENCY, never takes title, dual-agency conflict-of-interest
focus) is a related but structurally different (agency, not principal)
sibling, referenced here for the "a genuinely new regulatory concern
gets its own named check" precedent only.

ISIC 4662 is a PRINCIPAL trading model like 4671/4690/4620/4630 -- the
wholesaler takes title and resells. Its defining regulatory exposure is
genuinely different from every one of those siblings: not a
jurisdiction-of-the-trade regulatory citation (excise, food-safety,
biosecurity, export-control), but **chain-of-custody/provenance-
traceability of the COMMODITY ITSELF**. Real metals/ores trading --
especially tin, tantalum, tungsten, gold (the internationally-recognized
"3TG" minerals) and, increasingly, cobalt -- is gated by real,
well-known international regulation requiring the trader to trace the
mineral back through the supply chain to confirm it did not originate
from or finance conflict zones or human-rights abuses. This is why this
vertical's domain-defining check is gated on **metal type**, not
jurisdiction or a jurisdiction/kind pairing, a genuinely new gating
axis for this fleet's wholesale-trading cluster -- see Decision 4.

Like the four principal wholesale-trading siblings, this vertical has NO
bespoke domain capability library in `kotoba-lang` to wrap (verified: no
`kotoba-lang/metaltrade`-style repo exists, and `kotoba-lang/robotics` is
the generic cross-cutting robotics contract every cloud-itonami vertical
already uses, not a domain-specific library for this vertical). This
build therefore uses self-contained domain logic. The metal-trading
checks (credit-clearance, contract-on-file, conflict-minerals
provenance, sanctions-screening) are direct entity boolean reads in
`metaltrade.governor`, off dedicated `:credit-cleared?` / `:contract-
terms` / `:chain-of-custody-documented?` / `:conflict-free-smelter-
certified?` / `:sanctions-screened?` facts on the `metal-order` record --
NO pure range-check functions are needed (contrast the crude-extraction
sibling, whose registry hosts its reservoir/annular/water-cut/H2S range
checks).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:metal-trading-governor`, is grep-verified UNIQUE among the actor fleet
repos checked out at build time -- no naming-collision precedent
question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:metal-trading-governor` is grep-verified unique across every
`blueprint.edn` checked out locally at build time (340 governor entries
surveyed, no `metal`/`ore` match). This build follows the SAME
governed-actor architecture as every prior actor, but with its own
distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/metaltrade` to wrap, and no range-check functions to host)

Like the fuel-wholesale, general-trading, commission-brokerage, agri-
wholesale and provision-trading siblings (and unlike the crude-
extraction sibling, which hosts pure physical range-check functions in
its registry because its governor re-verifies measured physical
values), this metal-wholesale vertical needs no range-check functions:
there is no pre-existing metal-trading capability library to delegate
to, AND the governor's domain checks (credit-clearance, contract-on-
file, conflict-minerals provenance, sanctions-screening) are direct
entity boolean reads off the `metal-order` record's own dedicated facts
-- not measured-value-vs-limit range comparisons. So `metaltrade.
registry` is RECORD CONSTRUCTION ONLY (no range-check functions), and
`metaltrade.governor` reads the order's booleans directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `metal-order` entity

Like the fuel-wholesale sibling's `fuel-order` entity, the agri-
wholesale sibling's `agri-order` entity and the provision-trading
sibling's `provision-order` entity, this vertical's `dispatch` and
`settle` actuation events apply SEQUENTIALLY to the SAME `metal-order`
-- a bulk-metal/ore dispatch happens first (product leaves the wholesale
yard/warehouse), invoice settlement happens later (the money side of
the trade, custody / financial transfer), on the same order record.
`high-stakes` is `#{:delivery/dispatch :invoice/settle}`; neither ever
auto-commits at any phase.

### Decision 4: `conflict-minerals-provenance-unverified` -- gated on METAL TYPE alone, unconditional across jurisdiction; the defining design decision of this build

This is the decision that most distinguishes this vertical from every
prior wholesale-trading sibling, and it required a genuinely different
gating axis, not merely a new set of gated values on the SAME axis those
siblings already use.

The agri-wholesale sibling's phytosanitary/animal-health split and the
provision-trading sibling's food-safety/alcohol/tobacco split both gate
their domain-defining check(s) on a `:consignment-kind`/`:consignment-
category` field that is orthogonal to jurisdiction: for those verticals,
"what kind of goods is this?" determines WHICH certificate is required,
but the underlying regulatory model is still fundamentally
jurisdiction-shaped (a phytosanitary certificate is issued by a
jurisdiction's plant-health authority; a food-safety certificate is
issued under a jurisdiction's food-safety statute).

Conflict-minerals provenance is structurally different. Dodd-Frank
Section 1502 and EU Regulation 2017/821 do not regulate WHERE a trade
happens -- they regulate a DOWNSTREAM COMPANY'S disclosure/due-diligence
obligation (a US SEC-reporting issuer; an EU importer), which may sit
ANYWHERE in the supply chain relative to where this actor's own trade
occurs. A tin order sold in Japan and a tin order sold in the United
States face the IDENTICAL real-world provenance question -- did this
tin actually originate outside a conflict-affected/high-risk area, or
come from a certified-conflict-free smelter? -- while the generic
counterparty-diligence paperwork for those two orders (Japanese customs
evidence vs. US customs evidence) is naturally jurisdiction-specific.
Three design options were considered:

- **Option A (rejected): fold conflict-minerals evidence into the
  generic `evidence-incomplete-violations` checklist**, adding two
  conditional items (`chain-of-custody documentation`,
  `smelter-certification`) to `metaltrade.facts/catalog`'s per-
  jurisdiction `:required-evidence`. Rejected: this would force
  conflict-minerals diligence to inherit jurisdiction-gating it does not
  actually have in the real world (wrong: a US-jurisdiction copper order
  would need to ADD conditional logic to exempt itself, and a
  JPN-jurisdiction gold order's provenance requirement would depend on
  an accident of which jurisdiction happens to be seeded, rather than on
  the metal itself). It would also force every OTHER jurisdiction's
  evidence checklist -- covering metals that are never conflict minerals
  -- to carry conditional items irrelevant to most orders.
- **Option B (rejected): a jurisdiction-gated conflict-minerals check**,
  modeled the SAME way as the agri-wholesale/provision-trading
  siblings' kind/category-gated checks -- firing only for jurisdictions
  with a BINDING statute in `conflict-minerals-basis` (USA, DEU/EU).
  Rejected: this would produce the dishonest and operationally dangerous
  result that the SAME gold order, verified identically, dispatches
  cleanly in JPN (no binding statute seeded) but HOLDS in USA (Dodd-Frank
  1502 seeded) -- implying conflict-minerals risk is a property of WHERE
  the trade happens rather than WHAT is being traded. Real market
  practice does not work this way: a trader selling 3TG/cobalt applies
  chain-of-custody diligence as an operational floor across its whole
  book, because its OWN downstream counterparties (who may be
  SEC-reporting or EU-importing regardless of where THIS trader's sale
  happens) depend on that diligence existing somewhere upstream in the
  chain.
- **Option C (chosen): a metal-type-gated check, evaluated
  UNCONDITIONALLY across every jurisdiction** --
  `conflict-minerals-provenance-unverified-violations` fires whenever
  `metaltrade.facts/conflict-minerals-metal?` is true for the order's
  `:metal-type` (tin, tantalum, tungsten, gold, cobalt), regardless of
  `:jurisdiction`, and requires BOTH `:chain-of-custody-documented?` AND
  `:conflict-free-smelter-certified?` to be true. The CITATION shown to
  a human reviewer still varies honestly by jurisdiction
  (`metaltrade.facts/conflict-minerals-citation`: Dodd-Frank 1502 for
  USA, EU 2017/821 for DEU/EU, the OECD Guidance baseline everywhere
  else) -- but the CHECK itself does not, matching how real market
  participants who trade these metals actually operate. `mo-7` in the
  demo data (`metaltrade.store/demo-data`) proves the metal-type gating
  directly: a copper order carrying the SAME unverified `:chain-of-
  custody-documented? false` / `:conflict-free-smelter-certified? false`
  facts as the HELD gold order (`mo-6`) dispatches cleanly, because
  copper carries no conflict-minerals designation in this actor's scope.

The check additionally folds TWO distinct real-world sub-requirements
(a documented chain of custody back to the mine of origin; a conflict-
free-certified smelter/refiner) into ONE named governor rule rather than
two, because both are arms of the SAME real-world conflict-minerals-
provenance concern -- the OECD Guidance's own 5-step due-diligence
framework treats supply-chain traceability and smelter/refiner due
diligence as two steps of ONE process, not two independent regimes, and
a dispatch is equally unsafe whether the chain-of-custody trail or the
smelter certification is the one missing. This mirrors the SAME
discipline the provision-trading sibling's `tobacco-excise-age-
verification-missing` check establishes (see that sibling's ADR
Decision 4) -- the `:detail` string still names which sub-fact
specifically failed, so no audit-ledger precision is lost.

**Why cobalt is included alongside the legally-defined 3TG.** Dodd-Frank
1502 and EU 2017/821 both bind only tin, tantalum, tungsten and gold by
their own statutory text -- I am confident about this strict legal
scope, and `metaltrade.facts/conflict-minerals-basis`'s citations for
USA and DEU are written to reflect exactly that (3TG only, no cobalt
claim). Cobalt's inclusion in `metaltrade.facts/conflict-minerals-
metals` is a deliberate POLICY EXTENSION beyond either binding statute,
grounded in the OECD Guidance's own mineral-agnostic due-diligence
framework (explicitly written to apply to "any mineral," not only 3TG)
and the well-documented artisanal-cobalt-mining human-rights concerns
concentrated in the Democratic Republic of the Congo -- the same
reasoning a growing number of real downstream battery/electronics buyers
already apply as a matter of policy. This is documented explicitly, here
and in `docs/business-model.md`, so it is never mistaken for a claim
that Dodd-Frank 1502 or EU 2017/821 themselves cover cobalt -- they do
not.

This makes ISIC 4662 the first vertical in this fleet's wholesale-
trading cluster whose domain-defining check is gated on a property of
the COMMODITY alone (metal type), evaluated identically across every
jurisdiction -- a genuinely different gating shape from the agri-
wholesale sibling's kind-gated (`:consignment-kind`) and the provision-
trading sibling's category-gated (`:consignment-category`, via a
many-to-one regulatory-class mapping) checks, both of which remain
implicitly jurisdiction-shaped.

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline (reapplied, not new)

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the freight sibling's `delivery-exception-unresolved?` check
(and the fuel-wholesale/general-trading/commission-brokerage/agri-
wholesale/provision-trading siblings' own sanctions checks) establish --
an open concern cannot be silently suppressed to force a dispatch or
invoice through. Evaluated UNCONDITIONALLY at both `:delivery/dispatch`
and `:invoice/settle`, and UNCONDITIONALLY regardless of metal type
(unlike the conflict-minerals check in Decision 4, sanctions screening
applies uniformly to all metal types -- there is no regulatory reason to
differentiate it by commodity, only the conflict-minerals provenance
requirement itself is commodity-specific).

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`metal-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`metaltrade.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in
`test/metaltrade/store_contract_test.clj`. The ledger stays append-only
on every backend: which metal-order was verified for a jurisdiction with
no official spec-basis, which counterparty had credit-uncleared / no
contract / unverified conflict-minerals provenance / an unresolved
sanctions-screening flag, which order was dispatched, which invoice was
settled, on what jurisdictional and provenance basis, approved by whom
-- always a query over an immutable log.

### Decision 8: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`metaltrade.phase`'s phase table puts `:order/intake` (no direct capital
risk) in phase 3's `:auto` set as its only member; `:delivery/dispatch`
and `:invoice/settle` are deliberately ABSENT from every phase's `:auto`
set, including phase 3 -- a permanent structural fact.
`metaltrade.governor`'s high-stakes gate enforces the same invariant
independently: two layers agree that actuation is always a human
trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`metaltrade.metaltradeadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
metal/ore or auto-settle an invoice. The mock advisor's `verify-
provenance` proposal drafts the conflict-minerals citation
informationally (via `metaltrade.facts/conflict-minerals-citation`) for
a human reviewer's benefit, but this citation is NEVER what the governor
checks at `:delivery/dispatch` -- the governor independently re-reads
the order's own `:chain-of-custody-documented?`/`:conflict-free-smelter-
certified?` ground truth directly (Decision 4), so a compromised or
mistaken advisor citation can never substitute for the real facts.

### Decision 10: `:robotics true`, reasoned separately for bulk-ore and refined-metal handling

`:itonami.blueprint/robotics` is `true`, a deliberate call reasoned
specifically for this vertical rather than copied from a sibling
default -- following the SAME kind-differentiated reasoning discipline
the agri-wholesale sibling's Decision (README Robotics Premise) and the
provision-trading sibling's Decision 10 establish. This vertical spans
TWO physically distinct commodity forms with two distinct, well-
precedented automation claims:

- **Bulk ore** (iron ore and other unrefined ore shipments) is handled
  at bulk terminals with automated stacker-reclaimer and conveyor
  systems -- decades-old, well-established bulk-materials-handling
  automation.
- **Refined metal** (ingots, coils, cathodes, billets) is handled at
  LME-approved and comparable metal warehouses with automated overhead-
  crane systems and weighbridge-integrated loadout, directly analogous
  to the fuel-wholesale sibling's loading-rack/valve robot, the agri-
  wholesale sibling's elevator-loadout robot, and the provision-trading
  sibling's AS/RS-class warehouse pallet-picking robot.

Both automation claims terminate at the wholesaler's own yard/warehouse
dispatch point, matching every sibling's own scope disclaimer ("hand off
to a carrier" for the long-haul leg beyond the actor's own physical
dispatch act). This is a materially different physical claim from a pure
intermediation/brokerage vertical (the general-trading and commission-
brokerage siblings both correctly set `:robotics false`, having no
analogous physical dispatch act at all) -- so `:robotics true` here was
reasoned on this vertical's own terms (bulk-materials-handling and
warehouse automation are both real and load-bearing for THIS actor's
`:delivery/dispatch`), not defaulted from either extreme precedent.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/metaltrade` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not metal-trading-specific. Forcing
  a false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry (as the crude
  sibling does).** Considered and ruled out: the metal-trading domain
  checks are direct entity booleans (credit cleared? contract on file?
  provenance verified? sanctions screened?), not measured-value-vs-limit
  range comparisons, so there are no range checks to host.
  `metaltrade.registry` is record construction only.
- **Folding conflict-minerals evidence into the generic jurisdiction
  evidence checklist, or gating the conflict-minerals check on
  jurisdiction.** Considered and rejected -- see Decision 4 Options A and
  B above for the full reasoning: both would misrepresent conflict-
  minerals risk as a property of the trade's jurisdiction rather than
  the commodity itself.
- **Treating cobalt as legally equivalent to 3TG (citing Dodd-Frank 1502
  or EU 2017/821 as directly covering it).** Considered and rejected as
  dishonest -- neither statute's binding text covers cobalt. Cobalt is
  included as an explicit, separately-justified policy extension (see
  Decision 4), not folded into the same legal citation as 3TG.
- **A `:kind`-distinguished entity for dispatch vs. invoice** (matching
  the retail sibling's `order` shape). Rejected: dispatch and invoice
  settlement happen SEQUENTIALLY on the SAME metal-order in this domain,
  not as alternative actions -- the fuel-wholesale, agri-wholesale and
  provision-trading siblings' sequential shape is the honest match here.
- **Defaulting `:robotics` to `false`** (matching the general-trading and
  commission-brokerage siblings, which are non-physical intermediation/
  brokerage verticals with no analogous physical dispatch act).
  Considered and rejected: this vertical's `:delivery/dispatch` is a
  genuine physical act (bulk ore or refined metal actually leaving a
  wholesale yard/warehouse via automated reclaim/crane/forklift), closer
  in kind to the fuel-wholesale, agri-wholesale and provision-trading
  siblings' physical dispatch acts -- see Decision 10.
- **Building yard-slotting/freight-routing and trading-book optimization
  in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME governed-
  actor architecture as every prior sibling.
- Establishes the metal-trading checks as direct entity boolean reads
  (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Establishes the fleet's first METAL-TYPE-gated (rather than
  jurisdiction-or-kind-gated) domain-defining check, evaluated
  UNCONDITIONALLY across jurisdiction (Decision 4) -- a template for any
  future vertical whose defining regulatory concern attaches to the
  commodity itself rather than to where the trade happens.
- `MemStore` || `DatomicStore` parity is proven by
  `test/metaltrade/store_contract_test.clj`.
- 41 tests / 208 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean provenance-verify + dispatch +
  invoice lifecycle, six HARD-hold scenarios (no spec-basis, credit-
  uncleared, contract-missing, conflict-minerals-provenance-unverified,
  sanctions, double dispatch, double invoice), PLUS a control scenario
  (copper with the same unverified-provenance facts as the held gold
  order, dispatching cleanly) proving the conflict-minerals check is
  genuinely metal-type-gated, end-to-end.
- `blueprint.edn`'s `:robotics true` is a reasoned, vertical-specific
  call covering TWO distinct commodity forms (bulk ore, refined metal),
  documented in README and `docs/business-model.md`, not a default
  carried over from either extreme sibling precedent.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows most
  closely)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling)
- `cloud-itonami-isic-4610/docs/adr/0001-architecture.md` (commission-
  brokerage sibling; origin of the 'a genuinely new regulatory concern
  gets its own named check' precedent this build's Decision 4 follows)
- `cloud-itonami-isic-4620/docs/adr/0001-architecture.md` (agri-
  wholesale sibling; origin of the fleet's first kind-gated certificate
  split -- contrast: still implicitly jurisdiction-shaped, unlike this
  build's metal-type-only gating)
- `cloud-itonami-isic-4630/docs/adr/0001-architecture.md` (provision-
  trading sibling; origin of the fleet's first many-to-one category-
  gated split and the 'fold two sub-requirements into one named rule'
  precedent this build's Decision 4 follows for the conflict-minerals
  check)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- 関税法 (Customs Act); 輸出貿易管理令 (Export Trade Control Order)
  (Japan, MOF Customs / METI)
- Tariff Act of 1930 (19 U.S.C. Chapter 4); OFAC sanctions programs (US,
  CBP / Treasury)
- Taxation (Cross-border Trade) Act 2018; Sanctions and Anti-Money
  Laundering Act 2018 (SAMLA 2018) (UK, HMRC / OFSI)
- Union Customs Code (Regulation (EU) No 952/2013); EU financial
  sanctions regulations (EU; Germany, Zoll / BMF)
- Dodd-Frank Wall Street Reform and Consumer Protection Act, Section
  1502 (15 U.S.C. §78m note); SEC Rule 13p-1 (US, SEC)
- Regulation (EU) 2017/821 laying down supply chain due diligence
  obligations for Union importers of tin, tantalum, tungsten, their
  ores, and gold originating from conflict-affected and high-risk areas
  (EU; Germany, BAFA)
- OECD Due Diligence Guidance for Responsible Supply Chains of Minerals
  from Conflict-Affected and High-Risk Areas (3rd edition, 2016) (OECD)
