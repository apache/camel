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
package org.apache.camel.test.infra.spiffe.services;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SpiffeRotationConfigurationTest {

    @Test
    void retainsDefaultLifetimesAndKeyManager() {
        var service = new SpiffeLocalContainerInfraService("server", "agent");
        assertThat(service.serverConfiguration()).contains("ca_ttl = \"86400s\"",
                "default_x509_svid_ttl = \"3600s\"");
        assertThat(service.agentConfiguration()).contains("KeyManager \"memory\"").doesNotContain("KeyManager \"disk\"");
    }

    @Test
    void configuresRotationAndPersistentAgentKey() {
        var service = new SpiffeLocalContainerInfraService("server", "agent", Duration.ofSeconds(20), Duration.ofSeconds(120));
        assertThat(service.serverConfiguration()).contains("ca_ttl = \"120s\"",
                "default_x509_svid_ttl = \"20s\"");
        assertThat(service.agentConfiguration()).contains("KeyManager \"disk\"",
                "directory = \"/tmp/spire-agent/keys\"").doesNotContain("KeyManager \"memory\"");
    }

    @Test
    void rejectsLifetimesThatCannotBeGuaranteed() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SpiffeLocalContainerInfraService(
                "server", "agent",
                Duration.ofSeconds(20), Duration.ofSeconds(119)));
        assertThatIllegalArgumentException().isThrownBy(() -> new SpiffeLocalContainerInfraService(
                "server", "agent",
                Duration.ZERO, Duration.ofSeconds(120)));
    }
}
