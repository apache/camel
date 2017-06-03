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
package org.apache.camel.processor.aggregate.cassandra;

import java.util.Set;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.cassandra.BaseCassandraTest;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unite test for {@link CassandraAggregationRepository}
 */
public class CassandraAggregationRepositoryTest extends BaseCassandraTest {

    @Rule
    public CassandraCQLUnit cassandraRule = CassandraUnitUtils.cassandraCQLUnit("AggregationDataSet.cql");

    private Cluster cluster;
    private Session session;
    private CassandraAggregationRepository aggregationRepository;
    private CamelContext camelContext;

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();

        if (canTest()) {
            cluster = CassandraUnitUtils.cassandraCluster();
            session = cluster.connect(CassandraUnitUtils.KEYSPACE);
            aggregationRepository = new CassandraAggregationRepository(session);
            aggregationRepository.start();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (canTest()) {
            aggregationRepository.stop();
            session.close();
            cluster.close();
        }
    }

    private boolean exists(String key) {
        return session.execute(
                "select KEY from CAMEL_AGGREGATION where KEY=?", key)
                .one() != null;
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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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
