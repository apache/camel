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
package org.apache.camel.processor.idempotent.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.apache.camel.component.cassandra.BaseCassandraTest;
import org.apache.camel.component.cassandra.CassandraUnitUtils;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link CassandraIdempotentRepository}
 */
public class NamedCassandraIdempotentRepositoryTest extends BaseCassandraTest {

    @Rule
    public CassandraCQLUnit cassandraRule = CassandraUnitUtils.cassandraCQLUnit("NamedIdempotentDataSet.cql");

    private Cluster cluster;
    private Session session;
    private CassandraIdempotentRepository idempotentRepository;

    @Override
    @Before
    public void setUp() throws Exception {
        if (canTest()) {
            cluster = CassandraUnitUtils.cassandraCluster();
            session = cluster.connect(CassandraUnitUtils.KEYSPACE);
            idempotentRepository = new NamedCassandraIdempotentRepository(session, "ID");
            idempotentRepository.setTable("NAMED_CAMEL_IDEMPOTENT");
            idempotentRepository.start();
        }
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (canTest()) {
            idempotentRepository.stop();
            session.close();
            cluster.close();
        }
    }

    private boolean exists(String key) {
        return session.execute("select KEY from NAMED_CAMEL_IDEMPOTENT where NAME=? and KEY=?", "ID", key).one() != null;
    }

    @Test
    public void testAddNotExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Add_NotExists";
        assertFalse(exists(key));
        // When
        boolean result = idempotentRepository.add(key);
        // Then
        assertTrue(result);
        assertTrue(exists(key));
    }

    @Test
    public void testAddExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Add_Exists";
        assertTrue(exists(key));
        // When
        boolean result = idempotentRepository.add(key);
        // Then
        assertFalse(result);
        assertTrue(exists(key));
    }

    @Test
    public void testContainsNotExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Contains_NotExists";
        assertFalse(exists(key));
        // When
        boolean result = idempotentRepository.contains(key);
        // Then
        assertFalse(result);
    }

    @Test
    public void testContainsExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Contains_Exists";
        assertTrue(exists(key));
        // When
        boolean result = idempotentRepository.contains(key);
        // Then
        assertTrue(result);
    }

    @Test
    public void testRemoveNotExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Remove_NotExists";
        assertFalse(exists(key));
        // When
        boolean result = idempotentRepository.contains(key);
        // Then
        assertFalse(result);
    }

    @Test
    public void testRemoveExists() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Remove_Exists";
        assertTrue(exists(key));
        // When
        boolean result = idempotentRepository.remove(key);
        // Then
        assertTrue(result);
    }

    @Test
    public void testClear() {
        if (!canTest()) {
            return;
        }

        // Given
        String key = "Remove_Exists";
        assertTrue(exists(key));
        // When
        idempotentRepository.clear();
        // Then
        assertFalse(idempotentRepository.contains(key));
    }
}
