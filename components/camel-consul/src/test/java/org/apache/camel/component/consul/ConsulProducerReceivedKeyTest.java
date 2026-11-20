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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.EventClient;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.async.EventResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.EventResponse;
import org.kiwiproject.consul.model.ImmutableEventResponse;
import org.kiwiproject.consul.model.event.ImmutableEvent;
import org.kiwiproject.consul.model.kv.ImmutableValue;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.option.EventOptions;
import org.kiwiproject.consul.option.PutOptions;
import org.kiwiproject.consul.option.QueryOptions;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The key/value and event consumers set the CamelConsulKey header to the key they received. A producer with a key of
 * its own must act on that key, not on the key the message came from, unless the route chose another key.
 */
class ConsulProducerReceivedKeyTest extends CamelTestSupport {

    private final KeyValueClient keyValueClient = mock(KeyValueClient.class);
    private final EventClient eventClient = mock(EventClient.class);
    private final List<ConsulResponseCallback<Optional<Value>>> valueQueries = new CopyOnWriteArrayList<>();
    private final List<ConsulResponseCallback<List<Value>>> valuesQueries = new CopyOnWriteArrayList<>();
    private final List<EventResponseCallback> eventQueries = new CopyOnWriteArrayList<>();

    @BindToRegistry("consul")
    public Consul consul() {
        Consul consul = mock(Consul.class);
        when(consul.keyValueClient()).thenReturn(keyValueClient);
        when(consul.eventClient()).thenReturn(eventClient);
        // the blocking queries of the consumers stay pending until the test answers them
        doAnswer(inv -> valueQueries.add(inv.getArgument(2))).when(keyValueClient)
                .getValue(anyString(), any(QueryOptions.class), any());
        doAnswer(inv -> valuesQueries.add(inv.getArgument(2))).when(keyValueClient)
                .getValues(anyString(), any(QueryOptions.class), any());
        doAnswer(inv -> eventQueries.add(inv.getArgument(2))).when(eventClient)
                .listEvents(anyString(), any(QueryOptions.class), any(EventResponseCallback.class));
        return consul;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void putWritesTheKeyOfTheEndpoint() throws Exception {
        route("consul:kv?key=camel/source&valueAsString=true&blockSeconds=1&consulClient=#consul",
                "consul:kv?key=camel/target&action=PUT&consulClient=#consul");

        await().until(() -> !valueQueries.isEmpty());
        valueQueries.get(0).onComplete(valueResponse("camel/source", "bar"));

        verify(keyValueClient, timeout(5000)).putValue(eq("camel/target"), eq("bar"), anyLong(), any(PutOptions.class));
        verify(keyValueClient, never()).putValue(eq("camel/source"), anyString(), anyLong(), any(PutOptions.class));
    }

    @Test
    void deleteKeyDeletesTheKeyOfTheEndpoint() throws Exception {
        route("consul:kv?key=camel/source&valueAsString=true&blockSeconds=1&consulClient=#consul",
                "consul:kv?key=camel/target&action=DELETE_KEY&consulClient=#consul");

        await().until(() -> !valueQueries.isEmpty());
        valueQueries.get(0).onComplete(valueResponse("camel/source", "bar"));

        verify(keyValueClient, timeout(5000)).deleteKey("camel/target");
        verify(keyValueClient, never()).deleteKey("camel/source");
    }

    @Test
    void fireFiresTheEventOfTheEndpoint() throws Exception {
        route("consul:event?key=camel-source&blockSeconds=1&consulClient=#consul",
                "consul:event?key=camel-target&action=FIRE&consulClient=#consul");

        await().until(() -> !eventQueries.isEmpty());
        eventQueries.get(0).onComplete(eventResponse("camel-source", "bar"));

        verify(eventClient, timeout(5000)).fireEvent(eq("camel-target"), any(EventOptions.class), eq("bar"));
        verify(eventClient, never()).fireEvent(eq("camel-source"), any(EventOptions.class), anyString());
    }

    @Test
    void producerWithoutKeyActsOnTheReceivedKey() throws Exception {
        // a recursive watch of a folder, each changed key deleted once it is processed
        route("consul:kv?key=camel/jobs&recursive=true&valueAsString=true&blockSeconds=1&consulClient=#consul",
                "consul:kv?action=DELETE_KEY&consulClient=#consul");

        await().until(() -> !valuesQueries.isEmpty());
        valuesQueries.get(0).onComplete(new ConsulResponse<>(
                List.of(value("camel/jobs/1", "bar")), 0, true, BigInteger.TWO, (String) null, (String) null));

        verify(keyValueClient, timeout(5000)).deleteKey("camel/jobs/1");
    }

    @Test
    void keyChosenByTheRouteIsUsed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("consul:kv?key=camel/source&valueAsString=true&blockSeconds=1&consulClient=#consul")
                        .setHeader(ConsulConstants.CONSUL_KEY, constant("camel/other"))
                        .to("consul:kv?key=camel/target&action=PUT&consulClient=#consul");
            }
        });
        context.start();

        await().until(() -> !valueQueries.isEmpty());
        valueQueries.get(0).onComplete(valueResponse("camel/source", "bar"));

        verify(keyValueClient, timeout(5000)).putValue(eq("camel/other"), eq("bar"), anyLong(), any(PutOptions.class));
    }

    private void route(String from, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).to(to);
            }
        });
        context.start();
    }

    private static Value value(String key, String value) {
        return ImmutableValue.builder()
                .key(key)
                .value(Base64.getEncoder().encodeToString(value.getBytes()))
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(0)
                .flags(0)
                .build();
    }

    private static ConsulResponse<Optional<Value>> valueResponse(String key, String value) {
        return new ConsulResponse<>(Optional.of(value(key, value)), 0, true, BigInteger.TWO, (String) null, (String) null);
    }

    private static EventResponse eventResponse(String name, String payload) {
        return ImmutableEventResponse.builder()
                .addEvents(ImmutableEvent.builder()
                        .id("2a7d7cbc-d6ad-4d5e-8e3c-4b36a54f6bb7")
                        .name(name)
                        .payload(payload)
                        .version(1)
                        .lTime(1L)
                        .build())
                .index(BigInteger.ONE)
                .build();
    }
}
