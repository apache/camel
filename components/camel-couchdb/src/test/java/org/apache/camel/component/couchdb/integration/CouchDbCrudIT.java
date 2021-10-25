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
package org.apache.camel.component.couchdb.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.couchdb.CouchDbConstants;
import org.apache.camel.component.couchdb.CouchDbOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(Lifecycle.PER_CLASS)
public class CouchDbCrudIT extends CouchDbTestSupport {

    @EndpointInject("mock:deleteNotifications")
    private MockEndpoint mockDeleteNotifications;

    @EndpointInject("mock:updateNotifications")
    private MockEndpoint mockUpdateNotifications;

    @EndpointInject("direct:couchDbIn")
    private Endpoint couchDbIn;

    @Override
    protected RouteBuilder createRouteBuilder() {
        final String couchDbUrlFormat
                = "couchdb:http://{{couchdb.service.address}}/camelcouchdb?createDatabase=true%s";

        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF(couchDbUrlFormat, "&updates=false").to(mockDeleteNotifications);
                fromF(couchDbUrlFormat, "&deletes=false").to(mockUpdateNotifications);
                from(couchDbIn).toF(couchDbUrlFormat, "");
            }
        };
    }

    private String getDocumentRevision(Exchange createExchange) {
        return createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
    }

    private String getDocumentId(Exchange createExchange) {
        return createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
    }

    private JsonObject newJsonObject(String s) {
        return new Gson().fromJson(s, JsonObject.class);
    }

    @DisplayName("When the body is an object")
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @TestInstance(Lifecycle.PER_CLASS)
    class WhenObject {

        private Exchange createExchange;
        private String retrievedDocumentString;
        private Exchange updateExchange;
        private String retrievedUpdatedDocumentString;

        @DisplayName("tests whether can create a document")
        @Order(2)
        @Test
        void testCreate() throws InterruptedException {
            JsonObject testDocument = newJsonObject("{ \"randomString\" : \"36e8807f-0810-4b80-9ca9-b20859433034\" }");

            // Creating a document should trigger an update notification
            mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
            mockUpdateNotifications.expectedMessageCount(1);
            createExchange = template.request(couchDbIn, e -> e.getMessage().setBody(testDocument));

            assertNotNull(getDocumentId(createExchange));
            assertNotNull(getDocumentRevision(createExchange));
            mockUpdateNotifications.assertIsSatisfied();
        }

        @DisplayName("tests whether can consult the newly created document")
        @Order(3)
        @Test
        void testConsult() {
            JsonObject testDocument = newJsonObject("{ \"randomString\" : \"36e8807f-0810-4b80-9ca9-b20859433034\" }");

            // Consulting a created document should succeed
            Map<String, Object> retrieveExchangeHeaders = new HashMap<>();
            retrieveExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, getDocumentId(createExchange));
            retrieveExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
            retrievedDocumentString
                    = template.requestBodyAndHeaders(couchDbIn, testDocument, retrieveExchangeHeaders, String.class);
            assertNotNull(retrievedDocumentString);

            JsonObject retrievedDocument = newJsonObject(retrievedDocumentString);
            final String randomString = "36e8807f-0810-4b80-9ca9-b20859433034";
            assertEquals(randomString, retrievedDocument.get("randomString").getAsString());
            assertEquals(getDocumentId(createExchange), retrievedDocument.get("_id").getAsString());
            assertEquals(getDocumentRevision(createExchange), retrievedDocument.get("_rev").getAsString());
        }

        @DisplayName("tests whether can update a document")
        @Order(4)
        @Test
        void testUpdateDocument() throws InterruptedException {
            JsonObject retrievedDocument = newJsonObject(retrievedDocumentString);

            // Updating a document should trigger a second update notification
            mockUpdateNotifications.reset();
            mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
            mockUpdateNotifications.expectedMessageCount(1);
            retrievedDocument.addProperty("randomString", "36e8807f-0810-4b80-9ca9-b20859433035");
            updateExchange = template.request(couchDbIn, e -> {
                e.getMessage().setBody(retrievedDocument);
            });
            assertEquals(getDocumentId(createExchange), getDocumentId(updateExchange));
            assertNotEquals(getDocumentRevision(createExchange), getDocumentRevision(updateExchange));
            mockUpdateNotifications.assertIsSatisfied();
        }

        @DisplayName("tests whether can consult the updated document")
        @Order(5)
        @Test
        void testConsultUpdated() {
            JsonObject retrievedDocument = newJsonObject(retrievedDocumentString);

            // Consulting an updated document should succeed
            Map<String, Object> retrieveUpdatedExchangeHeaders = new HashMap<>();
            retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, getDocumentId(updateExchange));
            retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
            retrievedUpdatedDocumentString = template.requestBodyAndHeaders(couchDbIn, retrievedDocument,
                    retrieveUpdatedExchangeHeaders, String.class);
            assertNotNull(retrievedUpdatedDocumentString);
            JsonObject retrievedUpdatedDocument = newJsonObject(retrievedUpdatedDocumentString);
            assertEquals("36e8807f-0810-4b80-9ca9-b20859433035", retrievedUpdatedDocument.get("randomString").getAsString());
            assertEquals(getDocumentId(updateExchange), retrievedUpdatedDocument.get("_id").getAsString());
            assertEquals(getDocumentRevision(updateExchange), retrievedUpdatedDocument.get("_rev").getAsString());
        }

        @DisplayName("tests whether can delete the document")
        @Order(6)
        @Test
        void testDelete() throws InterruptedException {
            JsonObject retrievedUpdatedDocument = newJsonObject(retrievedUpdatedDocumentString);

            // Deleting a retrieved document should trigger a delete notification
            mockDeleteNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "DELETE");
            mockDeleteNotifications.expectedMessageCount(1);
            Exchange deleteExchange = template.request(couchDbIn, e -> {
                e.getMessage().setBody(retrievedUpdatedDocument);
                e.getMessage().setHeader(CouchDbConstants.HEADER_METHOD, CouchDbOperations.DELETE);
            });
            assertEquals(retrievedUpdatedDocument.get("_id").getAsString(), getDocumentId(deleteExchange));
            assertNotEquals(retrievedUpdatedDocument.get("_rev").getAsString(), getDocumentRevision(deleteExchange));
            mockDeleteNotifications.assertIsSatisfied();
        }
    }

    @DisplayName("When the body is a string")
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @TestInstance(Lifecycle.PER_CLASS)
    class WhenString {

        private Exchange createExchange;
        private String retrievedDocumentString;
        private Exchange updateExchange;
        private String retrievedUpdatedDocumentString;

        @DisplayName("tests whether can create a document")
        @Order(7)
        @Test
        void testCreate() throws InterruptedException {
            JsonObject testDocument = newJsonObject("{ \"randomString\" : \"36e8807f-0810-4b80-9ca9-b20859433044\" }");

            // Creating a document should trigger an update notification
            mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
            mockUpdateNotifications.expectedMessageCount(1);
            createExchange = template.request(couchDbIn, e -> e.getMessage().setBody(testDocument.toString()));
            assertNotNull(getDocumentId(createExchange));
            assertNotNull(getDocumentRevision(createExchange));
            mockUpdateNotifications.assertIsSatisfied();
        }

        @DisplayName("tests whether can consult the newly created document")
        @Order(8)
        @Test
        void testConsult() {
            // Consulting a created document should succeed
            Map<String, Object> retrieveExchangeHeaders = new HashMap<>();
            retrieveExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, getDocumentId(createExchange));
            retrieveExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
            retrievedDocumentString = template.requestBodyAndHeaders(couchDbIn, "", retrieveExchangeHeaders, String.class);
            assertNotNull(retrievedDocumentString);
            JsonObject retrievedDocument = newJsonObject(retrievedDocumentString);
            assertEquals("36e8807f-0810-4b80-9ca9-b20859433044", retrievedDocument.get("randomString").getAsString());
            assertEquals(getDocumentId(createExchange), retrievedDocument.get("_id").getAsString());
            assertEquals(getDocumentRevision(createExchange), retrievedDocument.get("_rev").getAsString());
        }

        @DisplayName("tests whether can update a document")
        @Order(9)
        @Test
        void testUpdate() throws InterruptedException {
            JsonObject retrievedDocument = newJsonObject(retrievedDocumentString);

            // Updating a document should trigger a second update notification
            mockUpdateNotifications.reset();
            mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
            mockUpdateNotifications.expectedMessageCount(1);
            retrievedDocument.addProperty("randomString", "36e8807f-0810-4b80-9ca9-b20859433045");
            updateExchange = template.request(couchDbIn, e -> {
                e.getMessage().setBody(retrievedDocument.toString());
            });
            assertEquals(getDocumentId(createExchange), getDocumentId(updateExchange));
            assertNotEquals(getDocumentRevision(createExchange), getDocumentRevision(updateExchange));
            mockUpdateNotifications.assertIsSatisfied();
        }

        @DisplayName("tests whether can consult the updated document")
        @Order(10)
        @Test
        void testConsultUpdated() {
            // Consulting an updated document should succeed
            Map<String, Object> retrieveUpdatedExchangeHeaders = new HashMap<>();
            retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, getDocumentId(updateExchange));
            retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
            retrievedUpdatedDocumentString
                    = template.requestBodyAndHeaders(couchDbIn, "", retrieveUpdatedExchangeHeaders, String.class);
            assertNotNull(retrievedUpdatedDocumentString);
            JsonObject retrievedUpdatedDocument = newJsonObject(retrievedUpdatedDocumentString);
            assertEquals("36e8807f-0810-4b80-9ca9-b20859433045", retrievedUpdatedDocument.get("randomString").getAsString());
            assertEquals(getDocumentId(updateExchange), retrievedUpdatedDocument.get("_id").getAsString());
            assertEquals(getDocumentRevision(updateExchange), retrievedUpdatedDocument.get("_rev").getAsString());
        }

        @DisplayName("tests whether can delete the document")
        @Order(11)
        @Test
        void testDelete() throws InterruptedException {
            JsonObject retrievedUpdatedDocument = newJsonObject(retrievedUpdatedDocumentString);

            // Deleting a retrieved document should trigger a delete notification
            mockDeleteNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "DELETE");
            mockDeleteNotifications.expectedMessageCount(1);
            Exchange deleteExchange = template.request(couchDbIn, e -> {
                e.getMessage().setBody(retrievedUpdatedDocument.toString());
                e.getMessage().setHeader(CouchDbConstants.HEADER_METHOD, CouchDbOperations.DELETE);
            });
            assertEquals(retrievedUpdatedDocument.get("_id").getAsString(), getDocumentId(deleteExchange));
            assertNotEquals(retrievedUpdatedDocument.get("_rev").getAsString(), getDocumentRevision(deleteExchange));
            mockDeleteNotifications.assertIsSatisfied();
        }
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    class UpdateNotifications {

        @DisplayName("Tests whether creating a given number of documents results in the same number of update notifications")
        @ParameterizedTest
        @ValueSource(ints = { 10, 50, 100 })
        void testUpdateNotifications(int messageCount) throws InterruptedException {
            mockUpdateNotifications.expectedMessageCount(messageCount);

            for (int messageNumber = 0; messageNumber < messageCount; messageNumber++) {
                JsonObject document
                        = new Gson().fromJson("{ \"randomString\" : \"" + UUID.randomUUID() + "\" }", JsonObject.class);
                template.requestBody(couchDbIn, document);
            }

            mockUpdateNotifications.assertIsSatisfied();
        }
    }

}
