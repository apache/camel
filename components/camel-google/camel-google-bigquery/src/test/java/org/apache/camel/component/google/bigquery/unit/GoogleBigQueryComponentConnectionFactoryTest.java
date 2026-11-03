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

import java.util.Map;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import org.apache.camel.component.google.bigquery.GoogleBigQueryComponent;
import org.apache.camel.component.google.bigquery.GoogleBigQueryConnectionFactory;
import org.apache.camel.component.google.bigquery.GoogleBigQueryEndpoint;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLComponent;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the endpoints of google-bigquery and google-bigquery-sql use the connection factory of their component
 * when they have none of their own. Each test checks the factory of the endpoint before it sends anything, so a default
 * client never gets a call.
 */
class GoogleBigQueryComponentConnectionFactoryTest extends CamelTestSupport {

    private static final String TABLE_URI = "google-bigquery:my-project:my_dataset:my_table";
    private static final String DELETE_URI = "google-bigquery-sql:my-project:delete from my_dataset.my_table where true";

    private final BigQuery bigquery = mock(BigQuery.class);
    private final GoogleBigQueryConnectionFactory componentFactory = new GoogleBigQueryConnectionFactory(bigquery);

    @Test
    void theEndpointUsesTheConnectionFactoryOfTheComponent() {
        when(bigquery.insertAll(any(InsertAllRequest.class))).thenReturn(mock(InsertAllResponse.class));
        context.getComponent("google-bigquery", GoogleBigQueryComponent.class).setConnectionFactory(componentFactory);

        GoogleBigQueryEndpoint endpoint = context.getEndpoint(TABLE_URI, GoogleBigQueryEndpoint.class);
        assertSame(componentFactory, endpoint.getConfiguration().getConnectionFactory());

        template.sendBody(TABLE_URI, Map.of("id", 1));

        verify(bigquery).insertAll(any(InsertAllRequest.class));
    }

    @Test
    void theConnectionFactoryOfTheEndpointWinsOverTheOneOfTheComponent() {
        GoogleBigQueryConnectionFactory endpointFactory = new GoogleBigQueryConnectionFactory(mock(BigQuery.class));
        context.getRegistry().bind("endpointFactory", endpointFactory);
        context.getComponent("google-bigquery", GoogleBigQueryComponent.class).setConnectionFactory(componentFactory);

        GoogleBigQueryEndpoint endpoint = context.getEndpoint(TABLE_URI + "?connectionFactory=#endpointFactory",
                GoogleBigQueryEndpoint.class);

        assertSame(endpointFactory, endpoint.getConfiguration().getConnectionFactory());
    }

    @Test
    void theSqlEndpointUsesTheConnectionFactoryOfTheComponent() throws Exception {
        Job job = mock(Job.class);
        JobStatistics.QueryStatistics statistics = mock(JobStatistics.QueryStatistics.class);
        when(bigquery.create(any(JobInfo.class))).thenReturn(job);
        when(job.waitFor()).thenReturn(job);
        when(job.getStatistics()).thenReturn(statistics);
        when(statistics.getStatementType()).thenReturn(JobStatistics.QueryStatistics.StatementType.DELETE);
        when(statistics.getNumDmlAffectedRows()).thenReturn(3L);
        context.getComponent("google-bigquery-sql", GoogleBigQuerySQLComponent.class).setConnectionFactory(componentFactory);

        GoogleBigQuerySQLEndpoint endpoint = context.getEndpoint(DELETE_URI, GoogleBigQuerySQLEndpoint.class);
        assertSame(componentFactory, endpoint.getConfiguration().getConnectionFactory());

        // a body that is not a Map is not used by the query
        assertEquals(3L, template.requestBody(DELETE_URI, "", Long.class));
        verify(bigquery).create(any(JobInfo.class));
    }

    @Test
    void theSqlConnectionFactoryOfTheEndpointWinsOverTheOneOfTheComponent() {
        GoogleBigQueryConnectionFactory endpointFactory = new GoogleBigQueryConnectionFactory(mock(BigQuery.class));
        context.getRegistry().bind("endpointFactory", endpointFactory);
        context.getComponent("google-bigquery-sql", GoogleBigQuerySQLComponent.class).setConnectionFactory(componentFactory);

        GoogleBigQuerySQLEndpoint endpoint = context.getEndpoint(DELETE_URI + "?connectionFactory=#endpointFactory",
                GoogleBigQuerySQLEndpoint.class);

        assertSame(endpointFactory, endpoint.getConfiguration().getConnectionFactory());
    }
}
