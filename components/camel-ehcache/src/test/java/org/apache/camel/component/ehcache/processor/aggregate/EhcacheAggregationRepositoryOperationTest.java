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
package org.apache.camel.component.ehcache.processor.aggregate;

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.ehcache.EhcacheTestSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EhcacheAggregationRepositoryOperationTest extends EhcacheTestSupport {
    private EhcacheAggregationRepository aggregationRepository;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        aggregationRepository = createAggregateRepository();
        aggregationRepository.start();
    }

    @Override
    public void doPostTearDown() {
        aggregationRepository.stop();
    }

    private boolean exists(String key) {
        DefaultExchangeHolder holder = aggregationRepository.getCache().get(key);
        if (holder == null) {
            return false;
        }
        return true;
    }

    @Test
    void testAdd() {
        // Given
        String key = "Add";
        assertFalse(exists(key));
        Exchange exchange = new DefaultExchange(context());
        // When
        aggregationRepository.add(context(), key, exchange);
        // Then
        assertTrue(exists(key));
    }

    @Test
    void testGetExists() {
        // Given
        String key = "Get_Exists";
        Exchange exchange = new DefaultExchange(context());
        aggregationRepository.add(context(), key, exchange);
        assertTrue(exists(key));

        // When
        Exchange exchange2 = aggregationRepository.get(context(), key);
        // Then
        assertNotNull(exchange2);
        assertEquals(exchange.getExchangeId(), exchange2.getExchangeId());
    }

    @Test
    void testGetNotExists() {
        // Given
        String key = "Get_NotExists";
        assertFalse(exists(key));
        // When
        Exchange exchange2 = aggregationRepository.get(context(), key);
        // Then
        assertNull(exchange2);
    }

    @Test
    void testRemoveExists() {
        // Given
        String key = "Remove_Exists";
        Exchange exchange = new DefaultExchange(context());
        aggregationRepository.add(context(), key, exchange);
        assertTrue(exists(key));
        // When
        aggregationRepository.remove(context(), key, exchange);
        // Then
        assertFalse(exists(key));
    }

    @Test
    void testRemoveNotExists() {
        // Given
        String key = "RemoveNotExists";
        Exchange exchange = new DefaultExchange(context());
        assertFalse(exists(key));
        // When
        aggregationRepository.remove(context(), key, exchange);
        // Then
        assertFalse(exists(key));
    }

    @Test
    void testGetKeys() {
        // Given
        String[] keys = { "GetKeys1", "GetKeys2" };
        addExchanges(keys);
        // When
        Set<String> keySet = aggregationRepository.getKeys();
        // Then
        for (String key : keys) {
            assertTrue(keySet.contains(key));
        }
    }

    @Test
    void testConfirmExist() {
        // Given
        Exchange exchange = new DefaultExchange(context());
        exchange.setExchangeId("Exchange_Confirm");
        aggregationRepository.add(context(), "Confirm_1", exchange);
        // completing the aggregation moves the exchange into the recovery store
        aggregationRepository.remove(context(), "Confirm_1", exchange);
        assertFalse(exists("Confirm_1"));
        assertNotNull(aggregationRepository.recover(context(), "Exchange_Confirm"));

        // When
        aggregationRepository.confirm(context(), "Exchange_Confirm");

        // Then
        assertNull(aggregationRepository.recover(context(), "Exchange_Confirm"));
        assertTrue(aggregationRepository.scan(context()).isEmpty());
    }

    @Test
    void testConfirmNotExist() {
        // Given
        String[] keys = new String[3];
        for (int i = 1; i < 4; i++) {
            keys[i - 1] = "Confirm" + i;
        }
        addExchanges(keys);
        for (String key : keys) {
            assertTrue(exists(key));
        }
        // When
        aggregationRepository.confirm(context(), "Exchange-Confirm5");
        // Then
        for (String key : keys) {
            assertTrue(exists(key));
        }
    }

    private void addExchanges(String... keys) {
        for (String key : keys) {
            Exchange exchange = new DefaultExchange(context());
            exchange.setExchangeId("Exchange-" + key);
            aggregationRepository.add(context(), key, exchange);
        }
    }

    @Test
    void testScan() {
        // Given
        String[] keys = { "Scan1", "Scan2", "Scan3" };
        addExchanges(keys);
        // the first two aggregations are completed, the third is still in progress
        for (int i = 0; i < 2; i++) {
            Exchange exchange = new DefaultExchange(context());
            exchange.setExchangeId("Exchange-" + keys[i]);
            aggregationRepository.remove(context(), keys[i], exchange);
        }

        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(context());

        // Then - the scan reports the exchange ids to recover, not the correlation keys still aggregating
        assertEquals(Set.of("Exchange-Scan1", "Exchange-Scan2"), exchangeIdSet);
    }

    @Test
    void testScanWithoutRecovery() {
        // Given
        aggregationRepository.setUseRecovery(false);
        String[] keys = { "Scan1", "Scan2" };
        addExchanges(keys);
        Exchange exchange = new DefaultExchange(context());
        exchange.setExchangeId("Exchange-Scan1");
        aggregationRepository.remove(context(), "Scan1", exchange);

        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(context());

        // Then
        assertTrue(exchangeIdSet.isEmpty());
        assertNull(aggregationRepository.recover(context(), "Exchange-Scan1"));
        assertEquals(Set.of("Scan2"), aggregationRepository.getKeys());
    }

    @Test
    void testRecover() {
        // Given
        Exchange exchange = new DefaultExchange(context());
        exchange.setExchangeId("Exchange-Recover1");
        exchange.getIn().setBody("Hello");
        aggregationRepository.add(context(), "Recover1", exchange);
        // the exchange that completed the aggregation has been aggregated after the last add
        exchange.getIn().setBody("Hello World");
        aggregationRepository.remove(context(), "Recover1", exchange);

        // When
        Exchange recovered = aggregationRepository.recover(context(), "Exchange-Recover1");
        Exchange unknown = aggregationRepository.recover(context(), "Exchange-Recover2");
        Exchange inProgress = aggregationRepository.recover(context(), "Recover1");

        // Then
        assertNotNull(recovered);
        assertEquals("Exchange-Recover1", recovered.getExchangeId());
        assertEquals("Hello World", recovered.getIn().getBody());
        assertNull(unknown);
        assertNull(inProgress);
    }

    @Test
    void testRecoverDoesNotReturnAggregationInProgress() {
        // Given
        addExchanges("Recover1");

        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(context());
        Exchange recovered = aggregationRepository.recover(context(), "Recover1");

        // Then
        assertTrue(exchangeIdSet.isEmpty());
        assertNull(recovered);
    }

    @Test
    void testGetKeysIgnoresExchangesToRecover() {
        // Given
        Exchange exchange = new DefaultExchange(context());
        exchange.setExchangeId("Exchange-Keys1");
        aggregationRepository.add(context(), "Keys1", exchange);
        aggregationRepository.add(context(), "Keys2", exchange);
        aggregationRepository.remove(context(), "Keys1", exchange);

        // When
        Set<String> keys = aggregationRepository.getKeys();

        // Then - only the aggregation still in progress is reported
        assertEquals(Set.of("Keys2"), keys);
    }
}
