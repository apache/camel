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
package org.apache.camel.component.aws2.s3vectors;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With deleteAfterRead the consumer keeps the vectors whose exchanges are in flight: such a vector is not consumed
 * again by the next poll, and a vector whose exchange failed is consumed again.
 */
class AWS2S3VectorsConsumerInProgressTest extends CamelTestSupport {

    private final Set<String> vectors = new ConcurrentSkipListSet<>();
    private final AtomicInteger consumed = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        S3VectorsClient client = Mockito.mock(S3VectorsClient.class);
        Mockito.when(client.queryVectors(Mockito.any(QueryVectorsRequest.class))).thenAnswer(i -> QueryVectorsResponse
                .builder()
                .vectors(vectors.stream().map(key -> QueryOutputVector.builder().key(key).build()).toList())
                .build());
        Mockito.when(client.deleteVectors(Mockito.any(DeleteVectorsRequest.class))).thenAnswer(i -> {
            i.<DeleteVectorsRequest> getArgument(0).keys().forEach(vectors::remove);
            return DeleteVectorsResponse.builder().build();
        });
        context.getRegistry().bind("client", client);
        // counts the polls that completed (the exchanges of the poll were handed to the route)
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                twoPolls.countDown();
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                return false;
            }
        });
        return context;
    }

    @AfterEach
    void releaseRoute() {
        release.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands the on completions (the delete) over to this route
                from("seda:hold")
                        .process(e -> {
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");

                from("seda:failOnce")
                        .process(e -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("first attempt fails");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void vectorInFlightIsNotConsumedAgain() throws Exception {
        vectors.add("v1");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(AWS2S3VectorsConstants.VECTOR_ID, "v1");

        addConsumerRoute("&pollStrategy=#countPolls", "seda:hold");

        assertThat(inRoute.await(20, TimeUnit.SECONDS)).as("the vector was not consumed").isTrue();
        // the second poll has completed while the first exchange is still held in the seda route
        assertThat(twoPolls.await(20, TimeUnit.SECONDS)).as("the consumer did not poll again").isTrue();
        int consumedWhileInFlight = consumed.get();
        release.countDown();

        assertThat(consumedWhileInFlight)
                .as("v1 was consumed again by a later poll while its first exchange was still in flight")
                .isEqualTo(1);
        mock.assertIsSatisfied();
        assertThat(vectors).as("v1 should be deleted after its exchange completed").isEmpty();
    }

    @Test
    void failedVectorIsConsumedAgain() throws Exception {
        vectors.add("v1");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived(AWS2S3VectorsConstants.VECTOR_ID, "v1");

        addConsumerRoute("", "seda:failOnce");

        mock.assertIsSatisfied();
        assertThat(attempts).as("v1 should be consumed again after its first exchange failed").hasValue(2);
    }

    private void addConsumerRoute(String options, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-s3-vectors://bucket?vectorIndexName=index&s3VectorsClient=#client&consumerQueryVector=0.1,0.2"
                     + "&deleteAfterRead=true&initialDelay=0&delay=10" + options)
                        .process(e -> consumed.incrementAndGet())
                        .to(to);
            }
        });
    }
}
