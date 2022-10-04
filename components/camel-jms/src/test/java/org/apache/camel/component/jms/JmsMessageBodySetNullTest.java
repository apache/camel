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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test setting null body
 */
public class JmsMessageBodySetNullTest extends AbstractJMSTest {

    @Test
    public void testSetNullBodyUsingProcessor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMessageBodySetNullTest")
                        .to("mock:foo")
                        .process(exchange -> exchange.getIn().setBody(null))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:JmsMessageBodySetNullTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingProcessorPreserveHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMessageBodySetNullTest")
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

        template.sendBodyAndHeader("jms:queue:JmsMessageBodySetNullTest", "Hello World", "code", 123);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingSetBody() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMessageBodySetNullTest")
                        .to("mock:foo")
                        .setBody(simple("${null}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").message(0).body().isNull();

        template.sendBody("jms:queue:JmsMessageBodySetNullTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetNullBodyUsingSetBodyPreserveHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMessageBodySetNullTest")
                        .to("mock:foo")
                        .setBody(simple("${null}"))
                        .to("mock:bar");
            }
        });
        context.start();

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
    public boolean isUseRouteBuilder() {
        return false;
    }
}
