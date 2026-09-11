(ns metaltrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean metal-order
  through intake -> provenance verification -> bulk-metal/ore dispatch
  (escalate/approve/commit) -> invoice settlement (escalate/approve/
  commit), then shows HARD-hold scenarios: a jurisdiction with no
  spec-basis, a counterparty whose credit has not been cleared, an
  order with no contract-terms on file, a 3TG metal (gold) with an
  unverified conflict-minerals chain of custody, a counterparty that has
  not passed sanctions screening, a double dispatch, and a double
  invoice -- PLUS a control scenario proving the conflict-minerals check
  is genuinely metal-type-gated: a copper order with the SAME
  unverified-provenance facts as the gold order dispatches CLEANLY,
  because copper carries no conflict-minerals designation in this
  actor's scope.

  Like every sibling actor's domain checks, this actor's checks
  (`credit-uncleared`, `contract-missing`,
  `conflict-minerals-provenance-unverified`,
  `counterparty-sanctions-flag-unresolved`) are evaluated directly at
  `:delivery/dispatch` (and sanctions at `:invoice/settle` too) rather
  than via a separate screening op -- a real dispatch decision validates
  counterparty credit, contract-on-file, conflict-minerals provenance
  (where applicable) and sanctions screening at the point of the act
  itself, not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one order per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [metaltrade.store :as store]
            [metaltrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake mo-1 (tin, JPN, clean) ==")
    (println (exec-op actor "t1" {:op :order/intake :subject "mo-1"
                                  :patch {:id "mo-1" :counterparty "Kaminski Metals Trading GmbH"}} operator))

    (println "== provenance/verify mo-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :provenance/verify :subject "mo-1"} operator))
    (println (approve! actor "t2"))

    (println "== delivery/dispatch mo-1 (always escalates -- :delivery/dispatch) ==")
    (let [r (exec-op actor "t3" {:op :delivery/dispatch :subject "mo-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t3")))

    (println "== invoice/settle mo-1 (always escalates -- :invoice/settle) ==")
    (let [r (exec-op actor "t4" {:op :invoice/settle :subject "mo-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t4")))

    (println "== provenance/verify mo-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :provenance/verify :subject "mo-2"} operator))

    (println "== provenance/verify mo-3 (escalates -- human approves; sets up the credit-uncleared test) ==")
    (println (exec-op actor "t6" {:op :provenance/verify :subject "mo-3"} operator))
    (println (approve! actor "t6"))

    (println "== delivery/dispatch mo-3 (credit not cleared -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :delivery/dispatch :subject "mo-3"} operator))

    (println "== provenance/verify mo-4 (escalates -- human approves; sets up the contract-missing test) ==")
    (println (exec-op actor "t8" {:op :provenance/verify :subject "mo-4"} operator))
    (println (approve! actor "t8"))

    (println "== delivery/dispatch mo-4 (no contract-terms on file -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :delivery/dispatch :subject "mo-4"} operator))

    (println "== provenance/verify mo-5 (escalates -- human approves; sets up the sanctions test) ==")
    (println (exec-op actor "t10" {:op :provenance/verify :subject "mo-5"} operator))
    (println (approve! actor "t10"))

    (println "== delivery/dispatch mo-5 (sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :delivery/dispatch :subject "mo-5"} operator))

    (println "== provenance/verify mo-6 (gold, escalates -- human approves; sets up the conflict-minerals test) ==")
    (println (exec-op actor "t12" {:op :provenance/verify :subject "mo-6"} operator))
    (println (approve! actor "t12"))

    (println "== delivery/dispatch mo-6 (gold, chain-of-custody + smelter-certification BOTH unverified -> HARD hold, the domain-defining check) ==")
    (println (exec-op actor "t13" {:op :delivery/dispatch :subject "mo-6"} operator))

    (println "== provenance/verify mo-7 (copper, escalates -- human approves; sets up the NON-conflict-mineral control) ==")
    (println (exec-op actor "t14" {:op :provenance/verify :subject "mo-7"} operator))
    (println (approve! actor "t14"))

    (println "== delivery/dispatch mo-7 (copper, SAME unverified chain-of-custody/smelter facts as mo-6, but copper is NOT a conflict mineral -> dispatches cleanly, escalates only for the usual human sign-off) ==")
    (let [r (exec-op actor "t15" {:op :delivery/dispatch :subject "mo-7"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t15")))

    (println "== delivery/dispatch mo-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :delivery/dispatch :subject "mo-1"} operator))

    (println "== invoice/settle mo-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t17" {:op :invoice/settle :subject "mo-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft metal-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft metal-invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
