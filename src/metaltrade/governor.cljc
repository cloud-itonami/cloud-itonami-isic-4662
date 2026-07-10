(ns metaltrade.governor
  "Metal Trading Governor -- the independent compliance layer that earns
  the MetalTradeAdvisor the right to commit. The LLM has no notion of
  jurisdictional customs/sanctions law, whether a counterparty's credit
  has actually been cleared, whether contract terms are actually on
  file, whether a REAL documented chain of custody back to the mine (or
  a conflict-free-certified smelter/refiner) actually exists for THIS
  metal-order, whether OFAC / equivalent sanctions screening has
  actually been passed, or when an act stops being a draft and becomes
  a real dispatch of bulk metal/ore or a real invoice settlement, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  Like the fuel-wholesale sibling's own governor, this metal-wholesale
  vertical has NO pre-existing metal-trading capability library to
  delegate to -- so the domain checks (credit-clearance, contract-on-
  file, conflict-minerals provenance, sanctions-screening) are direct
  entity boolean reads off the `metal-order` record, evaluated directly
  here, NOT delegated to a separate library's validated function.

  `:itonami.blueprint/governor` is `:metal-trading-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent question,
  a fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511` and applied by
  the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
  (`cloud-itonami-isic-4690`), commission-brokerage
  (`cloud-itonami-isic-4610`), agri-wholesale (`cloud-itonami-isic-4620`)
  and provision-trading (`cloud-itonami-isic-4630`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE from every prior wholesale-trading
  sibling: this vertical's domain-defining check
  (`conflict-minerals-provenance-unverified-violations`) is gated on
  METAL TYPE alone, NOT on jurisdiction AND kind/category the way the
  agri-wholesale sibling's phytosanitary/animal-health split or the
  provision-trading sibling's food-safety/alcohol/tobacco split are.
  Real conflict-minerals law (Dodd-Frank Section 1502, EU Regulation
  2017/821) does not bind the TRADE's jurisdiction the way excise or
  food-safety law does -- it binds a downstream company (a US
  SEC-reporting issuer, an EU importer) that may sit anywhere in the
  chain, and market participants who trade 3TG (tin/tantalum/tungsten/
  gold) or cobalt apply chain-of-custody diligence as an operational
  floor essentially everywhere they trade, not only where a local
  statute compels it (see `metaltrade.facts/conflict-minerals-basis` for
  the honest jurisdiction-by-jurisdiction citation picture). So this
  check applies UNCONDITIONALLY across every jurisdiction whenever the
  metal type qualifies -- see the check's own docstring below for the
  full reasoning, and `docs/adr/0001-architecture.md` Decision 4 for the
  three design options considered.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `metaltrade.phase`: for `:stake
  :delivery/dispatch`/`:invoice/settle` (a real dispatch or invoice
  settlement) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`metaltrade.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full counterparty-
                                       diligence evidence checklist on
                                       file?
    3. Credit uncleared            -- for `:delivery/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before dispatch.
    4. Contract missing            -- for `:delivery/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before dispatch.
    5. Conflict-minerals provenance
       unverified                    -- for `:delivery/dispatch`, WHEN
                                       `:metal-type` is a 3TG metal (tin/
                                       tantalum/tungsten/gold) or cobalt,
                                       the metal-order lacks a documented
                                       chain of custody back to the mine
                                       OR lacks a conflict-free-certified
                                       smelter/refiner (or both). THIS
                                       check has no analog in ANY prior
                                       wholesale-trading sibling's
                                       governor: it is this vertical's
                                       own defining regulatory content --
                                       the commodity's OWN provenance,
                                       not the trade's jurisdiction. NO-OP
                                       for every other metal type (copper,
                                       iron ore, aluminum, nickel, zinc,
                                       lead, ...). Evaluated before
                                       dispatch, UNCONDITIONALLY across
                                       every jurisdiction (see namespace
                                       docstring).
    6. Counterparty sanctions flag
       unresolved                    -- for `:delivery/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  metal-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [metaltrade.facts :as facts]
            [metaltrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching real bulk metal/ore to a counterparty from the wholesale
  yard/warehouse and settling a real metal invoice (real money moving
  between counterparty and trader) are the two real-world actuation
  events this actor performs -- a two-member set, matching every
  sibling's own dual-actuation shape."
  #{:delivery/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:provenance/verify` (or `:delivery/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's customs/sanctions requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:provenance/verify :delivery/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:invoice/settle`, the jurisdiction's
  required GENERAL counterparty-diligence evidence (credit-clearance
  record, contract/PO, sanctions-screening record) must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone. Deliberately does NOT check conflict-minerals provenance --
  that is `conflict-minerals-provenance-unverified-violations` below,
  gated on metal-type rather than jurisdiction."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [mo (store/metal-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction mo) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`, refuses to dispatch bulk metal/ore to a
  counterparty whose credit has NOT been cleared -- counterparty credit
  not cleared (the leasing collateral-coverage discipline, applied to
  counterparty credit). Evaluated at the yard, ahead of any physical
  loadout."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [mo (store/metal-order st subject)]
      (when (not (true? (:credit-cleared? mo)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`, refuses to dispatch bulk metal/ore when no
  contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [mo (store/metal-order st subject)]
      (when (or (nil? (:contract-terms mo)) (= "" (:contract-terms mo)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- conflict-minerals-provenance-unverified-violations
  "For `:delivery/dispatch`, WHEN `:metal-type` is a 3TG metal (tin/
  tantalum/tungsten/gold) or cobalt (`metaltrade.facts/conflict-
  minerals-metal?`), refuses to dispatch UNLESS BOTH a documented chain
  of custody back to the mine (`:chain-of-custody-documented?`) AND a
  conflict-free-certified smelter/refiner (`:conflict-free-smelter-
  certified?`, e.g. RMI/RMAP-conformant) are on file. Folds these TWO
  distinct real-world sub-requirements into ONE named rule -- the SAME
  discipline the provision-trading sibling's `tobacco-excise-age-
  verification-missing` check establishes (see
  `docs/adr/0001-architecture.md` Decision 4): a chain-of-custody paper
  trail without a conflict-free-certified endpoint smelter (or vice
  versa) is EQUALLY unsafe to dispatch against, because both are arms of
  the SAME real-world conflict-minerals-provenance concern (OECD
  Guidance's 5-step framework treats supply-chain traceability and
  smelter/refiner due diligence as two steps of ONE process, not two
  independent regimes) -- the `:detail` string still names which
  sub-fact specifically failed, so no audit-ledger precision is lost.

  This is a NO-OP for every non-conflict-mineral metal type (copper,
  iron ore, aluminum, nickel, zinc, lead, ...) -- `mo-7` in the demo
  data proves this directly: a copper order with BOTH facts false still
  dispatches cleanly, because copper carries no conflict-minerals
  designation in this actor's scope (see `metaltrade.facts/conflict-
  minerals-metals`).

  UNLIKE every jurisdiction-or-kind-gated check elsewhere in this fleet
  (phytosanitary/animal-health in the agri-wholesale sibling, food-
  safety/alcohol/tobacco in the provision-trading sibling), this check
  is gated on METAL TYPE ALONE, evaluated UNCONDITIONALLY regardless of
  `:jurisdiction` -- see namespace docstring for why: real conflict-
  minerals diligence is a practice market participants apply globally,
  not merely where a local statute compels it."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [mo (store/metal-order st subject)]
      (when (and (facts/conflict-minerals-metal? (:metal-type mo))
                 (not (and (true? (:chain-of-custody-documented? mo))
                           (true? (:conflict-free-smelter-certified? mo)))))
        [{:rule :conflict-minerals-provenance-unverified
          :detail (str subject " (" (:metal-type mo) ", 紛争鉱物対象)の"
                       "採掘地までのチェーン・オブ・カストディ記録(chain-of-custody)="
                       (boolean (:chain-of-custody-documented? mo))
                       " / 紛争フリー認証製錬所(conflict-free-certified smelter)="
                       (boolean (:conflict-free-smelter-certified? mo))
                       " -- いずれかが未充足のため出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither metal/ore nor
  money moves against an unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [mo (store/metal-order st subject)]
      (when (not (true? (:sanctions-screened? mo)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME metal-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/metal-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME metal-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/metal-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a MetalTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (conflict-minerals-provenance-unverified-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
