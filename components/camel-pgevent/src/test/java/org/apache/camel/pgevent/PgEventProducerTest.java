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
package org.apache.camel.pgevent;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.jdbc.PGDataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.pgevent.InvalidStateException;
import org.apache.camel.component.pgevent.PgEventEndpoint;
import org.apache.camel.component.pgevent.PgEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PgEventProducerTest {

    private PgEventEndpoint endpoint = mock(PgEventEndpoint.class);
    private PGDataSource dataSource = mock(PGDataSource.class);
    private PGConnection connection = mock(PGConnection.class);
    private PreparedStatement statement = mock(PreparedStatement.class);
    private Exchange exchange = mock(Exchange.class);
    private Message message = mock(Message.class);

    @Test
    public void testPgEventProducerStart() throws Exception {
        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();

        assertTrue(producer.isStarted());
    }

    @Test
    public void testPgEventProducerStop() throws Exception {
        when(endpoint.initJdbc()).thenReturn(connection);

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();
        producer.stop();

        verify(connection).close();
        assertTrue(producer.isStopped());
    }

    @Test
    public void testPgEventProducerProcessDbThrowsInvalidStateException() throws Exception {
        when(endpoint.initJdbc()).thenReturn(connection);
        when(connection.isClosed()).thenThrow(new SQLException("DB problem occurred"));

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();
        assertThrows(InvalidStateException.class,
                () -> producer.process(exchange));
    }

    @Test
    public void testPgEventProducerProcessDbConnectionClosed() throws Exception {
        PGConnection connectionNew = mock(PGConnection.class);

        when(endpoint.initJdbc()).thenReturn(connection);
        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection, connectionNew);
        when(connection.isClosed()).thenReturn(true);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("pgevent");
        when(endpoint.getChannel()).thenReturn("camel");
        when(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(statement);

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();
        producer.process(exchange);

        verify(statement).execute();
    }

    @Test
    public void testPgEventProducerProcessServerMinimumVersionMatched() throws Exception {
        CallableStatement statement = mock(CallableStatement.class);

        when(endpoint.initJdbc()).thenReturn(connection);
        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(connection.isClosed()).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("pgevent");
        when(endpoint.getChannel()).thenReturn("camel");
        when(connection.isServerMinimumVersion(9, 0)).thenReturn(true);
        when(connection.prepareCall(ArgumentMatchers.anyString())).thenReturn(statement);

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();
        producer.process(exchange);

        verify(statement).execute();
    }

    @Test
    public void testPgEventProducerProcessServerMinimumVersionNotMatched() throws Exception {
        when(endpoint.initJdbc()).thenReturn(connection);
        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(connection.isClosed()).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn("pgevent");
        when(endpoint.getChannel()).thenReturn("camel");
        when(connection.isServerMinimumVersion(9, 0)).thenReturn(false);
        when(connection.prepareStatement("NOTIFY camel, 'pgevent'")).thenReturn(statement);

        PgEventProducer producer = new PgEventProducer(endpoint);
        producer.start();
        producer.process(exchange);

        verify(statement).execute();
    }
}
