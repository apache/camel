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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.Exchange;
import org.apache.camel.spi.OptimisticLockingAggregationRepository.OptimisticLockingException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The optimistic locking add(camelContext, key, oldExchange, newExchange) is a compare-and-set against the exchange
 * that was read by get(), also when the group has been completed (removed) meanwhile, and a new group with the same key
 * never gets the version of a previous group.
 */
public class JdbcAggregationRepositoryOptimisticAddTest extends AbstractJdbcAggregationTestSupport {

    private static final String VERSION = "CamelOptimisticLockVersion";

    @Test
    public void testAddNewGroup() {
        repo.add(context, "foo", null, newExchange("a"));

        assertEquals("a", repo.get(context, "foo").getMessage().getBody(String.class));
    }

    @Test
    public void testAddNewGroupWhenGroupExists() {
        repo.add(context, "foo", null, newExchange("a"));

        // another thread or node started the group first
        assertThrows(OptimisticLockingException.class, () -> repo.add(context, "foo", null, newExchange("b")));
        assertEquals("a", repo.get(context, "foo").getMessage().getBody(String.class));
    }

    @Test
    public void testUpdateGroup() {
        repo.add(context, "foo", null, newExchange("a"));
        Exchange group = repo.get(context, "foo");
        group.getMessage().setBody("a,b");

        repo.add(context, "foo", group, group);
        assertEquals("a,b", repo.get(context, "foo").getMessage().getBody(String.class));

        // an update with the same (now stale) version fails
        Exchange stale = group;
        assertThrows(OptimisticLockingException.class, () -> repo.add(context, "foo", stale, stale));
    }

    @Test
    public void testUpdateGroupWithNewExchange() {
        repo.add(context, "foo", null, newExchange("a"));
        Exchange group = repo.get(context, "foo");

        // an aggregation strategy that returns the new exchange: the version is the one of the old exchange
        repo.add(context, "foo", group, newExchange("a,b"));
        assertEquals("a,b", repo.get(context, "foo").getMessage().getBody(String.class));
    }

    @Test
    public void testAddAfterGroupCompleted() {
        repo.add(context, "foo", null, newExchange("x"));
        // thread A reads the group [x]
        Exchange readByA = repo.get(context, "foo");
        // meanwhile the group is completed by another thread
        Exchange completed = repo.get(context, "foo");
        repo.remove(context, "foo", completed);
        repo.confirm(context, completed.getExchangeId());

        // thread A aggregates and stores the group it read: it must not be stored again (x would be sent twice)
        readByA.getMessage().setBody("x,a");
        assertThrows(OptimisticLockingException.class, () -> repo.add(context, "foo", readByA, readByA));
        assertNull(repo.get(context, "foo"));
    }

    @Test
    public void testAddAfterGroupCompletedAndNewGroupStarted() {
        repo.add(context, "foo", null, newExchange("x"));
        Exchange readByA = repo.get(context, "foo");
        Exchange completed = repo.get(context, "foo");
        repo.remove(context, "foo", completed);
        repo.confirm(context, completed.getExchangeId());
        // a new group is started for the same key
        repo.add(context, "foo", null, newExchange("c"));
        Exchange newGroup = repo.get(context, "foo");
        assertNotEquals(readByA.getProperty(VERSION, Long.class), newGroup.getProperty(VERSION, Long.class));

        // thread A must not overwrite the new group (c would be lost)
        readByA.getMessage().setBody("x,a");
        assertThrows(OptimisticLockingException.class, () -> repo.add(context, "foo", readByA, readByA));
        assertEquals("c", repo.get(context, "foo").getMessage().getBody(String.class));
    }

    @Test
    public void testRemoveAfterGroupCompletedAndNewGroupStarted() {
        repo.add(context, "foo", null, newExchange("x"));
        Exchange readByA = repo.get(context, "foo");
        Exchange completed = repo.get(context, "foo");
        repo.remove(context, "foo", completed);
        repo.confirm(context, completed.getExchangeId());
        repo.add(context, "foo", null, newExchange("c"));

        // a stale completion must not remove the new group
        assertThrows(OptimisticLockingException.class, () -> repo.remove(context, "foo", readByA));
        Exchange stored = repo.get(context, "foo");
        assertNotNull(stored);
        assertEquals("c", stored.getMessage().getBody(String.class));
    }

    private Exchange newExchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }
}
