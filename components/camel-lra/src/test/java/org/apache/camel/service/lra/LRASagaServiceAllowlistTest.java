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

import java.util.Collections;
import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LRASagaServiceAllowlistTest extends CamelTestSupport {

    private LRASagaService sagaService;

    public LRASagaServiceAllowlistTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @BeforeEach
    void initService() {
        sagaService = new LRASagaService();
        sagaService.setCoordinatorUrl("http://localhost:8080");
        sagaService.setLocalParticipantUrl("http://localhost:8081");
        sagaService.setLocalParticipantContextPath("/lra-participant");
        sagaService.setCoordinatorContextPath("/lra-coordinator");
        sagaService.setCamelContext(context());
    }

    @DisplayName("registerStep adds compensation URI only to the compensation allowlist")
    @Test
    void testCompensationUriSeparation() {
        Endpoint compensation = context().getEndpoint("direct:compensate1");
        CamelSagaStep step = new CamelSagaStep(compensation, null, Collections.emptyMap(), null);
        step.setRouteId("route1");

        sagaService.registerStep(step);

        assertTrue(sagaService.getRegisteredCompensationURIs().contains("direct://compensate1"));
        assertFalse(sagaService.getRegisteredCompletionURIs().contains("direct://compensate1"));
    }

    @DisplayName("registerStep adds completion URI only to the completion allowlist")
    @Test
    void testCompletionUriSeparation() {
        Endpoint completion = context().getEndpoint("direct:complete1");
        CamelSagaStep step = new CamelSagaStep(null, completion, Collections.emptyMap(), null);
        step.setRouteId("route1");

        sagaService.registerStep(step);

        assertTrue(sagaService.getRegisteredCompletionURIs().contains("direct://complete1"));
        assertFalse(sagaService.getRegisteredCompensationURIs().contains("direct://complete1"));
    }

    @DisplayName("getRegisteredURIs returns the union of both sets")
    @Test
    void testGetRegisteredURIsReturnsUnion() {
        Endpoint compensation = context().getEndpoint("direct:compensate1");
        Endpoint completion = context().getEndpoint("direct:complete1");
        CamelSagaStep step = new CamelSagaStep(compensation, completion, Collections.emptyMap(), null);
        step.setRouteId("route1");

        sagaService.registerStep(step);

        Set<String> all = sagaService.getRegisteredURIs();
        assertEquals(2, all.size());
        assertTrue(all.contains("direct://compensate1"));
        assertTrue(all.contains("direct://complete1"));
    }

    @DisplayName("unregisterSteps prunes URIs from the allowlists")
    @Test
    void testUnregisterStepsPrunesURIs() {
        Endpoint compensation = context().getEndpoint("direct:compensate1");
        Endpoint completion = context().getEndpoint("direct:complete1");
        CamelSagaStep step = new CamelSagaStep(compensation, completion, Collections.emptyMap(), null);
        step.setRouteId("testRoute");

        sagaService.registerStep(step);
        assertTrue(sagaService.getRegisteredCompensationURIs().contains("direct://compensate1"));
        assertTrue(sagaService.getRegisteredCompletionURIs().contains("direct://complete1"));

        sagaService.unregisterSteps("testRoute");

        assertFalse(sagaService.getRegisteredCompensationURIs().contains("direct://compensate1"));
        assertFalse(sagaService.getRegisteredCompletionURIs().contains("direct://complete1"));
        assertTrue(sagaService.getRegisteredURIs().isEmpty());
    }

    @DisplayName("Shared URIs are only pruned when all referencing routes are removed")
    @Test
    void testSharedUriRetainedUntilAllRoutesRemoved() {
        Endpoint sharedCompensation = context().getEndpoint("direct:sharedCompensate");

        CamelSagaStep step1 = new CamelSagaStep(sharedCompensation, null, Collections.emptyMap(), null);
        step1.setRouteId("routeA");
        sagaService.registerStep(step1);

        CamelSagaStep step2 = new CamelSagaStep(sharedCompensation, null, Collections.emptyMap(), null);
        step2.setRouteId("routeB");
        sagaService.registerStep(step2);

        assertTrue(sagaService.getRegisteredCompensationURIs().contains("direct://sharedCompensate"));

        // remove routeA — shared URI should remain because routeB still references it
        sagaService.unregisterSteps("routeA");
        assertTrue(sagaService.getRegisteredCompensationURIs().contains("direct://sharedCompensate"));

        // remove routeB — now the URI should be pruned
        sagaService.unregisterSteps("routeB");
        assertFalse(sagaService.getRegisteredCompensationURIs().contains("direct://sharedCompensate"));
    }

    @DisplayName("unregisterSteps for unknown routeId is a no-op")
    @Test
    void testUnregisterUnknownRouteIsNoOp() {
        Endpoint compensation = context().getEndpoint("direct:compensate1");
        CamelSagaStep step = new CamelSagaStep(compensation, null, Collections.emptyMap(), null);
        step.setRouteId("route1");
        sagaService.registerStep(step);

        sagaService.unregisterSteps("unknownRoute");

        assertTrue(sagaService.getRegisteredCompensationURIs().contains("direct://compensate1"));
    }
}
