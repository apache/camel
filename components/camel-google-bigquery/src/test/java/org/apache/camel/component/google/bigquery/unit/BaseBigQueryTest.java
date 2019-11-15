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
package org.apache.camel.component.google.bigquery.unit;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.TableDataInsertAllResponse;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConfiguration;
import org.apache.camel.component.google.bigquery.GoogleBigQueryEndpoint;
import org.apache.camel.component.google.bigquery.GoogleBigQueryProducer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseBigQueryTest extends CamelTestSupport {
    protected GoogleBigQueryEndpoint endpoint = mock(GoogleBigQueryEndpoint.class);
    protected Bigquery.Tabledata.InsertAll mockInsertall = mock(Bigquery.Tabledata.InsertAll.class);
    protected GoogleBigQueryProducer producer;
    protected Bigquery.Tabledata tabledataMock;
    protected String tableId = "testTableId";
    protected String datasetId = "testDatasetId";
    protected String projectId = "testProjectId";
    protected GoogleBigQueryConfiguration configuration = new GoogleBigQueryConfiguration();
    protected Bigquery bigquery;

    @Before
    public void init() throws Exception {
        setupBigqueryMock();
        producer = createProducer();

    }

    protected GoogleBigQueryProducer createProducer() throws Exception {
        configuration.setProjectId(projectId);
        configuration.setTableId(tableId);
        configuration.setDatasetId(datasetId);
        configuration.setTableId("testTableId");

        return new GoogleBigQueryProducer(bigquery, endpoint, configuration);
    }

    protected void setupBigqueryMock() throws Exception {
        bigquery = mock(Bigquery.class);

        tabledataMock = mock(Bigquery.Tabledata.class);
        when(bigquery.tabledata()).thenReturn(tabledataMock);
        when(tabledataMock.insertAll(anyString(), anyString(), anyString(), any())).thenReturn(mockInsertall);

        TableDataInsertAllResponse mockResponse = new TableDataInsertAllResponse();
        when(mockInsertall.execute()).thenReturn(mockResponse);
    }
}
