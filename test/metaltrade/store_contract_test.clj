(ns metaltrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [metaltrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/metal-order s "mo-1"))))
      (is (= "Kaminski Metals Trading GmbH" (:counterparty (store/metal-order s "mo-1"))))
      (is (= "tin" (:metal-type (store/metal-order s "mo-1"))))
      (is (= "ATL" (:jurisdiction (store/metal-order s "mo-2"))))
      (is (false? (:credit-cleared? (store/metal-order s "mo-3"))) "mo-3 credit not cleared")
      (is (nil? (:contract-terms (store/metal-order s "mo-4"))) "mo-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/metal-order s "mo-5"))) "mo-5 sanctions not screened")
      (is (= "gold" (:metal-type (store/metal-order s "mo-6"))))
      (is (false? (:chain-of-custody-documented? (store/metal-order s "mo-6"))) "mo-6 chain-of-custody undocumented")
      (is (false? (:conflict-free-smelter-certified? (store/metal-order s "mo-6"))) "mo-6 smelter not certified")
      (is (= "copper" (:metal-type (store/metal-order s "mo-7"))))
      (is (false? (:chain-of-custody-documented? (store/metal-order s "mo-7"))) "mo-7 same unverified facts as mo-6, different metal")
      (is (false? (:dispatched? (store/metal-order s "mo-1"))))
      (is (false? (:invoiced? (store/metal-order s "mo-1"))))
      (is (= ["mo-1" "mo-2" "mo-3" "mo-4" "mo-5" "mo-6" "mo-7"]
             (mapv :id (store/all-metal-orders s))))
      (is (nil? (store/assessment-of s "mo-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-invoice-sequence s "JPN")))
      (is (false? (store/metal-order-already-dispatched? s "mo-1")))
      (is (false? (store/metal-order-already-invoiced? s "mo-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "mo-1" :counterparty "Kaminski Metals Trading GmbH"}})
        (is (= "Kaminski Metals Trading GmbH" (:counterparty (store/metal-order s "mo-1"))))
        (is (= "JPN" (:jurisdiction (store/metal-order s "mo-1"))) "unrelated field preserved"))
      (testing "provenance-assessment payloads commit and read back"
        (store/commit-record! s {:effect :provenance-assessment/set :path ["mo-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "mo-1"))))
      (testing "metal dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["mo-1"]})
        (is (= "JPN-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "metal-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/metal-order s "mo-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/metal-order-already-dispatched? s "mo-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["mo-1"]})
        (is (= "JPN-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "metal-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/metal-order s "mo-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "JPN")))
        (is (true? (store/metal-order-already-invoiced? s "mo-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/metal-order s "nope")))
    (is (= [] (store/all-metal-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-invoice-sequence s "JPN")))
    (store/with-metal-orders s {"x" {:id "x" :order-id "MO-X" :metal-type "tin"
                                     :origin "Test District" :quantity-tonnes 10
                                     :counterparty "c" :price 32500.00
                                     :contract-terms "CIF, net 30 days"
                                     :credit-cleared? true :sanctions-screened? true
                                     :chain-of-custody-documented? true
                                     :conflict-free-smelter-certified? true
                                     :dispatched? false :invoiced? false
                                     :jurisdiction "JPN" :status :intake
                                     :dispatch-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/metal-order s "x"))))))
