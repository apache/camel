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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.ImmutableValue;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.option.QueryOptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The key/value consumer watches a key with a chain of blocking queries: each answer starts the next query.
 */
public class ConsulKeyValueWatchTest extends CamelTestSupport {

    private static final String KEY = "camel/watch";

    private final KeyValueClient keyValueClient = mock(KeyValueClient.class);
    private final List<ConsulResponseCallback<Optional<Value>>> queries = new CopyOnWriteArrayList<>();

    @BindToRegistry("consul")
    public Consul consul() {
        Consul consul = mock(Consul.class);
        when(consul.keyValueClient()).thenReturn(keyValueClient);
        return consul;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testWatchGoesOnAfterAFailedQuery() throws Exception {
        // the first query fails (for example while the Consul agent restarts), the next ones are pending
        doAnswer(inv -> {
            ConsulResponseCallback<Optional<Value>> callback = inv.getArgument(2);
            queries.add(callback);
            if (queries.size() == 1) {
                callback.onFailure(new ConsulException("Consul is not available"));
            }
            return null;
        }).when(keyValueClient).getValue(eq(KEY), any(QueryOptions.class), any());

        addRoute();
        context.start();

        // the consumer must query the key again
        verify(keyValueClient, timeout(5000).times(2)).getValue(eq(KEY), any(QueryOptions.class), any());

        MockEndpoint mock = getMockEndpoint("mock:kv");
        mock.expectedBodiesReceived("bar");
        lastQuery().onComplete(response("bar", 2));
        mock.assertIsSatisfied();
    }

    private void addRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("consul:kv?key=%s&valueAsString=true&blockSeconds=1&consulClient=#consul", KEY).routeId("kv")
                        .to("mock:kv");
            }
        });
    }

    private ConsulResponseCallback<Optional<Value>> lastQuery() {
        return queries.get(queries.size() - 1);
    }

    private static ConsulResponse<Optional<Value>> response(String value, long index) {
        Value v = ImmutableValue.builder()
                .key(KEY)
                .value(Base64.getEncoder().encodeToString(value.getBytes()))
                .createIndex(1)
                .modifyIndex(index)
                .lockIndex(0)
                .flags(0)
                .build();
        return new ConsulResponse<>(Optional.of(v), 0, true, BigInteger.valueOf(index), (String) null, (String) null);
    }
}
