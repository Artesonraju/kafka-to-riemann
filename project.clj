(defproject kafka-to-riemann "0.1.0-SNAPSHOT"
  :description "Passing message from Apache Kafka to Aphyr's Riemann"
  :url "http://github.com/Artesonraju/kafka-to-riemann"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-beta1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clj-yaml "0.4.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-kafka "0.2.8-0.8.1.1"]
                 [riemann-clojure-client "0.3.2"]
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]]
  :main kafka-to-riemann.core
  :aot [kafka-to-riemann.core])
