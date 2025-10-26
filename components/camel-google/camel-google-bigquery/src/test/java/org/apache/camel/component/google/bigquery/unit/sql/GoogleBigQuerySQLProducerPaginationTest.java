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
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.apache.camel.component.google.bigquery.sql.OutputType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GoogleBigQuerySQLProducerPaginationTest extends GoogleBigQuerySQLProducerBaseTest {

    @BeforeEach
    public void init() throws Exception {
        sql = "SELECT id, name FROM testDataset.testTable LIMIT 100";
        setupBigqueryMock();
        when(statistics.getStatementType()).thenReturn(JobStatistics.QueryStatistics.StatementType.SELECT);

        when(job.getQueryResults(any(BigQuery.QueryResultsOption[].class))).thenReturn(tableResult);

        producer = createAndStartProducer();
    }

    @Test
    public void fetchDataWithPageSizeInConfiguration() throws Exception {
        long expectedPageSize = 10;
        configuration.setPageSize(expectedPageSize);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        ArgumentCaptor<BigQuery.QueryResultsOption[]> dataCaptor = ArgumentCaptor.forClass(BigQuery.QueryResultsOption[].class);
        verify(job).getQueryResults(dataCaptor.capture());
        BigQuery.QueryResultsOption[] capturedOptions = dataCaptor.getValue();

        assertNotNull(capturedOptions, "Options should have been captured");
        assertEquals(1, capturedOptions.length, "Should have one option for page size");
        assertEquals(BigQuery.QueryResultsOption.pageSize(expectedPageSize), capturedOptions[0]);
    }

    @Test
    public void fetchDataWithPageSizeZeroDoesNotSetOption() throws Exception {
        configuration.setPageSize(0);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        ArgumentCaptor<BigQuery.QueryResultsOption[]> dataCaptor = ArgumentCaptor.forClass(BigQuery.QueryResultsOption[].class);
        verify(job).getQueryResults(dataCaptor.capture());
        BigQuery.QueryResultsOption[] capturedOptions = dataCaptor.getValue();

        assertNotNull(capturedOptions, "Options should have been captured");
        assertEquals(0, capturedOptions.length, "Should have no options when page size is 0");
    }

    @Test
    public void selectListSetsNextPageTokenAndJobId() throws Exception {
        configuration.setOutputType(OutputType.SELECT_LIST);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();

        assertEquals("next_page_token_123", message.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN));
        assertEquals(JobId.of("JOB_ID"), message.getHeader(GoogleBigQueryConstants.JOB_ID));

        List<Map<String, Object>> rows = message.getBody(List.class);
        assertNotNull(rows);
        assertEquals(2, rows.size());
    }

    @Test
    public void streamListDoesNotSetPaginationHeaders() throws Exception {
        configuration.setOutputType(OutputType.STREAM_LIST);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();

        assertNull(message.getHeader(GoogleBigQueryConstants.NEXT_PAGE_TOKEN));
        assertNull(message.getHeader(GoogleBigQueryConstants.JOB_ID));
    }

    @Test
    public void combinedPageSizeAndPageToken() throws Exception {
        long expectedPageSize = 50;
        String expectedToken = "start_token";
        configuration.setPageSize(expectedPageSize);
        configuration.setPageToken(expectedToken);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        ArgumentCaptor<BigQuery.QueryResultsOption[]> dataCaptor = ArgumentCaptor.forClass(BigQuery.QueryResultsOption[].class);
        verify(job).getQueryResults(dataCaptor.capture());
        BigQuery.QueryResultsOption[] capturedOptions = dataCaptor.getValue();

        assertNotNull(capturedOptions, "Options should have been captured");
        assertEquals(2, capturedOptions.length, "Should have two options for page token and page size");

        boolean hasPageToken = Arrays.stream(capturedOptions)
                .anyMatch(opt -> opt.equals(BigQuery.QueryResultsOption.pageToken(expectedToken)));
        boolean hasPageSize = Arrays.stream(capturedOptions)
                .anyMatch(opt -> opt.equals(BigQuery.QueryResultsOption.pageSize(expectedPageSize)));

        assertTrue(hasPageToken, "Should contain page token option");
        assertTrue(hasPageSize, "Should contain page size option");
    }

    @Override
    protected void setupBigqueryMock() throws Exception {
        super.setupBigqueryMock();

        Field idField = Field.of("id", StandardSQLTypeName.INT64);
        Field nameField = Field.of("name", StandardSQLTypeName.STRING);
        Schema schema = Schema.of(idField, nameField);

        FieldList fieldList = FieldList.of(idField, nameField);
        FieldValueList row1 = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 1L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Row1")),
                fieldList);
        FieldValueList row2 = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 2L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Row2")),
                fieldList);

        List<FieldValueList> rows = Arrays.asList(row1, row2);

        when(tableResult.getSchema()).thenReturn(schema);
        when(tableResult.getValues()).thenReturn(rows);
        when(tableResult.iterateAll()).thenReturn(rows::iterator);
        when(tableResult.getNextPageToken()).thenReturn("next_page_token_123");
        when(job.getJobId()).thenReturn(JobId.of("JOB_ID"));
    }
}
