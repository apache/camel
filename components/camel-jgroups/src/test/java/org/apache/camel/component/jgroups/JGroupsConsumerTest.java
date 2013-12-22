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
package org.apache.camel.component.jgroups;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.junit.Test;

public class JGroupsConsumerTest extends CamelTestSupport {

    // Fixtures

    String clusterName = "clusterName";

    String mockEndpoint = "mock:test";

    String message = "message";

    JChannel channel;

    // Routes fixture

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
    public void tearDown() throws Exception {
        channel.close();
        super.tearDown();
    }

    @Test
    public void shouldConsumeMulticastedMessage() throws Exception {
        // Given
        MockEndpoint mock = getMockEndpoint(mockEndpoint);
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(message);

        // When
        channel.send(new Message(null, null, message));

        // Then
        mock.assertIsSatisfied();
    }

}
