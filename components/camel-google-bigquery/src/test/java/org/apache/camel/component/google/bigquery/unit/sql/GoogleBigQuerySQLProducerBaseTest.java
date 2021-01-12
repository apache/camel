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

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLConfiguration;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLProducer;
import org.apache.camel.test.junit5.CamelTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleBigQuerySQLProducerBaseTest extends CamelTestSupport {
    protected GoogleBigQuerySQLEndpoint endpoint = mock(GoogleBigQuerySQLEndpoint.class);
    protected GoogleBigQuerySQLProducer producer;
    protected String sql;
    protected String projectId = "testProjectId";
    protected GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration();
    protected BigQuery bigquery;
    protected TableResult tableResult;

    protected GoogleBigQuerySQLProducer createAndStartProducer() throws Exception {
        configuration.setProjectId(projectId);
        configuration.setQuery(sql);

        GoogleBigQuerySQLProducer sqlProducer = new GoogleBigQuerySQLProducer(bigquery, endpoint, configuration);
        sqlProducer.start();
        return sqlProducer;
    }

    protected void setupBigqueryMock() throws Exception {
        bigquery = mock(BigQuery.class);
        tableResult = mock(TableResult.class);
        when(bigquery.query(any(QueryJobConfiguration.class), any(JobId.class))).thenReturn(tableResult);
    }
}
