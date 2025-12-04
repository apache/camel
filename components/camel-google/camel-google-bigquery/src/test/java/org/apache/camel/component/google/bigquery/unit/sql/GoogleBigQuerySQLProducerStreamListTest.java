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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.bigquery.sql.OutputType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GoogleBigQuerySQLProducerStreamListTest extends GoogleBigQuerySQLProducerBaseTest {

    @BeforeEach
    public void init() throws Exception {
        sql = "SELECT id, name, age FROM testDataset.testTable";
        setupBigqueryMock();
        when(statistics.getStatementType()).thenReturn(JobStatistics.QueryStatistics.StatementType.SELECT);
        configuration.setOutputType(OutputType.STREAM_LIST);
        producer = createAndStartProducer();
    }

    @Test
    public void fetchDataFromBigQueryAsStreamList() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();
        Object body = message.getBody();
        assertNotNull(body);
        assertInstanceOf(Iterator.class, body);

        Iterator<Map<String, Object>> iterator = (Iterator<Map<String, Object>>) body;
        assertTrue(iterator.hasNext(), "Iterator should have data");

        List<Map<String, Object>> rows = new ArrayList<>();
        iterator.forEachRemaining(rows::add);

        assertEquals(3, rows.size());

        Map<String, Object> firstRow = rows.get(0);
        assertEquals(1L, firstRow.get("id"));
        assertEquals("Alice", firstRow.get("name"));
        assertEquals(25L, firstRow.get("age"));

        Map<String, Object> secondRow = rows.get(1);
        assertEquals(2L, secondRow.get("id"));
        assertEquals("Bob", secondRow.get("name"));
        assertEquals(30L, secondRow.get("age"));

        Map<String, Object> thirdRow = rows.get(2);
        assertEquals(3L, thirdRow.get("id"));
        assertEquals("Charlie", thirdRow.get("name"));
        assertEquals(35L, thirdRow.get("age"));
    }

    @Test
    public void streamListWithNullValues() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();
        Iterator<Map<String, Object>> iterator = (Iterator<Map<String, Object>>) message.getBody();
        assertNotNull(iterator);

        List<Map<String, Object>> rows = new ArrayList<>();
        iterator.forEachRemaining(row -> rows.add(row));

        assertEquals(3, rows.size());

        Map<String, Object> thirdRow = rows.get(2);
        assertTrue(thirdRow.containsKey("age"), "Row should contain age key even if null");
    }

    @Test
    public void streamListWithEmptyResult() throws Exception {
        when(tableResult.getValues()).thenReturn(List.of());
        when(tableResult.iterateAll()).thenReturn(Collections::emptyIterator);

        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();
        Iterator<?> iterator = (Iterator<?>) message.getBody();
        assertNotNull(iterator);
        assertFalse(iterator.hasNext(), "Iterator should be empty");
    }

    @Test
    public void streamListDoesNotSetNextPageTokenHeader() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();
        assertFalse(
                message.getHeaders().containsKey("CamelGoogleBigQueryNextPageToken"),
                "STREAM_LIST mode should not set NextPageToken header");
        assertFalse(
                message.getHeaders().containsKey("CamelGoogleBigQueryJobId"),
                "STREAM_LIST mode should not set JobId header");
    }

    @Test
    public void streamListHandlesMultipleDataTypes() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        producer.process(exchange);

        Message message = exchange.getMessage();
        Iterator<Map<String, Object>> iterator = (Iterator<Map<String, Object>>) message.getBody();

        List<Map<String, Object>> rows = new ArrayList<>();
        iterator.forEachRemaining(row -> rows.add(row));

        assertEquals(3, rows.size());

        Map<String, Object> firstRow = rows.get(0);
        assertInstanceOf(Long.class, firstRow.get("id"), "ID should be Long");
        assertInstanceOf(String.class, firstRow.get("name"), "Name should be String");
        assertInstanceOf(Long.class, firstRow.get("age"), "Age should be Long");
    }

    @Override
    protected void setupBigqueryMock() throws Exception {
        super.setupBigqueryMock();

        Field idField = Field.of("id", StandardSQLTypeName.INT64);
        Field nameField = Field.of("name", StandardSQLTypeName.STRING);
        Field ageField = Field.of("age", StandardSQLTypeName.INT64);
        Schema schema = Schema.of(idField, nameField, ageField);

        FieldList fieldList = FieldList.of(idField, nameField, ageField);

        FieldValueList row1 = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 1L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Alice"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 25L)),
                fieldList);

        FieldValueList row2 = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 2L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Bob"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 30L)),
                fieldList);

        FieldValueList row3 = FieldValueList.of(
                Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 3L),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "Charlie"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, 35L)),
                fieldList);

        List<FieldValueList> rows = Arrays.asList(row1, row2, row3);

        when(tableResult.getSchema()).thenReturn(schema);
        when(tableResult.getValues()).thenReturn(rows);
        when(tableResult.iterateAll()).thenReturn(rows::iterator);
        when(statistics.getNumDmlAffectedRows()).thenReturn(null);
    }
}
