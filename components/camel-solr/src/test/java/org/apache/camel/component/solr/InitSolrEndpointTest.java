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
package org.apache.camel.component.solr;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InitSolrEndpointTest extends CamelTestSupport {

    private final String dummyUri = "solr://localhost:8983";
    private final SolrClient solrClient = new HttpJdkSolrClient.Builder(dummyUri).build();

    @Test
    public void endpointCreatedCorrectlyWithAutoWireEnabled() throws Exception {
        SolrEndpoint solrEndpoint;
        try (CamelContext camelContext = context()) {
            camelContext.getRegistry().bind("mySolrClient", solrClient);
            solrEndpoint = camelContext.getEndpoint(dummyUri, SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertNotNull(solrEndpoint.getConfiguration().getSolrClient());
            assertEquals(solrClient, solrEndpoint.getConfiguration().getSolrClient());
        }
    }

    @Test
    public void endpointCreatedCorrectlyWithAutoWireDisabled() throws Exception {
        SolrEndpoint solrEndpoint;
        SolrClient solrClient1 = new HttpJdkSolrClient.Builder(dummyUri.replace("solr://", "http://")).build();
        try (CamelContext camelContext = context()) {
            camelContext.setAutowiredEnabled(false);
            camelContext.getRegistry().bind("client1", solrClient1);
            solrEndpoint = camelContext.getEndpoint(dummyUri, SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertNull(solrEndpoint.getConfiguration().getSolrClient());
            solrEndpoint
                    = camelContext.getEndpoint(dummyUri + "?solrClient=#client1", SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertNotNull(solrEndpoint.getConfiguration().getSolrClient());
            assertEquals(solrClient1, solrEndpoint.getConfiguration().getSolrClient());
        }
    }

    @Test
    public void wrongURLFormatFailsEndpointCreation() throws Exception {
        SolrEndpoint solrEndpoint;
        String testUri = dummyUri.replace(":8983", ":89xx");
        try (CamelContext camelContext = context()) {
            // should fail as invalid uri
            assertThrows(ResolveEndpointFailedException.class,
                    () -> camelContext.getEndpoint(testUri));
            // should not fail as valid uri
            solrEndpoint = camelContext.getEndpoint(testUri.replace(":89xx", ""), SolrEndpoint.class);
            assertNotNull(solrEndpoint);
        }
    }

    @Test
    public void endpointCreatedWithSolrUriPath() throws Exception {
        SolrEndpoint solrEndpoint;
        try (CamelContext camelContext = context()) {
            solrEndpoint = camelContext.getEndpoint(dummyUri.concat("/solr/testcollection/update/xml"), SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertEquals("testcollection", solrEndpoint.getConfiguration().getCollection());
            assertEquals("/update/xml", solrEndpoint.getConfiguration().getRequestHandler());
            solrEndpoint = camelContext.getEndpoint(dummyUri.concat("/solr/testcollection/update/"), SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertEquals("testcollection", solrEndpoint.getConfiguration().getCollection());
            assertEquals("/update", solrEndpoint.getConfiguration().getRequestHandler());
            solrEndpoint = camelContext.getEndpoint(dummyUri.concat("/solr/testcollection"), SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertEquals("testcollection", solrEndpoint.getConfiguration().getCollection());
            assertNull(solrEndpoint.getConfiguration().getRequestHandler());
            solrEndpoint = camelContext.getEndpoint(dummyUri.concat("/sub-app/solr2/"), SolrEndpoint.class);
            assertNotNull(solrEndpoint);
            assertNull(solrEndpoint.getConfiguration().getCollection());
            assertNull(solrEndpoint.getConfiguration().getRequestHandler());
            assertEquals("/sub-app/solr2/", solrEndpoint.getConfiguration().getBasePath());
        }
    }

}
