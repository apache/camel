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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit5.params.Test;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.UpdateParams;
import org.junit.jupiter.api.BeforeEach;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SolrUpdateTest extends SolrComponentTestSupport {
    private SolrEndpoint solrEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        solrEndpoint = getMandatoryEndpoint(solrRouteUri(), SolrEndpoint.class);
    }

    @Test
    public void testInsertSolrInputDocumentAsXMLWithoutAddRoot() throws Exception {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        String docAsXml = ClientUtils.toXML(doc);
        template.sendBodyAndHeader("direct:start", docAsXml, SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocumentAsXMLWithAddRoot() throws Exception {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        String docAsXml = "<add>" + ClientUtils.toXML(doc) + "</add>";
        template.sendBodyAndHeader("direct:start", docAsXml, SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocument() throws Exception {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        template.sendBodyAndHeader("direct:start", doc, SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);

        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocumentList() throws Exception {
        List<SolrInputDocument> docList = new ArrayList<>(2);

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        docList.add(doc);

        doc = new SolrInputDocument();
        doc.addField("id", "KP147LL/A");
        docList.add(doc);

        template.sendBodyAndHeader("direct:start", docList, SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);

        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        response = executeSolrQuery("id:KP147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        response = executeSolrQuery("id:KP147LL/ABC");
        assertEquals(0, response.getStatus());
        assertEquals(0, response.getResults().getNumFound());
    }

    @Test
    public void testInsertStreaming() throws Exception {

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT_STREAMING);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");
        template.send("direct:start", exchange);

        Thread.sleep(500);

        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void indexSingleDocumentOnlyWithId() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");

        template.send("direct:start", exchange);
        solrCommit();

        // Check things were indexed.
        QueryResponse response = executeSolrQuery("id:MA147LL/A");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void caughtSolrExceptionIsHandledElegantly() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.name", "Missing required field throws exception.");

        template.send("direct:start", exchange);

        //noinspection ThrowableResultOfMethodCallIgnored
        assertIsInstanceOf(BaseHttpSolrClient.RemoteSolrException.class, exchange.getException());
    }

    @Test
    public void setHeadersAsSolrFields() throws Exception {
        Exchange exchange = createExchangeWithBody("Body is ignored");
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");
        exchange.getIn().setHeader("SolrField.name", "Apple 60 GB iPod with Video Playback Black");
        exchange.getIn().setHeader("SolrField.manu", "Apple Computer Inc.");

        template.send("direct:start", exchange);
        solrCommit();

        QueryResponse response = executeSolrQuery("id:MA147LL/A");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Apple 60 GB iPod with Video Playback Black", doc.getFieldValue("name"));
        assertEquals("Apple Computer Inc.", doc.getFieldValue("manu"));
    }

    @Test
    public void setMultiValuedFieldInHeader() throws Exception {
        String[] categories = { "electronics", "apple" };
        Exchange exchange = createExchangeWithBody("Test body for iPod.");
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");
        exchange.getIn().setHeader("SolrField.cat", categories);

        template.send("direct:start", exchange);
        solrCommit();

        // Check things were indexed.
        QueryResponse response = executeSolrQuery("id:MA147LL/A");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertArrayEquals(categories, ((List<?>) doc.getFieldValue("cat")).toArray());
    }

    @Test
    public void indexDocumentsAndThenCommit() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");
        exchange.getIn().setHeader("SolrField.name", "Apple 60 GB iPod with Video Playback Black");
        exchange.getIn().setHeader("SolrField.manu", "Apple Computer Inc.");
        template.send("direct:start", exchange);

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(0, response.getResults().getNumFound());

        solrCommit();

        QueryResponse afterCommitResponse = executeSolrQuery("*:*");
        assertEquals(0, afterCommitResponse.getStatus());
        assertEquals(1, afterCommitResponse.getResults().getNumFound());
    }

    @Test
    public void invalidSolrParametersAreIgnored() throws Exception {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrField.id", "MA147LL/A");
        exchange.getIn().setHeader("SolrField.name", "Apple 60 GB iPod with Video Playback Black");
        exchange.getIn().setHeader("SolrParam.invalid-param", "this is ignored");

        template.send("direct:start", exchange);
        solrCommit();

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void indexDocumentsToCSVUpdateHandlerWithoutParameters() throws Exception {
        solrEndpoint.setRequestHandler("/update/csv");

        Exchange exchange = createExchangeWithBody(new File("src/test/resources/data/books.csv"));
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader(SolrConstants.PARAM + UpdateParams.ASSUME_CONTENT_TYPE, "text/csv");
        template.send("direct:start", exchange);
        solrCommit();

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(10, response.getResults().getNumFound());

        response = executeSolrQuery("id:0553573403");
        SolrDocument doc = response.getResults().get(0);
        assertEquals("A Game of Thrones", doc.getFieldValue("name"));
        assertEquals(7.99f, doc.getFieldValue("price"));
    }

    @Test
    public void queryDocumentsToCSVUpdateHandlerWithoutParameters() {
        solrEndpoint.setRequestHandler("/update/csv");

        Exchange exchange = createExchangeWithBody(new File("src/test/resources/data/books.csv"));
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader(SolrConstants.PARAM + UpdateParams.ASSUME_CONTENT_TYPE, "text/csv");
        template.send("direct:start", exchange);
        solrCommit();

        Exchange exchange1 = createExchangeWithBody(null);
        exchange1.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_QUERY);
        exchange1.getIn().setHeader(SolrConstants.QUERY_STRING, "id:0553573403");
        Exchange result = template.send("direct:start", exchange1);

        SolrDocumentList list = result.getMessage().getBody(SolrDocumentList.class);
        assertEquals("A Game of Thrones", list.get(0).getFieldValue("name"));
        assertEquals(7.99f, list.get(0).getFieldValue("price"));
    }

    @Test
    public void queryDocumentsToMap() throws Exception {
        solrEndpoint.setRequestHandler("/update/csv");

        Exchange exchange = createExchangeWithBody(new File("src/test/resources/data/books.csv"));
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader(SolrConstants.PARAM + UpdateParams.ASSUME_CONTENT_TYPE, "text/csv");
        template.send("direct:start", exchange);
        solrCommit();

        // Required to reset request handler:
        // The Map-based insert request used to not respect the requesthandler that was explicitly set on the endpoint:
        // The insert-request was always using '/update' even when '/update/csv' was set on the endpoint.
        // This has now been changed: the explicitly set requesthandler is used.
        solrEndpoint.setRequestHandler(null);
        // 0553579908,book,A Clash of Kings,7.99,true,George R.R. Martin,"A Song of Ice and Fire",2,fantasy
        Exchange exchange1 = createExchangeWithBody(null);
        Map<String, String> map = new HashMap<>();
        map.put("id", "0553579934");
        map.put("cat", "Test");
        map.put("name", "Test");
        map.put("price", "7.99");
        map.put("author_t", "Test");
        map.put("series_t", "Test");
        map.put("sequence_i", "3");
        map.put("genre_s", "Test");
        exchange1.getMessage().setBody(map);
        exchange1.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        template.send("direct:start", exchange1);
        solrCommit();

        QueryResponse response = executeSolrQuery("id:0553579934");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void indexDocumentsToCSVUpdateHandlerWithParameters() throws Exception {
        solrEndpoint.setRequestHandler("/update/csv");

        Exchange exchange = createExchangeWithBody(new File("src/test/resources/data/books.csv"));
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader(SolrConstants.PARAM + UpdateParams.ASSUME_CONTENT_TYPE, "text/csv");
        exchange.getIn().setHeader("SolrParam.fieldnames", "id,cat,name,price,inStock,author_t,series_t,sequence_i,genre_s");
        exchange.getIn().setHeader("SolrParam.skip", "cat,sequence_i,genre_s");
        exchange.getIn().setHeader("SolrParam.skipLines", 1);

        template.send("direct:start", exchange);
        solrCommit();

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(10, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertFalse(doc.getFieldNames().contains("cat"));
    }

    @Test
    public void indexPDFDocumentToExtractingRequestHandler() throws Exception {
        solrEndpoint.setRequestHandler("/update/extract");

        Exchange exchange = createExchangeWithBody(new File("src/test/resources/data/tutorial.pdf"));
        exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
        exchange.getIn().setHeader("SolrParam.literal.id", "tutorial.pdf");

        template.send("direct:start", exchange);
        solrCommit();

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Solr", doc.getFieldValue("subject"));
        assertEquals("tutorial.pdf", doc.getFieldValue("id"));
        assertEquals(Arrays.asList("application/pdf"), doc.getFieldValue("content_type"));
    }
}
