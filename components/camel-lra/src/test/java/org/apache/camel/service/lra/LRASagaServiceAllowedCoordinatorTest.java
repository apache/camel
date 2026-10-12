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
package org.apache.camel.service.lra;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests which LRAs the LRA saga service accepts to join, complete or compensate.
 */
class LRASagaServiceAllowedCoordinatorTest {

    private final LRASagaService sagaService = new LRASagaService();

    @BeforeEach
    void configure() {
        sagaService.setCoordinatorUrl("http://lra-coordinator:8080");
        sagaService.setLocalParticipantUrl("http://participant:8080");
    }

    @Test
    void acceptsAnLraOfTheConfiguredCoordinator() {
        assertAccepted("http://lra-coordinator:8080/lra-coordinator/0_ffff0a1e1f05_1");
    }

    @Test
    void comparesSchemeAndHostIgnoringCase() {
        assertAccepted("HTTP://LRA-Coordinator:8080/lra-coordinator/0_1");
    }

    @Test
    void treatsTheDefaultPortLikeAnExplicitOne() {
        sagaService.setCoordinatorUrl("http://lra-coordinator");

        assertAccepted("http://lra-coordinator:80/lra-coordinator/0_1");
    }

    @Test
    void honoursTheCoordinatorContextPath() {
        sagaService.setCoordinatorContextPath("/api/v1/lra-coordinator");

        assertAccepted("http://lra-coordinator:8080/api/v1/lra-coordinator/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/0_1");
    }

    @Test
    void rejectsAnLraOfAnotherHost() {
        assertRejected("http://elsewhere:8080/lra-coordinator/0_1");
    }

    @Test
    void rejectsAnLraOfAnotherPortOrScheme() {
        assertRejected("http://lra-coordinator:9090/lra-coordinator/0_1");
        assertRejected("https://lra-coordinator:8080/lra-coordinator/0_1");
    }

    @Test
    void rejectsAPathOutsideTheCoordinatorContextPath() {
        assertRejected("http://lra-coordinator:8080/other/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator-other/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/../other/0_1");
    }

    @Test
    void rejectsEncodedOrParameterDotSegments() {
        assertRejected("http://lra-coordinator:8080/lra-coordinator/%2e%2e/other/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/%2E%2E/other/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/..;/other/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/..%2fother/0_1");
        assertRejected("http://lra-coordinator:8080/lra-coordinator/..%5Cother/0_1");
    }

    @Test
    void acceptsAnLraOfACoordinatorWithAnUnderscoreInItsHostName() {
        sagaService.setCoordinatorUrl("http://lra_coordinator:8080");

        assertAccepted("http://lra_coordinator:8080/lra-coordinator/0_1");
        assertRejected("http://other_coordinator:8080/lra-coordinator/0_1");
    }

    @Test
    void rejectsAnLraWithUserInfo() {
        assertRejected("http://user@lra-coordinator:8080/lra-coordinator/0_1");
    }

    @Test
    void acceptsAnLraOfAnAllowedCoordinatorUrl() {
        sagaService.setAllowedCoordinatorUrls(
                "http://lra-coordinator.lra.svc:8080/lra-coordinator, https://lra.example.com/coordinator,"
                                              + " http://lra_coordinator:8080/lra-coordinator");

        assertAccepted("http://lra-coordinator.lra.svc:8080/lra-coordinator/0_1");
        assertAccepted("https://lra.example.com/coordinator/0_1");
        assertAccepted("http://lra_coordinator:8080/lra-coordinator/0_1");
        assertRejected("http://elsewhere:8080/lra-coordinator/0_1");
    }

    @Test
    void failsToStartWithARelativeAllowedCoordinatorUrl() {
        sagaService.setAllowedCoordinatorUrls("lra-coordinator/lra-coordinator");

        assertThrows(IllegalArgumentException.class, sagaService::doStart);
    }

    @Test
    void failsToStartWithAnAllowedCoordinatorUrlThatIsNotHttp() {
        sagaService.setAllowedCoordinatorUrls("ftp://lra-coordinator/lra-coordinator");

        assertThrows(IllegalArgumentException.class, sagaService::doStart);
    }

    private void assertAccepted(String lra) {
        assertNotNull(sagaService.getSaga(lra).join(), lra + " must be accepted");
    }

    private void assertRejected(String lra) {
        CompletionException e = assertThrows(CompletionException.class, () -> sagaService.getSaga(lra).join(),
                lra + " must be rejected");
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }
}
