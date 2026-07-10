(ns metaltrade.metaltradeadvisor
  "MetalTradeAdvisor client -- the *contained intelligence node* for the
  wholesale-metal actor.

  It normalizes metal-order intake, drafts a per-jurisdiction
  counterparty-diligence / sanctions evidence checklist (citing the
  general trade spec-basis) PLUS a conflict-minerals provenance citation
  when the metal type warrants one, drafts the bulk-metal/ore dispatch
  action, and drafts the invoice-settlement action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record or a real dispatch/
  settlement. Every output is censored downstream by
  `metaltrade.governor` before anything touches the SSoT, and
  `:delivery/dispatch`/`:invoice/settle` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [metaltrade.facts :as facts]
            [metaltrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, metal-type, origin or any
  physical/commercial value. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "金属卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-provenance
  "Per-jurisdiction counterparty-diligence / sanctions evidence
  checklist draft, PLUS -- when `:metal-type` is a conflict mineral -- a
  conflict-minerals provenance citation drawn from
  `metaltrade.facts/conflict-minerals-citation`. `:no-spec?` injects the
  failure mode we must defend against: proposing a checklist for a
  jurisdiction with NO official spec-basis in `metaltrade.facts` -- the
  Metal Trading Governor must reject this (never invent a jurisdiction's
  requirements). The conflict-minerals citation is informational only
  here -- the governor's own `conflict-minerals-provenance-unverified-
  violations` check re-verifies the order's OWN
  `:chain-of-custody-documented?`/`:conflict-free-smelter-certified?`
  facts directly at `:delivery/dispatch`, independent of what this
  advisor cites."
  [db {:keys [subject no-spec?]}]
  (let [mo (store/metal-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction mo))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "metaltrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :provenance-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [conflict? (facts/conflict-minerals-metal? (:metal-type mo))
            cm-basis (when conflict? (facts/conflict-minerals-citation iso3))]
        {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                          (count (:required-evidence sb)) " 件を提案"
                          (when conflict? "、紛争鉱物チェーン・オブ・カストディ確認を含む"))
         :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb)
                          (when cm-basis
                            (str " / 紛争鉱物根拠: " (:legal-basis cm-basis)
                                 " (" (:owner-authority cm-basis) ")")))
         :cites      (cond-> [(:legal-basis sb) (:provenance sb)]
                       cm-basis (conj (:legal-basis cm-basis)))
         :effect     :provenance-assessment/set
         :value      (cond-> {:jurisdiction iso3
                              :checklist (:required-evidence sb)
                              :spec-basis (:provenance sb)
                              :legal-basis (:legal-basis sb)}
                       cm-basis (assoc :conflict-minerals-basis (:legal-basis cm-basis)))
         :stake      nil
         :confidence 0.9}))))

(defn- propose-dispatch
  "Draft the actual METAL-DISPATCH action -- dispatching real bulk
  metal/ore to a counterparty from the wholesale yard/warehouse. ALWAYS
  `:stake :delivery/dispatch` -- this is a REAL-WORLD act (an autonomous
  stacker-reclaimer/overhead-crane robot physically performs the bulk-
  metal/ore loadout at the yard, or an operator does), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this op
  to a phase's `:auto` set (`metaltrade.phase`); the governor also
  always escalates on `:delivery/dispatch`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [mo (store/metal-order db subject)
        credit-ok? (and mo (true? (:credit-cleared? mo)))
        contract-ok? (and mo (some? (:contract-terms mo))
                          (not= "" (:contract-terms mo)))
        conflict? (and mo (facts/conflict-minerals-metal? (:metal-type mo)))
        provenance-ok? (or (not conflict?)
                           (and (true? (:chain-of-custody-documented? mo))
                                (true? (:conflict-free-smelter-certified? mo))))
        sanctions-ok? (and mo (true? (:sanctions-screened? mo)))]
    {:summary    (str subject " 向け出荷提案"
                      (when mo (str " (counterparty=" (:counterparty mo)
                                    ", metal=" (:metal-type mo) ")")))
     :rationale  (if mo
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " conflict-minerals-metal?=" conflict?
                        " provenance-verified?=" provenance-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "metal-orderが見つかりません")
     :cites      (if mo [subject] [])
     :effect     :order/mark-dispatched
     :value      {:metal-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? provenance-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real metal
  invoice (the money side of a wholesale-metal trade, custody/
  financial transfer). ALWAYS `:stake :invoice/settle` -- this is a
  REAL-WORLD act (real money moves between counterparty and trader),
  never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set
  (`metaltrade.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [mo (store/metal-order db subject)
        dispatched? (and mo (:dispatched? mo))
        sanctions-ok? (and mo (true? (:sanctions-screened? mo)))]
    {:summary    (str subject " 向け請求提案"
                      (when mo (str " (counterparty=" (:counterparty mo) ")")))
     :rationale  (if mo
                   (str "dispatched?=" dispatched?
                        " sanctions-screened?=" sanctions-ok?)
                   "metal-orderが見つかりません")
     :cites      (if mo [subject] [])
     :effect     :order/mark-invoiced
     :value      {:metal-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake       (normalize-intake db request)
    :provenance/verify  (verify-provenance db request)
    :delivery/dispatch  (propose-dispatch db request)
    :invoice/settle     (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは金属卸売事業者の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:provenance-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:delivery/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の関税・制裁要件を絶対に創作してはいけません。"
       "錫・タンタル・タングステン・金・コバルトなど紛争鉱物対象の荷口について、"
       "採掘地までのチェーン・オブ・カストディや紛争フリー認証製錬所の状態を偽って"
       "報告してはいけません。取引先信用審査・契約有無・制裁スクリーニングの状態も"
       "偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :provenance/verify {:metal-order (store/metal-order st subject)}
    :delivery/dispatch {:metal-order (store/metal-order st subject)}
    :invoice/settle    {:metal-order (store/metal-order st subject)}
    {:metal-order (store/metal-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Metal Trading Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch metal or
  auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :metaltradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
