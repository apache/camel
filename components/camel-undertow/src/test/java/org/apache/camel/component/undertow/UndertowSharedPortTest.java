/**
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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndertowSharedPortTest extends BaseUndertowTest {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowSharedPortTest.class);

    @Test
    public void testFirstPath() throws Exception {
        testPath("first");
    }

    @Test
    public void testSecondPath() throws Exception {
        testPath("second");
    }

    private void testPath(String pathSuffix) throws InterruptedException {
        String response = template.requestBody("undertow://http://localhost:{{port}}/" + pathSuffix, "Hello Camel!", String.class);

        assertNotNull(response);

        assertEquals("Hello Camel!", response);

        MockEndpoint mockEndpoint = getMockEndpoint("mock:" + pathSuffix);
        mockEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");
        LOG.debug("Number of exchanges in mock:" + pathSuffix + mockEndpoint.getExchanges().size());

        for (Exchange exchange : mockEndpoint.getExchanges()) {
            assertEquals("Hello Camel! Bye Camel! " + pathSuffix, exchange.getIn().getBody(String.class));
        }

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/first")
                    .transform().constant("Bye Camel! first")
                    .to("mock:first");

                from("undertow:http://localhost:{{port}}/second")
                    .transform().constant("Bye Camel! second")
                    .to("mock:second");
            }
        };
    }

}
