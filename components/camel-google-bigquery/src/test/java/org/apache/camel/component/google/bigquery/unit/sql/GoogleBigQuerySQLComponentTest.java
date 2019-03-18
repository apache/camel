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

import org.apache.camel.CamelContext;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLComponent;
import org.apache.camel.component.google.bigquery.sql.GoogleBigQuerySQLEndpoint;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class GoogleBigQuerySQLComponentTest {
    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testQuerySet() throws Exception {
        String uri = "google-bigquery-sql:myproject:insert into testDatasetId.testTableId(id) values(1)";

        GoogleBigQuerySQLEndpoint endpoint = (GoogleBigQuerySQLEndpoint)new GoogleBigQuerySQLComponent(context).createEndpoint(uri);

        assertEquals("myproject", endpoint.getConfiguration().getProjectId());
        assertEquals("insert into testDatasetId.testTableId(id) values(1)", endpoint.getConfiguration().getQuery());
    }

    @Test
    public void testQueryFromResourceSet() throws Exception {
        String uri = "google-bigquery-sql:myproject:classpath:sql/delete.sql";

        GoogleBigQuerySQLEndpoint endpoint = (GoogleBigQuerySQLEndpoint)new GoogleBigQuerySQLComponent(context).createEndpoint(uri);

        assertEquals("myproject", endpoint.getConfiguration().getProjectId());
        assertEquals("classpath:sql/delete.sql", endpoint.getConfiguration().getQuery());
    }
}
