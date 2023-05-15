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
import org.apache.camel.component.nats.NatsConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class NatsConsumerReplyToIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @EndpointInject("mock:reply")
    protected MockEndpoint mockReplyEndpoint;

    @Test
    public void testReplyTo() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("World");
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "test");
        mockReplyEndpoint.expectedBodiesReceived("Bye World");
        mockReplyEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "myReplyQueue");

        template.sendBody("direct:send", "World");

        mockResultEndpoint.assertIsSatisfied();
        mockReplyEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("nats:test?replySubject=myReplyQueue&flushConnection=true");

                from("nats:test?flushConnection=true")
                        .to(mockResultEndpoint)
                        .convertBodyTo(String.class)
                        .setBody().simple("Bye ${body}");

                from("nats:myReplyQueue")
                        .to("mock:reply");
            }
        };
    }
}
