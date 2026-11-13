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
package org.apache.camel.oaipmh;

import java.util.List;

import org.apache.camel.oaipmh.component.OAIPMHEndpoint;
import org.apache.camel.oaipmh.component.model.OAIPMHConstants;
import org.apache.camel.oaipmh.utils.MockOaipmhServer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CamelOaimphOnlyFirst header applies to the exchange that has it, not to the later exchanges sent to the same
 * (shared) endpoint.
 */
class OAIPMHProducerHeadersPerExchangeTest extends CamelTestSupport {

    private static MockOaipmhServer mockOaipmhServer;

    @BeforeAll
    static void startServer() {
        mockOaipmhServer = MockOaipmhServer.create();
        mockOaipmhServer.start();
    }

    @AfterAll
    static void stopServer() {
        mockOaipmhServer.stop();
    }

    @Test
    void onlyFirstHeaderAppliesToItsExchangeOnly() {
        String uri = "oaipmh://localhost:" + mockOaipmhServer.getHttpPort() + "/oai/request?from=2020-06-01T00:00:00Z";

        List<?> allPages = template.requestBody(uri, null, List.class);
        List<?> firstPage = template.requestBodyAndHeader(uri, null, OAIPMHConstants.ONLY_FIRST, true, List.class);
        List<?> nextAllPages = template.requestBody(uri, null, List.class);

        assertTrue(allPages.size() > 1, "all the pages: " + allPages.size());
        assertEquals(1, firstPage.size());
        assertEquals(allPages.size(), nextAllPages.size(), "an exchange without the header harvests all the pages");
        assertFalse(context.getEndpoint(uri, OAIPMHEndpoint.class).isOnlyFirst());
    }

    @Test
    void onlyFirstHeaderFalseOverridesTheEndpointOption() {
        String uri = "oaipmh://localhost:" + mockOaipmhServer.getHttpPort()
                     + "/oai/request?from=2020-06-01T00:00:00Z&onlyFirst=true";

        List<?> allPages = template.requestBodyAndHeader(uri, null, OAIPMHConstants.ONLY_FIRST, false, List.class);
        List<?> firstPage = template.requestBody(uri, null, List.class);

        assertTrue(allPages.size() > 1, "a header of false harvests all the pages: " + allPages.size());
        assertEquals(1, firstPage.size(), "an exchange without the header uses the endpoint option");
        assertTrue(context.getEndpoint(uri, OAIPMHEndpoint.class).isOnlyFirst());
    }
}
