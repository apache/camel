/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.component.google.bigquery.GoogleBigQueryComponent;
import org.apache.camel.component.google.bigquery.GoogleBigQueryEndpoint;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class GoogleBigQueryComponentTest {
    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testPropertiesSet() throws Exception {
        String uri = "google-bigquery:myproject:mydataset:mytable?useAsInsertId=insertidfield";

        GoogleBigQueryEndpoint endpoint = (GoogleBigQueryEndpoint)new GoogleBigQueryComponent(context).createEndpoint(uri);

        assertEquals("myproject", endpoint.getConfiguration().getProjectId());
        assertEquals("mydataset", endpoint.getConfiguration().getDatasetId());
        assertEquals("mytable", endpoint.getConfiguration().getTableId());
        assertEquals("insertidfield", endpoint.getConfiguration().getUseAsInsertId());
    }
}
