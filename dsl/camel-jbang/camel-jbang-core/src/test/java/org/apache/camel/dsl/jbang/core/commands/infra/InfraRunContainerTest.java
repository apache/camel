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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * {@code camel infra run} keeps the container a service runs in with its service data (CAMEL-22280).
 */
class InfraRunContainerTest {

    @Test
    void theNameAndShortIdOfTheContainer() {
        Map<String, Object> properties = new TreeMap<>();

        InfraRun.addContainerDetails(
                new FakeContainerService(
                        new FakeContainer(
                                "/camel-kafka",
                                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")),
                properties);

        assertThat(properties).containsOnly(entry("containerName", "camel-kafka"), entry("containerId", "0123456789ab"));
    }

    @Test
    void aServiceThatRunsInNoContainer() {
        Map<String, Object> properties = new TreeMap<>();

        InfraRun.addContainerDetails(new Object(), properties);
        InfraRun.addContainerDetails(new FakeContainerService(null), properties);

        assertThat(properties).isEmpty();
    }

    static class FakeContainerService {
        private final FakeContainer container;

        FakeContainerService(FakeContainer container) {
            this.container = container;
        }

        public FakeContainer getContainer() {
            return container;
        }
    }

    static class FakeContainer {
        private final String name;
        private final String id;

        FakeContainer(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String getContainerName() {
            return name;
        }

        public String getContainerId() {
            return id;
        }
    }
}
