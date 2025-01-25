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
package org.apache.camel.component.solr.integration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.solr.SolrConstants;
import org.apache.camel.component.solr.SolrOperation;
import org.apache.camel.component.solr.SolrUtils;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SolrInsertAndDeleteTest extends SolrTestSupport {

    @Test
    public void testDeleteById() throws Exception {

        //insert, commit and verify
        solrInsertTestEntry();
        solrCommit();
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //delete
        template.sendBodyAndHeader("direct:start", TEST_ID, SolrConstants.PARAM_OPERATION,
                SolrConstants.OPERATION_DELETE_BY_ID);
        solrCommit();

        //verify
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testDeleteListOfIDsViaSplit() throws Exception {

        //insert, commit and verify
        solrInsertTestEntry(TEST_ID);
        solrInsertTestEntry(TEST_ID2);
        solrCommit();
        assertEquals(2, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");

        //delete
        template.sendBodyAndHeader(DEFAULT_START_ENDPOINT_SPLIT_THEN_COMMIT, Arrays.asList(TEST_ID, TEST_ID2),
                SolrConstants.PARAM_OPERATION,
                SolrConstants.OPERATION_DELETE_BY_ID);

        //verify
        assertEquals(0, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testDeleteListOfIDsInOneDeleteOperation() throws Exception {

        //insert, commit and verify
        solrInsertTestEntry(TEST_ID);
        solrInsertTestEntry(TEST_ID2);
        solrCommit();
        assertEquals(2, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");

        //delete
        Map<String, Object> headers = new HashMap<>(SolrUtils.getHeadersForCommit());
        headers.put(SolrConstants.PARAM_OPERATION, SolrConstants.OPERATION_DELETE_BY_ID);
        template.sendBodyAndHeaders(DEFAULT_START_ENDPOINT, Arrays.asList(TEST_ID, TEST_ID2), headers);

        //verify
        assertEquals(0, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testDeleteByQuery() throws Exception {

        //insert, commit and verify
        solrInsertTestEntry(TEST_ID);
        solrInsertTestEntry(TEST_ID2);
        solrCommit();
        assertEquals(2, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");

        //delete
        Map<String, Object> headers = new HashMap<>(SolrUtils.getHeadersForCommit());
        headers.put(SolrConstants.PARAM_OPERATION, SolrConstants.OPERATION_DELETE_BY_QUERY);
        template.sendBodyAndHeaders("direct:start", "id:test*", headers);

        //verify
        assertEquals(0, executeSolrQuery("id:test*").getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testInsertSolrInputDocumentAsXMLWithoutAddRoot() {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        String docAsXml = ClientUtils.toXML(doc);
        executeInsertFor(docAsXml);

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocumentAsXMLWithAddRoot() {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        String docAsXml = "<add>" + ClientUtils.toXML(doc) + "</add>";
        Map<String, Object> headers = Map.of(Exchange.CONTENT_TYPE, "text/xml");
        executeInsertFor(docAsXml, headers);

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocument() {

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        executeInsertFor(doc);

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void testInsertSolrInputDocumentList() {
        List<SolrInputDocument> docList = new ArrayList<>(2);

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "MA147LL/A");
        docList.add(doc);

        doc = new SolrInputDocument();
        doc.addField("id", "KP147LL/A");
        docList.add(doc);

        executeInsertFor(docList);

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
    public void testInsertStreaming() {
        // TODO rename method
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.id", "MA147LL/A");
        executeInsert(builder.build());

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void indexSingleDocumentOnlyWithId() {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.id", "MA147LL/A");
        executeInsert(builder.build());

        // Check things were indexed.
        QueryResponse response = executeSolrQuery("id:MA147LL/A");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void caughtSolrExceptionIsHandledElegantly() {
        // empty request
        Exchange exchange = executeInsertFor(null, Map.of(), false);
        assertInstanceOf(org.apache.camel.InvalidPayloadException.class, exchange.getException());
    }

    @Test
    public void setHeadersAsSolrFields() {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody("Body is ignored")
                .withHeader("SolrField.id", "MA147LL/A")
                .withHeader("SolrField.name_s", "Apple 60 GB iPod with Video Playback Black")
                .withHeader("SolrField.manu_s", "Apple Computer Inc.");
        executeInsert(builder.build());

        QueryResponse response = executeSolrQuery("id:MA147LL/A");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Apple 60 GB iPod with Video Playback Black", doc.getFieldValue("name_s"));
        assertEquals("Apple Computer Inc.", doc.getFieldValue("manu_s"));
    }

    @Test
    public void setMultiValuedFieldInHeader() {
        String[] categories = { "electronics", "apple" };
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody("Test body for iPod.")
                .withHeader("SolrField.id", "MA147LL/A")
                .withHeader("SolrField.cat", categories);
        executeInsert(builder.build());

        // Check things were indexed.
        QueryResponse response = executeSolrQuery("id:MA147LL/A");

        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertArrayEquals(categories, ((List<?>) doc.getFieldValue("cat")).toArray());
    }

    @Test
    public void indexDocumentsAndThenCommit() {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.id", "MA147LL/A")
                .withHeader("SolrField.name", "Apple 60 GB iPod with Video Playback Black")
                .withHeader("SolrField.manu", "Apple Computer Inc.");
        executeInsert(builder.build(), false);

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(0, response.getResults().getNumFound());

        executeInsertFor(null);

        QueryResponse afterCommitResponse = executeSolrQuery("*:*");
        assertEquals(0, afterCommitResponse.getStatus());
        assertEquals(1, afterCommitResponse.getResults().getNumFound());
    }

    @Test
    public void indexWithAutoCommit() {
        // new exchange - not autocommit route
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.content", "NO_AUTO_COMMIT");
        executeInsert(DEFAULT_START_ENDPOINT, builder.build(), false);
        // not committed
        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(0, response.getResults().getNumFound());
        // perform commit
        executeInsertFor(null);
        response = executeSolrQuery("*:*");
        assertEquals(1, response.getResults().getNumFound());

        // new exchange - autocommit route
        builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.content", "AUTO_COMMIT");
        executeInsert(DEFAULT_START_ENDPOINT_AUTO_COMMIT, builder.build(), false);
        // should be committed
        response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(2, response.getResults().getNumFound());
    }

    @Test
    public void invalidSolrParametersAreIgnored() {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withHeader("SolrField.id", "MA147LL/A")
                .withHeader("SolrField.name", "Apple 60 GB iPod with Video Playback Black")
                .withHeader("SolrParam.invalid-param", "this is ignored");
        executeInsert(builder.build());

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void queryDocumentsToCSVUpdateHandlerWithFileConsumer() throws Exception {
        context.getRouteController().startRoute("file-route");
        MockEndpoint mock = getMockEndpoint(DEFAULT_MOCK_ENDPOINT);
        mock.setExpectedMessageCount(1);
        mock.assertIsSatisfied();
        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(10, response.getResults().getNumFound());
        response = executeSolrQuery("id:0553573403");
        SolrDocumentList list = response.getResults();
        assertEquals("A Game of Thrones", list.get(0).getFieldValue("name_s"));
        assertEquals(7.99, list.get(0).getFieldValue("price_d"));
        context.getRouteController().stopRoute("file-route");
    }

    @Test
    public void queryDocumentsToMap() {
        solrEndpoint.getConfiguration().setRequestHandler("/update/csv");
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody(new File("src/test/resources/data/books.csv"))
                .withHeader(SolrConstants.PARAM_CONTENT_TYPE, "text/csv");
        executeInsert(builder.build());
        solrEndpoint.getConfiguration().setRequestHandler(null);
        Map<String, String> map = new HashMap<>();
        map.put("id", "0553579934");
        map.put("cat", "Test");
        map.put("name", "Test");
        map.put("price", "7.99");
        map.put("author_t", "Test");
        map.put("series_t", "Test");
        map.put("sequence_i", "3");
        map.put("genre_s", "Test");
        builder = ExchangeBuilder.anExchange(camelContext())
                .withBody(map);
        executeInsert(builder.build());
        QueryResponse response = executeSolrQuery("id:0553579934");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());
    }

    @Test
    public void queryDocumentsToCSVUpdateHandlerWithoutParameters() {
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody(new File("src/test/resources/data/books.csv"));
        executeInsert(builder.build());
        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(10, response.getResults().getNumFound());
        response = executeSolrQuery("id:0553573403");
        SolrDocumentList list = response.getResults();
        assertEquals("A Game of Thrones", list.get(0).getFieldValue("name_s"));
        assertEquals(7.99, list.get(0).getFieldValue("price_d"));
    }

    @Test
    public void indexDocumentsToCSVUpdateHandlerWithParameters() {
        solrEndpoint.getConfiguration().setRequestHandler("/update/csv");
        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody(new File("src/test/resources/data/books.csv"))
                .withHeader(SolrConstants.PARAM_CONTENT_TYPE, "text/csv")
                .withHeader("SolrParam.fieldnames", "id,cat,name,price,inStock,author_t,series_t,sequence_i,genre_s")
                .withHeader("SolrParam.skip", "cat,sequence_i,genre_s")
                .withHeader("SolrParam.skipLines", 1);
        executeInsert(builder.build());
        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(10, response.getResults().getNumFound());
        SolrDocument doc = response.getResults().get(0);
        assertFalse(doc.getFieldNames().contains("cat"));
    }

    @Test
    @Disabled("The extraction Solr Module is not available in slim version of solr test infra.")
    public void indexPDFDocumentToExtractingRequestHandler() {
        solrEndpoint.getConfiguration().setRequestHandler("/update/extract");

        ExchangeBuilder builder = ExchangeBuilder.anExchange(camelContext())
                .withBody(new File("src/test/resources/data/tutorial.pdf"))
                .withHeader("SolrParam.literal.id", "tutorial.pdf");
        executeInsert(builder.build());

        QueryResponse response = executeSolrQuery("*:*");
        assertEquals(0, response.getStatus());
        assertEquals(1, response.getResults().getNumFound());

        SolrDocument doc = response.getResults().get(0);
        assertEquals("Solr", doc.getFieldValue("subject"));
        assertEquals("tutorial.pdf", doc.getFieldValue("id"));
        assertEquals(List.of("application/pdf"), doc.getFieldValue("content_type"));
    }

    @Test
    public void testCommit() {
        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        //commit
        template.sendBodyAndHeaders("direct:start", null, SolrUtils.getHeadersForCommit());
        //verify exists after commit
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testSoftCommit() {
        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        //commit
        template.sendBodyAndHeaders("direct:start", null, SolrUtils.getHeadersForCommit("softCommit"));
        //verify exists after commit
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testRollback() {
        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        //rollback
        template.sendBodyAndHeaders("direct:start", null, SolrUtils.getHeadersForCommit("rollback"));
        //verify after rollback
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        //commit
        template.sendBodyAndHeaders("direct:start", null, SolrUtils.getHeadersForCommit());
        //verify after commit (again)
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

    }

    @Test
    public void testOptimize() {
        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        //optimize (be careful with this operation: it reorganizes your index!)
        template.sendBodyAndHeaders("direct:start", null, SolrUtils.getHeadersForCommit("optimize"));
        //verify exists after optimize
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testAddBean() {

        //add bean
        SolrInsertAndDeleteTest.Item item = new Item();
        item.id = TEST_ID;
        item.categories = new String[] { "aaa", "bbb", "ccc" };

        template.sendBodyAndHeaders(
                "direct:start",
                item,
                Map.of(
                        SolrConstants.PARAM_OPERATION, SolrConstants.OPERATION_ADD_BEAN,
                        SolrConstants.HEADER_PARAM_PREFIX + "commit", "true"));

        //verify
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testAddBeans() {

        List<SolrInsertAndDeleteTest.Item> beans = new ArrayList<>();

        //add bean1
        SolrInsertAndDeleteTest.Item item1 = new Item();
        item1.id = TEST_ID;
        item1.categories = new String[] { "aaa", "bbb", "ccc" };
        beans.add(item1);

        //add bean2
        SolrInsertAndDeleteTest.Item item2 = new Item();
        item2.id = TEST_ID2;
        item2.categories = new String[] { "aaa", "bbb", "ccc" };
        beans.add(item2);

        template.sendBodyAndHeaders(
                "direct:start",
                beans,
                Map.of(
                        SolrConstants.PARAM_OPERATION, SolrConstants.OPERATION_ADD_BEAN,
                        SolrConstants.HEADER_PARAM_PREFIX + "commit", "true"));

        //verify
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        assertEquals(1, executeSolrQuery("id:" + TEST_ID2).getResults().getNumFound(), "wrong number of entries found");
        assertEquals(2, executeSolrQuery("*:*").getResults().getNumFound(), "wrong number of entries found");
    }

    public static class Item {
        @Field
        String id;

        @Field("cat")
        String[] categories;
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            @Override
            public void configure() {
                from(DEFAULT_START_ENDPOINT)
                        .to(DEFAULT_SOLR_ENDPOINT);
                from(DEFAULT_START_ENDPOINT_AUTO_COMMIT)
                        .to(DEFAULT_SOLR_ENDPOINT + "?autoCommit=true");
                from(DEFAULT_START_ENDPOINT_SPLIT_THEN_COMMIT)
                        .filter(header(SolrConstants.PARAM_OPERATION).isNull())
                        .setHeader(SolrConstants.PARAM_OPERATION, constant(SolrOperation.INSERT))
                        .end()
                        .split(body())
                        .to(DEFAULT_SOLR_ENDPOINT)
                        .end()
                        .setBody(constant((Object) null))
                        .setHeader(SolrConstants.HEADER_PARAM_PREFIX + "commit", constant(true))
                        .to(DEFAULT_SOLR_ENDPOINT);
                from(TEST_DATA_PATH_URI + "?noop=true&initialDelay=0&filename=books.csv").autoStartup(false)
                        .routeId("file-route")
                        .to(DEFAULT_SOLR_ENDPOINT + "?autoCommit=true")
                        .to(DEFAULT_MOCK_ENDPOINT);
            }
        };
    }

}
