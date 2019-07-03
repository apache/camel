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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class PgReplicationSlotEndpointTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testUriParsing() {
        PgReplicationSlotEndpoint endpoint = null;
        PgReplicationSlotComponent component = mock(PgReplicationSlotComponent.class);

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:/database/slot:plugin", component);

        assertEquals(endpoint.getDatabase(), "database");
        assertEquals(endpoint.getPort(), Integer.valueOf(5432));
        assertEquals(endpoint.getHost(), "localhost");
        assertEquals(endpoint.getSlot(), "slot");
        assertEquals(endpoint.getOutputPlugin(), "plugin");

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:remote-server/database/slot:plugin", component);

        assertEquals(endpoint.getDatabase(), "database");
        assertEquals(endpoint.getPort(), Integer.valueOf(5432));
        assertEquals(endpoint.getHost(), "remote-server");
        assertEquals(endpoint.getSlot(), "slot");
        assertEquals(endpoint.getOutputPlugin(), "plugin");

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot:remote-server:333/database/slot:plugin", component);

        assertEquals(endpoint.getDatabase(), "database");
        assertEquals(endpoint.getPort(), Integer.valueOf(333));
        assertEquals(endpoint.getHost(), "remote-server");
        assertEquals(endpoint.getSlot(), "slot");
        assertEquals(endpoint.getOutputPlugin(), "plugin");

        endpoint = new PgReplicationSlotEndpoint("pg-replication-slot://remote-server:333/database/slot:plugin", component);

        assertEquals(endpoint.getDatabase(), "database");
        assertEquals(endpoint.getPort(), Integer.valueOf(333));
        assertEquals(endpoint.getHost(), "remote-server");
        assertEquals(endpoint.getSlot(), "slot");
        assertEquals(endpoint.getOutputPlugin(), "plugin");
    }

    @Test
    public void testParsingBadUri() {
        this.expectedException.expect(IllegalArgumentException.class);

        PgReplicationSlotComponent component = mock(PgReplicationSlotComponent.class);

        new PgReplicationSlotEndpoint("pg-replication-slot:/database/slot", component);
    }
}
