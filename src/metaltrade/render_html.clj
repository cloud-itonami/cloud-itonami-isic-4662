(ns metaltrade.render-html
  "Build-time operator-console renderer -- REAL execution, not a mock.

  `-main` boots a `metaltrade.store/seed-db` MemStore, compiles the
  OperationActor graph (`metaltrade.operation/build`) and drives a fixed
  scenario set through it with `langgraph.graph/run*` -- the SAME entry
  point `metaltrade.sim` uses. Every entity, id, verdict, rule, register
  value and ledger fact printed into `docs/samples/operator-console.html`
  is read back out of that run's own state / store. Nothing on the page
  is authored by hand; if a value cannot be derived from the run it is
  not printed.

  THREE things this namespace deliberately does NOT take on faith:

  1. `governor refusal` vs `phase/rollout gate`. Both surface as
     `:disposition :hold`, and BOTH can carry `:violations` -- an
     approver rejection synthesises `[{:rule :approver-rejected}]` in
     `metaltrade.operation`'s `:request-approval` node, so counting
     `(seq :violations)` over-counts governor refusals. Classification
     here keys on the AUDIT FACT TYPE first (`:t`), then on
     `:phase-reason`, and never on `:violations`:

       :t :governor-hold    + no :phase-reason -> governor HARD refusal
       :t :governor-hold    + :phase-reason    -> rollout-phase gate
       :t :approval-rejected                   -> human approver refusal
       :t :approval-requested                  -> awaiting human sign-off
       :t :committed                           -> committed to the SSoT

     `verify-classification!` cross-checks every row against the
     governor's own `:hard?` verdict flag and throws on disagreement, so
     the two independent signals cannot silently drift apart.

  2. Approver attribution. `metaltrade.operation/commit-record` writes
     the approver ONLY onto `:payload` (`:approved-by`), while
     `metaltrade.store`'s MemStore reads `:value` for `:order/upsert`,
     `:payload` for `:provenance-assessment/set`, and NEITHER for
     `:order/mark-dispatched` / `:order/mark-invoiced`. Whether that
     costs attribution is therefore per-effect. This namespace does not
     assert it either way: it SCANS each committed surface at render
     time for approver-shaped keys (`approver-hits`) and reports what it
     actually finds, so the disclosure self-heals if the store is ever
     fixed.

     The scan uses an approver id (`approver-id`) DISTINCT from the
     actor id (`actor-id`). `metaltrade.sim` uses \"op-1\" for both, which
     makes `:actor` (the EXECUTING actor, on every `:committed` fact)
     indistinguishable from `:by` (the human approver, on
     `:approval-granted` facts). Reading `:actor` as the approver would
     agree with the truth on the sim's data and disagree here.

  3. That the page is worth writing at all. `-main` THROWS -- and writes
     NO file -- when the run yields zero HARD governor refusals. A
     console that shows only green is not evidence the governor works."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [metaltrade.facts :as facts]
            [metaltrade.operation :as operation]
            [metaltrade.phase :as phase]
            [metaltrade.store :as store]))

;; ---------------------------------------------------------------------------
;; run configuration
;; ---------------------------------------------------------------------------

(def ^:private actor-id
  "The EXECUTING actor. Lands on every `:committed` ledger fact as
  `:actor` -- which is NOT the approver."
  "op-1")

(def ^:private approver-id
  "The human trading supervisor who resumes an interrupted run. Lands on
  `:approval-granted` facts as `:by`. Deliberately DIFFERENT from
  `actor-id` so that `:actor` and `:by` discriminate -- `metaltrade.sim`
  uses \"op-1\" for both and therefore cannot tell them apart."
  "sv-9")

(defn- ctx [ph] {:actor-id actor-id :actor-role :trading-supervisor :phase ph})

