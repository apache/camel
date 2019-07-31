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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndertowComponentTest extends BaseUndertowTest {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowComponentTest.class);

    @Test
    public void testUndertow() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:myapp");
        mockEndpoint.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        LOG.debug("Number of exchanges in mock:myapp " + mockEndpoint.getExchanges().size());

        String response = template.requestBody("undertow:http://localhost:{{port}}/myapp", "Hello Camel!", String.class);
        assertNotNull(response);
        assertEquals("Bye Camel!", response);

        mockEndpoint.assertIsSatisfied();

        for (Exchange exchange : mockEndpoint.getExchanges()) {
            assertEquals("Bye Camel!", exchange.getIn().getBody(String.class));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/myapp")
                    .transform().constant("Bye Camel!")
                    .to("mock:myapp");
            }
        };
    }


}
