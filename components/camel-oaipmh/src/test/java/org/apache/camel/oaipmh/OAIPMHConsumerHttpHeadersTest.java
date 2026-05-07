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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.oaipmh.utils.MockOaipmhServer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.awaitility.Awaitility.await;

public class OAIPMHConsumerHttpHeadersTest extends CamelTestSupport {

    private static MockOaipmhServer mockOaipmhServer;

    @BeforeAll
    public static void startServer() {
        mockOaipmhServer = MockOaipmhServer.create();
        mockOaipmhServer.start();
    }

    @AfterAll
    public static void stopServer() {
        mockOaipmhServer.stop();
    }

    @Test
    public void testConsumerSendsCustomHeaders() {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> mockOaipmhServer.getServer().verify(
                getRequestedFor(urlMatching("/oai/request.*"))
                        .withHeader("X-Custom-Header", equalTo("test-value"))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("oaipmh://localhost:" + mockOaipmhServer.getHttpPort()
                     + "/oai/request?initialDelay=100&delay=60000"
                     + "&httpHeader.X-Custom-Header=test-value")
                        .to("mock:result");
            }
        };
    }
}
