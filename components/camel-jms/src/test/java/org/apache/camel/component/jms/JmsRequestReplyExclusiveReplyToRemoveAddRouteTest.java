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
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsRequestReplyExclusiveReplyToRemoveAddRouteTest extends AbstractJMSTest {

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
                        .to("activemq:queue:foo?replyTo=bar&replyToType=Exclusive")
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
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .to("activemq:queue:foo?replyTo=bar&replyToType=Exclusive")
                        .to("log:start");

                from("activemq:queue:foo").routeId("foo")
                        .transform(body().prepend("Hello "));
            }
        };
    }
}
