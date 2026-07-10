# Business Model: Wholesale of Metals and Metal Ores

## Classification
- Repository: `cloud-itonami-isic-4662`
- ISIC Rev.5: `4662` — wholesale of metals and metal ores
- Domain: `midstream/metal-wholesale`
- Social impact: responsible sourcing, human rights, transparency
- Governor: `:metal-trading-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers metal-order intake through per-jurisdiction contract /
sanctions regulatory verification, conflict-minerals chain-of-custody
provenance verification (for tin, tantalum, tungsten, gold and cobalt),
bulk-metal/ore dispatch (metal or ore leaving the wholesale yard/
warehouse for a counterparty), and invoice settlement (the money side of
the trade, custody / financial transfer) for a wholesaler of base and
precious metals and metal ores. This vertical sits MIDSTREAM: mining/ore
extraction is upstream (a separate ISIC code, e.g. 0710/0729), smelting/
refining is a manufacturing step (ISIC 24xx), and this ISIC 4662 wholesale
of metals AND metal ores trades commodities anywhere from near-mine ore
to smelter-refined metal, in between. It does **not**, by itself, hold
any metal-wholesale licence, export/import authority or operating
authority required to run a metal-wholesale business in a given
jurisdiction, perform the actual physical yard/warehouse loadout, or
judge trading-book economics (freight routing and trading-book
optimization is a follow-up slice, not this R0). Whoever deploys a live
instance supplies the jurisdiction-specific operating authority, the real
stacker-reclaimer/overhead-crane dispatch equipment and ERP / accounts-
receivable integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold so
the operator does not have to build the compliance layer from scratch.

## Customer
- regional and independent metal and metal-ore wholesalers and yard/
  warehouse operators
- LME-approved-warehouse operators and metals-trading houses leaving
  closed metal-trading / ERP SaaS
- 3TG/cobalt buyers (electronics, EV-battery and jewelry manufacturers'
  direct/indirect metal suppliers) who need Dodd-Frank 1502 / EU 2017/821
  -aware dispatch controls their generic warehouse-management system
  does not enforce
- counterparties, banks, downstream conflict-minerals compliance teams
  and regulators who need an auditable, spec-cited, provenance-cited
  trade record

## Offer
- metal-order intake and directory management, across base metals (e.g.
  copper, aluminum, iron ore, nickel, zinc, lead) and conflict minerals
  (tin, tantalum, tungsten, gold, cobalt) in one system
- per-jurisdiction contract / sanctions regulatory verification with an
  official spec-basis citation
- conflict-minerals provenance verification (chain-of-custody + conflict-
  free smelter/refiner certification) for 3TG/cobalt orders, with an
  honest jurisdiction-by-jurisdiction legal-basis citation (Dodd-Frank
  1502, EU 2017/821, or the OECD Guidance baseline)
- dispatch (yard/warehouse dispatch) gated on full evidence, a credit-
  cleared counterparty, contract-terms on file, verified conflict-
  minerals provenance (where the metal type applies) and a passed
  sanctions screen
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO, sanctions-
  screening record, plus chain-of-custody documentation and smelter
  certification for 3TG/cobalt)
- sanctions and credit exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trader / yard / warehouse
- support retainer with SLA
- ERP and accounts-receivable integration
- downstream-buyer conflict-minerals compliance reporting add-on
  (Dodd-Frank 1502 / EU 2017/821 -aligned export of a counterparty's
  own provenance evidence trail)

## The `:metal-trading-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:metal-trading-
governor`. It is the single authority that stands between "bulk metal/
ore could be dispatched to a counterparty" and "it is allowed to leave
the wholesale yard/warehouse," and between "an invoice could be settled"
and "it is allowed to settle." Every rule it enforces is traceable to the
domain (Wholesale of Metals and Metal Ores, ISIC 4662) and to the three
`:social-impact` tags in `blueprint.edn` (`:responsible-sourcing`,
`:human-rights`, `:transparency`).

This is the rule the companion contract test
(`test/metaltrade/governor_contract_test.clj`) encodes end-to-end: the
MetalTradeAdvisor never dispatches bulk metal/ore to a counterparty or
settles an invoice the Metal Trading Governor would reject,
`:delivery/dispatch` and `:invoice/settle` NEVER auto-commit at any
phase, `:order/intake` (no direct capital risk) MAY auto-commit when
clean, and every decision (commit OR hold) leaves exactly one ledger
fact.

**Authorizes a dispatch (`:delivery/dispatch`) or invoice settlement
(`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:provenance/verify`, `:delivery/
   dispatch`, or `:invoice/settle` proposal whose jurisdiction has no
   entry in the `metaltrade.facts` catalog (`:no-spec-basis`). This is
   the direct enforcement of `:transparency`: a jurisdiction whose
   customs/sanctions requirements cannot be traced to an OFFICIAL public
   source is never guessed. The advisor must not fabricate a
   jurisdiction's requirements.
2. **The jurisdiction's required GENERAL evidence is fully on file** --
   for a dispatch or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   and the sanctions-screening (OFAC / equivalent) record
   (`:evidence-incomplete`). This is deliberately the SAME 3-item generic
   checklist every wholesale-trading sibling uses -- it does NOT include
   conflict-minerals evidence, which is check #5 below.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch bulk metal/ore when credit has NOT been cleared (the leasing
   collateral-coverage discipline, applied to counterparty credit)
   (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). Bulk metal/ore never leaves the yard/warehouse
   against an undocumented trade. Evaluated at `:delivery/dispatch`.
5. **For a 3TG/cobalt metal-order, conflict-minerals provenance is
   verified** -- the governor reads the dedicated `:chain-of-custody-
   documented?` AND `:conflict-free-smelter-certified?` facts and
   refuses to dispatch a tin, tantalum, tungsten, gold or cobalt order
   missing EITHER (`:conflict-minerals-provenance-unverified`). This
   check is a NO-OP for every other metal type (copper, iron ore,
   aluminum, nickel, zinc, lead, ...) -- it is specific to the conflict-
   minerals regime, and -- UNLIKE every other check in this actor -- it
   applies UNCONDITIONALLY regardless of `:jurisdiction`, because
   real-world conflict-minerals diligence (Dodd-Frank 1502, EU 2017/821,
   the OECD Guidance) is a practice this actor treats as a universal
   operational floor for these metals, not merely a where-legally-
   mandated courtesy. This is the check with NO analog anywhere else in
   this fleet's wholesale-trading cluster -- see Implementation notes and
   `docs/adr/0001-architecture.md` Decision 4. Evaluated at `:delivery/
   dispatch`.
6. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   product nor money moves against an unscreened counterparty. Evaluated
   UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`.
7. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, unverified
conflict-minerals provenance on a qualifying metal, an unresolved
sanctions-screening flag, or a double dispatch/invoice is held at the
governor node -- a human approver cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching real bulk metal/ore to a counterparty from the wholesale yard/
warehouse and settling a real metal invoice (real money moving between
counterparty and trader) are the two real-world actuation events this
actor performs; both are always a human trading supervisor's call. This
is enforced by TWO independent layers that agree on purpose: the
governor's confidence / actuation SOFT gate (a `:delivery/dispatch` /
`:invoice/settle` stake always escalates) and `metaltrade.phase`'s phase
table, which never puts either op in any phase's `:auto` set. The
`:human-rights` tag is enforced upstream of the governor, in the
conflict-minerals provenance-verification evidence step -- the
governor's job is dispatch/invoice authorization integrity, not
trading-book optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this
business, and what each one is actually load-bearing for here (not a
generic capability list):

| Technology | What it is FOR in Wholesale of Metals and Metal Ores |
|---|---|
| `:robotics` | The autonomous stacker-reclaimer/conveyor robot that performs the physical bulk-ore reclaim, and the autonomous overhead-crane/forklift robot that performs the physical ingot/coil/cathode pick, at the wholesale yard/warehouse. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, trading-supervisor, yard/warehouse-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for metal-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record), conflict-minerals provenance capture (chain-of-custody documentation, smelter/refiner certification), and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:metal-trading-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, conflict-minerals-provenance, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across metal-order intake, provenance verification, dispatch, and invoice settlement, including the sanctions / credit escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, sanctions flag, and hold -- this is what "an auditable, spec-cited, provenance-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator (or a downstream buyer running its OWN Dodd-Frank 1502 / EU 2017/821 due diligence) needs if a dispatch or an invoice is later disputed. |
| `:optimization` | Yard-slotting, freight-routing and trading-book optimization -- selects the profitable fulfillment strategy for a yard/warehouse. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:metaltrade` capability library in this stack
(unlike the freight sibling's `:logistics`): the metal-trading checks
(credit-clearance, contract-on-file, conflict-minerals provenance,
sanctions-screening) are direct entity boolean reads in
`metaltrade.governor`, on top of the generic robotics/identity/forms/
dmn/bpmn/audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a dispatch never starts with incomplete counterparty-diligence evidence
- a dispatch never starts with an uncleared counterparty credit or no
  contract-terms on file
- a 3TG/cobalt dispatch never starts without BOTH a documented chain of
  custody back to the mine AND a conflict-free-certified smelter/refiner
  on file
- a dispatch or invoice never settles against an unresolved sanctions-
  screening flag
- sanctions / credit / conflict-minerals-provenance flags cannot be
  silently suppressed
- the same order can never be dispatched or invoiced twice
- a dispatch or invoice never auto-commits; both always need a human
  trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, sanctions and conflict-minerals sourcing data
  stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`metaltrade.governor` as six HARD checks (a human approver cannot
override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:provenance/verify`, `:delivery/dispatch`, and
  `:invoice/settle`.
