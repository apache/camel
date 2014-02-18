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
package org.apache.camel.component.solr;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class SolrComponentTestSupport extends SolrTestSupport {
    protected static final String SOLR_ROUTE_URI = "solr://localhost:" + getPort() + "/solr";
    protected static final String TEST_ID = "test1";
    protected static final String TEST_ID2 = "test2";
    protected static JettySolrRunner solrRunner;
    protected static HttpSolrServer solrServer;

    protected void solrInsertTestEntry() {
        solrInsertTestEntry(TEST_ID);
    }

    protected void solrInsertTestEntry(String id) {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        headers.put("SolrField.id", id);
        template.sendBodyAndHeaders("direct:start", null, headers);
    }

    protected void solrCommit() {
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);
    }

    protected QueryResponse executeSolrQuery(String query) throws SolrServerException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        return solrServer.query(solrQuery);
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Set appropriate paths for Solr to use.
        System.setProperty("solr.solr.home", "src/test/resources/solr");
        System.setProperty("solr.data.dir", "target/test-classes/solr/data");

        // Instruct Solr to keep the index in memory, for faster testing.
        System.setProperty("solr.directoryFactory", "solr.RAMDirectoryFactory");

        // Start a Solr instance.
        solrRunner = new JettySolrRunner("/solr", getPort());
        solrRunner.start();

        solrServer = new HttpSolrServer("http://localhost:" + getPort() + "/solr");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        solrRunner.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(SOLR_ROUTE_URI);
                from("direct:splitThenCommit")
                    .split(body())
                        .to(SOLR_ROUTE_URI)
                    .end()
                    .setHeader(SolrConstants.OPERATION, constant(SolrConstants.OPERATION_COMMIT))
                    .to(SOLR_ROUTE_URI);
            }
        };
    }

    @Before
    public void clearIndex() throws Exception {
        // Clear the Solr index.
        solrServer.deleteByQuery("*:*");
        solrServer.commit();
    }
}
