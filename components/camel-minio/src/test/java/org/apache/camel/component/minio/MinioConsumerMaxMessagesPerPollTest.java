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
package org.apache.camel.component.minio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A poll lists and routes at most maxMessagesPerPoll objects: with more objects in the bucket, the following polls
 * continue after the last listed object until the bucket is drained, and a later poll starts over.
 */
class MinioConsumerMaxMessagesPerPollTest extends CamelTestSupport {

    private static final int OBJECTS = 30;
    private static final int MAX_PER_POLL = 10;

    private final List<Integer> polledCounts = new CopyOnWriteArrayList<>();
    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        for (int i = 1; i <= OBJECTS; i++) {
            s3.put(name(i), "content-" + i);
        }
        s3.start();
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("recordPolls", new DefaultPollingConsumerPollStrategy() {
            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                polledCounts.add(polledMessages);
            }
        });
        return context;
    }

    @AfterEach
    void stopServer() {
        if (s3 != null) {
            s3.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&delay=10&pollStrategy=#recordPolls"
                     + "&maxMessagesPerPoll=" + MAX_PER_POLL + "&deleteAfterRead=false&endpoint=" + s3.endpoint())
                        .to("mock:result");
            }
        };
    }

    @Test
    void pollRoutesAtMostMaxMessagesPerPoll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // a minimum, not an exact count: with deleteAfterRead=false the polls keep cycling through the bucket
        // after it has been drained, so more messages can arrive while the assertions run
        mock.expectedMinimumMessageCount(OBJECTS);
        mock.assertIsSatisfied();

        // the first OBJECTS messages are the whole bucket, in listing order
        List<String> bodies = mock.getExchanges().stream()
                .limit(OBJECTS)
                .map(e -> e.getIn().getBody(String.class))
                .toList();
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= OBJECTS; i++) {
            expected.add("content-" + i);
        }
        assertEquals(expected, bodies);

        // the bucket was drained by three polls of one full page each, continuing after the last listed object,
        // and no poll ever routed more than the limit (a poll that listed nothing is committed with 0)
        List<Integer> nonEmpty = polledCounts.stream().filter(n -> n > 0).toList();
        assertTrue(nonEmpty.size() >= 3, "expected at least three non-empty polls: " + polledCounts);
        assertEquals(List.of(MAX_PER_POLL, MAX_PER_POLL, MAX_PER_POLL), nonEmpty.subList(0, 3),
                "the first polls should each route one full page: " + polledCounts);
        assertTrue(nonEmpty.stream().allMatch(n -> n <= MAX_PER_POLL),
                "no poll should route more than maxMessagesPerPoll objects: " + polledCounts);
    }

    private static String name(int i) {
        return String.format("o%03d.txt", i);
    }
}
