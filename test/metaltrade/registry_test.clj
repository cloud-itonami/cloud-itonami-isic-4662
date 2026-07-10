(ns metaltrade.registry-test
  (:require [clojure.test :refer [deftest is]]
            [metaltrade.registry :as r]))

;; The metal-trading domain checks (credit-clearance, contract-on-file,
;; conflict-minerals provenance, sanctions-screening) are direct entity
;; booleans in the governor, NOT pure registry range functions -- so
;; this registry has NO range-check suite to test (unlike the crude-
;; extraction sibling's reservoir/annular/water-cut/H2S functions). Only
;; record construction is here.

;; ----------------------------- register-dispatch-record -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-dispatch-record "mo-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-dispatch-record "mo-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DISPATCH-000007"))
    (is (= (get-in result ["record" "metal_order_id"]) "mo-1"))
    (is (= (get-in result ["record" "kind"]) "metal-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-dispatch-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-dispatch-record "mo-1" "" 0)))
  (is (thrown? Exception (r/register-dispatch-record "mo-1" "JPN" -1))))

;; ----------------------------- register-invoice-record -----------------------------

(deftest invoice-is-a-draft-not-a-real-invoice
  (let [result (r/register-invoice-record "mo-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest invoice-assigns-invoice-number
  (let [result (r/register-invoice-record "mo-1" "JPN" 7)]
    (is (= (get result "invoice_number") "JPN-INVOICE-000007"))
    (is (= (get-in result ["record" "metal_order_id"]) "mo-1"))
    (is (= (get-in result ["record" "kind"]) "metal-invoice-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest invoice-validation-rules
  (is (thrown? Exception (r/register-invoice-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-invoice-record "mo-1" "" 0)))
  (is (thrown? Exception (r/register-invoice-record "mo-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-dispatch-record "mo-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-dispatch-record "mo-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DISPATCH-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DISPATCH-000001" (get-in hist2 [1 "record_id"])))))
