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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.apache.camel.test.infra.spiffe.services.SpiffeLocalContainerInfraService.uidInNamespace;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The uid the SPIRE agent's unix workload attestor sees for the test JVM, given the {@code uid_map} of the agent
 * container as each engine sets it up (the lines are as the kernel prints them).
 */
class SpiffeWorkloadUidTest {

    private static final long OVERFLOW_UID = 65534;

    @Test
    void aRootfulEngineLeavesTheUidUnchanged() {
        List<String> uidMap = List.of("         0          0 4294967295");

        assertThat(uidInNamespace(1000, uidMap, OVERFLOW_UID)).isEqualTo(1000);
        assertThat(uidInNamespace(0, uidMap, OVERFLOW_UID)).isZero();
    }

    @Test
    void aRootlessEngineMapsItsOwnerToRoot() {
        // rootless Podman and rootless Docker map the engine owner to 0 and its subordinate uids from 1 upwards
        List<String> uidMap = List.of(
                "         0       1000          1",
                "         1     524288      65536");

        assertThat(uidInNamespace(1000, uidMap, OVERFLOW_UID)).isZero();
        assertThat(uidInNamespace(524288, uidMap, OVERFLOW_UID)).isEqualTo(1);
    }

    @Test
    void aKeepIdNamespaceKeepsTheOwnerUid() {
        // Podman --userns=keep-id maps the engine owner onto its own uid
        List<String> uidMap = List.of(
                "         0     524288       1000",
                "      1000       1000          1",
                "      1001     525288      64536");

        assertThat(uidInNamespace(1000, uidMap, OVERFLOW_UID)).isEqualTo(1000);
    }

    @Test
    void anUnmappedUidIsSeenAsTheOverflowUid() {
        List<String> uidMap = List.of(
                "         0       1000          1",
                "         1     524288      65536");

        assertThat(uidInNamespace(1001, uidMap, OVERFLOW_UID)).isEqualTo(OVERFLOW_UID);
        assertThat(uidInNamespace(524288 + 65536, uidMap, OVERFLOW_UID)).isEqualTo(OVERFLOW_UID);
    }
}
