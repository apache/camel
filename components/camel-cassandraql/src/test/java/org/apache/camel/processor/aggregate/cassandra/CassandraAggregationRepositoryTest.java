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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.cassandra.BaseCassandraTest;
import org.apache.camel.component.cassandra.CassandraCQLUnit;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unite test for {@link CassandraAggregationRepository}
 */
public class CassandraAggregationRepositoryTest extends BaseCassandraTest {

    @RegisterExtension
    static CassandraCQLUnit cassandra = CassandraUnitUtils.cassandraCQLUnit("AggregationDataSet.cql");

    private CassandraAggregationRepository aggregationRepository;
    private CamelContext camelContext;

    @Override
    protected void doPreSetup() throws Exception {
        camelContext = new DefaultCamelContext();
        aggregationRepository = new CassandraAggregationRepository(cassandra.cluster, CassandraUnitUtils.KEYSPACE);
        aggregationRepository.start();
        super.doPreSetup();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        aggregationRepository.stop();
    }

    private boolean exists(String key) {
        return cassandra.session.execute("select KEY from CAMEL_AGGREGATION where KEY=?", key).one() != null;
    }

    @Test
    public void testAdd() {
        // Given
        String key = "Add";
        assertFalse(exists(key));
        Exchange exchange = new DefaultExchange(camelContext);
        // When
        aggregationRepository.add(camelContext, key, exchange);
        // Then
        assertTrue(exists(key));
    }

    @Test
    public void testGetExists() {
        // Given
        String key = "Get_Exists";
        Exchange exchange = new DefaultExchange(camelContext);
        aggregationRepository.add(camelContext, key, exchange);
        assertTrue(exists(key));
        // When
        Exchange exchange2 = aggregationRepository.get(camelContext, key);
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
        Exchange exchange2 = aggregationRepository.get(camelContext, key);
        // Then
        assertNull(exchange2);
    }

    @Test
    public void testRemoveExists() {
        // Given
        String key = "Remove_Exists";
        Exchange exchange = new DefaultExchange(camelContext);
        aggregationRepository.add(camelContext, key, exchange);
        assertTrue(exists(key));
        // When
        aggregationRepository.remove(camelContext, key, exchange);
        // Then
        assertFalse(exists(key));
    }

    @Test
    public void testRemoveNotExists() {
        // Given
        String key = "RemoveNotExists";
        Exchange exchange = new DefaultExchange(camelContext);
        assertFalse(exists(key));
        // When
        aggregationRepository.remove(camelContext, key, exchange);
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
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setExchangeId("Exchange_" + i);
            aggregationRepository.add(camelContext, key, exchange);
            assertTrue(exists(key));
        }
        // When
        aggregationRepository.confirm(camelContext, "Exchange_2");
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
        aggregationRepository.confirm(camelContext, "Exchange-Confirm5");
        // Then
        for (String key : keys) {
            assertTrue(exists(key));
        }
    }

    private void addExchanges(String... keys) {
        for (String key : keys) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setExchangeId("Exchange-" + key);
            aggregationRepository.add(camelContext, key, exchange);
        }
    }

    @Test
    public void testScan() {
        // Given
        String[] keys = {"Scan1", "Scan2"};
        addExchanges(keys);
        // When
        Set<String> exchangeIdSet = aggregationRepository.scan(camelContext);
        // Then
        for (String key : keys) {
            assertTrue(exchangeIdSet.contains("Exchange-" + key));
        }
    }

    @Test
    public void testRecover() {
        // Given
        String[] keys = {"Recover1", "Recover2"};
        addExchanges(keys);
        // When
        Exchange exchange2 = aggregationRepository.recover(camelContext, "Exchange-Recover2");
        Exchange exchange3 = aggregationRepository.recover(camelContext, "Exchange-Recover3");
        // Then
        assertNotNull(exchange2);
        assertNull(exchange3);
    }

}
