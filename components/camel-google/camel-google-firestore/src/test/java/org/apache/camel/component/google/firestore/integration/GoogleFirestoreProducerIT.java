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
package org.apache.camel.component.google.firestore.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.firestore.GoogleFirestoreConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Google Firestore Producer operations.
 *
 * <p>
 * These tests require valid Google Cloud credentials. To run:
 * </p>
 *
 * <pre>
 * mvn verify -pl components/camel-google/camel-google-firestore \
 *     -Dgoogle.firestore.serviceAccountKey=/path/to/service-account.json \
 *     -Dgoogle.firestore.projectId=my-project-id
 * </pre>
 */
@EnabledIf(value = "org.apache.camel.component.google.firestore.integration.GoogleFirestoreITSupport#hasCredentials",
           disabledReason = "Google Firestore credentials not provided. Set google.firestore.serviceAccountKey and google.firestore.projectId system properties.")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GoogleFirestoreProducerIT extends GoogleFirestoreITSupport {

    private static String testDocumentId;

    @Test
    @Order(1)
    void testCreateDocument() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:createResult");
        mock.expectedMessageCount(1);

        Map<String, Object> document = new HashMap<>();
        document.put("name", "John Doe");
        document.put("email", "john.doe@example.com");
        document.put("age", 30);
        document.put("active", true);

        template.sendBody("direct:createDocument", document);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        testDocumentId = exchange.getMessage().getHeader(
                GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, String.class);

        assertNotNull(testDocumentId, "Document ID should be generated");
        assertNotNull(exchange.getMessage().getHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME));
    }

    @Test
    @Order(2)
    void testSetDocument() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:setResult");
        mock.expectedMessageCount(1);

        String documentId = generateDocumentId();

        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Document");
        document.put("content", "This is a test document created by Camel");
        document.put("version", 1);

        template.sendBodyAndHeader("direct:setDocument", document,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(documentId, exchange.getMessage().getHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID));
    }

    @Test
    @Order(3)
    void testGetDocumentById() throws Exception {
        // First, create a document
        String documentId = generateDocumentId();
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "value1");
        document.put("field2", 42);

        template.sendBodyAndHeader("direct:setDocument", document,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        // Now retrieve it
        MockEndpoint mock = getMockEndpoint("mock:getResult");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:getDocument", null,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = exchange.getMessage().getBody(Map.class);

        assertNotNull(result);
        assertEquals("value1", result.get("field1"));
        assertEquals(42L, ((Number) result.get("field2")).longValue());
    }

    @Test
    @Order(4)
    void testUpdateDocument() throws Exception {
        // First, create a document
        String documentId = generateDocumentId();
        Map<String, Object> document = new HashMap<>();
        document.put("status", "pending");
        document.put("count", 0);

        template.sendBodyAndHeader("direct:setDocument", document,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        // Update the document
        MockEndpoint mock = getMockEndpoint("mock:updateResult");
        mock.expectedMessageCount(1);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("count", 5);

        template.sendBodyAndHeader("direct:updateDocument", updates,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        mock.assertIsSatisfied();

        // Verify the update
        Exchange getExchange = template.request("direct:getDocument", exchange -> {
            exchange.getMessage().setHeader(GoogleFirestoreConstants.DOCUMENT_ID, documentId);
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> result = getExchange.getMessage().getBody(Map.class);
        assertEquals("completed", result.get("status"));
        assertEquals(5L, ((Number) result.get("count")).longValue());
    }

    @Test
    @Order(5)
    void testListDocuments() throws Exception {
        // Create a few documents first
        for (int i = 0; i < 3; i++) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("index", i);
            doc.put("type", "list-test");
            template.sendBodyAndHeader("direct:setDocument", doc,
                    GoogleFirestoreConstants.DOCUMENT_ID, "list-doc-" + i);
        }

        MockEndpoint mock = getMockEndpoint("mock:listResult");
        mock.expectedMessageCount(1);

        template.sendBody("direct:listDocuments", null);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = exchange.getMessage().getBody(List.class);

        assertNotNull(results);
        assertTrue(results.size() >= 3, "Should have at least 3 documents");
    }

    @Test
    @Order(6)
    void testQueryCollection() throws Exception {
        // Create documents with specific field for querying
        for (int i = 0; i < 5; i++) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("category", "query-test");
            doc.put("score", i * 10);
            template.sendBodyAndHeader("direct:setDocument", doc,
                    GoogleFirestoreConstants.DOCUMENT_ID, "query-doc-" + i);
        }

        // Wait for eventual consistency
        Thread.sleep(1000);

        MockEndpoint mock = getMockEndpoint("mock:queryResult");
        mock.expectedMessageCount(1);

        // Query for documents with score >= 20
        template.sendBodyAndHeaders("direct:queryCollection", null, Map.of(
                GoogleFirestoreConstants.QUERY_FIELD, "score",
                GoogleFirestoreConstants.QUERY_OPERATOR, ">=",
                GoogleFirestoreConstants.QUERY_VALUE, 20));

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = exchange.getMessage().getBody(List.class);

        assertNotNull(results);
        assertTrue(results.size() >= 3, "Should have at least 3 documents with score >= 20");
    }

    @Test
    @Order(7)
    void testDeleteDocument() throws Exception {
        // Create a document to delete
        String documentId = generateDocumentId();
        Map<String, Object> document = new HashMap<>();
        document.put("toDelete", true);

        template.sendBodyAndHeader("direct:setDocument", document,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        // Delete it
        MockEndpoint mock = getMockEndpoint("mock:deleteResult");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:deleteDocument", null,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(true, exchange.getMessage().getBody());

        // Verify it's deleted
        Exchange getExchange = template.request("direct:getDocument", ex -> {
            ex.getMessage().setHeader(GoogleFirestoreConstants.DOCUMENT_ID, documentId);
        });

        assertNull(getExchange.getMessage().getBody(), "Document should be deleted");
    }

    @Test
    @Order(8)
    void testSetDocumentWithMerge() throws Exception {
        // Create initial document
        String documentId = generateDocumentId();
        Map<String, Object> document = new HashMap<>();
        document.put("field1", "original");
        document.put("field2", "original");

        template.sendBodyAndHeader("direct:setDocument", document,
                GoogleFirestoreConstants.DOCUMENT_ID, documentId);

        // Merge update (should only update field1, keep field2)
        MockEndpoint mock = getMockEndpoint("mock:mergeResult");
        mock.expectedMessageCount(1);

        Map<String, Object> mergeData = new HashMap<>();
        mergeData.put("field1", "updated");
        mergeData.put("field3", "new");

        template.sendBodyAndHeaders("direct:mergeDocument", mergeData, Map.of(
                GoogleFirestoreConstants.DOCUMENT_ID, documentId,
                GoogleFirestoreConstants.MERGE, true));

        mock.assertIsSatisfied();

        // Verify merge result
        Exchange getExchange = template.request("direct:getDocument", ex -> {
            ex.getMessage().setHeader(GoogleFirestoreConstants.DOCUMENT_ID, documentId);
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> result = getExchange.getMessage().getBody(Map.class);
        assertEquals("updated", result.get("field1"));
        assertEquals("original", result.get("field2"));
        assertEquals("new", result.get("field3"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=createDocument")
                        .to("mock:createResult");

                from("direct:setDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=setDocument")
                        .to("mock:setResult");

                from("direct:getDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=getDocumentById")
                        .to("mock:getResult");

                from("direct:updateDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=updateDocument")
                        .to("mock:updateResult");

                from("direct:deleteDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=deleteDocument")
                        .to("mock:deleteResult");

                from("direct:listDocuments")
                        .to("google-firestore:" + getTestCollection() + "?operation=listDocuments")
                        .to("mock:listResult");

                from("direct:queryCollection")
                        .to("google-firestore:" + getTestCollection() + "?operation=queryCollection")
                        .to("mock:queryResult");

                from("direct:mergeDocument")
                        .to("google-firestore:" + getTestCollection() + "?operation=setDocument")
                        .to("mock:mergeResult");
            }
        };
    }
}
