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
package org.apache.camel.component.sjms.consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

public class InOnlyTopicDurableConsumerTest extends JmsTestSupport {

    private static final String CONNECTION_ID = "test-connection-1";

    @Test
    public void testDurableTopic() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        // wait a bit and send the message
        Thread.sleep(1000);

        template.sendBody("sjms:topic:foo.topic.InOnlyTopicDurableConsumerTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SjmsComponent sjms = context.getComponent("sjms", SjmsComponent.class);

        // need to use a pooled CF so we reuse same connection for multiple client connections
        JmsPoolConnectionFactory pcf = new JmsPoolConnectionFactory();
        pcf.setConnectionFactory(sjms.getConnectionFactory());
        sjms.setConnectionFactory(pcf);
        sjms.setClientId(CONNECTION_ID);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:topic:foo.topic.InOnlyTopicDurableConsumerTest?durableSubscriptionName=bar1")
                        .to("mock:result");

                from("sjms:topic:foo.topic.InOnlyTopicDurableConsumerTest?durableSubscriptionName=bar2")
                        .to("mock:result2");
            }
        };
    }
}
