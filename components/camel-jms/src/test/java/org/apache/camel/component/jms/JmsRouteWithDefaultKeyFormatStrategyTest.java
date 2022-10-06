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
package org.apache.camel.component.jms;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class JmsRouteWithDefaultKeyFormatStrategyTest extends AbstractJMSTest {

    protected String getUri() {
        return "activemq:queue:JmsRouteWithDefaultKeyFormatStrategyTest?jmsKeyFormatStrategy=default";
    }

    @Test
    public void testIllegalOption() {
        try {
            context.getEndpoint("activemq:queue:bar?jmsHeaderStrategy=xxx");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    @Test
    public void testNoHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testWithPlainHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "cheese");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "cheese");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testWithMixedHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "cheese");
        mock.expectedHeaderReceived("Content-Type", "text/plain");
        mock.expectedHeaderReceived("org.apache.camel.MyKey", "foo");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "cheese");
        headers.put("Content-Type", "text/plain");
        headers.put("org.apache.camel.MyKey", "foo");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(getUri());

                from(getUri()).to("mock:result");
            }
        };
    }
}
