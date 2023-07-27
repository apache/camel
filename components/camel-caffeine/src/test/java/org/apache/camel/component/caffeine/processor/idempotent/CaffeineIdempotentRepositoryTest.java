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
package org.apache.camel.component.caffeine.processor.idempotent;

import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaffeineIdempotentRepositoryTest extends CamelTestSupport {

    private CaffeineIdempotentRepository repo;
    private Cache<String, Boolean> cache;
    private String key01;
    private String key02;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        repo = new CaffeineIdempotentRepository("test");

        key01 = generateRandomString();
        key02 = generateRandomString();
    }

    @Test
    void testAdd() {
        // add first key
        assertTrue(repo.add(key01));
        assertTrue(repo.getCache().asMap().containsKey(key01));

        // try to add the same key again
        assertFalse(repo.add(key01));

        // try to add another one
        assertTrue(repo.add(key02));
        assertTrue(repo.getCache().asMap().containsKey(key02));
    }

    @Test
    void testConfirm() {
        // add first key and confirm
        assertTrue(repo.add(key01));
        assertTrue(repo.confirm(key01));

        // try to confirm a key that isn't there
        assertFalse(repo.confirm(key02));
    }

    @Test
    void testContains() {
        assertFalse(repo.contains(key01));

        // add key and check again
        assertTrue(repo.add(key01));
        assertTrue(repo.contains(key01));

    }

    @Test
    void testRemove() {
        // add key to remove
        assertTrue(repo.add(key01));
        assertTrue(repo.add(key02));
        assertTrue(repo.getCache().asMap().containsKey(key01));
        assertTrue(repo.getCache().asMap().containsKey(key02));

        // clear repo
        repo.clear();
        assertFalse(repo.getCache().asMap().containsKey(key01));
        assertFalse(repo.getCache().asMap().containsKey(key02));
    }

    @Test
    void testClear() {
        // add key to remove
        assertTrue(repo.add(key01));
        assertTrue(repo.confirm(key01));

        // remove key
        assertTrue(repo.remove(key01));
        assertFalse(repo.confirm(key01));

        // try to remove a key that isn't there
        repo.remove(key02);
    }

    @Test
    void testRepositoryInRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedBodiesReceived("a", "b");
        // c is a duplicate

        // should be started
        assertTrue(repo.getStatus().isStarted(), "Should be started");

        // send 3 message with one duplicated key (key01)
        template.sendBodyAndHeader("direct://in", "a", "messageId", key01);
        template.sendBodyAndHeader("direct://in", "b", "messageId", key02);
        template.sendBodyAndHeader("direct://in", "c", "messageId", key01);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://in")
                        .idempotentConsumer(header("messageId"), repo)
                        .to("mock://out");
            }
        };
    }

    protected static String generateRandomString() {
        return UUID.randomUUID().toString();
    }
}
