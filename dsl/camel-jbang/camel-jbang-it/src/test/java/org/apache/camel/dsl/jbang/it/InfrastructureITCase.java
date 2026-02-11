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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Tag("container-only")
@DisabledOnOs(WINDOWS)
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
    public void runStopServiceTest() {
        String msg = execute("infra run --background " + SERVICE);
        String PID = getServicePID(msg);
        Assertions.assertThat(msg).contains(String.format("Running %s in background", SERVICE));
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps"))
                        .contains(PID));
        checkCommandOutputs("infra stop " + SERVICE, "Shutting down external services (PID: " + PID);
        checkCommandDoesNotOutput("infra ps", PID);
    }

    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Requires too much resources")
    @Test
    public void runServiceWithImplementationTest() {
        String msg = execute(String.format("infra run --background %s %s", IMPL_SERVICE, IMPLEMENTATION));
        String PID = getServicePID(msg);
        Assertions.assertThat(msg).contains(String.format("Running %s in background", IMPL_SERVICE));
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps"))
                        .containsPattern(PID + "\\s+" + IMPL_SERVICE + "\\s+" + IMPLEMENTATION));
        checkCommandOutputs("infra stop " + IMPL_SERVICE, "Shutting down external services (PID: " + PID);
        checkCommandDoesNotOutput("infra ps", PID);
    }

    private String getServicePID(String message) {
        return message.split(":")[1].replaceAll("[^0-9]", "");
    }
}
