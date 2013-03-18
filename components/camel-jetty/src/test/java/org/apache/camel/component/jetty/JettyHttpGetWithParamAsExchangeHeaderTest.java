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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit test to verify that we can have URI options for external system (endpoint is lenient)
 */
public class JettyHttpGetWithParamAsExchangeHeaderTest extends BaseJettyTest {
    
    private String serverUri = "http://localhost:" + getPort() + "/myservice";

    @Test
    public void testHttpGetWithParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", "einz");
        mock.expectedHeaderReceived("two", "twei");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBody(serverUri + "?one=einz&two=twei", null, Object.class);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHttpGetWithUTF8EncodedParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("message", "Keine g\u00FCltige GPS-Daten!");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBody(serverUri + "?message=Keine%20g%C3%BCltige%20GPS-Daten!", null, Object.class);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    @Ignore
    public void testHttpGetWithISO8859EncodedParamsViaURI() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("message", "Keine g\u00C6ltige GPS-Daten!");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBody(serverUri + "?message=Keine+g%C6ltige+GPS-Daten%21", null, Object.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpGetWithSpaceInParams() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("message", " World");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        // parameter starts with a space using %2B as decimal encoded
        template.requestBody(serverUri + "?message=%2BWorld", null, Object.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpGetWithSpaceAsPlusInParams() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("message", " World");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        // parameter starts with a space using + decoded
        template.requestBody(serverUri + "?message=+World", null, Object.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpGetWithParamsViaHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", "uno");
        mock.expectedHeaderReceived("two", "dos");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBodyAndHeader(serverUri, null, Exchange.HTTP_QUERY, "one=uno&two=dos");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpPost() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        template.requestBody(serverUri, "Hello World");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:" + serverUri).to("mock:result");
            }
        };
    }
}
