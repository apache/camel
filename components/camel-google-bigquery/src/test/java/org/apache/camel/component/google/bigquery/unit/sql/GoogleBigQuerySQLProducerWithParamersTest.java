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

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.apache.camel.component.google.bigquery.integration.BigQueryTestSupport.PROJECT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class GoogleBigQuerySQLProducerWithParamersTest extends GoogleBigQuerySQLProducerBaseTest {

    @BeforeEach
    public void init() throws Exception {
        sql = "insert into testDatasetId.testTableId(id, data) values(@id, @data)";
        setupBigqueryMock();
        producer = createAndStartProducer();
    }

    @Test
    public void sendMessageWithParametersInBody() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("id", "100");
        body.put("data", "some data");
        producer.process(createExchangeWithBody(body));

        ArgumentCaptor<QueryJobConfiguration> dataCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
        verify(bigquery).query(dataCaptor.capture(), any(JobId.class));

        QueryJobConfiguration request = dataCaptor.getValue();
        assertEquals(sql, request.getQuery());

        Map<String, QueryParameterValue> namedParameters = request.getNamedParameters();
        assertEquals(2, namedParameters.size());

        assertTrue(namedParameters.containsKey("id"));
        assertEquals("100", namedParameters.get("id").getValue());

        assertTrue(namedParameters.containsKey("data"));
        assertEquals("some data", namedParameters.get("data").getValue());
    }

    @Test
    public void sendMessageWithParametersInBodyAndHeaders() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("id", "100");

        Exchange exchange = createExchangeWithBody(body);
        Message message = exchange.getMessage();
        message.setHeader("id", "200");
        message.setHeader("data", "some data");

        producer.process(exchange);

        ArgumentCaptor<QueryJobConfiguration> dataCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
        verify(bigquery).query(dataCaptor.capture(), any(JobId.class));

        QueryJobConfiguration request = dataCaptor.getValue();
        assertEquals(sql, request.getQuery());

        Map<String, QueryParameterValue> namedParameters = request.getNamedParameters();
        assertEquals(2, namedParameters.size());

        assertTrue(namedParameters.containsKey("id"));
        assertEquals("100", namedParameters.get("id").getValue(), "Body data must have higher priority");

        assertTrue(namedParameters.containsKey("data"));
        assertEquals("some data", namedParameters.get("data").getValue());
    }

    @Test
    public void sendMessageWithJobIdHeader() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("id", "100");

        Exchange exchange = createExchangeWithBody(body);
        Message message = exchange.getMessage();
        message.setHeader("id", "200");
        message.setHeader("data", "some data");

        JobId jobId = JobId.of(PROJECT_ID, "a-test-job");
        message.setHeader(GoogleBigQueryConstants.JOB_ID, jobId);

        producer.process(exchange);

        ArgumentCaptor<QueryJobConfiguration> dataCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
        verify(bigquery).query(dataCaptor.capture(), eq(jobId));

        QueryJobConfiguration request = dataCaptor.getValue();
        assertEquals(sql, request.getQuery());

        Map<String, QueryParameterValue> namedParameters = request.getNamedParameters();
        assertEquals(2, namedParameters.size());

        assertTrue(namedParameters.containsKey("id"));
        assertEquals("100", namedParameters.get("id").getValue(), "Body data must have higher priority");

        assertTrue(namedParameters.containsKey("data"));
        assertEquals("some data", namedParameters.get("data").getValue());
    }

    @Test
    public void sendMessageWithoutParameters() throws Exception {
        assertThrows(RuntimeExchangeException.class,
                () -> producer.process(createExchangeWithBody(new HashMap<>())));
    }
}
