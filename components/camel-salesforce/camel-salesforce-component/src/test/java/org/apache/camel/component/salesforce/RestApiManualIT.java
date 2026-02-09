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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.SalesforceMultipleChoicesException;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.UpsertSObjectResult;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.apache.camel.component.salesforce.dto.generated.ContentVersion;
import org.apache.camel.component.salesforce.dto.generated.Document;
import org.apache.camel.component.salesforce.dto.generated.Folder;
import org.apache.camel.component.salesforce.dto.generated.Line_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsAccount;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsContact;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsFolder;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsLine_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Task;
import org.apache.camel.component.salesforce.dto.generated.User;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.EVENT_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.EVENT_SCHEMA_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("standalone")
public class RestApiManualIT extends AbstractSalesforceTestBase {

    /**
     * Request DTO for Salesforce APEX REST calls. See
     * https://developer.salesforce.com/docs/atlas.en-us.apexcode.meta/apexcode/apex_rest_methods.htm.
     */
    public static class MerchandiseRequest extends AbstractDTOBase {
        private Merchandise__c merchandise;

        public MerchandiseRequest(final Merchandise__c merchandise) {
            this.merchandise = merchandise;
        }

        public Merchandise__c getMerchandise() {
            return merchandise;
        }

        public void setMerchandise(final Merchandise__c merchandise) {
            this.merchandise = merchandise;
        }
    }

