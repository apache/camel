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
package org.apache.camel.component.drill;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DrillProducerTest {

    private DrillEndpoint endpoint;
    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private Exchange exchange;
    private Message message;

    @BeforeEach
    void setUp() {
        endpoint = mock(DrillEndpoint.class);
        when(endpoint.getCamelContext()).thenReturn(null);
        when(endpoint.getEndpointUri()).thenReturn("drill://localhost");

        connection = mock(Connection.class);
        statement = mock(Statement.class);
        resultSet = mock(ResultSet.class);
        exchange = mock(Exchange.class);
        message = mock(Message.class);

        when(exchange.getIn()).thenReturn(message);
    }

    @Test
    void testProcessClosesStatementAndResultSet() throws Exception {
        String query = "SELECT * FROM test";
        when(message.getHeader(DrillConstants.DRILL_QUERY, String.class)).thenReturn(query);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(query)).thenReturn(resultSet);
        when(endpoint.queryForList(any(ResultSet.class))).thenReturn(Collections.emptyList());

        DrillProducer producer = new DrillProducer(endpoint);
        setConnection(producer, connection);

        producer.process(exchange);

        verify(resultSet).close();
        verify(statement).close();
    }

    @Test
    void testProcessClosesResourcesOnQueryFailure() throws Exception {
        String query = "SELECT * FROM test";
        when(message.getHeader(DrillConstants.DRILL_QUERY, String.class)).thenReturn(query);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(query)).thenThrow(new SQLException("query failed"));

        DrillProducer producer = new DrillProducer(endpoint);
        setConnection(producer, connection);

        try {
            producer.process(exchange);
        } catch (SQLException e) {
            // expected
        }

        verify(statement).close();
    }

    @Test
    void testDoStopClosesConnection() throws Exception {
        DrillProducer producer = new DrillProducer(endpoint);
        setConnection(producer, connection);

        producer.doStop();

        verify(connection).close();
    }

    @Test
    void testDoStopHandlesCloseException() throws Exception {
        doThrow(new SQLException("close failed")).when(connection).close();

        DrillProducer producer = new DrillProducer(endpoint);
        setConnection(producer, connection);

        // should not throw
        producer.doStop();

        verify(connection).close();
    }

    @Test
    void testDoStopHandlesNullConnection() throws Exception {
        DrillProducer producer = new DrillProducer(endpoint);
        setConnection(producer, null);

        // should not throw
        producer.doStop();
    }

    private void setConnection(DrillProducer producer, Connection conn) throws Exception {
        java.lang.reflect.Field field = DrillProducer.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(producer, conn);
    }
}
