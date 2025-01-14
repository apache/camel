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
import org.apache.camel.telemetry.SpanDecorator;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ElasticsearchSpanDecoratorTest {

    @Test
    public void testOperationName() {
        String opName = "INDEX";
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("elasticsearch://local?operation="
                                                           + opName + "&indexName=twitter&indexType=tweet");

        SpanDecorator decorator = new ElasticsearchSpanDecorator();

        assertEquals(opName, decorator.getOperationName(null, endpoint));
    }

    @Test
    public void testPre() {
        String indexName = "twitter";
        String cluster = "local";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("elasticsearch://" + cluster
                                                           + "?operation=INDEX&indexName=" + indexName + "&indexType=tweet");
        Mockito.when(exchange.getIn()).thenReturn(message);

        SpanDecorator decorator = new ElasticsearchSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.beforeTracingEvent(span, exchange, endpoint);

        assertEquals(ElasticsearchSpanDecorator.ELASTICSEARCH_DB_TYPE, span.tags().get(TagConstants.DB_SYSTEM));
        assertEquals(indexName, span.tags().get(TagConstants.DB_NAME));
        assertNull(span.tags().get(TagConstants.SERVER_ADDRESS));
    }

}