- `evidence-incomplete-violations` -- the GENERAL evidence-completeness
  check above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `conflict-minerals-provenance-unverified-violations` -- the conflict-
  minerals check above, gated on `metaltrade.facts/conflict-minerals-
  metal?` (tin/tantalum/tungsten/gold/cobalt); evaluated on every
  `:delivery/dispatch`, UNCONDITIONALLY across jurisdiction. THIS IS THE
  DOMAIN-DEFINING CHECK -- no analog in the fuel-wholesale, general-
  trading, commission-brokerage, agri-wholesale or provision-trading
  siblings' governors: it is this vertical's own defining regulatory
  content, the commodity's OWN provenance rather than the trade's
  jurisdiction or a jurisdiction/kind pairing.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on both `:delivery/dispatch` and
  `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `metaltrade.phase` independently never auto-commits either op at
  any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the `metal-order`
record's own dedicated booleans directly. `:delivery/dispatch` and
`:invoice/settle` are the two real-world actuation events
(`#{:delivery/dispatch :invoice/settle}`), applied SEQUENTIALLY to the
SAME metal-order (dispatch first, invoice settlement later), the same
sequential dual-actuation shape the fuel-wholesale, agri-wholesale,
provision-trading, repair-shop, quarrying and crude-extraction clusters
use. Neither ever auto-commits at any phase. Yard-slotting/freight-
routing and trading-book optimization (the `:optimization` line above)
is a follow-up slice, not in this R0 build -- see README
`Business-process coverage`.

## Why conflict-minerals provenance is a SEPARATE check, not folded into evidence-incomplete

The generic `evidence-incomplete-violations` check (present in every
wholesale-trading sibling) verifies the jurisdiction's counterparty-
diligence paperwork -- credit-clearance record, contract/PO, sanctions-
screening record -- keyed by WHERE the trade happens. Conflict-minerals
provenance is a genuinely different kind of fact: it verifies the
COMMODITY ITSELF, keyed by WHAT metal it is, independent of where the
trade happens. A tin order sold in Japan and a tin order sold in the
United States face the exact same real-world question -- did this tin
actually originate outside a conflict-affected/high-risk area, or come
from a certified-conflict-free smelter? -- while an order's jurisdiction-
specific paperwork (Japanese customs evidence vs. US customs evidence)
is naturally different between the two. Folding the two into one check
would either force conflict-minerals diligence to inherit jurisdiction-
gating it does not have (wrong: a US-jurisdiction copper order does not
need it, but a JPN-jurisdiction gold order does), or force every
jurisdiction's generic evidence checklist to grow two conditional items
that are irrelevant to most orders. Keeping them as two independent
checks -- one gated by jurisdiction, one gated by metal type -- lets each
check state its own gating condition honestly and lets the audit ledger
show, unambiguously, WHICH kind of gap actually blocked a dispatch.

