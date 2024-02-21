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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unit test setting null body
 */
public class JmsMessageBodySetNullTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testSetNullBodyUsingProcessor() throws Exception {
        context.getRouteController().startRoute("testSetNullBodyUsingProcessor");

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:JmsMessageBodySetNullTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingProcessorPreserveHeaders() throws Exception {
        context.getRouteController().startRoute("testSetNullBodyUsingProcessorPreserveHeaders");

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("code", 123);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();
        getMockEndpoint("mock:bar").expectedHeaderReceived("code", 123);

        template.sendBodyAndHeader("jms:queue:JmsMessageBodySetNullTest", "Hello World", "code", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingSetBody() throws Exception {
        context.getRouteController().startRoute("testSetNullBodyUsingSetBody");

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:JmsMessageBodySetNullTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingSetBodyPreserveHeaders() throws Exception {
        context.getRouteController().startRoute("testSetNullBodyUsingSetBodyPreserveHeaders");

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("code", 123);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();
        getMockEndpoint("mock:bar").expectedHeaderReceived("code", 123);

        template.sendBodyAndHeader("jms:queue:JmsMessageBodySetNullTest", "Hello World", "code", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMessageBodySetNullTest")
                        .routeId("testSetNullBodyUsingProcessor")
                        .autoStartup(false)
                        .to("mock:foo")
                        .process(exchange -> exchange.getIn().setBody(null))
                        .to("mock:bar");

                from("jms:queue:JmsMessageBodySetNullTest")
                        .routeId("testSetNullBodyUsingProcessorPreserveHeaders")
                        .autoStartup(false)
                        .to("mock:foo")
                        .process(exchange -> exchange.getIn().setBody(null))
                        .to("mock:bar");

                from("jms:queue:JmsMessageBodySetNullTest")
                        .routeId("testSetNullBodyUsingSetBody")
                        .autoStartup(false)
                        .to("mock:foo")
                        .setBody(simple("${null}"))
                        .to("mock:bar");

                from("jms:queue:JmsMessageBodySetNullTest")
                        .routeId("testSetNullBodyUsingSetBodyPreserveHeaders")
                        .autoStartup(false)
                        .to("mock:foo")
                        .setBody(simple("${null}"))
                        .to("mock:bar");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
