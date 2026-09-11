(ns metaltrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    MetalTradeAdvisor never dispatches bulk metal/ore to a counterparty
    or settles an invoice the Metal Trading Governor would reject,
    `:delivery/dispatch`/`:invoice/settle` NEVER auto-commit at any
    phase, `:order/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [metaltrade.store :as store]
            [metaltrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through provenance verify -> approve, leaving a
  provenance assessment on file. Uses distinct thread-ids per call site
  by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :provenance/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "mo-1"
                   :patch {:id "mo-1" :counterparty "Kaminski Metals Trading GmbH"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Kaminski Metals Trading GmbH" (:counterparty (store/metal-order db "mo-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest provenance-verify-always-needs-approval
  (testing "provenance verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :provenance/verify :subject "mo-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "mo-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a provenance/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :provenance/verify :subject "mo-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "mo-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any provenance verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "mo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "mo-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "mo-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "mo-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "mo-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval (evaluated at both dispatch and invoice)"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "mo-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "mo-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest conflict-minerals-provenance-unverified-is-held-and-unoverridable
  (testing "a 3TG/cobalt metal-order (gold, mo-6) with NEITHER a documented chain of custody NOR a conflict-free-certified smelter -> HOLD, and never reaches request-approval -- the domain-defining check"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "mo-6")
          res (exec-op actor "t8" {:op :delivery/dispatch :subject "mo-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:conflict-minerals-provenance-unverified} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest conflict-minerals-check-is-a-no-op-for-non-conflict-metals
  (testing "copper (mo-7) carries the SAME unverified chain-of-custody/smelter facts as mo-6 (gold), but copper is not a conflict mineral in this actor's scope -> the check does NOT fire, dispatch still always escalates for the usual human sign-off"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "mo-7")
          r1 (exec-op actor "t9" {:op :delivery/dispatch :subject "mo-7"} operator)]
      (is (= :interrupted (:status r1)) "pauses for the ordinary human dispatch sign-off, NOT a conflict-minerals hold")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:dispatched? (store/metal-order db "mo-7"))))
        (is (= 1 (count (store/dispatch-history db))))))))

(deftest dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, provenance-verified, sanctions-screened order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "mo-1")
          r1 (exec-op actor "t10" {:op :delivery/dispatch :subject "mo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/metal-order db "mo-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "mo-1")
          _ (exec-op actor "t11dispatch" {:op :delivery/dispatch :subject "mo-1"} operator)
          _ (approve! actor "t11dispatch")
          r1 (exec-op actor "t11" {:op :invoice/settle :subject "mo-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/metal-order db "mo-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same metal-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "mo-1")
          _ (exec-op actor "t12a" {:op :delivery/dispatch :subject "mo-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :delivery/dispatch :subject "mo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same metal-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "mo-1")
          _ (exec-op actor "t13dispatch" {:op :delivery/dispatch :subject "mo-1"} operator)
          _ (approve! actor "t13dispatch")
          _ (exec-op actor "t13a" {:op :invoice/settle :subject "mo-1"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :invoice/settle :subject "mo-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "mo-1"
                          :patch {:id "mo-1" :counterparty "Kaminski Metals Trading GmbH"}} operator)
      (exec-op actor "b" {:op :provenance/verify :subject "mo-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
