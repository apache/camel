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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The infra services an example needs may name the implementation too ({@code aws sqs}): it is started with both, and
 * known as running by the service alone, as {@code camel infra run aws sqs} runs as {@code aws}.
 */
class LaunchManagerInfraTest {

    private final List<InfraInfo> running = new ArrayList<>();
    private final LaunchManager lm = new LaunchManager(() -> running);

    @Test
    void theServiceOfAnEntryWithItsImplementation() {
        assertThat(LaunchManager.serviceOf("aws sqs")).isEqualTo("aws");
        assertThat(LaunchManager.serviceOf("kafka")).isEqualTo("kafka");
    }

    @Test
    void aServiceWithItsImplementationIsMissingUntilItsServiceRuns() {
        JsonObject example = new JsonObject();
        example.put("infraServices", List.of("aws sqs", "kafka"));

        assertThat(lm.findMissingInfraServices(example)).containsExactly("aws sqs", "kafka");

        running.add(infra("aws"));
        assertThat(lm.findMissingInfraServices(example)).containsExactly("kafka");
    }

    @Test
    void aLaunchWaitsWhileItsInfraIsStartingAndRunsOnceItIsUp() throws Exception {
        List<String> notices = new ArrayList<>();
        lm.setNotificationCallback((msg, error) -> notices.add(msg));
        AtomicBoolean launched = new AtomicBoolean();
        // a first start pulls the image: the infra takes a while
        lm.launchInfra("kafka", List.of("sleep", "30"));
        lm.deferUntilInfra(List.of("kafka"), "kafka-orders", () -> launched.set(true));

        lm.tick(System.currentTimeMillis());
        assertThat(launched).isFalse();
        assertThat(notices).isEmpty();

        running.add(infra("kafka"));
        lm.tick(System.currentTimeMillis());
        assertThat(launched).isTrue();
        assertThat(notices).contains("Started: kafka");
        ProcessHandle.current().children()
                .filter(p -> p.info().command().map(c -> c.endsWith("sleep")).orElse(false))
                .forEach(ProcessHandle::destroy);
    }

    @Test
    void aLaunchIsDroppedWhenItsInfraFailsToStart() throws Exception {
        List<String> notices = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        lm.setNotificationCallback((msg, error) -> notices.add(msg));
        lm.setFailureLogCallback((name, log) -> failures.add(name));
        AtomicBoolean launched = new AtomicBoolean();
        // the container cannot bind its port: camel infra run ends with an error
        lm.launchInfra("mosquitto", List.of("sh", "-c", "echo port is already allocated; exit 1"));
        lm.deferUntilInfra(List.of("mosquitto"), "mqtt", () -> launched.set(true));
        long deadline = System.currentTimeMillis() + 10_000;
        while (failures.isEmpty() && System.currentTimeMillis() < deadline) {
            lm.tick(System.currentTimeMillis());
            Thread.sleep(50);
        }

        assertThat(launched).isFalse();
        assertThat(failures).containsExactly("mosquitto");
        assertThat(notices).contains("Not started: mqtt (its infra services failed to start)");
    }

    private static InfraInfo infra(String alias) {
        InfraInfo info = new InfraInfo();
        info.alias = alias;
        info.alive = true;
        return info;
    }
}
