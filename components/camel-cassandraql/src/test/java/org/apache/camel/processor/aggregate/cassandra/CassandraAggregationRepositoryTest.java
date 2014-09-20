/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.camel.processor.aggregate.cassandra.CassandraAggregationRepository;
import org.apache.camel.processor.aggregate.cassandra.NamedCassandraAggregationRepository;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import java.util.Set;
/**
 * Unite test for {@link CassandraAggregationRepository}
 */
public class CassandraAggregationRepositoryTest {
    private Cluster cluster;
    private Session session;
    private CassandraAggregationRepository aggregationRepository;
    private CamelContext camelContext;
    @Rule
    public CassandraCQLUnit cassandraRule = CassandraUnitUtils.cassandraCQLUnit("AggregationDataSet.cql");
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }
    @Before
    public void setUp() throws Exception {
        camelContext =new DefaultCamelContext();
        cluster = CassandraUnitUtils.cassandraCluster();
        session = cluster.connect(CassandraUnitUtils.KEYSPACE);
        aggregationRepository = new NamedCassandraAggregationRepository(session, "ID");
        aggregationRepository.start();
    }
    @After
    public void tearDown() throws Exception {
        aggregationRepository.stop();
        session.close();
        cluster.close();
    }
    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }
    private boolean exists(String key) {
        return session.execute(
                "select KEY from CAMEL_AGGREGATION where NAME=? and KEY=?", "ID", key)
                .one()!=null;
    }
    @Test
    public void testAdd() {
        // Given
        String key="Add";
        assertFalse(exists(key));
        Exchange exchange = new DefaultExchange(camelContext);
        // When
        aggregationRepository.add(camelContext, key, exchange);
        // Then
        assertTrue(exists(key));        
    }
    @Test
    public void testGet_Exists() {
        // Given
        String key="Get_Exists";
        Exchange exchange = new DefaultExchange(camelContext);
        aggregationRepository.add(camelContext, key, exchange);
        assertTrue(exists(key));
        // When
        Exchange exchange2=aggregationRepository.get(camelContext, key);
        // Then
        assertNotNull(exchange2);
        assertEquals(exchange.getExchangeId(), exchange2.getExchangeId());        
    }
    @Test
    public void testGet_NotExists() {
        // Given
        String key="Get_NotExists";
        assertFalse(exists(key));
        // When
        Exchange exchange2=aggregationRepository.get(camelContext, key);
        // Then
        assertNull(exchange2);
    }
    @Test
    public void testRemove_Exists() {
        // Given
        String key="Remove_Exists";
        Exchange exchange = new DefaultExchange(camelContext);
        aggregationRepository.add(camelContext, key, exchange);
        assertTrue(exists(key));
        // When
        aggregationRepository.remove(camelContext, key, exchange);
        // Then
        assertFalse(exists(key));
    }
    @Test
    public void testRemove_NotExists() {
        // Given
        String key="Remove_NotExists";
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
        String[] keys={"GetKeys1", "GetKeys2"};
        for(String key: keys) {
            Exchange exchange = new DefaultExchange(camelContext);
            aggregationRepository.add(camelContext, key, exchange);
        }
        // When
        Set<String> keySet=aggregationRepository.getKeys();
        // Then
        for(String key: keys) {
            assertTrue(keySet.contains(key));
        }
    }
    @Test
    public void testConfirm_Exist() {
        // Given
        for(int i=1;i<4;i++) {
            String key="Confirm_"+i;
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setExchangeId("Exchange_"+i);
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
    public void testConfirm_NotExist() {
        // Given
        for(int i=1;i<4;i++) {
            String key="Confirm_"+i;
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setExchangeId("Exchange_"+i);
            aggregationRepository.add(camelContext, key, exchange);
            assertTrue(exists(key));
        }
        // When
        aggregationRepository.confirm(camelContext, "Exchange_5");
        // Then
        for(int i=1;i<4;i++) {
            assertTrue(exists("Confirm_"+i));
        }
    }

}
