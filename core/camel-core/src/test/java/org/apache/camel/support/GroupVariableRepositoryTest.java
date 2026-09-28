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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroupVariableRepositoryTest {

    private GroupVariableRepository repo;

    @BeforeEach
    public void setUp() {
        repo = new GroupVariableRepository();
    }

    @Test
    public void testGetId() {
        assertEquals("group", repo.getId());
    }

    @Test
    public void testSetAndGetVariable() {
        repo.setVariable("teamA:foo", "bar");
        assertEquals("bar", repo.getVariable("teamA:foo"));
    }

    @Test
    public void testVariableIsolationBetweenGroups() {
        repo.setVariable("teamA:key", "valueA");
        repo.setVariable("teamB:key", "valueB");

        assertEquals("valueA", repo.getVariable("teamA:key"));
        assertEquals("valueB", repo.getVariable("teamB:key"));
    }

    @Test
    public void testMultipleVariablesInSameGroup() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamA:bar", "2");
        repo.setVariable("teamA:baz", "3");

        assertEquals("1", repo.getVariable("teamA:foo"));
        assertEquals("2", repo.getVariable("teamA:bar"));
        assertEquals("3", repo.getVariable("teamA:baz"));
        assertEquals(3, repo.size());
    }

    @Test
    public void testGetNonExistentVariable() {
        assertNull(repo.getVariable("teamA:missing"));
    }

    @Test
    public void testGetNonExistentGroup() {
        assertNull(repo.getVariable("noSuchGroup:key"));
    }

    @Test
    public void testRemoveVariable() {
        repo.setVariable("teamA:foo", "bar");
        Object removed = repo.removeVariable("teamA:foo");

        assertEquals("bar", removed);
        assertNull(repo.getVariable("teamA:foo"));
    }

    @Test
    public void testRemoveWildcard() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamA:bar", "2");
        repo.setVariable("teamB:baz", "3");

        repo.removeVariable("teamA:*");

        assertNull(repo.getVariable("teamA:foo"));
        assertNull(repo.getVariable("teamA:bar"));
        assertEquals("3", repo.getVariable("teamB:baz"));
        assertEquals(1, repo.size());
    }

    @Test
    public void testRemoveVariablesWithPrefix() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamA:foo.a", "2");
        repo.setVariable("teamA:foo.b", "3");
        repo.setVariable("teamA:foobar", "4");
        repo.setVariable("teamB:foo.a", "5");

        repo.removeVariablesWithPrefix("teamA:foo.");

        assertNull(repo.getVariable("teamA:foo.a"));
        assertNull(repo.getVariable("teamA:foo.b"));
        assertEquals("1", repo.getVariable("teamA:foo"));
        assertEquals("4", repo.getVariable("teamA:foobar"));
        assertEquals("5", repo.getVariable("teamB:foo.a"));
        assertEquals(3, repo.size());

        // no such group
        repo.removeVariablesWithPrefix("teamC:foo.");
        assertEquals(3, repo.size());
        assertThrows(IllegalArgumentException.class, () -> repo.removeVariablesWithPrefix("noColon"));
    }

    @Test
    public void testRemoveHeaderVariablesWithPrefix() {
        // the form used by ExchangeHelper.setVariableFromMessageBodyAndHeaders for group:grp:resp, where the
        // headers are stored as header:grp:resp.key in the shared header map (CAMEL-25050)
        repo.setVariable("grp:resp", "body");
        repo.setVariable("header:grp:resp.a", "1");
        repo.setVariable("header:grp:resp.b", "2");
        repo.setVariable("header:grp:response.a", "3");
        repo.setVariable("header:grp2:resp.a", "4");

        repo.removeVariablesWithPrefix("header:grp:resp.");

        assertNull(repo.getVariable("header:grp:resp.a"));
        assertNull(repo.getVariable("header:grp:resp.b"));
        assertEquals("body", repo.getVariable("grp:resp"));
        assertEquals("3", repo.getVariable("header:grp:response.a"));
        assertEquals("4", repo.getVariable("header:grp2:resp.a"));
        assertEquals(3, repo.size());
    }

    @Test
    public void testSetNullRemoves() {
        repo.setVariable("teamA:foo", "bar");
        repo.setVariable("teamA:foo", null);

        assertNull(repo.getVariable("teamA:foo"));
    }

    @Test
    public void testGetGroupIds() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamB:bar", "2");
        repo.setVariable("teamC:baz", "3");

        Set<String> ids = repo.getGroupIds();
        assertEquals(Set.of("teamA", "teamB", "teamC"), ids);
    }

    @Test
    public void testGetGroupIdsEmpty() {
        assertTrue(repo.getGroupIds().isEmpty());
    }

    @Test
    public void testGetGroupIdsAfterRemoveWildcard() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamB:bar", "2");

        repo.removeVariable("teamA:*");

        assertEquals(Set.of("teamB"), repo.getGroupIds());
    }

    @Test
    public void testNames() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamB:bar", "2");

        Set<String> names = repo.names().collect(Collectors.toSet());
        assertEquals(Set.of("teamA:foo", "teamB:bar"), names);
    }

    @Test
    public void testGetVariables() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamB:bar", "2");

        Map<String, Object> vars = repo.getVariables();
        assertEquals(2, vars.size());
        assertEquals("1", vars.get("teamA:foo"));
        assertEquals("2", vars.get("teamB:bar"));
    }

    @Test
    public void testHasVariables() {
        assertFalse(repo.hasVariables());

        repo.setVariable("teamA:foo", "bar");
        assertTrue(repo.hasVariables());
    }

    @Test
    public void testSize() {
        assertEquals(0, repo.size());

        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamA:bar", "2");
        repo.setVariable("teamB:baz", "3");
        assertEquals(3, repo.size());
    }

    @Test
    public void testClear() {
        repo.setVariable("teamA:foo", "1");
        repo.setVariable("teamB:bar", "2");

        repo.clear();

        assertFalse(repo.hasVariables());
        assertEquals(0, repo.size());
    }

    @Test
    public void testMissingColonThrows() {
        assertThrows(IllegalArgumentException.class, () -> repo.getVariable("noColon"));
        assertThrows(IllegalArgumentException.class, () -> repo.setVariable("noColon", "value"));
        assertThrows(IllegalArgumentException.class, () -> repo.removeVariable("noColon"));
    }
}
