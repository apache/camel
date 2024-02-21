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
package org.apache.camel.component.jetty;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiThreadedHttpGetTest extends BaseJettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedHttpGetTest.class);

    @Test
    public void testHttpGetWithConversion() throws Exception {

        // In this scenario response stream is converted to String
        // so the stream has to be read to the end. When this happens
        // the associated connection is released automatically.

        String endpointName = "seda:withConversion?concurrentConsumers=5";
        sendMessagesTo(endpointName, 5);
    }

    @Test
    public void testHttpGetWithoutConversion() throws Exception {

        // This is needed as by default there are 2 parallel
        // connections to some host and there is nothing that
        // closes the http connection here.
        // Need to set the httpConnectionManager
        PoolingHttpClientConnectionManager httpConnectionManager = new PoolingHttpClientConnectionManager();
        httpConnectionManager.setDefaultMaxPerRoute(5);
        context.getComponent("http", HttpComponent.class).setClientConnectionManager(httpConnectionManager);

        String endpointName = "seda:withoutConversion?concurrentConsumers=5";
        sendMessagesTo(endpointName, 5);
    }

    @Test
    public void testHttpGetWithExplicitStreamClose() throws Exception {

        // We close connections explicitely at the very end of the flow
        // (camel doesn't know when the stream is not needed any more)

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);

        for (int i = 0; i < 5; i++) {
            mockEndpoint.expectedMessageCount(1);
            template.sendBody("seda:withoutConversion?concurrentConsumers=5", null);
            mockEndpoint.assertIsSatisfied();
            Object response = mockEndpoint.getReceivedExchanges().get(0).getIn().getBody();
            InputStream responseStream = assertIsInstanceOf(InputStream.class, response);
            responseStream.close();
            mockEndpoint.reset();
        }
    }

    protected void sendMessagesTo(String endpointName, int count) throws InterruptedException {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(endpointName, null);
        }

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            String body = exchange.getIn().getBody(String.class);

            LOG.debug("Body: {}", body);
            assertNotNull(body, "Should have a body!");
            assertTrue(body.contains("<html"), "body should contain: <html");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:withConversion?concurrentConsumers=5")
                        .to("http://localhost:{{port}}/search")
                        .convertBodyTo(String.class).to("mock:results");

                from("seda:withoutConversion?concurrentConsumers=5")
                        .to("http://localhost:{{port}}/search").to("mock:results");

                from("jetty:http://localhost:{{port}}/search").process(new Processor() {
                    public void process(Exchange exchange) {
                        exchange.getMessage().setBody("<html>Bye World</html>");
                    }
                });
            }
        };
    }

}
