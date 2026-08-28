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
package org.apache.camel.support;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueAggregationRepositoryTest {

    private CamelContext camelContext;
    private MemoryKeyValueRepository kvRepository;
    private KeyValueAggregationRepository aggregationRepository;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        kvRepository = new MemoryKeyValueRepository();
        kvRepository.start();

        aggregationRepository = new KeyValueAggregationRepository(kvRepository);
        aggregationRepository.setCamelContext(camelContext);
        aggregationRepository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        aggregationRepository.stop();
        camelContext.stop();
    }

    @Test
    void testAddAndGet() {
        Exchange exchange = createExchange("Hello World");

        aggregationRepository.add(camelContext, "key1", exchange);

        Exchange retrieved = aggregationRepository.get(camelContext, "key1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getIn().getBody(String.class)).isEqualTo("Hello World");
    }

    @Test
    void testGetMissingKeyReturnsNull() {
        Exchange retrieved = aggregationRepository.get(camelContext, "nonexistent");
        assertThat(retrieved).isNull();
    }

    @Test
    void testAddReturnsOldExchange() {
        Exchange first = createExchange("First");
        Exchange second = createExchange("Second");

        Exchange oldFromFirst = aggregationRepository.add(camelContext, "key1", first);
        assertThat(oldFromFirst).isNull();

        Exchange oldFromSecond = aggregationRepository.add(camelContext, "key1", second);
        assertThat(oldFromSecond).isNotNull();
        assertThat(oldFromSecond.getIn().getBody(String.class)).isEqualTo("First");

        // Current value should be the second exchange
        Exchange current = aggregationRepository.get(camelContext, "key1");
        assertThat(current.getIn().getBody(String.class)).isEqualTo("Second");
    }

    @Test
    void testRemove() {
        Exchange exchange = createExchange("Hello");
        aggregationRepository.add(camelContext, "key1", exchange);

        aggregationRepository.remove(camelContext, "key1", exchange);

        assertThat(aggregationRepository.get(camelContext, "key1")).isNull();
    }

    @Test
    void testGetKeys() {
        aggregationRepository.add(camelContext, "key1", createExchange("One"));
        aggregationRepository.add(camelContext, "key2", createExchange("Two"));
        aggregationRepository.add(camelContext, "key3", createExchange("Three"));

        Set<String> keys = aggregationRepository.getKeys();

        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    void testGetKeysExcludesCompletedEntries() {
        Exchange ex1 = createExchange("One");
        Exchange ex2 = createExchange("Two");

        aggregationRepository.add(camelContext, "key1", ex1);
        aggregationRepository.add(camelContext, "key2", ex2);

        // Remove key1 (moves to completed for recovery)
        aggregationRepository.remove(camelContext, "key1", ex1);

        Set<String> keys = aggregationRepository.getKeys();

        // Only active aggregates should be returned
        assertThat(keys).containsExactly("key2");
    }

    @Test
    void testConfirmRemovesCompletedEntry() {
        Exchange exchange = createExchange("Hello");
        String exchangeId = exchange.getExchangeId();

        aggregationRepository.add(camelContext, "key1", exchange);
        aggregationRepository.remove(camelContext, "key1", exchange);

        // The exchange should be recoverable before confirmation
        Exchange recovered = aggregationRepository.recover(camelContext, exchangeId);
        assertThat(recovered).isNotNull();

        // After confirmation, it should no longer be recoverable
        aggregationRepository.confirm(camelContext, exchangeId);
        assertThat(aggregationRepository.recover(camelContext, exchangeId)).isNull();
    }

    @Test
    void testScanReturnsCompletedExchangeIds() {
        Exchange ex1 = createExchange("One");
        Exchange ex2 = createExchange("Two");

        aggregationRepository.add(camelContext, "key1", ex1);
        aggregationRepository.add(camelContext, "key2", ex2);

        // Remove both (move to completed)
        aggregationRepository.remove(camelContext, "key1", ex1);
        aggregationRepository.remove(camelContext, "key2", ex2);

        Set<String> scanned = aggregationRepository.scan(camelContext);

        assertThat(scanned).containsExactlyInAnyOrder(ex1.getExchangeId(), ex2.getExchangeId());
    }

    @Test
    void testRecoverWithRecoveryDisabled() {
        aggregationRepository.setUseRecovery(false);

        Exchange exchange = createExchange("Hello");
        aggregationRepository.add(camelContext, "key1", exchange);
        aggregationRepository.remove(camelContext, "key1", exchange);

        assertThat(aggregationRepository.recover(camelContext, exchange.getExchangeId())).isNull();
    }

    @Test
    void testRecoverySettings() {
        aggregationRepository.setRecoveryInterval(10000);
        assertThat(aggregationRepository.getRecoveryInterval()).isEqualTo(10000);

        aggregationRepository.setMaximumRedeliveries(5);
        assertThat(aggregationRepository.getMaximumRedeliveries()).isEqualTo(5);

        aggregationRepository.setDeadLetterUri("mock:dead");
        assertThat(aggregationRepository.getDeadLetterUri()).isEqualTo("mock:dead");

        aggregationRepository.setUseRecovery(false);
        assertThat(aggregationRepository.isUseRecovery()).isFalse();
    }

    @Test
    void testExchangeHeadersArePreserved() {
        Exchange exchange = createExchange("Body");
        exchange.getIn().setHeader("myHeader", "headerValue");
        exchange.getIn().setHeader("myNumber", 42);

        aggregationRepository.add(camelContext, "key1", exchange);

        Exchange retrieved = aggregationRepository.get(camelContext, "key1");
        assertThat(retrieved.getIn().getHeader("myHeader", String.class)).isEqualTo("headerValue");
        assertThat(retrieved.getIn().getHeader("myNumber", Integer.class)).isEqualTo(42);
    }

    @Test
    void testFactoryMethod() {
        KeyValueAggregationRepository created
                = KeyValueAggregationRepository.keyValueAggregationRepository(kvRepository);

        assertThat(created).isNotNull();
        assertThat(created.getRepository()).isSameAs(kvRepository);
    }

    private Exchange createExchange(String body) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(body);
        return exchange;
    }
}