## Capability layer

Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`), commission-brokerage
(`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`)
and provision-trading (`cloud-itonami-isic-4630`) siblings, this vertical
is SELF-CONTAINED: there is no `kotoba-lang/metaltrade` to delegate
metal-trading validation to. The credit-clearance / contract-on-file /
conflict-minerals-provenance / sanctions-screening checks live as direct
entity boolean reads in `metaltrade.governor` (off dedicated
`:credit-cleared?` / `:contract-terms` / `:chain-of-custody-documented?`
/ `:conflict-free-smelter-certified?` / `:sanctions-screened?` facts on
the `metal-order` record) -- this vertical's governor needs no pure
range-check functions at all, because its domain checks ARE direct
boolean reads.

## Jurisdiction coverage (honest)

`metaltrade.facts/catalog` currently seeds 4 jurisdictions with an
official GENERAL (customs/sanctions) spec-basis, each a REAL regime:

- **Japan (JPN)** -- 関税法 (Customs Act) and 輸出貿易管理令 (Export Trade
  Control Order), administered by 財務省 (MOF) Customs and 経済産業省
  (METI). I am highly confident about this citation (it is the same
  general customs/export-control basis the fuel-wholesale sibling
  established, honestly reused here since it genuinely covers ANY
  commodity, not fuel-specific).
- **United States (USA)** -- the Tariff Act of 1930 (19 U.S.C. Chapter 4)
  customs entry requirements, administered by U.S. Customs and Border
  Protection (CBP), plus OFAC (Treasury) sanctions programs. I am
  reasonably confident about CBP's general customs role and OFAC's
  sanctions role; the specific U.S. Code chapter pin-cite for the Tariff
  Act of 1930's modern customs-entry provisions should be independently
  verified.
- **United Kingdom (GBR)** -- the Taxation (Cross-border Trade) Act 2018,
  administered by HM Revenue & Customs (HMRC), plus UK financial
  sanctions under the Sanctions and Anti-Money Laundering Act 2018
  (SAMLA 2018), administered by the Office of Financial Sanctions
  Implementation (OFSI). I am reasonably confident about both Act names
  and the agency split, but have not independently verified the precise
  post-Brexit customs-code cross-references.
- **Germany (DEU)**, representing the EU regime -- the Union Customs Code
  (Regulation (EU) No 952/2013), administered on the ground by the
  Generalzolldirektion (German Customs) under the Bundesministerium der
  Finanzen (BMF), plus EU financial sanctions regulations. I am highly
  confident about the Union Customs Code citation (a well-known,
  directly-applicable EU regulation).

`metaltrade.facts/conflict-minerals-basis` seeds a SEPARATE,
metal-agnostic catalog with a BINDING conflict-minerals statute for only
2 of the 4 jurisdictions above:

- **United States (USA)** -- Dodd-Frank Wall Street Reform and Consumer
  Protection Act, Section 1502 (15 U.S.C. §78m note), implemented via SEC
  Rule 13p-1 ("Conflict Minerals"), administered by the U.S. Securities
  and Exchange Commission (SEC). I am highly confident about this
  citation -- it is one of the most widely-referenced conflict-minerals
  statutes and I am confident it covers 3TG (tin, tantalum, tungsten,
  gold) specifically, as a disclosure obligation on SEC-reporting
  issuers (not a wholesale-trade licence requirement on the trader
  itself -- see the honest caveat below).
- **Germany (DEU), representing the EU** -- Regulation (EU) 2017/821,
  "laying down supply chain due diligence obligations for Union
  importers of tin, tantalum, tungsten, their ores, and gold originating
  from conflict-affected and high-risk areas." I am highly confident
  about the regulation's existence, number and 3TG scope (it does NOT
  cover cobalt in its binding text -- cobalt inclusion in this actor's
  `conflict-minerals-metals` set is a POLICY EXTENSION, not a claim that
  EU 2017/821 itself covers cobalt). I am MODERATELY confident, not
  highly confident, about the specific German national competent
  authority attribution (Bundesamt für Wirtschaft und Ausfuhrkontrolle,
  BAFA) -- this should be independently verified before the catalog is
  relied on operationally.

Every other jurisdiction -- including JPN and GBR, both present in the
GENERAL catalog above -- has NO binding conflict-minerals statute seeded
here, and falls back to the **OECD Due Diligence Guidance for
Responsible Supply Chains of Minerals from Conflict-Affected and
High-Risk Areas (3rd edition, 2016)** as the citation
`metaltrade.facts/conflict-minerals-citation` returns. I am confident the
OECD Guidance exists, is the internationally-recognized reference
framework both Dodd-Frank 1502 and EU 2017/821 point to operationally,
and is NOT itself binding law absent an implementing statute -- this
honest distinction (binding statute vs. non-statutory operational
baseline) is the entire point of keeping `conflict-minerals-basis`
separate from `oecd-guidance` in the code, rather than presenting both as
equally authoritative.

**Why cobalt is in `conflict-minerals-metals` despite not being legally
"3TG":** Dodd-Frank Section 1502 and EU Regulation 2017/821 both bind
only tin, tantalum, tungsten and gold by their own statutory text -- this
is the strict legal "3TG" scope, and I am confident about it. Cobalt
sourcing (overwhelmingly concentrated in the Democratic Republic of the
Congo, with well-documented artisanal-mining human-rights concerns) has
become a widely-recognized responsible-sourcing concern in its own right
-- the OECD Guidance's own due-diligence framework (its five-step model)
is explicitly written to apply to "any mineral," not only 3TG, and
downstream battery/electronics buyers increasingly apply 3TG-equivalent
diligence to cobalt as a matter of policy rather than strict legal
compulsion. This actor's inclusion of cobalt in `conflict-minerals-
metals` is therefore an honest POLICY CHOICE -- extending the OECD
Guidance's mineral-agnostic framework to a mineral not (yet) named by
either binding statute seeded here -- not a claim that Dodd-Frank 1502 or
EU 2017/821 legally require it. This is documented here explicitly so it
is never mistaken for a legal citation it is not.

This is a starting catalog to prove the governor contract end-to-end, not
a claim of global coverage (4 of ~194 jurisdictions worldwide for the
general catalog; 2 of those 4 for the conflict-minerals-specific
catalog). Adding a jurisdiction, or a conflict-minerals statute for an
already-seeded jurisdiction, is additive: one map entry in
`metaltrade.facts/catalog` or `metaltrade.facts/conflict-minerals-basis`,
citing a real official source -- never fabricate a jurisdiction's or a
statute's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `MetalTradeAdvisor` + `Metal Trading Governor` run as
real, tested code (`clojure -M:dev:test`: 41 tests / 208 assertions, 0
failures; lint clean), following the SAME governed-actor architecture as
the other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean
metal-trading checks. See `docs/adr/0001-architecture.md` for the history
and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. This is a
reasoned call, not a default carried over from a sibling: real modern
metal/ore wholesale operations already run substantial physical
automation, in two distinct forms depending on the commodity form:

- **Bulk ore** (iron ore, and other unrefined ore shipments) is handled
  at bulk terminals with automated stacker-reclaimer and conveyor
  systems -- well-established, decades-old bulk-materials-handling
  automation at major ore export/import terminals.
- **Refined metal** (ingots, coils, cathodes, billets) is handled at
  LME-approved and comparable metal warehouses with automated overhead-
  crane systems, weighbridge-integrated loadout and increasingly AGV-
  class robotic forklifts, directly analogous to the fuel-wholesale
  sibling's loading-rack/valve robot, the agri-wholesale sibling's
  elevator-loadout robot, and the provision-trading sibling's warehouse
  pallet-picking robot.

An autonomous stacker-reclaimer/conveyor robot performs the physical bulk
-ore reclaim, and an autonomous overhead-crane/forklift robot performs
the physical refined-metal pick, at the wholesale yard/warehouse -- the
point at which this actor's `:delivery/dispatch` occurs -- under the
actor, gated by the independent Metal Trading Governor.

Either way, the governor never dispatches hardware itself: a dispatch-
clearing action must have cleared the same sign-off a human trading
supervisor would need. A robot may reclaim bulk ore or stage a metal
ingot pallet, but only after the governor (every HARD check clean) and a
human supervisor both agree it is safe to -- the same operating-state-
machine-gated-by-governor premise every cloud-itonami vertical restates
(ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robot(s) that perform the physical act, and the Metal Trading
Governor is the independent gate that robot's command must pass.
