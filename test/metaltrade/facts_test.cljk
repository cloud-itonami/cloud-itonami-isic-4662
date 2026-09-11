(ns metaltrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [metaltrade.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest arg-has-a-spec-basis
  ;; ARG (Argentina) -- Resolución General (AFIP) N° 2849/2010, "Registro
  ;; de Comercializadores de Materiales a Reciclar" (Art. 1, inc. e:
  ;; "Metales ferrosos o no ferrosos"), verified by direct fetch of
  ;; https://servicios.infoleg.gob.ar/infolegInternet/anexos/165000-169999/168455/norma.htm
  (let [basis (facts/spec-basis "ARG")]
    (is (some? basis))
    (is (= "ARG" (:name basis)))
    (is (string? (:provenance basis)))
    (is (re-find #"infoleg\.gob\.ar" (:provenance basis)))
    (is (re-find #"2849" (:legal-basis basis)) "cites AFIP RG 2849/2010")
    (is (re-find #"AFIP" (:owner-authority basis)))
    (is (= 3 (count (:required-evidence basis))))))

(deftest all-five-seeded-jurisdictions-have-required-evidence
  ;; every seeded metal-wholesale jurisdiction actually has a real
  ;; required-evidence set reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU" "ARG"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ----------------------------- conflict-minerals catalog -----------------------------

(deftest conflict-minerals-metals-is-3tg-plus-cobalt
  (is (= #{"tin" "tantalum" "tungsten" "gold" "cobalt"} facts/conflict-minerals-metals)))

(deftest conflict-minerals-metal-predicate
  (doseq [m ["tin" "tantalum" "tungsten" "gold" "cobalt"]]
    (is (true? (facts/conflict-minerals-metal? m)) (str m " is a conflict mineral")))
  (doseq [m ["copper" "iron-ore" "aluminum" "nickel" "zinc" "lead"]]
    (is (false? (facts/conflict-minerals-metal? m)) (str m " is NOT a conflict mineral"))))

(deftest usa-and-deu-have-a-binding-conflict-minerals-statute
  (is (true? (:binding? (get facts/conflict-minerals-basis "USA"))))
  (is (true? (:binding? (get facts/conflict-minerals-basis "DEU"))))
  (is (re-find #"Dodd-Frank" (:legal-basis (get facts/conflict-minerals-basis "USA"))))
  (is (re-find #"2017/821" (:legal-basis (get facts/conflict-minerals-basis "DEU")))))

(deftest jpn-and-gbr-fall-back-to-oecd-guidance
  (is (nil? (get facts/conflict-minerals-basis "JPN")))
  (is (nil? (get facts/conflict-minerals-basis "GBR")))
  (is (= facts/oecd-guidance (facts/conflict-minerals-citation "JPN")))
  (is (= facts/oecd-guidance (facts/conflict-minerals-citation "GBR")))
  (is (false? (:binding? facts/oecd-guidance))))

(deftest arg-has-no-binding-conflict-minerals-statute-falls-back-to-oecd
  ;; ARG has a `catalog` entry (general trade spec-basis) but, like JPN
  ;; and GBR, no Argentina-specific binding conflict-minerals statute was
  ;; found -- honest fallback to the OECD Guidance baseline, not a
  ;; fabricated statute.
  (is (nil? (get facts/conflict-minerals-basis "ARG")))
  (is (= facts/oecd-guidance (facts/conflict-minerals-citation "ARG"))))

(deftest conflict-minerals-citation-never-nil
  ;; every jurisdiction gets AT LEAST the OECD baseline -- even one with
  ;; no spec-basis at all in the general `catalog`.
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU" "ARG" "ATL"]]
    (is (some? (facts/conflict-minerals-citation iso3)))))
