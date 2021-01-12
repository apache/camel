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

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class AdviceWithIssueTest extends CamelTestSupport {

    final String pub
            = "activemq:topic:integrations?allowNullBody=false&asyncConsumer=true&concurrentConsumers=10&jmsMessageType=Map&preserveMessageQos=true";
    final String advicedPub = "activemq:topic:integrations";

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testAdviceWith() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinition("starter"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // when advicing then use wildcard as URI options cannot be matched
                mockEndpointsAndSkip(advicedPub + "?*");
            }
        });
        context.start();

        MockEndpoint topicEndpointMock = getMockEndpoint("mock:" + advicedPub);
        topicEndpointMock.expectedMessageCount(1);

        template.sendBody("direct:start", Collections.singletonMap("foo", "bar"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        JmsComponent activemq = JmsComponent.jmsComponent();
        camelContext.addComponent("activemq", activemq);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("starter")
                        .to(pub).to("mock:result");
            }
        };
    }
}
