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
package org.apache.camel.component.nats.jetstream;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsConstants;
import org.apache.camel.component.nats.integration.NatsITSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
@Isolated
public class NatsJetstreamConsumerRedeliveryIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @EndpointInject("mock:input")
    protected MockEndpoint mockInputEndpoint;

    @Test
    public void testConsumer() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("Hello World");
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "mytopic2");
        mockResultEndpoint.expectedHeaderReceived("counter", 3);
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_DELIVERY_COUNTER, 3);

        mockInputEndpoint.expectedMessageCount(3);
        mockInputEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "mytopic2");
        mockInputEndpoint.message(0).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(1);
        mockInputEndpoint.message(1).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(2);
        mockInputEndpoint.message(2).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(3);

        template.sendBody("direct:send", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri
                        = "nats:mytopic2?jetstreamEnabled=true&jetstreamName=mystream2&jetstreamAsync=false&durableName=camel2&pullSubscription=false&nackWait=10";

                from("direct:send")
                        // when running full test suite then send can fail due to nats server setup/teardown
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(uri);

                final AtomicInteger counter = new AtomicInteger();
                from(uri)
                        .to("mock:input")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (counter.incrementAndGet() < 3) {
                                    throw new IllegalArgumentException("Forced");
                                }
                                exchange.getMessage().setHeader("counter", counter.intValue());
                            }
                        })
                        .to(mockResultEndpoint);
            }
        };
    }
}
