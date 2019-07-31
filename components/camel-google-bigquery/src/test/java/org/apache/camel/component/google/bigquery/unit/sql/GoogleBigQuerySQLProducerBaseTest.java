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

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.QueryResponse;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLConfiguration;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLProducer;
import org.apache.camel.test.junit4.CamelTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleBigQuerySQLProducerBaseTest extends CamelTestSupport {
    protected GoogleBigQuerySQLEndpoint endpoint = mock(GoogleBigQuerySQLEndpoint.class);
    protected Bigquery.Jobs mockJobs = mock(Bigquery.Jobs.class);
    protected Bigquery.Jobs.Query mockQuery = mock(Bigquery.Jobs.Query.class);
    protected GoogleBigQuerySQLProducer producer;
    protected String sql;
    protected String projectId = "testProjectId";
    protected GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration();
    protected Bigquery bigquery;

    protected GoogleBigQuerySQLProducer createAndStartProducer() throws Exception {
        configuration.setProjectId(projectId);
        configuration.setQuery(sql);

        GoogleBigQuerySQLProducer sqlProducer = new GoogleBigQuerySQLProducer(bigquery, endpoint, configuration);
        sqlProducer.start();
        return sqlProducer;
    }

    protected void setupBigqueryMock() throws Exception {
        bigquery = mock(Bigquery.class);

        when(bigquery.jobs()).thenReturn(mockJobs);
        when(bigquery.jobs().query(anyString(), any())).thenReturn(mockQuery);

        QueryResponse mockResponse = new QueryResponse().setNumDmlAffectedRows(1L);
        when(mockQuery.execute()).thenReturn(mockResponse);
    }
}
