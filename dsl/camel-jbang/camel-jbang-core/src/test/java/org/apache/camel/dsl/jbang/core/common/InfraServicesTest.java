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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class InfraServicesTest {

    @TempDir
    Path camelDir;

    @Test
    void theServicesOfCamelInfraWithTheirPorts() throws Exception {
        // the pid files of camel infra run hold what the service tells about itself
        Files.writeString(camelDir.resolve("infra-kafka-101.json"),
                "{\"brokers\":\"localhost:32775\",\"getBootstrapServers\":\"PLAINTEXT://localhost:32775\"}");
        Files.writeString(camelDir.resolve("infra-artemis-102.json"),
                "{\"serviceAddress\":\"tcp://127.0.0.1:61616\",\"port\":61616,\"webPort\":\"8161\"}");
        // a service whose process is gone, and a file that is not a pid file
        Files.writeString(camelDir.resolve("infra-mosquitto-103.json"), "{\"port\":1883}");
        Files.writeString(camelDir.resolve("infra-notes.json"), "{}");

        List<InfraServices.Service> services = InfraServices.running(camelDir, pid -> pid != 103);

        assertThat(services).containsExactly(
                new InfraServices.Service("artemis", 102, Set.of(61616, 8161)),
                new InfraServices.Service("kafka", 101, Set.of(32775)));
    }

    @Test
    void addressesAllOnAServiceOfCamelInfra() {
        List<InfraServices.Service> services = List.of(
                new InfraServices.Service("kafka", 101, Set.of(32775)),
                new InfraServices.Service("artemis", 102, Set.of(61616)));

        assertThat(InfraServices.serving(List.of("localhost:32775"), services).name()).isEqualTo("kafka");
        assertThat(InfraServices.serving(List.of("jms:queue:orders", "tcp://127.0.0.1:61616"), services).name())
                .isEqualTo("artemis");
        // another host, another port, one broker of two elsewhere, or no address at all: not known to be local
        assertThat(InfraServices.serving(List.of("kafka.example.com:32775"), services)).isNull();
        assertThat(InfraServices.serving(List.of("localhost:9092"), services)).isNull();
        assertThat(InfraServices.serving(List.of("localhost:32775,localhost:9092"), services)).isNull();
        assertThat(InfraServices.serving(List.of("kafka://refunds"), services)).isNull();
    }
}
