(ns metaltrade.store
  "SSoT for the metal-wholesale actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/metaltrade/store_contract_test.clj), which is the whole point:
  the actor, the Metal Trading Governor and the audit ledger never know
  which SSoT they run on.

  Like the fuel-wholesale/agri-wholesale/provision-trading siblings'
  entities, this vertical's `dispatch` and `settle` actuation events
  apply SEQUENTIALLY to the SAME `metal-order` -- a bulk-metal/ore
  dispatch happens first (product leaves the wholesale yard/warehouse),
  invoice settlement happens later, on the same order record. This
  matches the sequential dual-actuation shape, with dedicated
  double-actuation-guard booleans (`:dispatched?`/`:invoiced?`, never a
  `:status` value).

  The `metal-order` record carries TWO evidence surfaces the Metal
  Trading Governor reads independently: the generic per-jurisdiction
  counterparty-diligence facts (`:credit-cleared?` / `:contract-terms` /
  `:sanctions-screened?`, same shape as every sibling), AND a pair of
  conflict-minerals-specific facts (`:chain-of-custody-documented?` /
  `:conflict-free-smelter-certified?`) that exist ONLY on this vertical
  -- see `metaltrade.governor`'s `conflict-minerals-provenance-
  unverified-violations` for why these are a SEPARATE check rather than
  folded into the generic evidence checklist.

  The ledger stays append-only on every backend: 'which metal-order was
  verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / an unverified
  conflict-minerals chain-of-custody / an unresolved sanctions-screening
  flag, which order had metal dispatched, which invoice was settled, on
  what jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a regulator, a downstream buyer
  running its own Dodd-Frank/EU 2017/821 due diligence, or an operator
  trusting a metal-wholesale actor needs, and the evidence an operator
  needs if a dispatch or an invoice is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [metaltrade.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (metal-order [s id])
  (all-metal-orders [s])
  (assessment-of [s metal-order-id] "committed provenance assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only metal-dispatch history (metaltrade.registry drafts)")
  (invoice-history [s] "the append-only metal-invoice history (metaltrade.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (metal-order-already-dispatched? [s metal-order-id] "has metal/ore already been dispatched for this order?")
  (metal-order-already-invoiced? [s metal-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-metal-orders [s metal-orders] "replace/seed the metal-order directory (map id->metal-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean metal-order shape (every field in its safe
  state), so each demo order below isolates exactly ONE failure mode by
  overriding a single field. `:metal-type` defaults to \"tin\" (a 3TG
  conflict mineral) so the base order also proves the happy path through
  the conflict-minerals check, not just around it."
  [overrides]
  (merge {:id "mo-1" :order-id "MO-2026-0001" :metal-type "tin"
          :origin "Democratic Republic of the Congo -- Manono tin district"
          :quantity-tonnes 25 :counterparty "Kaminski Metals Trading GmbH"
          :price 32500.00 :contract-terms "CIF, net 30 days"
          :credit-cleared? true :sanctions-screened? true
          :chain-of-custody-documented? true :conflict-free-smelter-certified? true
          :dispatched? false :invoiced? false
          :jurisdiction "JPN" :status :intake
          :dispatch-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained metal-order set covering both actuation
  lifecycles (dispatch, invoice settlement) plus the Metal Trading
  Governor's own checks, so the actor + tests run offline. Each
  violation order isolates exactly ONE failure mode (the rest stay
  clean) following the 'exercise the failure mode directly, never only
  via a happy-path actuation' discipline every sibling governor's demo
  data establishes. `mo-6` and `mo-7` together prove the conflict-
  minerals check is genuinely metal-type-gated: `mo-6` (gold, undocumented
  chain-of-custody) HOLDS, `mo-7` (copper, same undocumented facts) does
  NOT -- copper is not a conflict mineral in this actor's scope."
  []
  {:metal-orders
   (into {}
         (for [o [(base-order {:id "mo-1" :order-id "MO-2026-0001"})
                  (base-order {:id "mo-2" :order-id "MO-2026-0002"
                               :counterparty "Atlantis Metals Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "mo-3" :order-id "MO-2026-0003"
                               :counterparty "Cedar Nonferrous Corp"
                               :credit-cleared? false})
                  (base-order {:id "mo-4" :order-id "MO-2026-0004"
                               :counterparty "Delta Ores BV"
                               :contract-terms nil})
                  (base-order {:id "mo-5" :order-id "MO-2026-0005"
                               :counterparty "Eagle Metals SA"
                               :sanctions-screened? false})
                  (base-order {:id "mo-6" :order-id "MO-2026-0006"
                               :metal-type "gold"
                               :origin "unspecified artisanal source"
                               :counterparty "Fenwick Bullion Traders"
                               :chain-of-custody-documented? false
                               :conflict-free-smelter-certified? false})
                  (base-order {:id "mo-7" :order-id "MO-2026-0007"
                               :metal-type "copper"
                               :origin "Chile -- Escondida district"
                               :counterparty "Granite Copper Cathodes Inc"
                               :chain-of-custody-documented? false
                               :conflict-free-smelter-certified? false})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the metal-order
  via the protocol and drafts the metal-dispatch record, and returns
  {:result .. :metal-order-patch ..} for the caller to persist."
  [s metal-order-id]
  (let [mo (metal-order s metal-order-id)
        seq-n (next-dispatch-sequence s (:jurisdiction mo))
        result (registry/register-dispatch-record metal-order-id (:jurisdiction mo) seq-n)]
    {:result result
     :metal-order-patch {:dispatched? true
                         :dispatch-number (get result "dispatch_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the metal-order
  via the protocol and drafts the metal-invoice record, and returns
  {:result .. :metal-order-patch ..} for the caller to persist."
  [s metal-order-id]
  (let [mo (metal-order s metal-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction mo))
        result (registry/register-invoice-record metal-order-id (:jurisdiction mo) seq-n)]
    {:result result
     :metal-order-patch {:invoiced? true
                         :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (metal-order [_ id] (get-in @a [:metal-orders id]))
  (all-metal-orders [_] (sort-by :id (vals (:metal-orders @a))))
  (assessment-of [_ metal-order-id] (get-in @a [:assessments metal-order-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (invoice-history [_] (:invoices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (metal-order-already-dispatched? [_ metal-order-id] (boolean (get-in @a [:metal-orders metal-order-id :dispatched?])))
  (metal-order-already-invoiced? [_ metal-order-id] (boolean (get-in @a [:metal-orders metal-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:metal-orders (:id value)] merge value)

      :provenance-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [metal-order-id (first path)
            {:keys [result metal-order-patch]} (dispatch-order! s metal-order-id)
            jurisdiction (:jurisdiction (metal-order s metal-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:metal-orders metal-order-id] merge metal-order-patch)
                       (update :dispatches registry/append result))))
        result)

      :order/mark-invoiced
      (let [metal-order-id (first path)
            {:keys [result metal-order-patch]} (invoice-order! s metal-order-id)
            jurisdiction (:jurisdiction (metal-order s metal-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:metal-orders metal-order-id] merge metal-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-metal-orders [s metal-orders] (when (seq metal-orders) (swap! a assoc :metal-orders metal-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo metal-order set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  invoice records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:metal-order/id                       {:db/unique :db.unique/identity}
   :assessment/metal-order-id            {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :dispatch/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every metal-order field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). [field-key tx-attr boolean?]
(def ^:private metal-order-fields
  [[:id :metal-order/id false]
   [:order-id :metal-order/order-id false]
   [:metal-type :metal-order/metal-type false]
   [:origin :metal-order/origin false]
   [:quantity-tonnes :metal-order/quantity-tonnes false]
   [:counterparty :metal-order/counterparty false]
   [:price :metal-order/price false]
   [:contract-terms :metal-order/contract-terms false]
   [:credit-cleared? :metal-order/credit-cleared? true]
   [:sanctions-screened? :metal-order/sanctions-screened? true]
   [:chain-of-custody-documented? :metal-order/chain-of-custody-documented? true]
   [:conflict-free-smelter-certified? :metal-order/conflict-free-smelter-certified? true]
   [:dispatched? :metal-order/dispatched? true]
   [:invoiced? :metal-order/invoiced? true]
   [:jurisdiction :metal-order/jurisdiction false]
   [:status :metal-order/status false]
   [:dispatch-number :metal-order/dispatch-number false]
   [:invoice-number :metal-order/invoice-number false]])

(defn- metal-order->tx [mo]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get mo k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:metal-order/id (:id mo)}
          metal-order-fields))

(def ^:private metal-order-pull (mapv second metal-order-fields))

(defn- pull->metal-order [m]
  (when (:metal-order/id m)
    (reduce (fn [mo [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc mo k (boolean v))
                  (some? v)    (assoc mo k v)
                  :else        mo)))
            {:id (:metal-order/id m)}
            metal-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (metal-order [_ id]
    (pull->metal-order (d/pull (d/db conn) metal-order-pull [:metal-order/id id])))
  (all-metal-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :metal-order/id ?id]] (d/db conn))
         (map #(pull->metal-order (d/pull (d/db conn) metal-order-pull [:metal-order/id %])))
         (sort-by :id)))
  (assessment-of [_ metal-order-id]
    (dec* (d/q '[:find ?p . :in $ ?moid
                :where [?a :assessment/metal-order-id ?moid] [?a :assessment/payload ?p]]
              (d/db conn) metal-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (metal-order-already-dispatched? [s metal-order-id]
    (boolean (:dispatched? (metal-order s metal-order-id))))
  (metal-order-already-invoiced? [s metal-order-id]
    (boolean (:invoiced? (metal-order s metal-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(metal-order->tx value)])

      :provenance-assessment/set
      (d/transact! conn [{:assessment/metal-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [metal-order-id (first path)
            {:keys [result metal-order-patch]} (dispatch-order! s metal-order-id)
            jurisdiction (:jurisdiction (metal-order s metal-order-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(metal-order->tx (assoc metal-order-patch :id metal-order-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [metal-order-id (first path)
            {:keys [result metal-order-patch]} (invoice-order! s metal-order-id)
            jurisdiction (:jurisdiction (metal-order s metal-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(metal-order->tx (assoc metal-order-patch :id metal-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-metal-orders [s metal-orders]
    (when (seq metal-orders) (d/transact! conn (mapv metal-order->tx (vals metal-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:metal-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [metal-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-metal-orders s metal-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo metal-order set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
