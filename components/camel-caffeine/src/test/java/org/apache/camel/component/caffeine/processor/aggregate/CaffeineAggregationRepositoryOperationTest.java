/**
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
package org.apache.camel.component.caffeine.processor.aggregate;

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CaffeineAggregationRepositoryOperationTest extends CamelTestSupport {
    private CaffeineAggregationRepository aggregationRepository;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        aggregationRepository = new CaffeineAggregationRepository();
        aggregationRepository.start();
    }

    @Override
    public void tearDown() throws Exception {
        aggregationRepository.stop();
        super.tearDown();
    }

    private boolean exists(String key) {
        DefaultExchangeHolder holder = aggregationRepository.getCache().getIfPresent(key);
        if (holder == null) {
            return false;
        }
        return true;
    }

    @Test
    public void testAdd() {
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
    public void testGetExists() {
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
    public void testGetNotExists() {
        // Given
        String key = "Get_NotExists";
        assertFalse(exists(key));
        // When
        Exchange exchange2 = aggregationRepository.get(context(), key);
        // Then
        assertNull(exchange2);
    }

    @Test
    public void testRemoveExists() {
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
    public void testRemoveNotExists() {
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
    public void testGetKeys() {
        // Given
        String[] keys = {"GetKeys1", "GetKeys2"};
        addExchanges(keys);
        // When
        Set<String> keySet = aggregationRepository.getKeys();
        // Then
        for (String key : keys) {
            assertTrue(keySet.contains(key));
        }
    }

    @Test
    public void testConfirmExist() {
        // Given
        for (int i = 1; i < 4; i++) {
            String key = "Confirm_" + i;
            Exchange exchange = new DefaultExchange(context());
            exchange.setExchangeId("Exchange_" + i);
            aggregationRepository.add(context(), key, exchange);
            assertTrue(exists(key));
        }
        // When
        aggregationRepository.confirm(context(), "Confirm_2");
        // Then
        assertTrue(exists("Confirm_1"));
        assertFalse(exists("Confirm_2"));
        assertTrue(exists("Confirm_3"));
    }

    @Test
    public void testConfirmNotExist() {
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
    public void testScan() {
        // Given
        String[] keys = {"Scan1", "Scan2"};
        addExchanges(keys);
        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(context());
        // Then
        for (String key : keys) {
            assertTrue(exchangeIdSet.contains(key));
        }
    }

    @Test
    public void testRecover() {
        // Given
        String[] keys = {"Recover1", "Recover2"};
        addExchanges(keys);
        // When
        Exchange exchange2 = aggregationRepository.recover(context(), "Recover2");
        Exchange exchange3 = aggregationRepository.recover(context(), "Recover3");
        // Then
        assertNotNull(exchange2);
        assertNull(exchange3);
    }
}
