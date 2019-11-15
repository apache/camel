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

import java.sql.PreparedStatement;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.jdbc.PGDataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.pgevent.PgEventConsumer;
import org.apache.camel.component.pgevent.PgEventEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PgEventConsumerTest {

    @Test
    public void testPgEventConsumerStart() throws Exception {
        PGDataSource dataSource = mock(PGDataSource.class);
        PGConnection connection = mock(PGConnection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        PgEventEndpoint endpoint = mock(PgEventEndpoint.class);
        Processor processor = mock(Processor.class);

        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("LISTEN camel")).thenReturn(statement);
        when(endpoint.getChannel()).thenReturn("camel");

        PgEventConsumer consumer = new PgEventConsumer(endpoint, processor);
        consumer.start();

        verify(connection).addNotificationListener("camel", "camel", consumer);
        assertTrue(consumer.isStarted());
    }

    @Test
    public void testPgEventConsumerStop() throws Exception {
        PGDataSource dataSource = mock(PGDataSource.class);
        PGConnection connection = mock(PGConnection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        PgEventEndpoint endpoint = mock(PgEventEndpoint.class);
        Processor processor = mock(Processor.class);

        when(endpoint.getDatasource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("LISTEN camel")).thenReturn(statement);
        when(endpoint.getChannel()).thenReturn("camel");
        when(connection.prepareStatement("UNLISTEN camel")).thenReturn(statement);

        PgEventConsumer consumer = new PgEventConsumer(endpoint, processor);
        consumer.start();
        consumer.stop();

        verify(connection).removeNotificationListener("camel");
        verify(connection).close();
        assertTrue(consumer.isStopped());
    }

    @Test
    public void testPgEventNotification() throws Exception {
        PgEventEndpoint endpoint = mock(PgEventEndpoint.class);
        Processor processor = mock(Processor.class);
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);

        when(endpoint.createExchange()).thenReturn(exchange);
        when(exchange.getIn()).thenReturn(message);

        PgEventConsumer consumer = new PgEventConsumer(endpoint, processor);
        consumer.notification(1, "camel", "some event");

        verify(message).setHeader("channel", "camel");
        verify(message).setBody("some event");
        verify(processor).process(exchange);
    }
}
