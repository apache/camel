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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsConstants;
import org.apache.camel.component.nats.NatsManualAck;
import org.apache.camel.component.nats.integration.NatsITSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Isolated
public class NatsJetstreamConsumerManualAckIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @EndpointInject("mock:input")
    protected MockEndpoint mockInputEndpoint;

    @Test
    public void testManualAck() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("Hello World");
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "mytopic-manualack");

        template.sendBody("direct:send", "Hello World");

        mockResultEndpoint.setAssertPeriod(5000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testManualNakWithDelay() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("Hello World");
        mockResultEndpoint.expectedHeaderReceived("counter", 2);
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_DELIVERY_COUNTER, 2);

        mockInputEndpoint.expectedMessageCount(2);
        mockInputEndpoint.message(0).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(1);
        mockInputEndpoint.message(1).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(2);

        template.sendBody("direct:send-nakdelay", "Hello World");

        mockResultEndpoint.setAssertPeriod(5000);
        mockInputEndpoint.setAssertPeriod(5000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testManualTerm() throws Exception {
        mockInputEndpoint.expectedMessageCount(1);
        mockInputEndpoint.expectedHeaderReceived(NatsConstants.NATS_DELIVERY_COUNTER, 1);

        template.sendBody("direct:send-term", "Hello World");

        mockInputEndpoint.setAssertPeriod(5000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testManualNak() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("Hello World");
        mockResultEndpoint.expectedHeaderReceived("counter", 2);
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_DELIVERY_COUNTER, 2);

        mockInputEndpoint.expectedMessageCount(2);
        mockInputEndpoint.message(0).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(1);
        mockInputEndpoint.message(1).header(NatsConstants.NATS_DELIVERY_COUNTER).isEqualTo(2);

        template.sendBody("direct:send-nak", "Hello World");

        mockResultEndpoint.setAssertPeriod(5000);
        mockInputEndpoint.setAssertPeriod(5000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String ackUri
                        = "nats:mytopic-manualack?jetstreamEnabled=true&jetstreamName=mystream-manualack&jetstreamAsync=false&durableName=camel-manualack&pullSubscription=false&manualAck=true";
                String nakUri
                        = "nats:mytopic-manualack-nak?jetstreamEnabled=true&jetstreamName=mystream-manualack-nak&jetstreamAsync=false&durableName=camel-manualack-nak&pullSubscription=false&manualAck=true&nackWait=10";
                String nakDelayUri
                        = "nats:mytopic-manualack-nakdelay?jetstreamEnabled=true&jetstreamName=mystream-manualack-nakdelay&jetstreamAsync=false&durableName=camel-manualack-nakdelay&pullSubscription=false&manualAck=true";
                String termUri
                        = "nats:mytopic-manualack-term?jetstreamEnabled=true&jetstreamName=mystream-manualack-term&jetstreamAsync=false&durableName=camel-manualack-term&pullSubscription=false&manualAck=true&nackWait=10&maxDeliver=5";

                from("direct:send")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(ackUri);

                from(ackUri)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                NatsManualAck manualAck
                                        = exchange.getIn().getHeader(NatsConstants.NATS_MANUAL_ACK, NatsManualAck.class);
                                assertNotNull(manualAck);
                                assertInstanceOf(NatsManualAck.class, manualAck);
                                manualAck.ack();
                            }
                        })
                        .to(mockResultEndpoint);

                from("direct:send-nakdelay")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(nakDelayUri);

                final AtomicInteger nakDelayCounter = new AtomicInteger();
                from(nakDelayUri)
                        .to("mock:input")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                NatsManualAck manualAck
                                        = exchange.getIn().getHeader(NatsConstants.NATS_MANUAL_ACK, NatsManualAck.class);
                                if (nakDelayCounter.incrementAndGet() < 2) {
                                    manualAck.nakWithDelay(Duration.ofMillis(100));
                                    exchange.setRouteStop(true);
                                } else {
                                    manualAck.ack();
                                    exchange.getMessage().setHeader("counter", nakDelayCounter.intValue());
                                }
                            }
                        })
                        .to(mockResultEndpoint);

                from("direct:send-term")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(termUri);

                from(termUri)
                        .to("mock:input")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                NatsManualAck manualAck
                                        = exchange.getIn().getHeader(NatsConstants.NATS_MANUAL_ACK, NatsManualAck.class);
                                manualAck.term();
                                exchange.setRouteStop(true);
                            }
                        });

                from("direct:send-nak")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(nakUri);

                final AtomicInteger counter = new AtomicInteger();
                from(nakUri)
                        .to("mock:input")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                NatsManualAck manualAck
                                        = exchange.getIn().getHeader(NatsConstants.NATS_MANUAL_ACK, NatsManualAck.class);
                                if (counter.incrementAndGet() < 2) {
                                    manualAck.nak();
                                    exchange.setRouteStop(true);
                                } else {
                                    manualAck.ack();
                                    exchange.getMessage().setHeader("counter", counter.intValue());
                                }
                            }
                        })
                        .to(mockResultEndpoint);
            }
        };
    }
}
