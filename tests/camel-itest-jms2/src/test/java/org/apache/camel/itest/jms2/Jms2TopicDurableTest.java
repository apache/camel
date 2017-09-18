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
package org.apache.camel.itest.jms2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class Jms2TopicDurableTest extends BaseJms2TestSupport {

    // Jms1TopicDurableTest and Jms2TopicDurableTest are similar
    // as the test using JMS 1.1 style durable topic which does not
    // use any of the JMS 2.0 APIs but it works on a JMS 2.0 broker as well

    @Test
    public void testDurableTopic() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        // wait a bit and send the message
        Thread.sleep(500);

        template.sendBody("jms:topic:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:topic:foo?clientId=123&durableSubscriptionName=one")
                    .to("log:test.log.1?showBody=true")
                    .to("mock:result");

                from("jms:topic:foo?clientId=456&durableSubscriptionName=two")
                    .to("log:test.log.2?showBody=true")
                    .to("mock:result2");
            }
        };
    }
}
