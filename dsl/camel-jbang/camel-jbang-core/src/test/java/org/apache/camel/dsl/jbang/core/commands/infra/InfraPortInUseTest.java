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

import java.net.BindException;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A service that does not start because a port it maps on the host is taken says so in one line, naming the infra
 * service that has the port (CAMEL-24823).
 */
class InfraPortInUseTest extends InfraCommandTestSupport {

    // as Docker and testcontainers report it, a few causes down
    private static final RuntimeException DOCKER = new RuntimeException(
            "Container startup failed for image artemis",
            new RuntimeException(
                    "Could not create/start container",
                    new RuntimeException(
                            "Status 500: {\"message\":\"failed to set up container networking: driver failed"
                                         + " programming external connectivity on endpoint camel-artemis (3f2a9c1b7d4e):"
                                         + " Bind for 0.0.0.0:1883 failed: port is already allocated\"}")));

    @Test
    void thePortDockerSaysIsTaken() {
        assertThat(InfraPortInUse.port(DOCKER)).isEqualTo(1883);
        assertThat(InfraPortInUse.port("Bind for [::]:5672 failed: port is already allocated")).isEqualTo(5672);
    }

    @Test
    void thePortPodmanSaysIsTaken() {
        assertThat(InfraPortInUse.port(
                "rootlessport listen tcp 0.0.0.0:1883: bind: address already in use")).isEqualTo(1883);
    }

    @Test
    void notAboutAPortInUse() {
        assertThat(InfraPortInUse.port(new RuntimeException("Could not pull image: manifest unknown"))).isEqualTo(-1);
        // an embedded service does not say which port
        assertThat(InfraPortInUse.port(new BindException("Address already in use"))).isEqualTo(-1);
        assertThat(InfraPortInUse.port((Throwable) null)).isEqualTo(-1);
    }

    @Test
    void theServiceDataOfTheServiceThatHasThePort() {
        assertThat(InfraPortInUse.hasPort("{\"port\":1883}", 1883)).isTrue();
        assertThat(InfraPortInUse.hasPort("{\"getServiceAddress\":\"tcp://localhost:1883\"}", 1883)).isTrue();
        assertThat(InfraPortInUse.hasPort("{\"port\":\"1883\"}", 1883)).isTrue();
        // not another port that starts the same, nor a container id
        assertThat(InfraPortInUse.hasPort("{\"port\":18830}", 1883)).isFalse();
        assertThat(InfraPortInUse.hasPort("{\"containerId\":\"1883ab7d4e9f\"}", 1883)).isFalse();
        assertThat(InfraPortInUse.hasPort(null, 1883)).isFalse();
    }

    @Test
    void aPortThatARunningInfraServiceHas() throws Exception {
        writePidFile("mosquitto", 4321, "{\"getPort\":1883,\"serviceAddress\":\"tcp://localhost:1883\"}");
        writePidFile("kafka", 1234, "{\"getBootstrapServers\":\"localhost:9092\"}");

        assertThat(command().portInUseMessage("artemis", DOCKER)).isEqualTo(
                "Cannot start artemis: port 1883 is in use by mosquitto (PID 4321), stop it first with: camel infra stop mosquitto");
    }

    @Test
    void aPortThatAnotherProgramHas() throws Exception {
        writePidFile("kafka", 1234, "{\"getBootstrapServers\":\"localhost:9092\"}");

        assertThat(command().portInUseMessage("artemis", DOCKER))
                .isEqualTo("Cannot start artemis: port 1883 is in use by another program");
        assertThat(command().portInUseMessage("artemis", new RuntimeException("Could not pull image"))).isNull();
    }

    private InfraRun command() {
        InfraRun command = new InfraRun(new CamelJBangMain().withPrinter(printer));
        // the pid files carry synthetic pids, so treat every pid as a live process
        command.aliveCheck = pid -> true;
        return command;
    }
}
