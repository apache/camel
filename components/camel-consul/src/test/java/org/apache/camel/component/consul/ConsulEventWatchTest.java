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
package org.apache.camel.component.consul;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.EventClient;
import org.kiwiproject.consul.async.EventResponseCallback;
import org.kiwiproject.consul.model.EventResponse;
import org.kiwiproject.consul.model.ImmutableEventResponse;
import org.kiwiproject.consul.model.event.ImmutableEvent;
import org.kiwiproject.consul.option.QueryOptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The event consumer watches the events with a chain of blocking queries: each answer schedules the next query.
 */
public class ConsulEventWatchTest extends CamelTestSupport {

    private static final String EVENT = "camel-watch";

    private final EventClient eventClient = mock(EventClient.class);
    private final List<EventResponseCallback> queries = new CopyOnWriteArrayList<>();

    @BindToRegistry("consul")
    public Consul consul() {
        Consul consul = mock(Consul.class);
        when(consul.eventClient()).thenReturn(eventClient);
        return consul;
    }

    @Test
    public void testWatchGoesOnAfterAFailedQuery() throws Exception {
        // the first query fails (for example while the Consul agent restarts), the next ones are pending
        doAnswer(inv -> {
            EventResponseCallback callback = inv.getArgument(2);
            queries.add(callback);
            if (queries.size() == 1) {
                callback.onFailure(new ConsulException("Consul is not available"));
            }
            return null;
        }).when(eventClient).listEvents(eq(EVENT), any(QueryOptions.class), any(EventResponseCallback.class));

        // the consumer must query the events again
        verify(eventClient, timeout(5000).times(2)).listEvents(eq(EVENT), any(QueryOptions.class),
                any(EventResponseCallback.class));

        MockEndpoint mock = getMockEndpoint("mock:event");
        mock.expectedBodiesReceived("bar");
        queries.get(1).onComplete(response("bar"));
        mock.assertIsSatisfied();
    }

    private static EventResponse response(String payload) {
        return ImmutableEventResponse.builder()
                .addEvents(ImmutableEvent.builder()
                        .id("2a7d7cbc-d6ad-4d5e-8e3c-4b36a54f6bb7")
                        .name(EVENT)
                        .payload(payload)
                        .version(1)
                        .lTime(1L)
                        .build())
                .index(BigInteger.ONE)
                .build();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("consul:event?key=%s&blockSeconds=1&consulClient=#consul", EVENT)
                        .to("mock:event");
            }
        };
    }
}
