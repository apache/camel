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
package org.apache.camel.dsl.jbang.it;

import java.time.Duration;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

public class InfrastructureITCase extends JBangTestSupport {
    private static final String SERVICE = "ftp";
    private static final String IMPL_SERVICE = "artemis";
    private static final String IMPLEMENTATION = "amqp";

    @Test
    public void infraListTest() {
        checkCommandOutputsPattern("infra list", "ALIAS\s+IMPLEMENTATION\s+DESCRIPTION");
    }

    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Requires too much resources")
    @Test
    public void runServiceTest() {
        execInContainer(String.format("nohup camel infra run %s&", SERVICE));
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps")).containsPattern(SERVICE));
        Assertions.assertThat(execute("infra stop " + SERVICE))
                .contains("Shutting down external services");
    }

    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Requires too much resources")
    @Test
    public void runServiceWithImplementationTest() {
        execInContainer(String.format("nohup camel infra run %s %s&", IMPL_SERVICE, IMPLEMENTATION));
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps"))
                        .containsPattern(IMPL_SERVICE + "\\s+" + IMPLEMENTATION));
    }
}
