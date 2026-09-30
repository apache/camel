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
package org.apache.camel.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RouteVariableRepositoryTest {

    private RouteVariableRepository repo;

    @BeforeEach
    public void setUp() {
        repo = new RouteVariableRepository();
    }

    @Test
    public void testRemoveVariablesWithPrefix() {
        repo.setVariable("route1:foo", "1");
        repo.setVariable("route1:foo.a", "2");
        repo.setVariable("route1:foo.b", "3");
        repo.setVariable("route1:foobar", "4");
        repo.setVariable("route2:foo.a", "5");

        repo.removeVariablesWithPrefix("route1:foo.");

        assertNull(repo.getVariable("route1:foo.a"));
        assertNull(repo.getVariable("route1:foo.b"));
        assertEquals("1", repo.getVariable("route1:foo"));
        assertEquals("4", repo.getVariable("route1:foobar"));
        assertEquals("5", repo.getVariable("route2:foo.a"));
        assertEquals(3, repo.size());

        // no such route
        repo.removeVariablesWithPrefix("route3:foo.");
        assertEquals(3, repo.size());
        assertThrows(IllegalArgumentException.class, () -> repo.removeVariablesWithPrefix("noColon"));
    }

    @Test
    public void testRemoveHeaderVariablesWithPrefix() {
        // the form used by ExchangeHelper.setVariableFromMessageBodyAndHeaders for route:rs:resp, where the
        // headers are stored as header:rs:resp.key in the shared header map (CAMEL-25050)
        repo.setVariable("rs:resp", "body");
        repo.setVariable("header:rs:resp.a", "1");
        repo.setVariable("header:rs:resp.b", "2");
        repo.setVariable("header:rs:response.a", "3");
        repo.setVariable("header:rs2:resp.a", "4");

        repo.removeVariablesWithPrefix("header:rs:resp.");

        assertNull(repo.getVariable("header:rs:resp.a"));
        assertNull(repo.getVariable("header:rs:resp.b"));
        assertEquals("body", repo.getVariable("rs:resp"));
        assertEquals("3", repo.getVariable("header:rs:response.a"));
        assertEquals("4", repo.getVariable("header:rs2:resp.a"));
        assertEquals(3, repo.size());
    }
}
