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
package org.apache.camel.component.couchdb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class CouchDbCrudTest extends CouchDbTestSupport {

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

    @Order(1)
    @Test
    void jsonObjectDocumentCrudShouldTriggerUpdateAndDeleteNotifications() throws InterruptedException {
        JsonObject testDocument
                = new Gson().fromJson("{ \"randomString\" : \"36e8807f-0810-4b80-9ca9-b20859433034\" }", JsonObject.class);

        // Creating a document should trigger an update notification
        mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
        mockUpdateNotifications.expectedMessageCount(1);
        Exchange createExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(testDocument);
        });
        final String createdDocumentId = createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String createdDocumentRevision
                = createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertNotNull(createdDocumentId);
        assertNotNull(createdDocumentRevision);
        mockUpdateNotifications.assertIsSatisfied();

        // Consulting a created document should succeed
        Map<String, Object> retrieveExchangeHeaders = new HashMap<>();
        retrieveExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, createdDocumentId);
        retrieveExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
        String retrievedDocumentString
                = template.requestBodyAndHeaders(couchDbIn, testDocument, retrieveExchangeHeaders, String.class);
        assertNotNull(retrievedDocumentString);
        JsonObject retrievedDocument = new Gson().fromJson(retrievedDocumentString, JsonObject.class);
        assertEquals("36e8807f-0810-4b80-9ca9-b20859433034", retrievedDocument.get("randomString").getAsString());
        assertEquals(createdDocumentId, retrievedDocument.get("_id").getAsString());
        assertEquals(createdDocumentRevision, retrievedDocument.get("_rev").getAsString());

        // Updating a document should trigger a second update notification
        mockUpdateNotifications.reset();
        mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
        mockUpdateNotifications.expectedMessageCount(1);
        retrievedDocument.addProperty("randomString", "36e8807f-0810-4b80-9ca9-b20859433035");
        Exchange updateExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(retrievedDocument);
        });
        final String updatedDocumentId = updateExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String updatedDocumentRevision
                = updateExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertEquals(createdDocumentId, updatedDocumentId);
        assertNotEquals(createdDocumentRevision, updatedDocumentRevision);
        mockUpdateNotifications.assertIsSatisfied();

        // Consulting an updated document should succeed
        Map<String, Object> retrieveUpdatedExchangeHeaders = new HashMap<>();
        retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, updatedDocumentId);
        retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
        String retrievedUpdatedDocumentString
                = template.requestBodyAndHeaders(couchDbIn, retrievedDocument, retrieveUpdatedExchangeHeaders, String.class);
        assertNotNull(retrievedUpdatedDocumentString);
        JsonObject retrievedUpdatedDocument = new Gson().fromJson(retrievedUpdatedDocumentString, JsonObject.class);
        final String retrievedUpdatedDocumentId = retrievedUpdatedDocument.get("_id").getAsString();
        final String retrievedUpdatedDocumentRevision = retrievedUpdatedDocument.get("_rev").getAsString();
        assertEquals("36e8807f-0810-4b80-9ca9-b20859433035", retrievedUpdatedDocument.get("randomString").getAsString());
        assertEquals(updatedDocumentId, retrievedUpdatedDocumentId);
        assertEquals(updatedDocumentRevision, retrievedUpdatedDocumentRevision);

        // Deleting a retrieved document should trigger a delete notification
        mockDeleteNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "DELETE");
        mockDeleteNotifications.expectedMessageCount(1);
        Exchange deleteExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(retrievedUpdatedDocument);
            e.getMessage().setHeader(CouchDbConstants.HEADER_METHOD, CouchDbOperations.DELETE);
        });
        final String deletedDocumentId = deleteExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String deletedDocumentRevision
                = deleteExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertEquals(retrievedUpdatedDocumentId, deletedDocumentId);
        assertNotEquals(retrievedUpdatedDocumentRevision, deletedDocumentRevision);
        mockDeleteNotifications.assertIsSatisfied();
    }

    @Order(2)
    @Test
    void jsonStringDocumentCrudShouldTriggerUpdateAndDeleteNotifications() throws InterruptedException {
        JsonObject testDocument
                = new Gson().fromJson("{ \"randomString\" : \"36e8807f-0810-4b80-9ca9-b20859433044\" }", JsonObject.class);

        // Creating a document should trigger an update notification
        mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
        mockUpdateNotifications.expectedMessageCount(1);
        Exchange createExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(testDocument.toString());
        });
        final String createdDocumentId = createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String createdDocumentRevision
                = createExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertNotNull(createdDocumentId);
        assertNotNull(createdDocumentRevision);
        mockUpdateNotifications.assertIsSatisfied();

        // Consulting a created document should succeed
        Map<String, Object> retrieveExchangeHeaders = new HashMap<>();
        retrieveExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, createdDocumentId);
        retrieveExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
        String retrievedDocumentString = template.requestBodyAndHeaders(couchDbIn, "", retrieveExchangeHeaders, String.class);
        assertNotNull(retrievedDocumentString);
        JsonObject retrievedDocument = new Gson().fromJson(retrievedDocumentString, JsonObject.class);
        assertEquals("36e8807f-0810-4b80-9ca9-b20859433044", retrievedDocument.get("randomString").getAsString());
        assertEquals(createdDocumentId, retrievedDocument.get("_id").getAsString());
        assertEquals(createdDocumentRevision, retrievedDocument.get("_rev").getAsString());

        // Updating a document should trigger a second update notification
        mockUpdateNotifications.reset();
        mockUpdateNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "UPDATE");
        mockUpdateNotifications.expectedMessageCount(1);
        retrievedDocument.addProperty("randomString", "36e8807f-0810-4b80-9ca9-b20859433045");
        Exchange updateExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(retrievedDocument.toString());
        });
        final String updatedDocumentId = updateExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String updatedDocumentRevision
                = updateExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertEquals(createdDocumentId, updatedDocumentId);
        assertNotEquals(createdDocumentRevision, updatedDocumentRevision);
        mockUpdateNotifications.assertIsSatisfied();

        // Consulting an updated document should succeed
        Map<String, Object> retrieveUpdatedExchangeHeaders = new HashMap<>();
        retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_DOC_ID, updatedDocumentId);
        retrieveUpdatedExchangeHeaders.put(CouchDbConstants.HEADER_METHOD, CouchDbOperations.GET);
        String retrievedUpdatedDocumentString
                = template.requestBodyAndHeaders(couchDbIn, "", retrieveUpdatedExchangeHeaders, String.class);
        assertNotNull(retrievedUpdatedDocumentString);
        JsonObject retrievedUpdatedDocument = new Gson().fromJson(retrievedUpdatedDocumentString, JsonObject.class);
        final String retrievedUpdatedDocumentId = retrievedUpdatedDocument.get("_id").getAsString();
        final String retrievedUpdatedDocumentRevision = retrievedUpdatedDocument.get("_rev").getAsString();
        assertEquals("36e8807f-0810-4b80-9ca9-b20859433045", retrievedUpdatedDocument.get("randomString").getAsString());
        assertEquals(updatedDocumentId, retrievedUpdatedDocumentId);
        assertEquals(updatedDocumentRevision, retrievedUpdatedDocumentRevision);

        // Deleting a retrieved document should trigger a delete notification
        mockDeleteNotifications.expectedHeaderReceived(CouchDbConstants.HEADER_METHOD, "DELETE");
        mockDeleteNotifications.expectedMessageCount(1);
        Exchange deleteExchange = template.request(couchDbIn, e -> {
            e.getMessage().setBody(retrievedUpdatedDocument.toString());
            e.getMessage().setHeader(CouchDbConstants.HEADER_METHOD, CouchDbOperations.DELETE);
        });
        final String deletedDocumentId = deleteExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
        final String deletedDocumentRevision
                = deleteExchange.getMessage().getHeader(CouchDbConstants.HEADER_DOC_REV, String.class);
        assertEquals(retrievedUpdatedDocumentId, deletedDocumentId);
        assertNotEquals(retrievedUpdatedDocumentRevision, deletedDocumentRevision);
        mockDeleteNotifications.assertIsSatisfied();
    }

    @Order(3)
    @Test
    void aHundredDocumentCreationsShouldTriggerAHundredUpdateNotifications() throws InterruptedException {
        final int messageCount = 100;
        mockUpdateNotifications.expectedMessageCount(messageCount);

        for (int messageNumber = 0; messageNumber < messageCount; messageNumber++) {
            JsonObject document = new Gson().fromJson("{ \"randomString\" : \"" + UUID.randomUUID() + "\" }", JsonObject.class);
            template.requestBody(couchDbIn, document);
        }

        mockUpdateNotifications.assertIsSatisfied();
    }

}
