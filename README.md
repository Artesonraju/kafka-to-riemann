# kafka-to-riemann

Passing message from Apache Kafka to Aphyr's Riemann

## Usage

lein uberjar

java -jar target/kafka-to-riemann-0.1.0-SNAPSHOT-standalone.jar ~/your/path/to/config.yml

## Configuration example

```yaml
kafka :
  zookeeper.connect : "localhost:2181"
  group.id : "kafka-to-riemann"
  consumer.id : "kafka-to-riemann"
  topic : "riemann"
  auto.commit.enable : "true"
  auto.commit.interval.ms : "5000"
riemann :
  host     : localhost
  port     : 5555
```

## License

Copyright © 2015 Etienne Molto

Distributed under the Eclipse Public License either version 1.0.
