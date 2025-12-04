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

package org.apache.camel.component.tahu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisabledIfSystemProperty(
        named = "ci.env.name",
        matches = ".*",
        disabledReason = "CAMEL-21143: requires too much resources and blocks the CI")
public class TahuEdgeProducerIT extends TahuTestSupport {

    enum EdgeNodeTestProfile {
        SESSION_ESTABLISHMENT_TEST("edge SessionEstablishmentTest IamHost G2 E2 D2", false, 0),
        SESSION_TERMINATION_TEST("edge SessionTerminationTest IamHost G2 E2 D2", false, 3),
        SEND_DATA_TEST("edge SendDataTest IamHost G2 E2 D2", false, 15),
        SEND_COMPLEX_DATA_TEST("edge SendComplexDataTest IamHost G2 E2 D2", false, 15),
        RECEIVE_COMMAND_TEST("edge ReceiveCommandTest IamHost G2 E2 D2", false, 15),
        PRIMARY_HOST_TEST("edge PrimaryHostTest IamHost G2 E2 D2", true, 15);
        ;

        private EdgeNodeTestProfile(String testConfig, boolean startRoutesBeforeTCKInitiate, int maxSentDataCount) {
            this.testConfig = testConfig;
            this.startRoutesBeforeTCKInitiate = startRoutesBeforeTCKInitiate;
            this.maxSentDataCount = maxSentDataCount;
        }

        final String testConfig;
        final boolean startRoutesBeforeTCKInitiate;
        final int maxSentDataCount;
    }

    @ParameterizedTest
    @EnumSource
    public void tckSessionTest(EdgeNodeTestProfile profile) throws Exception {

        CamelContext context = camelContextExtension.getContext();

        assertTrue(context::isStopped, () -> "CamelContext is stopped");

        // Manually controlling context startup to ensure initiating the TCK test occurs
        // in the required order

        if (profile.startRoutesBeforeTCKInitiate) {
            startContext();
        }

        spTckService.initiateTckTest(profile.testConfig);

        if (!profile.startRoutesBeforeTCKInitiate) {
            startContext();
        }

        ProducerTemplate template = camelContextExtension.getProducerTemplate();

        Instant timeout = Instant.now().plusSeconds(15L);

        int sentDataCount = 0;
        while (sentDataCount < profile.maxSentDataCount && Instant.now().isBefore(timeout)) {

            template.sendBody(TahuEdgeProducerRouteBuilder.NODE_DATA_URI, null);
            template.sendBody(TahuEdgeProducerRouteBuilder.DEVICE_DATA_URI, null);

            sentDataCount += 1;

            if (spTckService.spTckResultMockNotify.matches(1L, TimeUnit.SECONDS)) {
                break;
            }
        }

        if (profile == EdgeNodeTestProfile.SESSION_TERMINATION_TEST) {
            stopContext();
        }

        spTckService.spTckResultMockEndpoint.assertIsSatisfied();
    }

    protected void configureComponent(CamelContext context, TahuConfiguration tahuConfig) {
        TahuEdgeComponent tahuComponent = context.getComponent(TahuConstants.EDGE_NODE_SCHEME, TahuEdgeComponent.class);
        tahuComponent.setConfiguration(tahuConfig);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new TahuEdgeProducerRouteBuilder(context));
    }
}
