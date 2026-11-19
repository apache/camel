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
package org.apache.camel.component.redis.processor.aggregate.integration;

import java.net.URI;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.redis.processor.aggregate.RedisAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the repository operations with the codec option set to the codec that Camel 4.22.2 and older used by default.
 */
public class RedisAggregationRepositoryCodecIT extends RedisAggregationRepositoryOperationsIT {

    @Override
    protected void configure(RedisAggregationRepository repo) {
        repo.setCodec(new Kryo5Codec());
    }

    @Override
    @Test
    public void testBodyOfClassOutsideTheDeserializationFilterIsNotRead() {
        // with a codec, the deserialization filter does not apply
        RedisAggregationRepository repo = createRepo("deserializationFilterDefault", false);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(URI.create("http://localhost/orders"));
            repo.add(context, "key1", exchange);

            assertEquals(URI.create("http://localhost/orders"), repo.get(context, "key1").getIn().getBody());
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testReadEntriesStoredByOlderVersions() {
        // store the entries as Camel 4.22.2 and older did: with the default codec of the Redisson client
        Exchange active = createExchange("active");
        active.setProperty(Exchange.AGGREGATED_SIZE, 2);
        Exchange completed = createExchange("completed");
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s", service.getServiceAddress()));
        RedissonClient client = Redisson.create(config);
        try {
            client.getMap("legacy").put("key1", DefaultExchangeHolder.marshal(active, true, false));
            client.getMap("legacy-completed").put(completed.getExchangeId(),
                    DefaultExchangeHolder.marshal(completed, true, false));
        } finally {
            client.shutdown();
        }

        RedisAggregationRepository repo = createRepo("legacy", false);
        try {
            assertEquals(Set.of("key1"), repo.getKeys());
            Exchange result = repo.get(context, "key1");
            assertNotNull(result);
            assertEquals(active.getExchangeId(), result.getExchangeId());
            assertEquals("active", result.getIn().getBody(String.class));
            assertEquals(2, result.getProperty(Exchange.AGGREGATED_SIZE, Integer.class));

            assertEquals(Set.of(completed.getExchangeId()), repo.scan(context));
            Exchange recovered = repo.recover(context, completed.getExchangeId());
            assertEquals("completed", recovered.getIn().getBody(String.class));

            // the aggregation goes on, and completes
            Exchange next = createExchange("active and next");
            next.setExchangeId(active.getExchangeId());
            assertNotNull(repo.add(context, "key1", next));
            repo.remove(context, "key1", next);
            assertNull(repo.get(context, "key1"));
            assertTrue(repo.scan(context).contains(next.getExchangeId()));

            repo.confirm(context, completed.getExchangeId());
            repo.confirm(context, next.getExchangeId());
            assertTrue(repo.scan(context).isEmpty());
        } finally {
            repo.stop();
        }
    }
}
