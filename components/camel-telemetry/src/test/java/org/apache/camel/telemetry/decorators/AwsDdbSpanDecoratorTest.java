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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AwsDdbSpanDecoratorTest {

    @Test
    public void testPre() {
        String tableName = "myTable";
        String operation = "PutItem";
        String indexName = "myIndex";
        Double consumedCapacity = 5.0;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("aws2-ddb:myTable");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getExchangeId()).thenReturn("exchange-1");
        Mockito.when(message.getHeader(AwsDdbSpanDecorator.TABLE_NAME, String.class)).thenReturn(tableName);
        Mockito.when(message.getHeader(AwsDdbSpanDecorator.OPERATION)).thenReturn(operation);
        Mockito.when(message.getHeader(AwsDdbSpanDecorator.INDEX_NAME, String.class)).thenReturn(indexName);
        Mockito.when(message.getHeader(AwsDdbSpanDecorator.CONSUMED_CAPACITY, Double.class)).thenReturn(consumedCapacity);

        AbstractSpanDecorator decorator = new AwsDdbSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals("dynamodb", span.tags().get(TagConstants.DB_SYSTEM));
        assertEquals(tableName, span.tags().get(AwsDdbSpanDecorator.DDB_TABLE_NAME));
        assertEquals(tableName, span.tags().get(TagConstants.DB_NAME));
        assertEquals(operation, span.tags().get(AwsDdbSpanDecorator.DDB_OPERATION));
        assertEquals(indexName, span.tags().get(AwsDdbSpanDecorator.DDB_INDEX_NAME));
        assertEquals(consumedCapacity.toString(), span.tags().get(AwsDdbSpanDecorator.DDB_CONSUMED_CAPACITY));
    }

}
