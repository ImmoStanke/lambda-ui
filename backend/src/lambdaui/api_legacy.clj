(ns lambdaui.api-legacy
  (:use [lambdaui.common.summaries])
  (:require [org.httpkit.server :refer [with-channel on-close on-receive send! close]]
            [compojure.core :refer [routes GET POST defroutes context]]
            [ring.util.response :refer [response header]]
            [lambdacd.presentation.unified]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [lambdacd.util]
            [lambdacd.internal.pipeline-state :as state]
            [lambdacd.presentation.unified :as presentation]
            [clojure.tools.logging :as log]))

(defn deep-merge
  "Recursively merges maps. If vals are not maps, the last value wins."
  [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn state-from-pipeline [pipeline]
  (state/get-all (:pipeline-state-component (:context pipeline))))

(defn- cross-origin-response [data]
  (header (response data) "Access-Control-Allow-Origin" "*"))

(defn summaries-response [pipeline]
  (cross-origin-response (summaries (state-from-pipeline pipeline))))


(defn- to-iso-string [timestamp]
  (when timestamp
    (str timestamp)))

(defn- finished? [status]
  (contains? #{:success :failure :killed} status))

(defn- step-id-str [step-id]
  (clojure.string/join "-" step-id))

(defn get-type [step]
  (when-let [type (:type step)]
    (case type
      :manual-trigger :trigger
      type)))



(defn- to-ui-params [params]
  (let [f (fn [[p-name props]] {:key (name p-name) :name (:desc props)})]
    (map f params)))

(defn get-trigger-data [trigger-path-prefix step]
  (let [trigger-id (get-in step [:result :trigger-id])
        url-template "%s/api/dynamic/%s"
        parameters (if-let [params (:parameters (:result step))] {:parameter (to-ui-params params)} {})
        url {:url (format url-template trigger-path-prefix trigger-id)}]
    (if (seq trigger-id)
      {:trigger (merge url parameters)}
      {})))

(defn to-output-format [trigger-path-prefix step]
  (let [status (:status (:result step))
        trigger (get-trigger-data trigger-path-prefix step)
        base {:stepId    (step-id-str (:step-id step))
              :name      (:name step)
              :state     (or status :pending)
              :startTime (to-iso-string (:first-updated-at (:result step)))
              :endTime   (when (finished? status) (to-iso-string (:most-recent-update-at (:result step))))}
        type (if (empty? trigger) {:type (get-type step)} {:type :trigger})
        children (if-let [children (:children step)] {:steps (map (partial to-output-format trigger-path-prefix) children)} {})]
    (merge base type children trigger)))

(defn build-details-from-pipeline [pipeline-def pipeline-state build-id ui-config]
  (let [unified-steps-map (->> (presentation/unified-presentation pipeline-def pipeline-state)
                               (map (partial to-output-format (:path-prefix ui-config "")))
                               (map (fn [step] [(:stepId step) step]))
                               (into {}))
        steps (vals unified-steps-map)]

    {:buildId build-id
     :steps   steps}))

(defn- build-details-response [pipeline build-id]
  (let [build-state (get (state-from-pipeline pipeline) (Integer/parseInt build-id))
        pipeline-def (:pipeline-def pipeline)]
    (build-details-from-pipeline pipeline-def build-state build-id (get-in pipeline [:context :config :ui-config]))))

(defn only-matching-step [event-updates-ch build-id step-id]
  (let [result (async/chan)
        transducer (comp
                     (filter #(= build-id (:build-number %)))
                     (filter #(= step-id (step-id-str (:step-id %))))
                     (map (fn [x] {:stepId     (step-id-str (:step-id x))
                                   :buildId    (:build-number x)
                                   :stepResult (:step-result x)})))]

    (async/pipeline 1 result transducer event-updates-ch)
    result))

(defn- to-step-id [step-id-s]
  (map #(Integer/parseInt %) (s/split step-id-s #"-")))



(defn finished-step? [pipeline build-id step-id]
  (finished? (:status
               (-> (state-from-pipeline pipeline)
                   (get build-id)
                   (get (to-step-id step-id))))))



(defn output-events [pipeline ws-ch build-id step-id]
  (let [ctx (:context pipeline)
        subscription (event-bus/subscribe ctx :step-result-updated)
        payloads (event-bus/only-payload subscription)
        filtered (only-matching-step payloads build-id step-id)
        sliding-window (async/pipe filtered (async/chan (async/sliding-buffer 1)))]

    (on-close ws-ch (fn [_] (do (event-bus/unsubscribe ctx :step-result-updated subscription) (println "closed channel!"))))
    (send! ws-ch (lambdacd.util/to-json {:stepResult (-> (state-from-pipeline pipeline)
                                                         (get build-id)
                                                         (get (to-step-id step-id)))
                                         :buildId    build-id
                                         :stepId     step-id}))

    (if (not (finished-step? pipeline build-id step-id))
      (async/thread []
                    (let [event (async/<!! sliding-window)]
                      (send! ws-ch (json/write-str event))
                      (async/<!! (async/timeout 1000))
                      (if (finished? (get-in event [:stepResult :status]))
                        (close ws-ch)
                        (recur))))
      (close ws-ch))))


(defn- output-buildstep-websocket [pipeline req build-id step-id]
  (with-channel req ws-ch
                (output-events pipeline ws-ch (Integer/parseInt build-id) step-id)))

(defn- subscribe-to-summary-update [request pipeline]
  (with-channel request websocket-channel

                (let [ctx (:context pipeline)]
                      ;subscription (event-bus/subscribe ctx :step-result-updated)
                      ;payloads     (event-bus/only-payload subscription)


                  (send! websocket-channel (lambdacd.util/to-json (summaries (state-from-pipeline pipeline))))
                  (close websocket-channel))))

                  ;(async/go-loop []
                  ;  (if-let [event (async/<! payloads)]
                  ;    (do
                  ;      (println @current-count " -- " event)
                  ;      (send! websocket-channel (lambdacd.util/to-json (merge {:updateNo @current-count} (summaries (state-from-pipeline pipeline)))))
                  ;      (swap! current-count inc)
                  ;      (recur))))


(defn websocket-connection-for-details [pipeline build-id websocket-channel]
  (send! websocket-channel (json/write-str (build-details-response pipeline build-id)))
  (close websocket-channel))

(defn wrap-websocket [request handler]
  (with-channel request channel
                (handler channel)))

(defn api-routes [pipeline]
  (routes
    (GET "/builds" [:as request] (subscribe-to-summary-update request pipeline))
    (GET "/builds/:build-id" [build-id :as request] (wrap-websocket request (partial websocket-connection-for-details pipeline build-id)))
    (GET "/builds/:build-id/:step-id" [build-id step-id :as request] (output-buildstep-websocket pipeline request build-id step-id))))
