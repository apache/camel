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
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLConfiguration;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLProducer;
import org.apache.camel.test.junit5.CamelTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class GoogleBigQuerySQLProducerBaseTest extends CamelTestSupport {
    protected GoogleBigQuerySQLEndpoint endpoint = mock(GoogleBigQuerySQLEndpoint.class);
    protected GoogleBigQuerySQLProducer producer;
    protected String sql;
    protected String projectId = "testProjectId";
    protected GoogleBigQuerySQLConfiguration configuration = new GoogleBigQuerySQLConfiguration();
    protected BigQuery bigquery;
    protected TableResult tableResult;
    protected Job job;
    protected JobStatistics.QueryStatistics statistics;

    protected GoogleBigQuerySQLProducer createAndStartProducer() {
        configuration.setProjectId(projectId);
        configuration.setQueryString(sql);

        GoogleBigQuerySQLProducer sqlProducer = new GoogleBigQuerySQLProducer(bigquery, endpoint, configuration);
        sqlProducer.start();
        return sqlProducer;
    }

    protected void setupBigqueryMock() throws Exception {
        bigquery = mock(BigQuery.class);
        tableResult = mock(TableResult.class);
        job = mock(Job.class);
        statistics = mock(JobStatistics.QueryStatistics.class);
        when(bigquery.query(any(QueryJobConfiguration.class), any(JobId.class))).thenReturn(tableResult);
        when(bigquery.create(any(JobInfo.class))).thenReturn(job);
        when(job.waitFor()).thenReturn(job);
        when(job.getQueryResults()).thenReturn(tableResult);
        when(job.getStatistics()).thenReturn(statistics);
        when(statistics.getNumDmlAffectedRows()).thenReturn(1L);
    }
}
