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

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.redis.processor.aggregate.RedisAggregationRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.redis.services.RedisService;
import org.apache.camel.test.infra.redis.services.RedisServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedisAggregationRepositoryOperationsIT extends CamelTestSupport {

    @RegisterExtension
    static RedisService service = RedisServiceFactory.createService();

    private RedisAggregationRepository createRepo(String mapName, boolean optimistic) {
        RedisAggregationRepository repo = new RedisAggregationRepository(mapName, service.getServiceAddress(), optimistic);
        repo.start();
        return repo;
    }

    private Exchange createExchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    public void testPessimisticAddAndGet() {
        RedisAggregationRepository repo = createRepo("pessimisticAddGet", false);
        try {
            Exchange exchange = createExchange("testBody");
            Exchange old = repo.add(context, "key1", exchange);
            assertNull(old);

            Exchange result = repo.get(context, "key1");
            assertNotNull(result);
            assertEquals("testBody", result.getIn().getBody(String.class));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testPessimisticReplace() {
        RedisAggregationRepository repo = createRepo("pessimisticReplace", false);
        try {
            Exchange first = createExchange("first");
            repo.add(context, "key1", first);

            Exchange second = createExchange("second");
            Exchange old = repo.add(context, "key1", second);
            assertNotNull(old);
            assertEquals("first", old.getIn().getBody(String.class));

            Exchange result = repo.get(context, "key1");
            assertEquals("second", result.getIn().getBody(String.class));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testOptimisticAddAndGet() {
        RedisAggregationRepository repo = createRepo("optimisticAddGet", true);
        try {
            Exchange exchange = createExchange("testBody");
            Exchange old = repo.add(context, "key1", null, exchange);
            assertNull(old);

            Exchange result = repo.get(context, "key1");
            assertNotNull(result);
            assertEquals("testBody", result.getIn().getBody(String.class));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testOptimisticUpdate() {
        RedisAggregationRepository repo = createRepo("optimisticUpdate", true);
        try {
            Exchange first = createExchange("first");
            repo.add(context, "key1", null, first);

            Exchange retrieved = repo.get(context, "key1");
            Exchange updated = createExchange("updated");
            Exchange old = repo.add(context, "key1", retrieved, updated);
            assertNotNull(old);

            Exchange result = repo.get(context, "key1");
            assertEquals("updated", result.getIn().getBody(String.class));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testOptimisticRemoveWithRecovery() {
        RedisAggregationRepository repo = createRepo("optimisticRemoveRecovery", true);
        try {
            Exchange exchange = createExchange("recoverable");
            repo.add(context, "key1", null, exchange);

            Exchange toRemove = repo.get(context, "key1");
            repo.remove(context, "key1", toRemove);

            assertNull(repo.get(context, "key1"));

            Set<String> scanned = repo.scan(context);
            assertEquals(1, scanned.size());

            String exchangeId = scanned.iterator().next();
            Exchange recovered = repo.recover(context, exchangeId);
            assertNotNull(recovered);
            assertEquals("recoverable", recovered.getIn().getBody(String.class));

            repo.confirm(context, exchangeId);
            assertTrue(repo.scan(context).isEmpty());
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testPessimisticRemoveWithRecovery() {
        RedisAggregationRepository repo = createRepo("pessimisticRemoveRecovery", false);
        try {
            Exchange exchange = createExchange("recoverable");
            repo.add(context, "key1", exchange);

            Exchange toRemove = repo.get(context, "key1");
            repo.remove(context, "key1", toRemove);

            assertNull(repo.get(context, "key1"));

            Set<String> scanned = repo.scan(context);
            assertEquals(1, scanned.size());

            String exchangeId = scanned.iterator().next();
            Exchange recovered = repo.recover(context, exchangeId);
            assertNotNull(recovered);

            repo.confirm(context, exchangeId);
            assertTrue(repo.scan(context).isEmpty());
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testRemoveWithoutRecovery() {
        RedisAggregationRepository repo
                = new RedisAggregationRepository("removeNoRecovery", service.getServiceAddress(), true);
        repo.setUseRecovery(false);
        repo.start();
        try {
            Exchange exchange = createExchange("noRecover");
            repo.add(context, "key1", null, exchange);

            Exchange toRemove = repo.get(context, "key1");
            repo.remove(context, "key1", toRemove);

            assertNull(repo.get(context, "key1"));
            assertTrue(repo.scan(context).isEmpty());
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testGetKeysAndContainsKey() {
        RedisAggregationRepository repo = createRepo("keysAndContains", false);
        try {
            repo.add(context, "a", createExchange("1"));
            repo.add(context, "b", createExchange("2"));

            Set<String> keys = repo.getKeys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains("a"));
            assertTrue(keys.contains("b"));

            assertTrue(repo.containsKey("a"));
            assertFalse(repo.containsKey("nonexistent"));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testOptimisticRepoRejectsPessimisticAdd() {
        RedisAggregationRepository repo = createRepo("rejectPessimistic", true);
        try {
            Exchange exchange = createExchange("test");
            assertThrows(UnsupportedOperationException.class, () -> repo.add(context, "key1", exchange));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testPessimisticRepoRejectsOptimisticAdd() {
        RedisAggregationRepository repo = createRepo("rejectOptimistic", false);
        try {
            Exchange exchange = createExchange("test");
            assertThrows(UnsupportedOperationException.class, () -> repo.add(context, "key1", null, exchange));
        } finally {
            repo.stop();
        }
    }

    @Test
    public void testCustomRedissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s", service.getServiceAddress()));
        RedissonClient customClient = Redisson.create(config);

        try {
            RedisAggregationRepository repo = new RedisAggregationRepository();
            repo.setMapName("customClient");
            repo.setRedisson(customClient);
            repo.start();

            Exchange exchange = createExchange("custom");
            repo.add(context, "key1", exchange);

            Exchange result = repo.get(context, "key1");
            assertNotNull(result);
            assertEquals("custom", result.getIn().getBody(String.class));

            repo.stop();

            assertFalse(customClient.isShutdown());
        } finally {
            customClient.shutdown();
        }
    }

    @Test
    public void testEndpointValidation() {
        RedisAggregationRepository repo = new RedisAggregationRepository();
        repo.setMapName("validation");
        assertThrows(IllegalArgumentException.class, () -> repo.init());
    }
}
