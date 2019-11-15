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
package org.apache.camel.component.jgroups;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.junit.After;
import org.junit.Test;

import static org.apache.camel.component.jgroups.JGroupsEndpoint.HEADER_JGROUPS_ORIGINAL_MESSAGE;

public class JGroupsConsumerTest extends CamelTestSupport {

    // Fixtures

    String clusterName = "clusterName";

    String message = "message";

    JChannel channel;

    // Routes fixture

    @EndpointInject("mock:test")
    MockEndpoint mockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jgroups:" + clusterName).to(mockEndpoint);
            }
        };
    }

    // Fixture setup

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        channel = new JChannel();
        channel.connect(clusterName);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        channel.close();
        super.tearDown();
    }

    // Tests

    @Test
    public void shouldConsumeMulticastedMessage() throws Exception {
        // Given
        mockEndpoint.setExpectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived(message);

        // When
        Message msg = new Message(null, message);
        msg.setSrc(null);
        channel.send(msg);

        // Then
        assertMockEndpointsSatisfied();
    }

    @Test
    public void shouldKeepOriginalMessage() throws Exception {
        // Given
        mockEndpoint.setExpectedMessageCount(1);
        mockEndpoint.message(0).header(HEADER_JGROUPS_ORIGINAL_MESSAGE).isInstanceOf(Message.class);

        // When
        Message msg = new Message(null, message);
        msg.setSrc(null);
        channel.send(msg);

        // Then
        assertMockEndpointsSatisfied();
    }

}
