(ns metaltrade.registry
  "Pure-function metal-dispatch + metal-invoice record construction -- an
  append-only wholesale-metal book-of-record draft.

  Unlike the crude-extraction sibling's own registry (which ALSO hosts
  the pure well-safety range-check functions its governor calls to
  re-verify a well's own physical ground truth before any lift), this
  metal-wholesale vertical's Metal Trading Governor needs NO registry
  range-check functions at all: its domain checks (credit-uncleared,
  contract-missing, conflict-minerals-provenance-unverified,
  counterparty-sanctions-flag-unresolved) are direct entity boolean
  reads in `metaltrade.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:chain-of-custody-documented?` /
  `:conflict-free-smelter-certified?` / `:sanctions-screened?` facts on
  the `metal-order` record. So this namespace is RECORD CONSTRUCTION
  ONLY -- no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a metal-dispatch or metal-invoice
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `metaltrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real weighbridge/warehouse-management/ERP/billing system.
  It builds the RECORD an operator would keep, not the act of
  dispatching real bulk metal/ore at the wholesale yard or settling a
  real invoice itself (that is `metaltrade.operation`'s `:delivery/
  dispatch`/`:invoice/settle`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the METAL-DISPATCH registration DRAFT -- the
  operator's own legal act of dispatching real bulk metal/ore to a
  counterparty from the wholesale yard/warehouse. Pure function -- does
  not touch any real weighbridge/warehouse-management/ERP system; it
  builds the RECORD an operator would keep. `metaltrade.governor`
  independently re-verifies the counterparty's credit-clearance,
  contract-on-file, conflict-minerals provenance (where applicable) and
  sanctions-screening ground truth, and blocks a double-dispatch of the
  same metal-order, before this is ever allowed to commit."
  [metal-order-id jurisdiction sequence]
  (when-not (and metal-order-id (not= metal-order-id ""))
    (throw (ex-info "metal-dispatch: metal_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "metal-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "metal-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "metal-dispatch-draft"
                "metal_order_id" metal-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "MetalDispatch" dispatch-number dispatch-number)}))

(defn register-invoice-record
  "Validate + construct the METAL-INVOICE registration DRAFT -- the
  operator's own legal act of settling a real metal invoice (the money
  side of a wholesale-metal trade, custody/financial transfer). Pure
  function -- does not touch any real billing or accounts-receivable
  system; it builds the RECORD an operator would keep. `metaltrade.
  governor` independently re-verifies the sanctions-screening and
  evidence-completeness ground truth, and blocks a double-invoice of
  the same metal-order, before this is ever allowed to commit."
  [metal-order-id jurisdiction sequence]
  (when-not (and metal-order-id (not= metal-order-id ""))
    (throw (ex-info "metal-invoice: metal_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "metal-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "metal-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "metal-invoice-draft"
                "metal_order_id" metal-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "MetalInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
