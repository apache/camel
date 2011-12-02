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

import java.io.File;
import java.util.Arrays;
import static junit.framework.Assert.assertEquals;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration (locations = {"/SolrSpringTest-context.xml"})
public class SolrSpringTest extends AbstractJUnit4SpringContextTests {

    private static JettySolrRunner solrRunner;
    private static CommonsHttpSolrServer solrServer;

    @Produce(uri = "direct:xml-start")
    protected ProducerTemplate xmlRoute;

    @Produce(uri = "direct:pdf-start")
    protected ProducerTemplate pdfRoute;

    @DirtiesContext
    @Test
    public void endToEndIndexXMLDocuments() throws Exception {
        xmlRoute.sendBody(new File("src/test/resources/data/books.xml"));

        // Check things were indexed.
        QueryResponse response = executeSolrQuery("*:*");

        assertEquals(0, response.getStatus());
        assertEquals(4, response.getResults().getNumFound());

        // Check fields were indexed correctly.
        response = executeSolrQuery("title:Learning XML");

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Learning XML", doc.getFieldValue("id"));
        assertEquals(Arrays.asList("Web", "Technology", "Computers"), doc.getFieldValue("cat"));
    }

    @DirtiesContext
    @Test
    public void endToEndIndexPDFDocument() throws Exception {
        pdfRoute.sendBody(new File("src/test/resources/data/tutorial.pdf"));

        QueryResponse response = executeSolrQuery("*:*");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Solr", doc.getFieldValue("subject"));
        assertEquals("tutorial.pdf", doc.getFieldValue("id"));
        assertEquals(Arrays.asList("application/pdf"), doc.getFieldValue("content_type"));
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Set appropriate paths for Solr to use.
        System.setProperty("solr.solr.home", "src/test/resources/solr");
        System.setProperty("solr.data.dir", "target/test-classes/solr/data");

        // Instruct Solr to keep the index in memory, for faster testing.
        System.setProperty("solr.directoryFactory", "solr.RAMDirectoryFactory");

        // Start a Solr instance.
        solrRunner = new JettySolrRunner("/solr", 8899);
        solrRunner.start();

        solrServer = new CommonsHttpSolrServer("http://localhost:8899/solr");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        solrRunner.stop();
    }

    @Before
    public void clearIndex() throws Exception {
        // Clear the Solr index.
        solrServer.deleteByQuery("*:*");
        solrServer.commit();
    }

    private QueryResponse executeSolrQuery(String query) throws SolrServerException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        return solrServer.query(solrQuery);
    }
}
