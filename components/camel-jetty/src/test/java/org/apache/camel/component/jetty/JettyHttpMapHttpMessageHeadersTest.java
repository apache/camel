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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JettyHttpMapHttpMessageHeadersTest extends BaseJettyTest {

    private final String serverUriFiltered = "http://localhost:" + getPort() + "/myservice";
    private final String serverUriNotFiltered = "http://localhost:" + getPort() + "/myservice1";

    @Test
    public void testHttpGetWithParamsViaURIFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", null);
        mock.expectedHeaderReceived("two", null);
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBody(serverUriFiltered + "?one=einz&two=twei", null, Object.class);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetWithParamsViaURINotFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", "einz");
        mock.expectedHeaderReceived("two", "twei");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBody(serverUriNotFiltered + "?one=einz&two=twei", null, Object.class);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetWithParamsViaHeaderFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", null);
        mock.expectedHeaderReceived("two", null);
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBodyAndHeader(serverUriFiltered, null, Exchange.HTTP_QUERY, "one=uno&two=dos");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpGetWithParamsViaHeaderNotFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("one", "uno");
        mock.expectedHeaderReceived("two", "dos");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        template.requestBodyAndHeader(serverUriNotFiltered, null, Exchange.HTTP_QUERY, "one=uno&two=dos");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpPostNotFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        mock.expectedHeaderReceived("header1", "pippo");

        template.requestBodyAndHeader(serverUriNotFiltered, "Hello World", "header1", "pippo");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpPostFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        mock.expectedHeaderReceived("header1", null);

        template.requestBodyAndHeader(serverUriFiltered, "Hello World", "header1", "pippo");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:" + serverUriFiltered + "?mapHttpMessageHeaders=false").to("mock:result");
                from("jetty:" + serverUriNotFiltered).to("mock:result1");
            }
        };
    }
}
