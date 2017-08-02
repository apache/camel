/**
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
package org.apache.camel.component.google.bigquery.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.bigquery.model.TableDataInsertAllRequest;
import com.google.common.collect.Lists;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GoogleBigQueryProducerTest extends BaseBigQueryTest {
    @Test
    public void sendMessage() throws Exception {
        producer.process(createExchangeWithBody(new HashMap<>()));
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock).insertAll(eq(projectId), eq(datasetId), eq(tableId), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(1, requests.size());
        assertEquals(1, requests.get(0).getRows().size());
        assertNull(requests.get(0).getRows().get(0).getInsertId());
    }

    @Test
    public void sendMessageWithTableId() throws Exception {
        Exchange exchange = createExchangeWithBody(new HashMap<>());
        exchange.getIn().setHeader(GoogleBigQueryConstants.TABLE_ID, "exchange_table_id");
        producer.process(exchange);
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock).insertAll(eq(projectId), eq(datasetId), eq("exchange_table_id"), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(1, requests.size());
        assertEquals(1, requests.get(0).getRows().size());
        assertNull(requests.get(0).getRows().get(0).getInsertId());
    }


    @Test
    public void useAsInsertIdConfig() throws Exception {
        configuration.setUseAsInsertId("row1");
        Map<String, String> object = new HashMap<>();
        object.put("row1", "value1");
        producer.process(createExchangeWithBody(object));
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock).insertAll(eq(projectId), eq(datasetId), eq(tableId), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(1, requests.size());
        assertEquals(1, requests.get(0).getRows().size());
        assertEquals("value1", requests.get(0).getRows().get(0).getInsertId());
    }

    @Test
    public void listOfMessages() throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(new HashMap<>());
        messages.add(new HashMap<>());
        producer.process(createExchangeWithBody(messages));
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock).insertAll(eq(projectId), eq(datasetId), eq(tableId), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(1, requests.size());
        assertEquals(2, requests.get(0).getRows().size());
    }

    @Test
    public void groupExchange() throws Exception {
        Exchange exchange1 = createExchangeWithBody(new HashMap<>());
        Exchange exchange2 = createExchangeWithBody(new HashMap<>());
        Exchange exchange = createExchangeWithBody(null);
        exchange.setProperty(Exchange.GROUPED_EXCHANGE, Lists.newArrayList(exchange1, exchange2));

        producer.process(exchange);
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock).insertAll(eq(projectId), eq(datasetId), eq(tableId), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(1, requests.size());
        assertEquals(2, requests.get(0).getRows().size());
    }

    @Test
    public void groupExchangeDifferentTableSuffix() throws Exception {
        Exchange exchange1 = createExchangeWithBody(new HashMap<>());
        exchange1.getIn().setHeader(GoogleBigQueryConstants.TABLE_SUFFIX, "_SUFFIX1");
        Exchange exchange2 = createExchangeWithBody(new HashMap<>());
        exchange2.getIn().setHeader(GoogleBigQueryConstants.TABLE_SUFFIX, "_SUFFIX2");
        Exchange exchange = createExchangeWithBody(null);
        exchange.setProperty(Exchange.GROUPED_EXCHANGE, Lists.newArrayList(exchange1, exchange2));

        producer.process(exchange);
        ArgumentCaptor<TableDataInsertAllRequest> dataCaptor = ArgumentCaptor.forClass(TableDataInsertAllRequest.class);
        verify(tabledataMock, times(2)).insertAll(eq(projectId), eq(datasetId), eq(tableId), dataCaptor.capture());
        List<TableDataInsertAllRequest> requests = dataCaptor.getAllValues();
        assertEquals(2, requests.size());
        assertEquals(1, requests.get(0).getRows().size());
        assertEquals(1, requests.get(1).getRows().size());
    }
}
