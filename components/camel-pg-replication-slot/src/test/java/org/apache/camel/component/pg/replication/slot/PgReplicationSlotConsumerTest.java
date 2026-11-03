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

import java.util.HashSet;
import java.util.List;

import org.apache.camel.component.pg.replication.slot.PgReplicationSlotConsumerReconnectTest.FakeServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.postgresql.replication.LogSequenceNumber;

import static org.apache.camel.component.pg.replication.slot.PgReplicationSlotConsumerReconnectTest.pollUntilReceived;
import static org.apache.camel.component.pg.replication.slot.PgReplicationSlotConsumerReconnectTest.startRoute;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The exchanges of the consumer. The JDBC objects are the mocks of {@link PgReplicationSlotConsumerReconnectTest}.
 */
class PgReplicationSlotConsumerTest {

    @Test
    void messagesWithTheSameLsnHaveTheirOwnExchangeId() throws Exception {
        FakeServer server = new FakeServer(new IllegalStateException("not used"));
        // test_decoding sends the BEGIN and the first change of a transaction with the same LSN
        server.sameLsn = LogSequenceNumber.valueOf("0/16B3748");
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("BEGIN 750");
            server.publish("table public.account: INSERT: id[integer]:1");
            pollUntilReceived(consumer, server, 2);

            assertEquals(2, new HashSet<>(server.exchangeIds).size(),
                    "the exchange ids are not unique: " + server.exchangeIds);
            assertEquals(List.of("0/16B3748", "0/16B3748"), server.lsns, "the LSN header");
        }
    }

    @Test
    void theLsnOfTheMessageIsInAHeader() throws Exception {
        FakeServer server = new FakeServer(new IllegalStateException("not used"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            server.publish("second");
            pollUntilReceived(consumer, server, 2);

            assertEquals(List.of("first", "second"), server.received);
            assertEquals(List.of("0/1", "0/2"), server.lsns, "the LSN header");
        }
    }
}
