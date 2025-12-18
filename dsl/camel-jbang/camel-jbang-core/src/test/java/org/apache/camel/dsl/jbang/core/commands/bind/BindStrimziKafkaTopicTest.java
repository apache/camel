/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.dsl.jbang.core.commands.bind;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BindStrimziKafkaTopicTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldBindToKafkaTopic() throws Exception {
        Bind command = createCommand("timer-source", "kafka:topic:my-topic");
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-topic-my-topic
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindToNamespacedKafkaTopic() throws Exception {
        Bind command = createCommand("timer-source", "kafka:topic:my-topic");

        command.sink = "kafka:topic:my-namespace/my-topic";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-topic-my-topic
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                      namespace: my-namespace
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindToKafkaTopicWithProperties() throws Exception {
        Bind command = createCommand("timer-source", "kafka:topic:my-topic");

        command.properties = new String[] {
                "source.message=Hello",
                "sink.brokers=my-cluster-kafka-bootstrap:9092",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-topic-my-topic
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    properties:
                      brokers: "my-cluster-kafka-bootstrap:9092"
                """.trim(), output);
    }

    @Test
    public void shouldBindToKafkaTopicWithUriProperties() throws Exception {
        Bind command = createCommand("timer-source", "kafka:topic:my-topic?brokers=my-cluster-kafka-bootstrap:9092");

        command.properties = new String[] {
                "source.message=Hello",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-topic-my-topic
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    properties:
                      brokers: "my-cluster-kafka-bootstrap:9092"
                """.trim(), output);
    }

    @Test
    public void shouldBindKafkaTopicSource() throws Exception {
        Bind command = createCommand("kafka:topic:my-topic", "log-sink");
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: topic-my-topic-to-log
                spec:
                  source:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    #properties:
                      #key: "value"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindNamespacedKafkaTopicSource() throws Exception {
        Bind command = createCommand("kafka:topic:my-topic", "log-sink");

        command.source = "kafka:topic:my-namespace/my-topic";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: topic-my-topic-to-log
                spec:
                  source:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                      namespace: my-namespace
                    #properties:
                      #key: "value"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindKafkaTopicSourceWithProperties() throws Exception {
        Bind command = createCommand("kafka:topic:my-topic", "log-sink");

        command.properties = new String[] {
                "source.brokers=my-cluster-kafka-bootstrap:9092",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: topic-my-topic-to-log
                spec:
                  source:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    properties:
                      brokers: "my-cluster-kafka-bootstrap:9092"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindKafkaTopicSourceWithUriProperties() throws Exception {
        Bind command = createCommand("kafka:topic:my-topic?brokers=my-cluster-kafka-bootstrap:9092", "log-sink");

        command.properties = new String[] {
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: topic-my-topic-to-log
                spec:
                  source:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                    properties:
                      brokers: "my-cluster-kafka-bootstrap:9092"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    private Bind createCommand(String source, String sink) {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));

        String sourceName;
        if (source.startsWith("kafka:")) {
            sourceName = StringHelper.after(source, "kafka:").replaceAll(":", "-");
            if (sourceName.contains("?")) {
                sourceName = StringHelper.before(sourceName, "?");
            }
        } else {
            sourceName = StringHelper.before(source, "-source");
        }

        String sinkName;
        if (sink.startsWith("kafka:")) {
            sinkName = StringHelper.after(sink, "kafka:").replaceAll(":", "-");
            if (sinkName.contains("?")) {
                sinkName = StringHelper.before(sinkName, "?");
            }
        } else {
            sinkName = StringHelper.before(sink, "-sink");
        }

        command.file = sourceName + "-to-" + sinkName + ".yaml";
        command.source = source;
        command.sink = sink;
        command.output = "yaml";

        return command;
    }
}