    /**
     * Response DTO for Salesforce APEX REST calls. See
     * https://developer.salesforce.com/docs/atlas.en-us.apexcode.meta/apexcode/apex_rest_methods.htm.
     */
    public static class MerchandiseResponse extends Merchandise__c {
        // XML response contains a type string with the SObject type name
        private String type;

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }
    }

    private static final AtomicInteger NEW_LINE_ITEM_ID = new AtomicInteger(100);

    private static final String TEST_DOCUMENT_ID = "Test Document";

    private static final AtomicInteger TEST_LINE_ITEM_ID = new AtomicInteger(1);

    private String merchandiseId;
    private String accountId;
    private String contactId;

    @AfterEach
    public void removeData() {
        template.request("salesforce:deleteSObject?sObjectName=Merchandise__c&sObjectId=" + merchandiseId, (Processor) e -> {
            // NOOP
        });
        template.requestBody("direct:deleteLineItems", "");
    }

    @BeforeEach
    public void setupData() {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("Test Merchandise");
        merchandise.setPrice__c(10.0);
        merchandise.setTotal_Inventory__c(100.0);
        merchandise.setDescription__c("Test Merchandise!");
        final CreateSObjectResult merchandiseResult
                = template().requestBody("salesforce:createSObject", merchandise, CreateSObjectResult.class);

        merchandiseId = merchandiseResult.getId();
    }

    private void createLineItem() {
        Line_Item__c lineItem = new Line_Item__c();
        final String lineItemId = String.valueOf(TEST_LINE_ITEM_ID.incrementAndGet());
        lineItem.setName(lineItemId);
        CreateSObjectResult result = template().requestBody("direct:createLineItem", lineItem, CreateSObjectResult.class);
    }

    private void createLineItems(int count) {
        List<Line_Item__c> lineItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Line_Item__c lineItem = new Line_Item__c();
            final String lineItemId = String.valueOf(TEST_LINE_ITEM_ID.incrementAndGet());
            lineItem.setName(lineItemId);
            lineItems.add(lineItem);
        }
        template().requestBody("direct:createLineItems", lineItems);
    }

    private void createAccountAndContact() {
        final Account account = new Account();
        account.setName("Child Test");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        CreateSObjectResult accountResult
                = template().requestBody("salesforce:createSObject", account, CreateSObjectResult.class);
        accountId = accountResult.getId();

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        final Contact contact = new Contact();
        contact.setAccount(accountRef);
        contact.setLastName("RelationshipTest");
        CreateSObjectResult contactResult
                = template().requestBody("salesforce:createSObject", contact, CreateSObjectResult.class);
        contactId = contactResult.getId();
    }

    private void deleteAccountAndContact() {
        if (accountId != null) {
            template.request("salesforce:deleteSObject?sObjectName=Account&sObjectId=" + accountId, (Processor) e -> {
                // NOOP
            });
        }
        if (contactId != null) {
            template.request("salesforce:deleteSObject?sObjectName=Contact&sObjectId=" + contactId, (Processor) e -> {
                // NOOP
            });
        }
    }

    @Test
    public void testApexCall() throws Exception {
        // request merchandise with id in URI template
        Merchandise__c merchandise
                = template().requestBodyAndHeader("direct:apexCallGet", null, "id", merchandiseId, Merchandise__c.class);
        assertNotNull(merchandise);

        // request merchandise with id as query param
        merchandise = template().requestBodyAndHeader("direct:apexCallGetWithId", null,
                SalesforceEndpointConfig.APEX_QUERY_PARAM_PREFIX + "id", merchandiseId,
                Merchandise__c.class);
        assertNotNull(merchandise);

        // patch merchandise
        // clear fields that won't be modified
        merchandise.clearBaseFields();
        merchandise.setId(merchandiseId);
        merchandise.setPrice__c(null);
        merchandise.setTotal_Inventory__c(null);

        merchandise = template().requestBody("direct:apexCallPatch", new MerchandiseRequest(merchandise), Merchandise__c.class);
        assertNotNull(merchandise);

        Exchange exchange = new DefaultExchange(context);
        template.send("direct:apexCallPostCustomError", exchange);
        SalesforceException exception = exchange.getException(SalesforceException.class);
        assertNotNull(exception);
        assertEquals("test response", IOUtils.toString(exception.getResponseContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void testApexCallDetectResponseType() throws Exception {
        // request merchandise with id in URI template
        Merchandise__c merchandise
                = template().requestBodyAndHeader("direct:apexCallGetDetectResponseType", null, "id", merchandiseId,
                        Merchandise__c.class);
        assertNotNull(merchandise);
    }

    @Test
    public void returnsHttpResponseStatusAndText() {
        Exchange exchange = new DefaultExchange(context);
        template().send("direct:query", exchange);
        assertEquals("200", exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNotNull(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT));
    }

    @Test
    public void testCreateUpdateDelete() throws Exception {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("Wee Wee Wee Plane");
        merchandise.setDescription__c("Microlite plane");
        merchandise.setPrice__c(2000.0);
        merchandise.setTotal_Inventory__c(50.0);
        final CreateSObjectResult result
                = template().requestBody("salesforce:createSObject", merchandise, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess(), "Create success");

        // test JSON update
        // make the plane cheaper
        merchandise.setPrice__c(1500.0);
        // change inventory to half
        merchandise.setTotal_Inventory__c(25.0);
        // also need to set the Id
        merchandise.setId(result.getId());

        assertNotNull(
                template().requestBodyAndHeader("salesforce:updateSObject", merchandise, SalesforceEndpointConfig.SOBJECT_ID,
                        result.getId()));

        // delete the newly created SObject
        assertNotNull(template().requestBody("salesforce:deleteSObject?sObjectName=Merchandise__c", result.getId()));
    }

    @Test
    public void testCreateMultipart() {
        final ContentVersion cv = new ContentVersion();
        cv.setPathOnClient("camel-test-doc.pdf");
        cv.setVersionDataBinary(getClass().getClassLoader().getResourceAsStream("camel-test-doc.pdf"));
        final CreateSObjectResult result
                = template.requestBody("salesforce:createSObject?sObjectName=ContentVersion",
                        cv,
                        CreateSObjectResult.class);
        assertTrue(result.getSuccess());
    }

    @Test
    public void testUpdateMultipart() {
        final QueryRecordsFolder queryResult = template.requestBody("salesforce:query" +
                                                                    "?sObjectQuery=SELECT Id FROM Folder WHERE Name = 'Test Documents'"
                                                                    +
                                                                    "&sObjectName=QueryRecordsFolder",
                null, QueryRecordsFolder.class);
        final Folder folder = queryResult.getRecords().get(0);

        // Create a Document
        final Document doc = new Document();
        doc.setFolderId(folder.getId());
        doc.setName("camel-test-doc.pdf");
        doc.setBodyBinary(getClass().getClassLoader().getResourceAsStream("camel-test-doc.pdf"));
        final CreateSObjectResult createResult = template.requestBody(
                "salesforce:createSObject?sObjectName=Document",
                doc,
                CreateSObjectResult.class);
        assertTrue(createResult.getSuccess());
        assertNotNull(createResult.getId());

        // Update the Document (e.g., change the name)
        final Document updateDoc = new Document();
        updateDoc.setId(createResult.getId());
        updateDoc.setName("camel-test-doc-updated.pdf");
        updateDoc.setBodyBinary(getClass().getClassLoader().getResourceAsStream("camel-test-doc.pdf"));
        final Object updateResult = template.requestBody(
                "salesforce:updateSObject?sObjectName=Document",
                updateDoc);
        assertNotNull(updateResult);
    }

    @Test
    public void testRelationshipCreateDelete() throws Exception {
        final Account account = new Account();
        account.setName("Account 1");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        final CreateSObjectResult accountResult
                = template().requestBody("salesforce:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue(accountResult.getSuccess(), "Create success");

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        final Contact contact = new Contact();
        contact.setAccount(accountRef);
        contact.setLastName("RelationshipTest");
        final CreateSObjectResult contactResult
                = template().requestBody("salesforce:createSObject", contact, CreateSObjectResult.class);
        assertNotNull(contactResult);
        assertTrue(contactResult.getSuccess(), "Create success");

        // delete the Contact
        template().requestBodyAndHeader("salesforce:deleteSObject", contactResult.getId(), "sObjectName", "Contact");

        // delete the Account
        template().requestBodyAndHeader("salesforce:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testFieldsToNull() throws Exception {
        final Account account = new Account();
        account.setName("Account 1");
        account.setSite("test site");
        final CreateSObjectResult accountResult
                = template().requestBody("salesforce:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue(accountResult.getSuccess(), "Create success");

        account.setId(accountResult.getId());
        account.setSite(null);
        account.getFieldsToNull().add("Site");

        final Object updateAccountResult = template().requestBodyAndHeader("salesforce:updateSObject", account,
                SalesforceEndpointConfig.SOBJECT_ID, account.getId());
        assertNotNull(updateAccountResult);

        Account updatedAccount = (Account) template().requestBodyAndHeader("salesforce:getSObject?sObjectFields=Id,Name,Site",
                account.getId(), "sObjectName", "Account");
        assertNull(updatedAccount.getSite());

        // delete the Account
        template().requestBodyAndHeader("salesforce:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testRelationshipUpdate() throws Exception {
        final Contact contact = new Contact();
        contact.setLastName("RelationshipTest");
        final CreateSObjectResult contactResult
                = template().requestBody("salesforce:createSObject", contact, CreateSObjectResult.class);
        assertNotNull(contactResult);
        assertTrue(contactResult.getSuccess(), "Create success");

        final Account account = new Account();
        account.setName("Account 1");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        final CreateSObjectResult accountResult
                = template().requestBody("salesforce:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue(accountResult.getSuccess(), "Create success");

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        contact.setAccount(accountRef);
        contact.setId(contactResult.getId());

        final Object updateContactResult = template().requestBodyAndHeader("salesforce:updateSObject", contact,
                SalesforceEndpointConfig.SOBJECT_ID, contact.getId());
        assertNotNull(updateContactResult);

        // delete the Contact
        template().requestBodyAndHeader("salesforce:deleteSObject", contactResult.getId(), "sObjectName", "Contact");

        // delete the Account
        template().requestBodyAndHeader("salesforce:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testCreateUpdateDeleteTasks() throws Exception {
        final Task taken = new Task();
        taken.setDescription("Task1");
        taken.setActivityDate(ZonedDateTime.of(1700, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));
        final CreateSObjectResult result = template().requestBody("salesforce:createSObject", taken, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess(), "Create success");

        // test JSON update
        // make the plane cheaper
        taken.setId(result.getId());
        taken.setActivityDate(ZonedDateTime.of(1991, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));

        assertNotNull(template().requestBodyAndHeader("salesforce:updateSObject", taken, SalesforceEndpointConfig.SOBJECT_ID,
                result.getId()));

        // delete the newly created SObject
        assertNotNull(template().requestBody("salesforce:deleteSObject?sObjectName=Task", result.getId()));
    }

    @Test
    public void testCreateUpdateDeleteWithId() throws Exception {
        Line_Item__c lineItem = new Line_Item__c();
        final String lineItemId = String.valueOf(TEST_LINE_ITEM_ID.incrementAndGet());
        lineItem.setName(lineItemId);
        CreateSObjectResult result = template().requestBody("direct:createLineItem", lineItem, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess());

        // get line item with Name 1
        lineItem = template().requestBody("salesforce:getSObjectWithId?sObjectIdName=Name&sObjectName=Line_Item__c",
                lineItemId, Line_Item__c.class);
        assertNotNull(lineItem);

        // test insert with id
        // set the unit price and sold
        lineItem.setUnit_Price__c(1000.0);
        lineItem.setUnits_Sold__c(50.0);
        // update line item with Name NEW_LINE_ITEM_ID
        final String newLineItemId = String.valueOf(NEW_LINE_ITEM_ID.incrementAndGet());
        lineItem.setName(newLineItemId);

        UpsertSObjectResult upsertResult = template().requestBodyAndHeader("direct:upsertSObject", lineItem,
                SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, newLineItemId, UpsertSObjectResult.class);
        assertNotNull(upsertResult);
        assertTrue(upsertResult.getSuccess());

        // clear read only parent type fields
        lineItem.setMerchandise__c(null);
        // change the units sold
        lineItem.setUnits_Sold__c(25.0);

        // update line item with Name NEW_LINE_ITEM_ID
        upsertResult = template().requestBodyAndHeader("direct:upsertSObject", lineItem,
                SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, newLineItemId, UpsertSObjectResult.class);
        assertNotNull(upsertResult);

        // delete the SObject with Name NEW_LINE_ITEM_ID
        assertNotNull(template().requestBody("direct:deleteSObjectWithId", newLineItemId));
    }

    @Test
    public void testUpsert() throws Exception {
        Line_Item__c lineItem = new Line_Item__c();
        final String lineItemId = String.valueOf(TEST_LINE_ITEM_ID.incrementAndGet());
        lineItem.setName(lineItemId);
        UpsertSObjectResult result = template().requestBody("direct:upsertSObject", lineItem, UpsertSObjectResult.class);
        assertNotNull(result);
        assertNotNull(lineItem.getName());
        assertTrue(result.getSuccess());
        assertTrue(result.getCreated());
    }

    @Test
    public void testGetBasicInfo() throws Exception {
        final SObjectBasicInfo objectBasicInfo = template().requestBody("direct:getBasicInfo", null, SObjectBasicInfo.class);
        assertNotNull(objectBasicInfo);

        // set test Id for testGetSObject
        assertFalse(objectBasicInfo.getRecentItems().isEmpty(), "RecentItems is empty");
        merchandiseId = objectBasicInfo.getRecentItems().get(0).getId();
    }

    @Test
    public void testGetBlobField() throws Exception {
        // get document with Name "Test Document"
        final HashMap<String, Object> headers = new HashMap<>();
        headers.put(SalesforceEndpointConfig.SOBJECT_NAME, "Document");
        headers.put(SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME, "Name");
        final Document document
                = template().requestBodyAndHeaders("salesforce:getSObjectWithId", TEST_DOCUMENT_ID, headers, Document.class);
        assertNotNull(document);

        // get Body field for this document
        try (final InputStream body = template().requestBody("direct:getBlobField", document, InputStream.class)) {
            assertNotNull(body);
            assertTrue(body.available() > 0);
        }
    }

    @Test
    public void testUploadBlob() throws Exception {
        final InputStream inputStream = this.getClass().getResourceAsStream("/camel-test-doc.pdf");
        final byte[] bytes = inputStream.readAllBytes();
        ObjectMapper mapper = new ObjectMapper();
        String enc = mapper.convertValue(bytes, String.class);
        ContentVersion cv = new ContentVersion();
        cv.setVersionData(enc);
        cv.setPathOnClient("camel-test-doc.pdf");
        cv.setTitle("Camel Test Doc");
        final CreateSObjectResult result = template.requestBody("salesforce:createSObject", cv, CreateSObjectResult.class);
        assertNotNull(result.getId());
    }

    @Test
    public void testGetDescription() throws Exception {
        final SObjectDescription sObjectDescription
                = template().requestBody("direct:getDescription", null, SObjectDescription.class);
        assertNotNull(sObjectDescription);
    }

    @Test
    public void testGetGlobalObjects() throws Exception {
        final GlobalObjects globalObjects = template().requestBody("direct:getGlobalObjects", null, GlobalObjects.class);
        assertNotNull(globalObjects);
    }

    @Test
    public void testGetResources() throws Exception {
        @SuppressWarnings("unchecked")
        final Map<String, String> resources = (Map<String, String>) template().requestBody("direct:getResources", "");
        assertNotNull(resources);
        assertTrue(resources.containsKey("metadata"));
    }

    @Test
    public void testGetSObject() throws Exception {
        final Merchandise__c merchandise = template().requestBody("direct:getSObject", merchandiseId, Merchandise__c.class);
        assertNotNull(merchandise);

        assertNull(merchandise.getTotal_Inventory__c());
        assertNotNull(merchandise.getPrice__c());
    }

    @Test
    public void testGetVersions() throws Exception {
        // test getVersions doesn't need a body
        // assert expected result
        final Object o = template().requestBody("direct:getVersions", (Object) null);
        List<Version> versions = null;
        if (o instanceof Versions) {
            versions = ((Versions) o).getVersions();
        } else {
            @SuppressWarnings("unchecked")
            final List<Version> tmp = (List<Version>) o;
            versions = tmp;
        }
        assertNotNull(versions);
    }

    @Test
    public void testGetEventSchemaByEventName() {
        final Object expandedResult
                = template.requestBodyAndHeader("salesforce:getEventSchema", "", EVENT_NAME, "BatchApexErrorEvent");
        assertNotNull(expandedResult);

        final Object compactResult = template.requestBodyAndHeaders("salesforce:getEventSchema", "",
                Map.of(EVENT_NAME, "BatchApexErrorEvent",
                        SalesforceEndpointConfig.EVENT_SCHEMA_FORMAT, "compact"));
        assertNotNull(compactResult);
    }

    @Test
    public void testGetEventSchemaBySchemaId() throws IOException {
        final Object schemaResult = template.requestBodyAndHeaders("salesforce:getEventSchema", "",
                Map.of(EVENT_NAME, "BatchApexErrorEvent",
                        SalesforceEndpointConfig.EVENT_SCHEMA_FORMAT, "compact"));
        assertNotNull(schemaResult);

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = (Map<String, Object>) mapper.readValue((InputStream) schemaResult, Map.class);
        final String schemaId = (String) map.get("uuid");

        final Object idResult = template.requestBodyAndHeader("salesforce:getEventSchema", "", EVENT_SCHEMA_ID, schemaId);
        assertNotNull(idResult);
    }

    @Test
    public void testQuery() throws Exception {
        createLineItem();
        final QueryRecordsLine_Item__c queryRecords
                = template().requestBody("direct:query", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
        // verify polymorphic query resulted in the correct type
        assertEquals(User.class, queryRecords.getRecords().get(0).getOwner().getClass());
        final Line_Item__c lineItem = queryRecords.getRecords().get(0);
        User user = (User) queryRecords.getRecords().get(0).getOwner();
        assertNotNull(user.getUsername());
        assertNotNull(lineItem.getRecordType());
    }

    @Test
    public void testQueryDetectResponseClass() throws Exception {
        createLineItem();
        final QueryRecordsLine_Item__c queryRecords
                = template().requestBody("direct:queryDetectResponseClass", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
    }

    @Test
    public void testQueryWithSObjectName() throws Exception {
        createLineItem();
        final QueryRecordsLine_Item__c queryRecords
                = template().requestBody("direct:queryWithSObjectName", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
        // verify polymorphic query resulted in the correct type
        assertEquals(User.class, queryRecords.getRecords().get(0).getOwner().getClass());
        final Line_Item__c lineItem = queryRecords.getRecords().get(0);
        User user = (User) queryRecords.getRecords().get(0).getOwner();
        assertNotNull(user.getUsername());
        assertNotNull(lineItem.getRecordType());
    }

    @Test
    public void testQueryStreamResults() throws Exception {
        final int createCount = 300;
        createLineItems(createCount);
        Exchange exchange = new DefaultExchange(context);
        template().send("direct:queryStreamResult", exchange);
        Iterator<?> queryRecords = exchange.getMessage(Iterator.class);
        assertNotNull(exchange.getMessage().getHeader("CamelSalesforceQueryResultTotalSize"));
        int count = 0;
        while (queryRecords.hasNext()) {
            count = count + 1;
            queryRecords.next();
        }
        assertTrue(count >= createCount);
    }

    @Test
    public void querySyncAsyncDoesntTimeout() throws Exception {
        final Object result = template.requestBody("direct:querySyncAsync", "");
        assertNotNull(result);
    }

    @Test
    public void testParentRelationshipQuery() throws Exception {
        try {
            createAccountAndContact();
            final QueryRecordsContact queryRecords
                    = template().requestBody("direct:parentRelationshipQuery", null, QueryRecordsContact.class);
            Account account = queryRecords.getRecords().get(0).getAccount();
            assertNotNull(account, "Account was null");
        } finally {
            deleteAccountAndContact();
        }
    }

    @Test
    public void testChildRelationshipQuery() throws Exception {
        try {
            createAccountAndContact();
            final QueryRecordsAccount queryRecords
                    = template().requestBody("direct:childRelationshipQuery", null, QueryRecordsAccount.class);

            assertFalse(queryRecords.getRecords().isEmpty());
            Account account1 = queryRecords.getRecords().get(0);
            assertFalse(account1.getContacts().getRecords().isEmpty());
        } finally {
            deleteAccountAndContact();
        }
    }

    @Test
    public void testQueryAll() throws Exception {
        final QueryRecordsLine_Item__c queryRecords
                = template().requestBody("direct:queryAll", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
    }

    @Test
    public void testQueryAllStreamResults() throws Exception {
        final int createCount = 300;
        createLineItems(createCount);
        final Iterator<Line_Item__c> queryRecords
                = template().requestBody("direct:queryAllStreamResult", "", Iterator.class);
        int count = 0;
        while (queryRecords.hasNext()) {
            count = count + 1;
            queryRecords.next();
        }
        assertTrue(count >= createCount);
    }

    @Test
    public void testRetry() throws Exception {
        final SalesforceComponent sf = context().getComponent("salesforce", SalesforceComponent.class);
        final String accessToken = sf.getSession().getAccessToken();

        final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(context));
        final ClientConnector connector = new ClientConnector();
        connector.setSslContextFactory(sslContextFactory);
        final HttpClientTransport transport = new HttpClientTransportOverHTTP(connector);
        final HttpClient httpClient = new HttpClient(transport);
        httpClient.setConnectTimeout(60000);
        httpClient.start();

        try {
            final String uri = sf.getLoginConfig().getLoginUrl() + "/services/oauth2/revoke?token=" + accessToken;
            final Request logoutGet = httpClient.newRequest(uri).method(HttpMethod.GET).timeout(1, TimeUnit.MINUTES);

            final ContentResponse response = logoutGet.send();
            assertEquals(HttpStatus.OK_200, response.getStatus());

            testGetGlobalObjects();
        } finally {
            httpClient.stop();
        }
    }

    @Test
    public void testRetryFailure() throws Exception {
        final SalesforceComponent sf = context().getComponent("salesforce", SalesforceComponent.class);
        final String accessToken = sf.getSession().getAccessToken();

        final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(context));
        final ClientConnector connector = new ClientConnector();
        connector.setSslContextFactory(sslContextFactory);
        final HttpClientTransport transport = new HttpClientTransportOverHTTP(connector);
        final HttpClient httpClient = new HttpClient(transport);
        httpClient.setConnectTimeout(60000);
        httpClient.start();

        try {
            final String uri = sf.getLoginConfig().getLoginUrl() + "/services/oauth2/revoke?token=" + accessToken;
            final Request logoutGet = httpClient.newRequest(uri).method(HttpMethod.GET).timeout(1, TimeUnit.MINUTES);

            final ContentResponse response = logoutGet.send();
            assertEquals(HttpStatus.OK_200, response.getStatus());

            // set component config to bad password to cause relogin attempts to
            // fail. Also clear refresh token and set explicit auth type to avoid ambiguous config.
            // Important: save password and refreshToken first, then clear refreshToken before
            // doing anything else - getType() throws if both password and refreshToken are set.
            final String password = sf.getLoginConfig().getPassword();
            final String refreshToken = sf.getLoginConfig().getRefreshToken();
            sf.getLoginConfig().setRefreshToken(null);
            sf.getLoginConfig().setPassword("bad_password");
            sf.getLoginConfig().setType(AuthenticationType.USERNAME_PASSWORD);

            try {
                testGetGlobalObjects();
                fail("Expected CamelExecutionException!");
            } catch (final CamelExecutionException e) {
                if (e.getCause() instanceof SalesforceException) {
                    final SalesforceException cause = (SalesforceException) e.getCause();
                    assertEquals(HttpStatus.BAD_REQUEST_400, cause.getStatusCode(),
                            "Expected 400 on authentication retry failure");
                } else {
                    fail("Expected SalesforceException!");
                }
            } finally {
                // reset config to allow other tests to pass
                // restore refreshToken first, then password, then clear type to let it auto-determine
                sf.getLoginConfig().setRefreshToken(refreshToken);
                sf.getLoginConfig().setPassword(password);
                sf.getLoginConfig().setType(null);
            }
        } finally {
            httpClient.stop();
        }
    }

    @Test
    public void testSearch() throws Exception {

        final Object obj = template().requestBody("direct:search", (Object) null);
        assertNotNull(obj);
    }

    @Test
    public void testStatus300() throws Exception {
        // get test merchandise
        // note that the header value overrides sObjectFields in endpoint
        final Merchandise__c merchandise = template().requestBodyAndHeader("direct:getSObject", merchandiseId, "sObjectFields",
                "Name,Description__c,Price__c,Total_Inventory__c",
                Merchandise__c.class);
        assertNotNull(merchandise);
        assertNotNull(merchandise.getName());
        assertNotNull(merchandise.getPrice__c());
        assertNotNull(merchandise.getTotal_Inventory__c());

        CreateSObjectResult result = null;
        try {
            merchandise.clearBaseFields();
            result = template().requestBody("salesforce:createSObject", merchandise, CreateSObjectResult.class);
            assertNotNull(result);
            assertNotNull(result.getId());

            // look by external Id to cause 300 error
            // note that the request SObject overrides settings on the endpoint
            // for LineItem__c
            try {
                template().requestBody("salesforce:getSObjectWithId?sObjectIdName=Name", merchandise, Merchandise__c.class);
                fail("Expected SalesforceException with statusCode 300");
            } catch (final CamelExecutionException e) {
                final Throwable cause = e.getCause();
                assertTrue(cause instanceof SalesforceMultipleChoicesException);
                final SalesforceMultipleChoicesException multipleChoices = (SalesforceMultipleChoicesException) cause;
                assertEquals(300, multipleChoices.getStatusCode());
                final List<String> choices = multipleChoices.getChoices();
                assertNotNull(choices);
                assertFalse(choices.isEmpty());
            }
        } finally {
            // delete the test clone
            if (result != null) {
                template().requestBody("salesforce:deleteSObject?sObjectName=Merchandise__c", result.getId());
            }
        }
    }

    @Test
    public void testStatus400() throws Exception {
        // get test merchandise
        // note that the header value overrides sObjectFields in endpoint
        final Merchandise__c merchandise = template().requestBodyAndHeader("direct:getSObject", merchandiseId, "sObjectFields",
                "Description__c,Price__c", Merchandise__c.class);
        assertNotNull(merchandise);
        assertNotNull(merchandise.getPrice__c());
        assertNull(merchandise.getTotal_Inventory__c());

        merchandise.clearBaseFields();
        // required field Total_Inventory__c is missing
        CreateSObjectResult result = null;
        try {
            result = template().requestBody("salesforce:createSObject", merchandise, CreateSObjectResult.class);
            fail("Expected SalesforceException with statusCode 400");
        } catch (final CamelExecutionException e) {
            final Throwable cause = e.getCause();
            assertTrue(cause instanceof SalesforceException);
            final SalesforceException badRequest = (SalesforceException) cause;
            assertEquals(400, badRequest.getStatusCode());
            assertEquals(1, badRequest.getErrors().size());
            assertEquals("[Total_Inventory__c]", badRequest.getErrors().get(0).getFields().toString());
        } finally {
            // delete the clone if created
            if (result != null) {
                template().requestBody("salesforce:deleteSObject", result.getId());
            }
        }
    }

    @Test
    public void testStatus404() throws Exception {
        // try to get a non existent SObject
        try {
            template().requestBody("direct:getSObject", "ILLEGAL_ID", Merchandise__c.class);
            fail("Expected SalesforceException");
        } catch (final CamelExecutionException e) {
            final Throwable cause = e.getCause();
            assertTrue(cause instanceof NoSuchSObjectException);
            final NoSuchSObjectException noSuchObject = (NoSuchSObjectException) cause;
            assertEquals(404, noSuchObject.getStatusCode());
            assertEquals(1, noSuchObject.getErrors().size());
        }
    }

    @Test
    public void testFetchingGlobalObjects() throws Exception {
        final GlobalObjects globalObjects = template().requestBody("salesforce:getGlobalObjects", null, GlobalObjects.class);

        assertNotNull(globalObjects);
        assertFalse(globalObjects.getSobjects().isEmpty());
    }

    @Test
    public void testBodyIsPreservedAfterError() throws Exception {
        Contact contact = new Contact();

        final Object result = template.requestBody("direct:createSObjectContinueOnException", contact);
        assertNotNull(result);
        assertEquals(contact, result);
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {

        // create test route
        return new RouteBuilder() {
            @Override
            public void configure() {

                // testGetVersion
                from("direct:getVersions").to("salesforce:getVersions");

                // testGetResources
                from("direct:getResources").to("salesforce:getResources");

                // testGetGlobalObjects
                from("direct:getGlobalObjects").to("salesforce:getGlobalObjects");

                // testGetBasicInfo
                from("direct:getBasicInfo").to("salesforce:getBasicInfo?sObjectName=Merchandise__c");

                // testGetDescription
                from("direct:getDescription").to("salesforce:getDescription?sObjectName=Merchandise__c");

                // testGetSObject
                from("direct:getSObject")
                        .to("salesforce:getSObject?sObjectName=Merchandise__c&sObjectFields=Description__c,Price__c");

                from("direct:deleteLineItems")
						.to("salesforce:query?sObjectQuery=SELECT Id FROM Line_Item__C&sObjectClass="
								+ QueryRecordsLine_Item__c.class.getName())
						.filter(simple("${body.records.size} > 0"))
						.split(simple("${body.records}"),
								AggregationStrategies.flexible().accumulateInCollection(ArrayList.class))
						.transform(simple("${body.id}"))
						.end()
						.split(simple("${collate(200)}"))
						.to("salesforce:compositeDeleteSObjectCollections")
						.end();

                from("direct:createLineItem").to("salesforce:createSObject?sObjectName=Line_Item__c");

                from("direct:createLineItems")
                        .split(simple("${collate(200)}"))
                        .to("salesforce:compositeCreateSObjectCollections");

                from("direct:upsertSObject")
                        .to("salesforce:upsertSObject?sObjectName=Line_Item__c&sObjectIdName=Name");

                // testDeleteSObjectWithId
                from("direct:deleteSObjectWithId")
                        .to("salesforce:deleteSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name");

                // testGetBlobField
                from("direct:getBlobField")
                        .to("salesforce:getBlobField?sObjectName=Document&sObjectBlobFieldName=Body");

                // testQuery
                from("direct:queryDetectResponseClass")
                        .to("salesforce:query?sObjectQuery=SELECT Id, name, Typeof Owner WHEN User Then Username End, recordTypeId, RecordType.Name "
                            + "from Line_Item__c "
                            + "ORDER BY CreatedDate DESC "
                            + "LIMIT 1");

                // testQuery
                from("direct:query")
                        .to("salesforce:query?sObjectQuery=SELECT Id, name, Typeof Owner WHEN User Then Username End, recordTypeId, RecordType.Name "
                            + "from Line_Item__c "
                            + "ORDER BY CreatedDate DESC "
                            + "LIMIT 1"
                            + "&sObjectClass=" + QueryRecordsLine_Item__c.class.getName());

                // testQuery
                from("direct:queryWithSObjectName")
                        .to("salesforce:query?sObjectQuery=SELECT Id, name, Typeof Owner WHEN User Then Username End, recordTypeId, RecordType.Name from Line_Item__c"
                            + "&sObjectName=QueryRecordsLine_Item__c");

                // testQuery
                from("direct:queryStreamResult")
                        .setHeader("sObjectClass", constant(QueryRecordsLine_Item__c.class.getName()))
                        .setHeader("Sforce-Query-Options", constant("batchSize=200"))
                        .to("salesforce:query?sObjectQuery=SELECT Id, name, Typeof Owner WHEN User Then Username End, recordTypeId, RecordType.Name from Line_Item__c Order By Name"
                            + "&streamQueryResult=true");

                // testQuery
                from("direct:queryAllStreamResult")
                        .setHeader("sObjectClass", constant(QueryRecordsLine_Item__c.class.getName()))
                        .setHeader("Sforce-Query-Options", constant("batchSize=200"))
                        .to("salesforce:queryAll?sObjectQuery=SELECT Id, name, Typeof Owner WHEN User Then Username End, recordTypeId, RecordType.Name from Line_Item__c Order By Name"
                            + "&streamQueryResult=true");

                // testParentRelationshipQuery
                from("direct:parentRelationshipQuery")
                        .process(exchange -> exchange.getIn()
                                .setBody("SELECT LastName, Account.Name FROM Contact WHERE Id = '" + contactId + "'"))
                        .to("salesforce:query?sObjectClass=" + QueryRecordsContact.class.getName() + "");

                // testChildRelationshipQuery
                from("direct:childRelationshipQuery")
                        .process(exchange -> exchange.getIn()
                                .setBody("SELECT Id, Name, (SELECT Id, LastName FROM Contacts)" + " FROM Account WHERE Id = '"
                                         + accountId + "'"))
                        .to("salesforce:query?sObjectClass=" + QueryRecordsAccount.class.getName() + "");

                // testQueryAll
                from("direct:queryAll")
                        .to("salesforce:queryAll?sObjectQuery=SELECT name from Line_Item__c&sObjectClass="
                            + QueryRecordsLine_Item__c.class.getName() + "");

                from("direct:querySyncAsync")
                        .to("direct:querySync")
                        .to("direct:queryAsync");

                from("direct:querySync?synchronous=false").routeId("r.querySync")
                        .to("salesforce:query?rawPayload=true&sObjectQuery=Select Id From Contact Where Name = 'Sync'");

                from("direct:queryAsync?synchronous=true").routeId("r.queryAsync")
                        .to("salesforce:query?rawPayload=true&sObjectQuery=Select Id From Contact  Where Name = 'Sync'");

                // testSearch
                from("direct:search").to("salesforce:search?sObjectSearch=FIND {Wee}");

                // testApexCall
                from("direct:apexCallGet")
                        .to("salesforce:apexCall?apexMethod=GET&apexUrl=Merchandise/{id}&sObjectName=Merchandise__c");

                // testApexCall
                from("direct:apexCallGetDetectResponseType")
                        .to("salesforce:apexCall?apexMethod=GET&apexUrl=Merchandise/{id}");

                from("direct:apexCallGetWithId")
                        .to("salesforce:apexCall/Merchandise/?apexMethod=GET&id=dummyId" + "&sObjectClass="
                            + Merchandise__c.class.getName());

                from("direct:apexCallPatch").to("salesforce:apexCall/Merchandise/"
                                                + "?apexMethod=PATCH&sObjectClass=" + MerchandiseResponse.class.getName());

                from("direct:apexCallPostCustomError").to("salesforce:apexCall/Merchandise/"
                                                          + "?apexMethod=POST&sObjectClass=java.lang.String");

                from("direct:createSObjectContinueOnException").onException(Exception.class).continued(true).end()
						.to("salesforce:createSObject");
            }
        };
    }
}
