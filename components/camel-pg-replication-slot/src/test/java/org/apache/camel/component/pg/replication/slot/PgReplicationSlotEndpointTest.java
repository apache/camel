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
package org.apache.camel.component.pg.replication.slot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class PgReplicationSlotEndpointTest {

    @Test
    public void testUriParsing() {
        PgReplicationSlotEndpoint endpoint = null;
        PgReplicationSlotComponent component = mock(PgReplicationSlotComponent.class);

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:/database/slot:plugin", component);

        assertEquals("database", endpoint.getDatabase());
        assertEquals(Integer.valueOf(5432), endpoint.getPort());
        assertEquals("localhost", endpoint.getHost());
        assertEquals("slot", endpoint.getSlot());
        assertEquals("plugin", endpoint.getOutputPlugin());

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:remote-server/database/slot:plugin", component);

        assertEquals("database", endpoint.getDatabase());
        assertEquals(Integer.valueOf(5432), endpoint.getPort());
        assertEquals("remote-server", endpoint.getHost());
        assertEquals("slot", endpoint.getSlot());
        assertEquals("plugin", endpoint.getOutputPlugin());

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:remote-server:333/database/slot:plugin", component);

        assertEquals("database", endpoint.getDatabase());
        assertEquals(Integer.valueOf(333), endpoint.getPort());
        assertEquals("remote-server", endpoint.getHost());
        assertEquals("slot", endpoint.getSlot());
        assertEquals("plugin", endpoint.getOutputPlugin());

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot://remote-server:333/database/slot:plugin", component);

        assertEquals("database", endpoint.getDatabase());
        assertEquals(Integer.valueOf(333), endpoint.getPort());
        assertEquals("remote-server", endpoint.getHost());
        assertEquals("slot", endpoint.getSlot());
        assertEquals("plugin", endpoint.getOutputPlugin());
    }

    @Test
    public void testParsingBadUri() {
        PgReplicationSlotComponent component = mock(PgReplicationSlotComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> new PgReplicationSlotEndpoint("pg-replication-slot:/database/slot", component));
    }
}
