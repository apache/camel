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
package org.apache.camel.component.atomix.client.messaging;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Component;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;


public class AtomixMessagingTest extends AtomixClientTestSupport {
    private static final String NODE_NAME = UUID.randomUUID().toString();

    @EndpointInject(uri = "direct:start")
    private FluentProducerTemplate template;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixMessagingComponent component = new AtomixMessagingComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-messaging", component);
    }

    // ************************************
    // Test
    // ************************************

    @Test
    public void testMessaging() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:member-1");
        mock1.expectedMessageCount(2);
        mock1.expectedBodiesReceived("direct-message", "broadcast-message");

        MockEndpoint mock2 = getMockEndpoint("mock:member-2");
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived("broadcast-message");

        template.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMessaging.Action.DIRECT)
            .withHeader(AtomixClientConstants.MEMBER_NAME, "member-1")
            .withHeader(AtomixClientConstants.CHANNEL_NAME, "channel")
            .withBody("direct-message")
            .send();

        template.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMessaging.Action.BROADCAST)
            .withHeader(AtomixClientConstants.CHANNEL_NAME, "channel")
            .withBody("direct-message")
            .send();
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("atomix-messaging:group");

                from("atomix-messaging:group?memberName=member-1&channelName=channel")
                    .to("mock:member-1");
                from("atomix-messaging:group?memberName=member-2&channelName=channel")
                    .to("mock:member-2");
            }
        };
    }
}
