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
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsRequestReplyExclusiveReplyToRemoveAddRouteTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsRequestReplyExclusiveFixedReplyTo() throws Exception {
        assertEquals("Hello A", template.requestBody("direct:start", "A"));

        // stop and remove route
        context.getRouteController().stopRoute("start");
        context.removeRoute("start");

        // add new route using same jms endpoint uri
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start2").routeId("start2")
                        .to("activemq:queue:JmsRequestReplyExclusiveReplyToRemoveAddRouteTest?replyTo=JmsRequestReplyExclusiveReplyToRemoveAddRouteTest.reply&replyToType=Exclusive")
                        .to("log:start2");
            }
        });
        // and it should still work

        assertEquals("Hello B", template.requestBody("direct:start2", "B"));
        assertEquals("Hello C", template.requestBody("direct:start2", "C"));
        assertEquals("Hello D", template.requestBody("direct:start2", "D"));
        assertEquals("Hello E", template.requestBody("direct:start2", "E"));
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
                from("direct:start").routeId("start")
                        .to("activemq:queue:JmsRequestReplyExclusiveReplyToRemoveAddRouteTest?replyTo=JmsRequestReplyExclusiveReplyToRemoveAddRouteTest.reply&replyToType=Exclusive")
                        .to("log:start");

                from("activemq:queue:JmsRequestReplyExclusiveReplyToRemoveAddRouteTest").routeId("foo")
                        .transform(body().prepend("Hello "));
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
