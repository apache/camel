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
package org.apache.camel.component.iec60870;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConnectionIdTest {
    @Test
    public void testNotEqual1() {
        ConnectionId id1 = new ConnectionId("host", 1202, "id1");
        ConnectionId id2 = new ConnectionId("host", 1202, "id2");
        assertNotEquals(id1, id2, "Must be different");
    }

    @Test
    public void testNotEqual2() {
        ConnectionId id1 = new ConnectionId("host1", 1202, "id");
        ConnectionId id2 = new ConnectionId("host2", 1202, "id");
        assertNotEquals(id1, id2, "Must be different");
    }

    @Test
    public void testNotEqual3() {
        ConnectionId id1 = new ConnectionId("host", 1202_1, "id");
        ConnectionId id2 = new ConnectionId("host", 1202_2, "id");
        assertNotEquals(id1, id2, "Must be different");
    }

    @Test
    public void testIllegal1() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionId("host", -1, "id"));
    }

    @Test
    public void testGetters() {
        ConnectionId id = new ConnectionId("host", 1202, "id");
        assertEquals("host", id.getHost());
        assertEquals(1202, id.getPort());
        assertEquals("id", id.getConnectionId());
    }

    @Test
    public void testEqual1() {
        ConnectionId id1 = new ConnectionId("host", 1202, "id");
        ConnectionId id2 = new ConnectionId("host", 1202, "id");
        assertEquals(id1, id2, "Must be equal");
    }

    @Test
    public void testEqual2() {
        ConnectionId id1 = new ConnectionId("host", 1202, "id");
        ConnectionId id2 = new ConnectionId("host", 1202, "id");

        assertEquals(id1.hashCode(), id2.hashCode(), "Hash code must be equal");
    }
}
