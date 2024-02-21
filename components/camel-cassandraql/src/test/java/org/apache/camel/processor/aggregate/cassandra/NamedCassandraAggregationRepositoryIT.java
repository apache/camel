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
package org.apache.camel.processor.aggregate.cassandra;

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.integration.BaseCassandra;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unite test for {@link CassandraAggregationRepository}
 */
public class NamedCassandraAggregationRepositoryIT extends BaseCassandra {

    private CassandraAggregationRepository aggregationRepository;

    @BeforeEach
    protected void doPreSetup() {
        aggregationRepository = new NamedCassandraAggregationRepository(getSession(), "ID");
        aggregationRepository.setTable("NAMED_CAMEL_AGGREGATION");
        aggregationRepository.start();
    }

    @AfterEach
    public void tearDown() {
        aggregationRepository.stop();
    }

    private boolean exists(String key) {
        return getSession().execute("select KEY from NAMED_CAMEL_AGGREGATION where NAME='ID' and KEY=?", key).one() != null;
    }

    @Test
    public void testAdd() {
        // Given
        String key = "Add";
        assertFalse(exists(key));
        Exchange exchange = new DefaultExchange(context);
        // When
        aggregationRepository.add(context, key, exchange);
        // Then
        assertTrue(exists(key));
    }

    @Test
    public void testGetExists() {
        // Given
        String key = "Get_Exists";
        Exchange exchange = new DefaultExchange(context);
        aggregationRepository.add(context, key, exchange);
        assertTrue(exists(key));
        // When
        Exchange exchange2 = aggregationRepository.get(context, key);
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
        Exchange exchange2 = aggregationRepository.get(context, key);
        // Then
        assertNull(exchange2);
    }

    @Test
    public void testRemoveExists() {
        // Given
        String key = "Remove_Exists";
        Exchange exchange = new DefaultExchange(context);
        aggregationRepository.add(context, key, exchange);
        assertTrue(exists(key));
        // When
        aggregationRepository.remove(context, key, exchange);
        // Then
        assertFalse(exists(key));
    }

    @Test
    public void testRemoveNotExists() {
        // Given
        String key = "RemoveNotExists";
        Exchange exchange = new DefaultExchange(context);
        assertFalse(exists(key));
        // When
        aggregationRepository.remove(context, key, exchange);
        // Then
        assertFalse(exists(key));
    }

    @Test
    public void testGetKeys() {
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

    @DisabledOnOs(OS.MAC)
    @Test
    public void testConfirmExist() {
        // Given
        for (int i = 1; i < 4; i++) {
            String key = "Confirm_" + i;
            Exchange exchange = new DefaultExchange(context);
            exchange.setExchangeId("Exchange_" + i);
            aggregationRepository.add(context, key, exchange);
            assertTrue(exists(key));
        }
        // When
        aggregationRepository.confirm(context, "Exchange_2");
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
        aggregationRepository.confirm(context, "Exchange-Confirm5");
        // Then
        for (String key : keys) {
            assertTrue(exists(key));
        }
    }

    private void addExchanges(String... keys) {
        for (String key : keys) {
            Exchange exchange = new DefaultExchange(context);
            exchange.setExchangeId("Exchange-" + key);
            aggregationRepository.add(context, key, exchange);
        }
    }

    @Test
    public void testScan() {
        // Given
        String[] keys = { "Scan1", "Scan2" };
        addExchanges(keys);
        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(context);
        // Then
        for (String key : keys) {
            assertTrue(exchangeIdSet.contains("Exchange-" + key));
        }
    }

    @Test
    public void testRecover() {
        // Given
        String[] keys = { "Recover1", "Recover2" };
        addExchanges(keys);
        // When
        Exchange exchange2 = aggregationRepository.recover(context, "Exchange-Recover2");
        Exchange exchange3 = aggregationRepository.recover(context, "Exchange-Recover3");
        // Then
        assertNotNull(exchange2);
        assertNull(exchange3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return null;
    }
}
