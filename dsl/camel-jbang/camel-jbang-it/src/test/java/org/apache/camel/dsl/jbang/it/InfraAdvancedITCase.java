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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Tag("container-only")
@DisabledOnOs(WINDOWS)
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much resources")
public class InfraAdvancedITCase extends JBangTestSupport {

    private static final String SERVICE = "ftp";

    @AfterEach
    public void stopInfraService() {
        execInContainer(getMainCommand() + " infra stop " + SERVICE + " || true");
    }

    private String getServicePID(String message) {
        return message.split(":")[1].replaceAll("[^0-9]", "");
    }

    @Test
    public void testInfraGet() {
        String msg = execute("infra run --background " + SERVICE);
        String PID = getServicePID(msg);
        Awaitility.await()
                .atMost(Duration.ofSeconds(ASSERTION_WAIT_SECONDS))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps")).contains(PID));
        checkCommandOutputs("infra get " + SERVICE, SERVICE);
    }

    @Test
    public void testInfraLog() {
        String msg = execute("infra run --background " + SERVICE);
        String PID = getServicePID(msg);
        Awaitility.await()
                .atMost(Duration.ofSeconds(ASSERTION_WAIT_SECONDS))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps")).contains(PID));
        // infra log tails the service logs; we request with --follow=false so it returns immediately
        execute("infra log --follow=false " + SERVICE);
    }

    @Test
    public void testInfraRestart() {
        String msg = execute("infra run --background " + SERVICE);
        String oldPID = getServicePID(msg);
        Awaitility.await()
                .atMost(Duration.ofSeconds(ASSERTION_WAIT_SECONDS))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertThat(execute("infra ps")).contains(oldPID));
        checkCommandOutputs("infra restart " + SERVICE, SERVICE);
    }
}