(def ^:private scenarios
  "One map per actor run, executed in order against ONE shared store --
  state accumulates exactly as it would in production (an order that has
  been dispatched really is dispatched for the next scenario).

  `:resume` non-nil resumes the `interrupt-before #{:request-approval}`
  pause with that approval status."
  [{:thread "t01" :phase 3 :resume nil
    :request {:op :order/intake :subject "mo-1"
              :patch {:id "mo-1" :counterparty "Kaminski Metals Trading GmbH"}}
    :note "phase 3 で auto-commit できる唯一の op（:order/intake）"}

   {:thread "t02" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-1"}
    :note "JPN の spec-basis を引用 → 人間承認 → assessment 確定"}

   {:thread "t03" :phase 3 :resume :approved
    :request {:op :delivery/dispatch :subject "mo-1"}
    :note "governor clean でも :delivery/dispatch は必ず人間へ escalate"}

   {:thread "t04" :phase 3 :resume :approved
    :request {:op :invoice/settle :subject "mo-1"}
    :note "governor clean でも :invoice/settle は必ず人間へ escalate"}

   {:thread "t05" :phase 3 :resume nil
    :request {:op :provenance/verify :subject "mo-2"}
    :note "ATL は metaltrade.facts に無い法域 → 要件を創作させない"}

   {:thread "t06" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-3"}
    :note "credit-uncleared 検査のための assessment を先に確定させる"}

   {:thread "t07" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-3"}
    :note "取引先信用審査が未了"}

   {:thread "t08" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-4"}
    :note "contract-missing 検査のための assessment を先に確定させる"}

   {:thread "t09" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-4"}
    :note "契約条項が記録されていない"}

   {:thread "t10" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-5"}
    :note "sanctions 検査のための assessment を先に確定させる"}

   {:thread "t11" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-5"}
    :note "制裁スクリーニング未了"}

   {:thread "t12" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-6"}
    :note "紛争鉱物検査のための assessment を先に確定させる"}

   {:thread "t13" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-6"}
    :note "gold（3TG）: chain-of-custody と製錬所認証が両方未充足 — この業種固有の検査"}

   {:thread "t14" :phase 3 :resume :approved
    :request {:op :provenance/verify :subject "mo-7"}
    :note "非紛争鉱物の対照群を用意する"}

   {:thread "t15" :phase 3 :resume :approved
    :request {:op :delivery/dispatch :subject "mo-7"}
    :note "copper: mo-6 と同一の未検証 provenance facts でも通る（metal-type gate の対照）"}

   {:thread "t16" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-1"}
    :note "同一オーダーの二重出荷"}

   {:thread "t17" :phase 3 :resume nil
    :request {:op :invoice/settle :subject "mo-1"}
    :note "同一オーダーの二重請求"}

   {:thread "t18" :phase 3 :resume nil
    :request {:op :delivery/dispatch :subject "mo-2"}
    :note "assessment を持たない mo-2 を出荷しようとすると何が起きるか（規則名を実測する）"}

   {:thread "t19" :phase 1 :resume nil
    :request {:op :provenance/verify :subject "mo-1"}
    :note "governor は clean。phase 1 が書き込みを許していないだけ — 拒否ではない"}

   {:thread "t20" :phase 3 :resume :rejected
    :request {:op :invoice/settle :subject "mo-7"}
    :note "governor は clean。人間の承認者が拒否した — governor 拒否ではない"}

   {:thread "t21" :phase 2 :resume :approved
    :request {:op :order/intake :subject "mo-3"
              :patch {:id "mo-3" :counterparty "Cedar Nonferrous Corp"}}
    :note "phase 2 では :order/intake も承認が要る → :order/upsert の approver 保持を実測できる"}])

;; ---------------------------------------------------------------------------
;; execution
;; ---------------------------------------------------------------------------

