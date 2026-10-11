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
package org.apache.camel.component.pgevent;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import com.impossibl.postgres.api.jdbc.PGConnection;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer must connect again and listen to the channel again every time the connection to the database is lost,
 * not only the first time.
 */
class PgEventConsumerReconnectTest extends CamelTestSupport {

    // the connections the data source has handed out
    private final List<PGConnection> connections = new CopyOnWriteArrayList<>();

    @BindToRegistry("pgDataSource")
    private final DataSource dataSource = mock(DataSource.class);

    @Test
    void reconnectsAfterEveryConnectionLoss() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            PGConnection connection = mock(PGConnection.class);
            when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
            connections.add(connection);
            return connection;
        });

        PgEventEndpoint endpoint = context.getEndpoint(
                "pgevent:///testdb/testchannel?datasource=#pgDataSource&reconnectDelay=100", PgEventEndpoint.class);
        PgEventConsumer consumer = (PgEventConsumer) endpoint.createConsumer(exchange -> {
        });
        consumer.start();
        try {
            assertEquals(1, connections.size());

            // the driver reports a lost connection; the call returns when the consumer has connected again
            consumer.getPgEventListener().closed();
            assertEquals(2, connections.size(), "The consumer must reconnect after the first connection loss");

            consumer.getPgEventListener().closed();
            assertEquals(3, connections.size(), "The consumer must reconnect after the second connection loss");

            // and it listens to the channel on the new connection
            PGConnection last = connections.get(2);
            verify(last).prepareStatement("LISTEN testchannel");
            verify(last).addNotificationListener("testchannel", "testchannel", consumer.getPgEventListener());
        } finally {
            consumer.stop();
        }
    }
}
