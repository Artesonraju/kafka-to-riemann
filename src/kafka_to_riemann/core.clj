(ns kafka-to-riemann.core
  "Passing message from Apache Kafka to Aphyr's Riemann"
  (:require [clojure.core.async
               :refer [>!! <!! go chan thread pipeline]]
            [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [riemann.client :as riemann]
            [clj-kafka.core :as kafka]
            [clj-kafka.consumer.zk :as zkconsumer]
            [clojure.tools.logging :refer [info error]])
  (:import kafka.consumer.KafkaStream)
  (:gen-class))

(def downtime-ms 1000)
(def riemann-timeout-ms 1000)

(def message-template {:service "kafkaToRiemann"
                       :state "warning"
                       :tags ["kafka" "riemann"]})

; messages read from kafka
(def kafka-chan (chan))

; messages to be read from riemann
(def riemann-chan (chan))

(defn current-timestamp
  "Current time at unix timestamp format"
  []
  (quot (System/currentTimeMillis) 1000))

(defn stringify
  "Transforms the key of a map into srings"
  [m]
  (reduce merge {} (map (juxt (comp name key) val) m)))

(defn retry-on-ioexception
  [tries f & args]
  (let [res (try {:value (apply f args)}
                 (catch java.io.IOException e
                   (if (= 1 tries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (recur (dec tries) f args)
      (:value res))))

(defn log-error
  "Print the error message and send it to riemann"
  [error-message exception]
  (do
    (error exception error-message)
    (>!! riemann-chan
      (assoc message-template
        :description error-message
        :time (current-timestamp)))))

(defn transform-message
  "Transform a kafka json payload into a Riemann message"
  [message]
  (try
    (let [{:keys [value]} (kafka/to-clojure message)
          payload (String. value)
          json (json/read-str payload)]
      (reduce-kv (fn [m k v] (assoc m k (str v))) {} json))
    (catch Exception e
      (let [message (str "Unable to read payload : " (.getMessage e))]
        (error e message)
        (assoc message-template
          :description message
          :time (current-timestamp))))))

(defn consume-kafka-topic
  "Consume kafka messages and pass it to the channel kafka-chan"
  [consumer topic]
  (let [stream-map  (.createMessageStreams consumer {topic (int 1)})
        [stream & _] (get stream-map topic)
        it (.iterator ^KafkaStream stream)]
    (while (.hasNext it)
      (let [message (.next it)]
        (>!! kafka-chan message)))))

(defn produce-riemann
  "Produces riemann events from the channel riemann-chan"
  [producer]
  (while true
    (info "Riemann channel waiting for messages")
    (let [message (<!! riemann-chan)]
      (info (str "Riemann channel recieved a message : " message))
      (retry-on-ioexception 3
        (-> producer (riemann/send-event message)
          (deref riemann-timeout-ms ::timeout))))))

(defn launch-kafka-consumer
  "Creates a thread consuming kafka messages"
  [config]
    (let [topic (config :topic)
          kafka-config (stringify (dissoc config :topic))]
      (thread
        (while true
          (try
            (let [consumer (zkconsumer/consumer kafka-config)]
              (try
                (consume-kafka-topic consumer topic)
                (catch Exception e
                  (log-error (str "Kafka consumption returned with exception : " (.getMessage e)) e))
                (finally
                  (.shutdown consumer))))
            (catch Exception e
                  (log-error (str "Kafka consumption returned with exception : " (.getMessage e)) e)))
          (Thread/sleep downtime-ms)))))

(defn launch-riemann-producer
  "Creates a thread producing riemann messages"
  [config]
  (thread
    (while true
      (try
        (let [producer (riemann/tcp-client config)]
          (produce-riemann producer))
        (catch Exception e
          (do
            (error e (str "Riemann production returned with exception : " (.getMessage e)))
            (Thread/sleep downtime-ms)))))))

(defn start-config
  "Takes a path to a yaml config, parses it, and runs it"
  [config]
  (let [yaml (yaml/parse-string (slurp config))]
    (info (str "Config :" yaml))
    (pipeline 1 riemann-chan (map transform-message) kafka-chan)
    (launch-riemann-producer (yaml :riemann))
    (<!! (launch-kafka-consumer (yaml :kafka)))))

(defn -main
  "Passing messages from Kafka to Riemann, to be run with Kafka auto.commit"
  [arg]
  (start-config arg))
