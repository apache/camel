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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test setting null body
 */
public class JmsMessageBodySetNullTest extends CamelTestSupport {

    @Test
    public void testSetNullBodyUsingProcessor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:foo")
                    .to("mock:foo")
                    .process(exchange -> exchange.getIn().setBody(null))
                    .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetNullBodyUsingProcessorPreserveHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:foo")
                    .to("mock:foo")
                    .process(exchange -> exchange.getIn().setBody(null))
                    .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("code", 123);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();
        getMockEndpoint("mock:bar").expectedHeaderReceived("code", 123);

        template.sendBodyAndHeader("jms:queue:foo", "Hello World", "code", 123);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetNullBodyUsingSetBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:foo")
                    .to("mock:foo")
                    .setBody(constant(null))
                    .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetNullBodyUsingSetBodyPreserveHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:foo")
                    .to("mock:foo")
                    .setBody(constant(null))
                    .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("code", 123);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();
        getMockEndpoint("mock:bar").expectedHeaderReceived("code", 123);

        template.sendBodyAndHeader("jms:queue:foo", "Hello World", "code", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}

