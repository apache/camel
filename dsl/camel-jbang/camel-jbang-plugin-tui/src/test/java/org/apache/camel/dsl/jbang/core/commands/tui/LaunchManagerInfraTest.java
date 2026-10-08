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
    void aLaunchThatNeedsAServiceWithItsImplementationRunsOnceTheServiceIsUp() throws Exception {
        AtomicBoolean launched = new AtomicBoolean();
        // camel infra run aws sqs runs as the aws service
        lm.launchInfra("aws sqs", List.of("sleep", "30"));
        lm.deferUntilInfra(List.of("aws sqs"), "aws-sqs", () -> launched.set(true));

        lm.tick(System.currentTimeMillis());
        assertThat(launched).isFalse();

        running.add(infra("aws"));
        lm.tick(System.currentTimeMillis());
        assertThat(launched).isTrue();
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

    @Test
    void thePullProgressOfAnImage() {
        // the total is not known until Docker has reported the size of every layer
        assertThat(LaunchManager.pullProgress("""
                Pulling docker image: mirror.gcr.io/openfga/openfga:v1.21.0. Please be patient
                Pulling image layers:  0 pending,  0 downloaded,  0 extracted, (0 bytes/0 bytes)
                Pulling image layers:  2 pending,  2 downloaded,  1 extracted, (21 MB/? MB)
                """)).isEqualTo("2 of 4 layers, 21 MB");
        assertThat(LaunchManager.pullProgress(
                "Pulling image layers:  3 pending,  2 downloaded,  0 extracted, (1.2 GB/3.4 GB)"))
                .isEqualTo("2 of 5 layers, 1.2 GB of 3.4 GB (35%)");
        // nothing pulled yet, and a pull that has completed
        assertThat(LaunchManager.pullProgress(
                "Pulling image layers:  0 pending,  0 downloaded,  0 extracted, (0 bytes/0 bytes)")).isNull();
        assertThat(LaunchManager.pullProgress("""
                Pulling image layers:  1 pending,  3 downloaded,  3 extracted, (22 MB/? MB)
                Pull complete. 4 layers, pulled in 3s (downloaded 22 MB at 7 MB/s)
                """)).isNull();
    }

    @Test
    void theProgressOfAPullIsShownWhileTheInfraStarts() throws Exception {
        List<String> notices = new ArrayList<>();
        lm.setNotificationCallback((msg, error) -> notices.add(msg));
        lm.launchInfra("ollama", List.of("sh", "-c",
                "echo 'Pulling image layers:  3 pending,  2 downloaded,  0 extracted, (1.2 GB/3.4 GB)'; sleep 30"));
        long deadline = System.currentTimeMillis() + 10_000;
        while (notices.isEmpty() && System.currentTimeMillis() < deadline) {
            lm.tick(System.currentTimeMillis());
            Thread.sleep(50);
        }

        assertThat(notices).containsExactly("Pulling image of ollama: 2 of 5 layers, 1.2 GB of 3.4 GB (35%)");
        // the same progress is not shown again
        lm.tick(System.currentTimeMillis());
        assertThat(notices).hasSize(1);
        ProcessHandle.current().children()
                .filter(p -> p.info().command().map(c -> c.endsWith("sh")).orElse(false))
                .forEach(ph -> {
                    ph.descendants().forEach(ProcessHandle::destroy);
                    ph.destroy();
                });
    }

    private static InfraInfo infra(String alias) {
        InfraInfo info = new InfraInfo();
        info.alias = alias;
        info.alive = true;
        return info;
    }
}
