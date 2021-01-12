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
package org.apache.camel.component.infinispan.embedded;

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanEmbeddedAggregationRepositoryOperationsTest extends InfinispanEmbeddedTestSupport {
    private InfinispanEmbeddedAggregationRepository aggregationRepository;

    @Override
    public void setupResources() throws Exception {
        super.setupResources();

        InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();
        configuration.setCacheContainer(cacheContainer);

        aggregationRepository = new InfinispanEmbeddedAggregationRepository(getCacheName());
        aggregationRepository.setConfiguration(configuration);
        aggregationRepository.start();
    }

    @Override
    public void cleanupResources() {
        if (aggregationRepository != null) {
            aggregationRepository.stop();
        }
    }

    private boolean exists(String key) {
        return aggregationRepository.getCache().get(key) != null;
    }

    @Test
    public void testAdd() {
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
    public void testConfirmExist() {
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
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
        // cleanup
        aggregationRepository.getCache().clear();
        for (String key : keys) {
            Exchange exchange = new DefaultExchange(context());
            exchange.setExchangeId("Exchange-" + key);
            aggregationRepository.add(context(), key, exchange);
        }
    }

    @Test
    public void testScan() {
        // cleanup
        aggregationRepository.getCache().clear();
        // Given
        String[] keys = { "Scan1", "Scan2" };
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
        // cleanup
        aggregationRepository.getCache().clear();
        // Given
        String[] keys = { "Recover1", "Recover2" };
        addExchanges(keys);
        // When
        Exchange exchange2 = aggregationRepository.recover(context(), "Recover2");
        Exchange exchange3 = aggregationRepository.recover(context(), "Recover3");
        // Then
        assertNotNull(exchange2);
        assertNull(exchange3);
    }

}
