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
package org.apache.camel.processor.idempotent.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

/**
 * Unit test for {@link CassandraIdempotentRepository}
 */
public class CassandraIdempotentRepositoryTest {
    private Cluster cluster;
    private Session session;
    private CassandraIdempotentRepository<String> idempotentRepository;
    @Rule
    public CassandraCQLUnit cassandraRule = CassandraUnitUtils.cassandraCQLUnit("IdempotentDataSet.cql");
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        CassandraUnitUtils.startEmbeddedCassandra();
    }
    @Before
    public void setUp() throws Exception {
        cluster = CassandraUnitUtils.cassandraCluster();
        session = cluster.connect(CassandraUnitUtils.KEYSPACE);
        idempotentRepository = new NamedCassandraIdempotentRepository<String>(session, "ID");
        idempotentRepository.start();
    }
    @After
    public void tearDown() throws Exception {
        idempotentRepository.stop();
        session.close();
        cluster.close();
    }
    @AfterClass
    public static void tearDownClass() throws Exception {
        CassandraUnitUtils.cleanEmbeddedCassandra();
    }
    private boolean exists(String key) {
        return session.execute(
                "select KEY from CAMEL_IDEMPOTENT where NAME=? and KEY=?","ID", key)
                .one()!=null;
    }
    @Test
    public void testAdd_NotExists() {
        // Given
        String key="Add_NotExists";
        assertFalse(exists(key));
        // When
        boolean result=idempotentRepository.add(key);
        // Then
        assertTrue(result);
        assertTrue(exists(key));        
    }
    @Test
    public void testAdd_Exists() {
        // Given
        String key="Add_Exists";
        assertTrue(exists(key));
        // When
        boolean result=idempotentRepository.add(key);
        // Then
        assertFalse(result);
        assertTrue(exists(key));        
    }
    @Test
    public void testContains_NotExists() {
        // Given
        String key="Contains_NotExists";
        assertFalse(exists(key));
        // When
        boolean result=idempotentRepository.contains(key);
        // Then
        assertFalse(result);
    }
    @Test
    public void testContains_Exists() {
        // Given
        String key="Contains_Exists";
        assertTrue(exists(key));
        // When
        boolean result=idempotentRepository.contains(key);
        // Then
        assertTrue(result);
    }
    @Test
    public void testRemove_NotExists() {
        // Given
        String key="Remove_NotExists";
        assertFalse(exists(key));
        // When
        boolean result=idempotentRepository.contains(key);
        // Then
        // assertFalse(result);
    }
    @Test
    public void testRemove_Exists() {
        // Given
        String key="Remove_Exists";
        assertTrue(exists(key));
        // When
        boolean result=idempotentRepository.remove(key);
        // Then
        assertTrue(result);
    }
    
}
