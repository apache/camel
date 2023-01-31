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
package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 *
 */
public class DynamicRoutersWithJMSMessageLostHeadersIssueTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/inbox");
        deleteDirectory("target/outbox");
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
                from("activemq:queue1")
                        .setHeader("HEADER1", constant("header1"))
                        .dynamicRouter(method(DynamicRouter.class, "dynamicRoute"))
                        .to("mock:checkHeader");

                from("direct:foo")
                        .setHeader("HEADER1", constant("header1"))
                        .dynamicRouter(method(DynamicRouter.class, "dynamicRoute"))
                        .to("mock:checkHeader");
            }
        };
    }

    @Test
    public void testHeaderShouldExisted() throws InterruptedException {
        // direct
        getMockEndpoint("mock:checkHeader").expectedMessageCount(1);
        getMockEndpoint("mock:checkHeader").expectedHeaderReceived("HEADER1", "header1");

        template.sendBody("direct:foo", "A");

        MockEndpoint.assertIsSatisfied(context);
        MockEndpoint.resetMocks(context);

        // actvivemq
        getMockEndpoint("mock:checkHeader").expectedMessageCount(1);
        getMockEndpoint("mock:checkHeader").expectedHeaderReceived("HEADER1", "header1");

        template.sendBody("activemq:queue1", "A");

        MockEndpoint.assertIsSatisfied(context);
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

    public static class DynamicRouter {

        public String dynamicRoute(Exchange exchange, @Header(Exchange.SLIP_ENDPOINT) String previous) {
            if (previous == null) {
                return "file://target/outbox";
            } else {
                //end slip
                return null;
            }
        }

    }
}