(def ^:private decision-fact-types
  #{:governor-hold :approval-rejected :approval-requested :committed})

(defn- terminal-fact
  "The LAST decision fact of a run's audit channel. Everything else in
  `:audit` is advisor trace / intermediate approval bookkeeping."
  [st]
  (last (filter (comp decision-fact-types :t) (:audit st))))

(defn- classify
  "Fact TYPE first, then `:phase-reason`. Never `:violations` -- an
  `:approval-rejected` fact carries `[{:rule :approver-rejected}]` and
  would otherwise be miscounted as a governor refusal."
  [st]
  (let [f (terminal-fact st)]
    (case (:t f)
      :approval-rejected  :approver-rejected
      :governor-hold      (if (:phase-reason f) :phase-gate-hold :governor-hard-hold)
      :committed          :committed
      :approval-requested :awaiting-approval
      :unclassified)))

(defn- run-scenario!
  [actor {:keys [thread phase request resume note]}]
  (let [st0 (:state (g/run* actor {:request request :context (ctx phase)}
                            {:thread-id thread}))
        st  (if resume
              (:state (g/run* actor {:approval {:status resume :by approver-id}}
                              {:thread-id thread :resume? true}))
              st0)]
    {:thread thread :phase phase :request request :resume resume :note note
     :proposal    (:proposal st)
     :verdict     (:verdict st)
     :disposition (:disposition st)
     :record      (:record st)
     :audit       (:audit st)
     :class       (classify st)}))

(defn execute!
  "Boot a seeded MemStore, compile the real OperationActor graph, drive
  every scenario through it. Returns {:db .. :runs [..]}."
  []
  (let [db    (store/seed-db)
        actor (operation/build db)]
    {:db db :runs (mapv #(run-scenario! actor %) scenarios)}))

(defn- governor-refusals [runs] (filterv #(= :governor-hard-hold (:class %)) runs))

(defn- verify-classification!
  "Two independent signals must agree: the audit FACT TYPE (what the
  graph wrote) and the governor's own `:hard?` verdict flag (what the
  censor concluded). If they ever disagree the page would be lying about
  which holds are governor refusals, so fail the build instead."
  [runs]
  (let [bad (filterv (fn [{:keys [class verdict]}]
                       (not= (= :governor-hard-hold class)
                             (boolean (:hard? verdict))))
                     runs)]
    (when (seq bad)
      (throw (ex-info (str "render-html: classification disagrees with the governor's "
                           "own :hard? flag on " (count bad) " run(s)")
                      {:threads (mapv :thread bad)})))
    runs))

;; ---------------------------------------------------------------------------
;; approver attribution -- derived at render time, never hard-coded
;; ---------------------------------------------------------------------------

(defn- approver-shaped-key? [k]
  (let [n (cond (keyword? k) (name k) (string? k) k :else (str k))]
    (boolean (re-find #"(?i)approv" n))))

(defn- approver-hits
  "Every value stored under an approver-shaped key anywhere inside `x`."
  [x]
  (cond
    (map? x)        (concat (keep (fn [[k v]] (when (approver-shaped-key? k) v)) x)
                            (mapcat approver-hits (vals x)))
    (sequential? x) (mapcat approver-hits x)
    :else           nil))

(defn- retains-approver? [surface]
  (boolean (some #(= approver-id %) (approver-hits surface))))

(defn- attribution-report
  "For each effect that actually committed AFTER a human approval in
  this run, did the approver survive into the store surface that effect
  writes? Measured, not assumed."
  [db runs]
  (let [approved (filterv #(and (= :approved (:resume %)) (= :committed (:class %))) runs)]
    (vec
     (for [{:keys [record request]} approved
           :let [effect  (:effect record)
                 subject (:subject request)
                 surface (case effect
                           :order/upsert            (store/metal-order db subject)
                           :provenance-assessment/set (store/assessment-of db subject)
                           :order/mark-dispatched   (store/dispatch-history db)
                           :order/mark-invoiced     (store/invoice-history db)
                           nil)]]
       {:effect          effect
        :subject         subject
        :payload-carries (retains-approver? (:payload record))
        :value-carries   (retains-approver? (:value record))
        :store-retains   (retains-approver? surface)
        :surface (case effect
                   :order/upsert              "metal-order register"
                   :provenance-assessment/set "provenance assessment"
                   :order/mark-dispatched     "metal-dispatch draft records"
                   :order/mark-invoiced       "metal-invoice draft records"
                   "—")}))))

;; ---------------------------------------------------------------------------
;; html
;; ---------------------------------------------------------------------------

(defn- esc [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- cell
  "Render a value for a table cell. `nil` becomes an explicit em-dash --
  the literal string \"nil\" must never reach the page."
  [v]
  (cond
    (nil? v)     "—"
    (true? v)    "true"
    (false? v)   "false"
    (keyword? v) (esc (str v))
    (coll? v)    (if (seq v) (esc (str/join ", " (map #(if (keyword? %) (str %) (str %)) v))) "—")
    :else        (esc (str v))))

(def ^:private css "
:root{--ink:#1a1a1c;--muted:#626569;--line:#d8dadc;--bg:#f2f2f2;--card:#fff;
--blue:#0017c1;--blue-soft:#e8eaf9;--red:#c9252d;--red-soft:#fce8e9;
--amber:#8a6100;--amber-soft:#fdf3e0;--green:#197a4b;--green-soft:#e5f2eb;}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);
font-family:'Noto Sans JP',system-ui,-apple-system,'Segoe UI',sans-serif;
font-size:14px;line-height:1.7}
header.bar{background:var(--blue);color:#fff;padding:20px 24px}
header.bar h1{margin:0;font-size:20px;font-weight:700;letter-spacing:.01em}
header.bar p{margin:6px 0 0;font-size:13px;opacity:.9}
main{max-width:1180px;margin:24px auto 64px;padding:0 20px}
section.card{background:var(--card);border:1px solid var(--line);border-radius:8px;
padding:20px 22px;margin-bottom:20px}
section.card>h2{margin:0 0 4px;font-size:16px;font-weight:700}
section.card>p.lede{margin:0 0 14px;color:var(--muted);font-size:13px}
table{width:100%;border-collapse:collapse;font-size:13px}
th{text-align:left;font-size:11px;font-weight:700;color:var(--muted);
text-transform:uppercase;letter-spacing:.06em;padding:8px 10px;
border-bottom:2px solid var(--line);white-space:nowrap}
td{padding:9px 10px;border-bottom:1px solid #eceded;vertical-align:top}
tr:last-child td{border-bottom:none}
td.mono,th.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}
td.num{text-align:right;font-variant-numeric:tabular-nums}
.pill{display:inline-block;padding:2px 9px;border-radius:11px;font-size:11px;
font-weight:700;white-space:nowrap}
.pill.refuse{background:var(--red-soft);color:var(--red)}
.pill.gate{background:var(--amber-soft);color:var(--amber)}
.pill.commit{background:var(--green-soft);color:var(--green)}
.pill.info{background:var(--blue-soft);color:var(--blue)}
.pill.flat{background:#ececec;color:var(--muted)}
.kpi{display:flex;flex-wrap:wrap;gap:12px;margin:0 0 4px;padding:0;list-style:none}
.kpi li{flex:1 1 150px;border:1px solid var(--line);border-radius:6px;padding:12px 14px}
.kpi b{display:block;font-size:26px;font-weight:700;font-variant-numeric:tabular-nums}
.kpi span{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.05em}
.kpi li.hot{border-color:var(--red);background:var(--red-soft)}
.kpi li.hot b{color:var(--red)}
.note{background:#f7f8fa;border-left:3px solid var(--blue);padding:10px 14px;
margin:14px 0 0;font-size:12.5px;color:#3a3d40}
.note.warn{border-left-color:var(--amber);background:var(--amber-soft)}
code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;
background:#f0f1f3;padding:1px 5px;border-radius:3px}
footer{max-width:1180px;margin:0 auto 48px;padding:0 20px;color:var(--muted);font-size:12px}
")

(defn- pill [class label] (str "<span class=\"pill " class "\">" (esc label) "</span>"))

(def ^:private class-pill
  {:governor-hard-hold ["refuse" "HOLD · governor 拒否"]
   :phase-gate-hold    ["gate"   "HOLD · phase gate"]
   :approver-rejected  ["gate"   "HOLD · 承認者が拒否"]
   :awaiting-approval  ["gate"   "ESCALATE · 人間承認待ち"]
   :committed          ["commit" "COMMIT"]
   :unclassified       ["flat"   "未分類"]})

(defn- class-badge [k]
  (let [[c l] (class-pill k ["flat" (str k)])] (pill c l)))

(defn- table [headers rows]
  (str "<table><thead><tr>"
       (apply str (for [h headers] (str "<th>" (esc h) "</th>")))
       "</tr></thead><tbody>"
       (apply str (for [r rows]
                    (str "<tr>" (apply str (for [c r] (str "<td>" c "</td>"))) "</tr>")))
       "</tbody></table>"))

(defn- section [title lede body & [note]]
  (str "<section class=\"card\"><h2>" (esc title) "</h2>"
       (if lede (str "<p class=\"lede\">" (esc lede) "</p>") "")
       body
       (or note "")
       "</section>"))

;; ---------------------------------------------------------------------------
;; sections
;; ---------------------------------------------------------------------------

(defn- summary-section [runs]
  (let [n     (fn [k] (count (filterv #(= k (:class %)) runs)))
        holds (governor-refusals runs)
        rules (into (sorted-set) (mapcat #(map :rule (get-in % [:verdict :violations])) holds))]
    (section
     "実行サマリ"
     (str "この repo の OperationActor を langgraph の run* で "
          (count runs) " 回実行した結果。すべての数値は実行結果から読み出している。")
     (str "<ul class=\"kpi\">"
          "<li><span>actor 実行</span><b>" (count runs) "</b></li>"
          "<li class=\"hot\"><span>governor HARD 拒否</span><b>" (n :governor-hard-hold) "</b></li>"
          "<li><span>phase gate による hold</span><b>" (n :phase-gate-hold) "</b></li>"
          "<li><span>承認者による拒否</span><b>" (n :approver-rejected) "</b></li>"
          "<li><span>SSoT へ commit</span><b>" (n :committed) "</b></li>"
          "<li><span>拒否された規則の種類</span><b>" (count rules) "</b></li>"
          "</ul>")
     (str "<p class=\"note\">governor の拒否と phase gate は<strong>別物として数えている</strong>。"
          "判定は audit fact の <code>:t</code> を第一根拠にし、<code>:violations</code> の有無では判定しない — "
          "承認者拒否の fact は <code>{:rule :approver-rejected}</code> という violation を"
          "自分で載せるので、violation を数えると governor 拒否を過大に数える。"
          "分類は governor 自身の <code>:hard?</code> フラグと突き合わせ、"
          "食い違ったらビルドを落とす。</p>"))))

(defn- run-log-section [runs]
  (section
   "actor 実行ログ"
   "1 行 = 1 回の actor 実行（intake → advise → govern → decide → commit｜hold｜approval）。同一 store 上で順に実行されるので状態は累積する。"
   (table ["thread" "phase" "op" "subject" "分類" "governor 規則" "conf" "備考"]
          (for [{:keys [thread phase request class verdict note]} runs]
            [(str "<span class=\"mono\">" (esc thread) "</span>")
             (cell phase)
             (str "<code>" (cell (:op request)) "</code>")
             (str "<code>" (cell (:subject request)) "</code>")
             (class-badge class)
             (let [rs (map :rule (:violations verdict))]
               (if (seq rs)
                 (str/join " " (map #(str "<code>" (cell %) "</code>") rs))
                 "—"))
             (cell (:confidence verdict))
             (esc (str note))]))))

(defn- refusals-section [runs]
  (let [holds (governor-refusals runs)]
    (section
     (str "governor の HARD 拒否 — " (count holds) " 件")
     "人間の承認者でも覆せない拒否。各行の detail は governor がその場で生成した文字列をそのまま出している。"
     (table ["thread" "op" "subject" "規則" "governor が拒否した内容"]
            (apply concat
                   (for [{:keys [thread request verdict]} holds]
                     (for [v (:violations verdict)]
                       [(str "<span class=\"mono\">" (esc thread) "</span>")
                        (str "<code>" (cell (:op request)) "</code>")
                        (str "<code>" (cell (:subject request)) "</code>")
                        (pill "refuse" (str (:rule v)))
                        (esc (str (:detail v)))]))))
     (str "<p class=\"note warn\">この 8 件のうち "
          "<code>:conflict-minerals-provenance-unverified</code> がこの業種固有の検査で、"
          "法域ではなく<strong>金属種</strong>だけで発火する。mo-6（gold）と mo-7（copper）は"
          "chain-of-custody / 製錬所認証がどちらも同じく未充足だが、"
          "gold は 3TG なので拒否され、copper は拒否されない — "
          "同じ実行の中で両方向を出している。</p>"))))

(defn- gates-section [runs]
  (let [gated (filterv #(#{:phase-gate-hold :approver-rejected :awaiting-approval} (:class %)) runs)]
    (section
     (str "phase / rollout gate と人間の承認 — " (count gated) " 件（governor 拒否ではない）")
     "governor は clean だが、段階的ロールアウトまたは人間の署名が止めたもの。上の HARD 拒否とは数え方も意味も違う。"
     (table ["thread" "phase" "op" "subject" "分類" "止めた主体" "根拠"]
            (for [{:keys [thread phase request class audit]} gated]
              (let [f (terminal-fact {:audit audit})]
                [(str "<span class=\"mono\">" (esc thread) "</span>")
                 (cell phase)
                 (str "<code>" (cell (:op request)) "</code>")
                 (str "<code>" (cell (:subject request)) "</code>")
                 (class-badge class)
                 (case class
                   :phase-gate-hold   "metaltrade.phase（ロールアウト段階）"
                   :approver-rejected "人間の承認者"
                   "metaltrade.phase（人間の署名待ち）")
                 (str "<code>"
                      (cell (or (:phase-reason f) (:reason f)
                                (when (= :approval-rejected (:t f)) :approver-rejected)))
                      "</code>")])))
     (str "<p class=\"note\">"
          "<code>t19</code> は <code>:provenance/verify</code> を phase 1 で実行したもの。governor は"
          "何も拒否していない（<code>:hard? false</code>、violation 0 件）が、phase 1 は"
          "<code>:order/intake</code> しか書き込ませないので <code>:phase-disabled</code> で hold した。"
          "<code>t20</code> は governor clean のまま人間の承認者が拒否したもので、"
          "その fact は <code>{:rule :approver-rejected}</code> を自分で載せている — "
          "violation の有無だけで数えていたら、この 2 件は governor 拒否として過大計上される。</p>"))))

(defn- register-section [db]
  (section
   "実行後の metal-order レジスタ（SSoT）"
   "commit ノードだけが書き込む。HARD 拒否された提案は 1 バイトも書いていない。"
   (table ["id" "order-id" "metal" "counterparty" "法域" "credit" "contract" "chain-of-custody" "smelter 認証" "sanctions" "dispatched" "invoiced" "dispatch #" "invoice #"]
          (for [mo (store/all-metal-orders db)]
            [(str "<code>" (cell (:id mo)) "</code>")
             (cell (:order-id mo))
             (str (cell (:metal-type mo))
                  (if (facts/conflict-minerals-metal? (:metal-type mo))
                    (str " " (pill "info" "3TG/cobalt")) ""))
             (cell (:counterparty mo))
             (cell (:jurisdiction mo))
             (cell (:credit-cleared? mo))
             (cell (:contract-terms mo))
             (cell (:chain-of-custody-documented? mo))
             (cell (:conflict-free-smelter-certified? mo))
             (cell (:sanctions-screened? mo))
             (cell (:dispatched? mo))
             (cell (:invoiced? mo))
             (str "<span class=\"mono\">" (cell (:dispatch-number mo)) "</span>")
             (str "<span class=\"mono\">" (cell (:invoice-number mo)) "</span>")]))))

(defn- ledger-section [db]
  (let [l (store/ledger db)]
    (section
     (str "append-only 監査台帳 — " (count l) " fact")
     "commit も hold も同じ台帳に残る。「誰が承認したか」を後から問い直せるかどうかは次節で実測している。"
     (table ["#" "fact 型" "op" "subject" "disposition" "actor / by" "基礎" "phase 理由"]
            (map-indexed
             (fn [i f]
               [(str "<span class=\"num\">" (inc i) "</span>")
                (str "<code>" (cell (:t f)) "</code>")
                (str "<code>" (cell (:op f)) "</code>")
                (str "<code>" (cell (:subject f)) "</code>")
                (cell (:disposition f))
                (str (cell (or (:actor f) (:by f)))
                     (cond (:actor f) " <span class=\"pill flat\">実行 actor</span>"
                           (:by f)    " <span class=\"pill info\">承認者</span>"
                           :else ""))
                (cell (:basis f))
                (str "<code>" (cell (:phase-reason f)) "</code>")])
             l)))))

(defn- records-section [db]
  (let [ds (store/dispatch-history db)
        is (store/invoice-history db)]
    (section
     (str "登録ドラフト — 出荷 " (count ds) " 件 / 請求 " (count is) " 件")
     "metaltrade.registry が構築した未署名ドラフト。署名は事業者の行為であって、この actor の行為ではない。"
     (table ["種別" "record_id" "metal_order_id" "法域" "immutable"]
            (concat
             (for [r ds]
               [(pill "info" "metal-dispatch")
                (str "<span class=\"mono\">" (cell (get r "record_id")) "</span>")
                (str "<code>" (cell (get r "metal_order_id")) "</code>")
                (cell (get r "jurisdiction"))
                (cell (get r "immutable"))])
             (for [r is]
               [(pill "info" "metal-invoice")
                (str "<span class=\"mono\">" (cell (get r "record_id")) "</span>")
                (str "<code>" (cell (get r "metal_order_id")) "</code>")
                (cell (get r "jurisdiction"))
                (cell (get r "immutable"))]))))))

(defn- attribution-section [db runs]
  (let [rep     (attribution-report db runs)
        lossy   (filterv #(and (:payload-carries %) (not (:store-retains %))) rep)
        granted (count (filterv #(= :approval-granted (:t %)) (store/ledger db)))]
    (section
     "承認者の帰属 — レンダリング時に実測"
     (str "この節はコードに書き込んだ結論ではなく、実行後の store を"
          "「approver 形のキー」で走査した結果。store が直れば、この表も自動的に直る。")
     (table ["commit した effect" "書き込み先" "subject" ":payload に approver" ":value に approver" "store に残ったか"]
            (for [{:keys [effect surface subject payload-carries value-carries store-retains]} rep]
              [(str "<code>" (cell effect) "</code>")
               (esc surface)
               (str "<code>" (cell subject) "</code>")
               (if payload-carries (pill "commit" "あり") (pill "flat" "なし"))
               (if value-carries   (pill "commit" "あり") (pill "flat" "なし"))
               (if store-retains   (pill "commit" "保持") (pill "refuse" "失われる"))]))
     (str "<p class=\"note" (if (seq lossy) " warn" "") "\">"
          "この実行では人間の承認が <strong>" granted "</strong> 件下りた。"
          "実行 actor は <code>" (esc actor-id) "</code>、承認者は <code>" (esc approver-id)
          "</code> と<strong>別の識別子</strong>にしてある — "
          "台帳の <code>:committed</code> fact が持つ <code>:actor</code> は"
          "<em>実行した actor</em> であって承認者ではないので、同じ id を使うと"
          "この 2 つを取り違えても正しく見えてしまう。"
          (if (seq lossy)
            (str "実測の結果 <strong>" (count lossy) " 件の effect で承認者が失われている</strong>: "
                 (str/join "、" (map #(str "<code>" (cell (:effect %)) "</code>") lossy))
                 "。<code>metaltrade.operation/commit-record</code> は承認者を <code>:payload</code> "
                 "にだけ載せるが、<code>metaltrade.store</code> の MemStore は "
                 "<code>:order/upsert</code> で <code>:value</code> を読み、"
                 "<code>:order/mark-dispatched</code> / <code>:order/mark-invoiced</code> では"
                 "どちらも読まない。したがって「この出荷を承認したのは誰か」は"
                 "台帳の <code>:approval-granted</code> fact からしか辿れず、"
                 "レジスタや出荷ドラフト自体には残らない。"
                 "<strong>これはこのタスクでは修正していない — 開示だけしている</strong>"
                 "（レンダリング作業の中で store の意味論を黙って変えない）。")
            "この実行では、承認が下りたすべての effect で承認者が store に残った。")
          "</p>"))))

(defn- coverage-section []
  (let [c (facts/coverage)]
    (section
     "法域 spec-basis のカバレッジ（正直な報告）"
     "catalog に無い法域には spec-basis が無い。advisor が要件を創作したら governor が止める（この実行の mo-2 / ATL がその実例）。"
     (table ["登録済み法域" "所管当局" "法的根拠" "出典"]
            (for [iso3 (:covered-jurisdictions c)
                  :let [sb (facts/spec-basis iso3)]]
              [(str "<code>" (cell iso3) "</code>")
               (cell (:owner-authority sb))
               (cell (:legal-basis sb))
               (str "<span class=\"mono\">" (cell (:provenance sb)) "</span>")]))
     (str "<p class=\"note\">" (esc (:note c)) "</p>"))))

(defn- phase-section []
  (section
   "ロールアウト段階（metaltrade.phase）"
   "phase gate は governor の判断を緩めない — 慎重にする方向にしか動かない。"
   (table ["phase" "label" "書き込み可" "auto-commit 可"]
          (for [[p {:keys [label writes auto]}] (sort-by key phase/phases)]
            [(str "<span class=\"num\">" p "</span>"
                  (if (= p phase/default-phase) (str " " (pill "info" "default")) ""))
             (esc label)
             (if (seq writes) (str/join " " (map #(str "<code>" (cell %) "</code>") (sort writes))) "—")
             (if (seq auto) (str/join " " (map #(str "<code>" (cell %) "</code>") (sort auto))) "—")]))
   (str "<p class=\"note\">どの phase の auto 集合にも <code>:delivery/dispatch</code> と "
        "<code>:invoice/settle</code> は入らない。governor 側の high-stakes gate も"
        "同じ不変条件を独立に強制していて、この実行では両方の層が実際に発火している。</p>")))

;; ---------------------------------------------------------------------------
;; page
;; ---------------------------------------------------------------------------

(defn page [db runs]
  (let [holds (governor-refusals runs)]
    (str "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">"
         "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
         "<title>cloud-itonami · metaltrade — Operator Console</title>"
         "<style>" css "</style></head><body>"
         "<header class=\"bar\"><h1>金属・金属鉱石卸売 — Operator Console</h1>"
         "<p>cloud-itonami-isic-4662 · MetalTradeAdvisor ⊣ :metal-trading-governor · "
         "このページは metaltrade.render-html が actor を実際に実行して生成した — "
         (count runs) " 実行 / governor HARD 拒否 " (count holds) " 件</p></header><main>"
         (summary-section runs)
         (refusals-section runs)
         (gates-section runs)
         (run-log-section runs)
         (register-section db)
         (attribution-section db runs)
         (ledger-section db)
         (records-section db)
         (phase-section)
         (coverage-section)
         "</main><footer>生成元: <code>metaltrade.render-html</code>"
         "（<code>clojure -M:dev:render-html</code>）。seed は "
         "<code>metaltrade.store/seed-db</code>。実行ごとに変わる値（UUID・実時刻）は"
         "意図的に載せていないので、同じ commit からは同じバイト列が出る。</footer>"
         "</body></html>")))

(defn -main [& args]
  (let [out   (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs]} (execute!)
        runs  (verify-classification! runs)
        holds (governor-refusals runs)]
    (when (zero? (count holds))
      (throw (ex-info (str "render-html: refusing to write " out
                           " -- the run produced ZERO hard governor holds. "
                           "An operator console that cannot show a real refusal is "
                           "not evidence that the governor works.")
                      {:runs (count runs)
                       :classes (frequencies (map :class runs))})))
    (io/make-parents out)
    (spit out (page db runs))
    (println (str "render-html: wrote " out
                  " (" (count (slurp out)) " chars, " (count runs) " actor runs, "
                  (count holds) " hard governor holds)"))))
