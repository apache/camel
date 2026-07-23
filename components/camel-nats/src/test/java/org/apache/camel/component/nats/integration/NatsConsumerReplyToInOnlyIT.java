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
package org.apache.camel.component.nats.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class NatsConsumerReplyToInOnlyIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @EndpointInject("mock:reply")
    protected MockEndpoint mockReplyEndpoint;

    @Test
    public void testInOnlyNoReply() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("World");
        // reply endpoint should NOT receive any message when exchange pattern is InOnly
        mockReplyEndpoint.expectedMessageCount(0);

        template.sendBody("direct:send", "World");

        mockResultEndpoint.setAssertPeriod(5000);
        mockResultEndpoint.assertIsSatisfied();
        mockReplyEndpoint.setAssertPeriod(2000);
        mockReplyEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("nats:testInOnly?replySubject=myReplyInOnly&flushConnection=true");

                from("nats:testInOnly?flushConnection=true&exchangePattern=InOnly")
                        .to(mockResultEndpoint)
                        .convertBodyTo(String.class)
                        .setBody().simple("Bye ${body}");

                from("nats:myReplyInOnly")
                        .to("mock:reply");
            }
        };
    }
}
