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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsComponent;
import org.apache.camel.component.nats.integration.NatsITSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server deletes the ephemeral consumer of a pull subscription while the route is busy with a message, so that no
 * pull request is waiting and no 409 Consumer Deleted arrives: the consumer must find out that its consumer is gone and
 * subscribe again after the message it routed.
 */
@Isolated
class NatsJetstreamConsumerPullDeletedIT extends NatsITSupport {

    private static final String STREAM = "mystream-pull-deleted";

    private final CountDownLatch busy = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @EndpointInject("mock:result")
    MockEndpoint mockResultEndpoint;

    @Test
    void consumesAgainAfterTheEphemeralConsumerWasDeleted() throws Exception {
        waitForNatsConsumers(1);
        mockResultEndpoint.expectedBodiesReceived("order-1", "order-2");

        template.sendBody("direct:send", "order-1");
        assertTrue(busy.await(20, TimeUnit.SECONDS), "order-1 is routed");

        String[] servers = Arrays.stream(
                context.getComponent("nats", NatsComponent.class).getConfiguration().getServers().split(","))
                .map(server -> server.contains("://") ? server : "nats://" + server)
                .toArray(String[]::new);
        try (Connection connection = Nats.connect(new Options.Builder().servers(servers).build())) {
            JetStreamManagement jsm = connection.jetStreamManagement();
            List<String> consumers = jsm.getConsumerNames(STREAM);
            assertEquals(1, consumers.size(), "the ephemeral consumer of the route");
            jsm.deleteConsumer(STREAM, consumers.get(0));
        }
        release.countDown();

        template.sendBody("direct:send", "order-2");
        mockResultEndpoint.setResultWaitTime(30_000);
        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String producerUri = "nats:mytopic-pull-deleted?jetstreamEnabled=true&jetstreamName=" + STREAM
                                     + "&jetstreamAsync=false";
                String consumerUri = "nats:mytopic-pull-deleted?jetstreamEnabled=true&jetstreamName=" + STREAM
                                     + "&pullSubscription=true&pullFetchTimeout=500";

                from("direct:send")
                        // when running full test suite then send can fail due to nats server setup/teardown
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(producerUri);

                from(consumerUri)
                        .convertBodyTo(String.class)
                        .process(exchange -> {
                            if ("order-1".equals(exchange.getIn().getBody(String.class))) {
                                busy.countDown();
                                release.await(30, TimeUnit.SECONDS);
                            }
                        })
                        .to(mockResultEndpoint);
            }
        };
    }
}
