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
package org.apache.camel.component.google.bigquery.unit.sql;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GoogleBigQuerySQLProducerSelectTest extends GoogleBigQuerySQLProducerBaseTest {
    protected TableResult nextPageTableResult;

    @BeforeEach
    public void init() throws Exception {
        sql = "SELECT id, name FROM testDataset.testTable";
        setupBigqueryMock();
        producer = createAndStartProducer();
    }

    @Test
    public void fetchDataFromBigQuery() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        ArgumentCaptor<JobInfo> jobCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigquery).create(jobCaptor.capture());

        QueryJobConfiguration executedQuery = jobCaptor.getValue().getConfiguration();
        assertEquals(sql, executedQuery.getQuery());

        Message message = exchange.getMessage();
        List<Map<String, Object>> rows = message.getBody(List.class);
        assertNotNull(rows);
        assertEquals(2, rows.size());

        Map<String, Object> firstRow = rows.get(0);
        assertEquals(1L, firstRow.get("id"));
        assertEquals("Alice", firstRow.get("name"));

        Map<String, Object> secondRow = rows.get(1);
        assertEquals(2L, secondRow.get("id"));
        assertEquals("Bob", secondRow.get("name"));

        assertEquals("PAGE_TWO_TOKEN", message.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN));
    }

    @Test
    public void fetchDataWithPageTokenFromHeader() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getMessage().setHeader(GoogleBigQueryConstants.PAGE_TOKEN, "HEADER_TOKEN");
        producer.process(exchange);

        ArgumentCaptor<JobInfo> jobCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigquery).create(jobCaptor.capture());

        QueryJobConfiguration executedQuery = jobCaptor.getValue().getConfiguration();
        assertEquals(sql, executedQuery.getQuery());

        Message message = exchange.getMessage();
        List<Map<String, Object>> rows = message.getBody(List.class);
        assertNotNull(rows);
        assertEquals(2, rows.size());

        Map<String, Object> firstRow = rows.get(0);
        assertEquals(3L, firstRow.get("id"));
        assertEquals("Charlie", firstRow.get("name"));

        Map<String, Object> secondRow = rows.get(1);
        assertEquals(4L, secondRow.get("id"));
        assertEquals("Delta", secondRow.get("name"));

        assertEquals("PAGE_THREE_TOKEN", message.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN));
    }

    @Test
    public void fetchDataWithPageTokenFromEndpoint() throws Exception {
        configuration.setPageToken("ENDPOINT_TOKEN");

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        ArgumentCaptor<JobInfo> jobCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigquery).create(jobCaptor.capture());

        QueryJobConfiguration executedQuery = jobCaptor.getValue().getConfiguration();
        assertEquals(sql, executedQuery.getQuery());

        Message message = exchange.getMessage();
        List<Map<String, Object>> rows = message.getBody(List.class);
        assertNotNull(rows);
        assertEquals(2, rows.size());

        Map<String, Object> firstRow = rows.get(0);
        assertEquals(3L, firstRow.get("id"));
        assertEquals("Charlie", firstRow.get("name"));

        Map<String, Object> secondRow = rows.get(1);
        assertEquals(4L, secondRow.get("id"));
        assertEquals("Delta", secondRow.get("name"));

        assertEquals("PAGE_THREE_TOKEN", message.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN));
    }

    @Override
    protected void setupBigqueryMock() throws Exception {
        super.setupBigqueryMock();

        // Mock schema
        Field idField = Field.of("id", StandardSQLTypeName.INT64);
        Field nameField = Field.of("name", StandardSQLTypeName.STRING);
        Schema schema = Schema.of(idField, nameField);

        // Mock row data
        FieldList fieldList = FieldList.of(idField, nameField);
        FieldValueList row1 = FieldValueList.of(
                Arrays.asList(FieldValue.of(FieldValue.Attribute.PRIMITIVE, 1L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Alice")),
                fieldList);
        FieldValueList row2 = FieldValueList.of(
                Arrays.asList(FieldValue.of(FieldValue.Attribute.PRIMITIVE, 2L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Bob")),
                fieldList);
        FieldValueList row3 = FieldValueList.of(
                Arrays.asList(FieldValue.of(FieldValue.Attribute.PRIMITIVE, 3L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Charlie")),
                fieldList);
        FieldValueList row4 = FieldValueList.of(
                Arrays.asList(FieldValue.of(FieldValue.Attribute.PRIMITIVE, 4L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Delta")),
                fieldList);

        when(tableResult.getSchema()).thenReturn(schema);
        when(tableResult.getValues()).thenReturn(Arrays.asList(row1, row2));
        when(tableResult.getNextPageToken()).thenReturn("PAGE_TWO_TOKEN");
        when(statistics.getNumDmlAffectedRows()).thenReturn(null);

        // Mock paging
        nextPageTableResult = mock(TableResult.class);
        when(job.getQueryResults(any(BigQuery.QueryResultsOption.class))).thenReturn(nextPageTableResult);
        when(nextPageTableResult.getSchema()).thenReturn(schema);
        when(nextPageTableResult.getValues()).thenReturn(Arrays.asList(row3, row4));
        when(nextPageTableResult.getNextPageToken()).thenReturn("PAGE_THREE_TOKEN");
    }

}
