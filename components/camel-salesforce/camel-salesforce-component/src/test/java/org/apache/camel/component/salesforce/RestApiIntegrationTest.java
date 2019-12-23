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

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.SalesforceMultipleChoicesException;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.RestResources;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.apache.camel.component.salesforce.dto.generated.Document;
import org.apache.camel.component.salesforce.dto.generated.Line_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsAccount;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsContact;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsLine_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Task;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@Category(Standalone.class)
@RunWith(Parameterized.class)
public class RestApiIntegrationTest extends AbstractSalesforceTestBase {

    /**
     * Request DTO for Salesforce APEX REST calls. See
     * https://www.salesforce.com/us/developer/docs/apexcode/Content/apex_rest_methods.htm.
     */
    @XStreamAlias("request")
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
     * https://www.salesforce.com/us/developer/docs/apexcode/Content/apex_rest_methods.htm.
     */
    @XStreamAlias("response")
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

    @Parameter
    public String format;

    private String merchandiseId;
    private String accountId;
    private String contactId;

    @After
    public void removeData() {
        template.request("salesforce:deleteSObject?sObjectName=Merchandise__c&sObjectId=" + merchandiseId, (Processor) e -> {
            // NOOP
        });
        template.request("direct:deleteLineItems", (Processor) e -> {
            // NOOP
        });
    }

    @Before
    public void setupData() {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("Test Merchandise");
        merchandise.setPrice__c(10.0);
        merchandise.setTotal_Inventory__c(100.0);
        merchandise.setDescription__c("Test Merchandise!");
        final CreateSObjectResult merchandiseResult = template().requestBody("salesforce:createSObject", merchandise, CreateSObjectResult.class);

        merchandiseId = merchandiseResult.getId();
    }

    private void createAccountAndContact() {
        final Account account = new Account();
        account.setName("Child Test");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        CreateSObjectResult accountResult = template().requestBody("direct:createSObject", account, CreateSObjectResult.class);
        accountId = accountResult.getId();

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        final Contact contact = new Contact();
        contact.setAccount(accountRef);
        contact.setLastName("RelationshipTest");
        CreateSObjectResult contactResult = template().requestBody("direct:createSObject", contact, CreateSObjectResult.class);
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
        Merchandise__c merchandise = template().requestBodyAndHeader("direct:apexCallGet", null, "id", merchandiseId, Merchandise__c.class);
        assertNotNull(merchandise);

        // request merchandise with id as query param
        merchandise = template().requestBodyAndHeader("direct:apexCallGetWithId", null, SalesforceEndpointConfig.APEX_QUERY_PARAM_PREFIX + "id", merchandiseId,
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
    }

    @Test
    public void testCreateUpdateDelete() throws Exception {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("Wee Wee Wee Plane");
        merchandise.setDescription__c("Microlite plane");
        merchandise.setPrice__c(2000.0);
        merchandise.setTotal_Inventory__c(50.0);
        final CreateSObjectResult result = template().requestBody("direct:createSObject", merchandise, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue("Create success", result.getSuccess());

        // test JSON update
        // make the plane cheaper
        merchandise.setPrice__c(1500.0);
        // change inventory to half
        merchandise.setTotal_Inventory__c(25.0);
        // also need to set the Id
        merchandise.setId(result.getId());

        assertNull(template().requestBodyAndHeader("direct:updateSObject", merchandise, SalesforceEndpointConfig.SOBJECT_ID, result.getId()));

        // delete the newly created SObject
        assertNull(template().requestBody("direct:deleteSObject", result.getId()));
    }

    @Test
    public void testRelationshipCreateDelete() throws Exception {
        final Account account = new Account();
        account.setName("Account 1");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        final CreateSObjectResult accountResult = template().requestBody("direct:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue("Create success", accountResult.getSuccess());

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        final Contact contact = new Contact();
        contact.setAccount(accountRef);
        contact.setLastName("RelationshipTest");
        final CreateSObjectResult contactResult = template().requestBody("direct:createSObject", contact, CreateSObjectResult.class);
        assertNotNull(contactResult);
        assertTrue("Create success", contactResult.getSuccess());

        // delete the Contact
        template().requestBodyAndHeader("direct:deleteSObject", contactResult.getId(), "sObjectName", "Contact");

        // delete the Account
        template().requestBodyAndHeader("direct:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testFieldsToNull() throws Exception {
        final Account account = new Account();
        account.setName("Account 1");
        account.setSite("test site");
        final CreateSObjectResult accountResult = template().requestBody("direct:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue("Create success", accountResult.getSuccess());

        account.setId(accountResult.getId());
        account.setSite(null);
        account.getFieldsToNull().add("Site");

        final Object updateAccountResult = template().requestBodyAndHeader("salesforce:updateSObject", account, SalesforceEndpointConfig.SOBJECT_ID, account.getId());
        assertNull(updateAccountResult);

        Account updatedAccount = (Account)template().requestBodyAndHeader("salesforce:getSObject?sObjectFields=Id,Name,Site", account.getId(), "sObjectName", "Account");
        assertNull(updatedAccount.getSite());

        // delete the Account
        template().requestBodyAndHeader("direct:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testRelationshipUpdate() throws Exception {
        final Contact contact = new Contact();
        contact.setLastName("RelationshipTest");
        final CreateSObjectResult contactResult = template().requestBody("direct:createSObject", contact, CreateSObjectResult.class);
        assertNotNull(contactResult);
        assertTrue("Create success", contactResult.getSuccess());

        final Account account = new Account();
        account.setName("Account 1");
        String accountExternalId = UUID.randomUUID().toString();
        account.setExternal_Id__c(accountExternalId);
        final CreateSObjectResult accountResult = template().requestBody("direct:createSObject", account, CreateSObjectResult.class);
        assertNotNull(accountResult);
        assertTrue("Create success", accountResult.getSuccess());

        final Account accountRef = new Account();
        accountRef.setExternal_Id__c(accountExternalId);
        contact.setAccount(accountRef);
        contact.setId(contactResult.getId());

        final Object updateContactResult = template().requestBodyAndHeader("salesforce:updateSObject", contact, SalesforceEndpointConfig.SOBJECT_ID, contact.getId());
        assertNull(updateContactResult);

        // delete the Contact
        template().requestBodyAndHeader("direct:deleteSObject", contactResult.getId(), "sObjectName", "Contact");

        // delete the Account
        template().requestBodyAndHeader("direct:deleteSObject", accountResult.getId(), "sObjectName", "Account");
    }

    @Test
    public void testCreateUpdateDeleteTasks() throws Exception {
        final Task taken = new Task();
        taken.setDescription("Task1");
        taken.setActivityDate(ZonedDateTime.of(1700, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));
        final CreateSObjectResult result = template().requestBody("direct:createSObject", taken, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue("Create success", result.getSuccess());

        // test JSON update
        // make the plane cheaper
        taken.setId(result.getId());
        taken.setActivityDate(ZonedDateTime.of(1991, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));

        assertNull(template().requestBodyAndHeader("direct:updateSObject", taken, SalesforceEndpointConfig.SOBJECT_ID, result.getId()));

        // delete the newly created SObject
        assertNull(template().requestBody("direct:deleteSObjectTaken", result.getId()));
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
        lineItem = template().requestBody("direct:getSObjectWithId", lineItemId, Line_Item__c.class);
        assertNotNull(lineItem);

        // test insert with id
        // set the unit price and sold
        lineItem.setUnit_Price__c(1000.0);
        lineItem.setUnits_Sold__c(50.0);
        // update line item with Name NEW_LINE_ITEM_ID
        final String newLineItemId = String.valueOf(NEW_LINE_ITEM_ID.incrementAndGet());
        lineItem.setName(newLineItemId);

        result = template().requestBodyAndHeader("direct:upsertSObject", lineItem, SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, newLineItemId, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess());

        // clear read only parent type fields
        lineItem.setMerchandise__c(null);
        // change the units sold
        lineItem.setUnits_Sold__c(25.0);

        // update line item with Name NEW_LINE_ITEM_ID
        result = template().requestBodyAndHeader("direct:upsertSObject", lineItem, SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, newLineItemId, CreateSObjectResult.class);
        assertNull(result);

        // delete the SObject with Name NEW_LINE_ITEM_ID
        assertNull(template().requestBody("direct:deleteSObjectWithId", newLineItemId));
    }

    @Test
    public void testGetBasicInfo() throws Exception {
        final SObjectBasicInfo objectBasicInfo = template().requestBody("direct:getBasicInfo", null, SObjectBasicInfo.class);
        assertNotNull(objectBasicInfo);

        // set test Id for testGetSObject
        assertFalse("RecentItems is empty", objectBasicInfo.getRecentItems().isEmpty());
        merchandiseId = objectBasicInfo.getRecentItems().get(0).getId();
    }

    @Test
    public void testGetBlobField() throws Exception {
        // get document with Name "Test Document"
        final HashMap<String, Object> headers = new HashMap<>();
        headers.put(SalesforceEndpointConfig.SOBJECT_NAME, "Document");
        headers.put(SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME, "Name");
        final Document document = template().requestBodyAndHeaders("direct:getSObjectWithId", TEST_DOCUMENT_ID, headers, Document.class);
        assertNotNull(document);

        // get Body field for this document
        try (final InputStream body = template().requestBody("direct:getBlobField", document, InputStream.class)) {
            assertNotNull(body);
            assertTrue(body.available() > 0);
        }
    }

    @Test
    public void testGetDescription() throws Exception {

        final SObjectDescription sObjectDescription = template().requestBody("direct:getDescription", null, SObjectDescription.class);
        assertNotNull(sObjectDescription);
    }

    @Test
    public void testGetGlobalObjects() throws Exception {

        final GlobalObjects globalObjects = template().requestBody("direct:getGlobalObjects", null, GlobalObjects.class);
        assertNotNull(globalObjects);
    }

    @Test
    public void testGetResources() throws Exception {

        final RestResources resources = template().requestBody("direct:getResources", null, RestResources.class);
        assertNotNull(resources);
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
        final Object o = template().requestBody("direct:getVersions", (Object)null);
        List<Version> versions = null;
        if (o instanceof Versions) {
            versions = ((Versions)o).getVersions();
        } else {
            @SuppressWarnings("unchecked")
            final List<Version> tmp = (List<Version>)o;
            versions = tmp;
        }
        assertNotNull(versions);
    }

    @Test
    public void testQuery() throws Exception {
        final QueryRecordsLine_Item__c queryRecords = template().requestBody("direct:query", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
    }

    @Test
    public void testParentRelationshipQuery() throws Exception {
        try {
            createAccountAndContact();
            final QueryRecordsContact queryRecords = template().requestBody("direct:parentRelationshipQuery", null, QueryRecordsContact.class);
            Account account = queryRecords.getRecords().get(0).getAccount();
            assertNotNull("Account was null", account);
        } finally {
            deleteAccountAndContact();
        }
    }

    @Test
    public void testChildRelationshipQuery() throws Exception {
        try {
            createAccountAndContact();
            final QueryRecordsAccount queryRecords = template().requestBody("direct:childRelationshipQuery", null, QueryRecordsAccount.class);

            assertFalse(queryRecords.getRecords().isEmpty());
            Account account1 = queryRecords.getRecords().get(0);
            assertFalse(account1.getContacts().getRecords().isEmpty());
        } finally {
            deleteAccountAndContact();
        }
    }

    @Test
    public void testQueryAll() throws Exception {
        final QueryRecordsLine_Item__c queryRecords = template().requestBody("direct:queryAll", null, QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
    }

    @Test
    public void testRetry() throws Exception {
        final SalesforceComponent sf = context().getComponent("salesforce", SalesforceComponent.class);
        final String accessToken = sf.getSession().getAccessToken();

        final SslContextFactory sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(context));
        final HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setConnectTimeout(60000);
        httpClient.start();

        final String uri = sf.getLoginConfig().getLoginUrl() + "/services/oauth2/revoke?token=" + accessToken;
        final Request logoutGet = httpClient.newRequest(uri).method(HttpMethod.GET).timeout(1, TimeUnit.MINUTES);

        final ContentResponse response = logoutGet.send();
        assertEquals(HttpStatus.OK_200, response.getStatus());

        testGetGlobalObjects();
    }

    @Test
    public void testRetryFailure() throws Exception {
        final SalesforceComponent sf = context().getComponent("salesforce", SalesforceComponent.class);
        final String accessToken = sf.getSession().getAccessToken();

        final SslContextFactory sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(context));
        final HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setConnectTimeout(60000);
        httpClient.start();

        final String uri = sf.getLoginConfig().getLoginUrl() + "/services/oauth2/revoke?token=" + accessToken;
        final Request logoutGet = httpClient.newRequest(uri).method(HttpMethod.GET).timeout(1, TimeUnit.MINUTES);

        final ContentResponse response = logoutGet.send();
        assertEquals(HttpStatus.OK_200, response.getStatus());

        // set component config to bad password to cause relogin attempts to
        // fail
        final String password = sf.getLoginConfig().getPassword();
        sf.getLoginConfig().setPassword("bad_password");

        try {
            testGetGlobalObjects();
            fail("Expected CamelExecutionException!");
        } catch (final CamelExecutionException e) {
            if (e.getCause() instanceof SalesforceException) {
                final SalesforceException cause = (SalesforceException)e.getCause();
                assertEquals("Expected 400 on authentication retry failure", HttpStatus.BAD_REQUEST_400, cause.getStatusCode());
            } else {
                fail("Expected SalesforceException!");
            }
        } finally {
            // reset password and retries to allow other tests to pass
            sf.getLoginConfig().setPassword(password);
        }
    }

    @Test
    public void testSearch() throws Exception {

        final Object obj = template().requestBody("direct:search", (Object)null);
        assertNotNull(obj);
    }

    @Test
    public void testStatus300() throws Exception {
        // get test merchandise
        // note that the header value overrides sObjectFields in endpoint
        final Merchandise__c merchandise = template().requestBodyAndHeader("direct:getSObject", merchandiseId, "sObjectFields", "Name,Description__c,Price__c,Total_Inventory__c",
                                                                           Merchandise__c.class);
        assertNotNull(merchandise);
        assertNotNull(merchandise.getName());
        assertNotNull(merchandise.getPrice__c());
        assertNotNull(merchandise.getTotal_Inventory__c());

        CreateSObjectResult result = null;
        try {
            merchandise.clearBaseFields();
            result = template().requestBody("direct:createSObject", merchandise, CreateSObjectResult.class);
            assertNotNull(result);
            assertNotNull(result.getId());

            // look by external Id to cause 300 error
            // note that the request SObject overrides settings on the endpoint
            // for LineItem__c
            try {
                template().requestBody("direct:getSObjectWithId", merchandise, Merchandise__c.class);
                fail("Expected SalesforceException with statusCode 300");
            } catch (final CamelExecutionException e) {
                final Throwable cause = e.getCause();
                assertTrue(cause instanceof SalesforceMultipleChoicesException);
                final SalesforceMultipleChoicesException multipleChoices = (SalesforceMultipleChoicesException)cause;
                assertEquals(300, multipleChoices.getStatusCode());
                final List<String> choices = multipleChoices.getChoices();
                assertNotNull(choices);
                assertFalse(choices.isEmpty());
            }
        } finally {
            // delete the test clone
            if (result != null) {
                template().requestBody("direct:deleteSObject", result.getId());
            }
        }
    }

    @Test
    public void testStatus400() throws Exception {
        // get test merchandise
        // note that the header value overrides sObjectFields in endpoint
        final Merchandise__c merchandise = template().requestBodyAndHeader("direct:getSObject", merchandiseId, "sObjectFields", "Description__c,Price__c", Merchandise__c.class);
        assertNotNull(merchandise);
        assertNotNull(merchandise.getPrice__c());
        assertNull(merchandise.getTotal_Inventory__c());

        merchandise.clearBaseFields();
        // required field Total_Inventory__c is missing
        CreateSObjectResult result = null;
        try {
            result = template().requestBody("direct:createSObject", merchandise, CreateSObjectResult.class);
            fail("Expected SalesforceException with statusCode 400");
        } catch (final CamelExecutionException e) {
            final Throwable cause = e.getCause();
            assertTrue(cause instanceof SalesforceException);
            final SalesforceException badRequest = (SalesforceException)cause;
            assertEquals(400, badRequest.getStatusCode());
            assertEquals(1, badRequest.getErrors().size());
            assertEquals("[Total_Inventory__c]", badRequest.getErrors().get(0).getFields().toString());
        } finally {
            // delete the clone if created
            if (result != null) {
                template().requestBody("direct:deleteSObject", result.getId());
            }
        }
    }

    @Test
    public void testStatus404() {
        // try to get a non existent SObject
        try {
            template().requestBody("direct:getSObject", "ILLEGAL_ID", Merchandise__c.class);
            fail("Expected SalesforceException");
        } catch (final CamelExecutionException e) {
            final Throwable cause = e.getCause();
            assertTrue(cause instanceof NoSuchSObjectException);
            final NoSuchSObjectException noSuchObject = (NoSuchSObjectException)cause;
            assertEquals(404, noSuchObject.getStatusCode());
            assertEquals(1, noSuchObject.getErrors().size());
        }
    }

    @Test
    public void testFetchingGlobalObjects() {
        final GlobalObjects globalObjects = template().requestBody("salesforce:getGlobalObjects", null, GlobalObjects.class);

        assertNotNull(globalObjects);
        assertFalse(globalObjects.getSobjects().isEmpty());
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {

        // create test route
        return new RouteBuilder() {
            @Override
            public void configure() {

                // testGetVersion
                from("direct:getVersions").to("salesforce:getVersions?format=" + format);

                // testGetResources
                from("direct:getResources").to("salesforce:getResources?format=" + format);

                // testGetGlobalObjects
                from("direct:getGlobalObjects").to("salesforce:getGlobalObjects?format=" + format);

                // testGetBasicInfo
                from("direct:getBasicInfo").to("salesforce:getBasicInfo?sObjectName=Merchandise__c&format=" + format);

                // testGetDescription
                from("direct:getDescription").to("salesforce:getDescription?sObjectName=Merchandise__c&format=" + format);

                // testGetSObject
                from("direct:getSObject").to("salesforce:getSObject?sObjectName=Merchandise__c&sObjectFields=Description__c,Price__c&format=" + format);

                // testCreateSObject
                from("direct:createSObject").to("salesforce:createSObject?sObjectName=Merchandise__c&format=" + format);

                // testUpdateSObject
                from("direct:updateSObject").to("salesforce:updateSObject?sObjectName=Merchandise__c&format=" + format);

                // testDeleteSObject
                from("direct:deleteSObject").to("salesforce:deleteSObject?sObjectName=Merchandise__c&format=" + format);

                from("direct:deleteSObjectTaken").to("salesforce:deleteSObject?sObjectName=Task&format=" + format);

                // testGetSObjectWithId
                from("direct:getSObjectWithId").to("salesforce:getSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name&format=" + format);

                // testUpsertSObject
                from("direct:deleteLineItems").to("salesforce:query?sObjectQuery=SELECT Id FROM Line_Item__C&sObjectClass=" + QueryRecordsLine_Item__c.class.getName())
                    .transform(simple("${body.records}")).split(body()).transform(simple("${body.id}")).to("salesforce:deleteSObject?sObjectName=Line_Item__c");

                from("direct:createLineItem").to("salesforce:createSObject?sObjectName=Line_Item__c");

                from("direct:upsertSObject").to("salesforce:upsertSObject?sObjectName=Line_Item__c&sObjectIdName=Name&format=" + format);

                // testDeleteSObjectWithId
                from("direct:deleteSObjectWithId").to("salesforce:deleteSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name&format=" + format);

                // testGetBlobField
                from("direct:getBlobField").to("salesforce:getBlobField?sObjectName=Document&sObjectBlobFieldName=Body&format=" + format);

                // testQuery
                from("direct:query")
                    .to("salesforce:query?sObjectQuery=SELECT name from Line_Item__c&sObjectClass=" + QueryRecordsLine_Item__c.class.getName() + "&format=" + format);

                // testParentRelationshipQuery
                from("direct:parentRelationshipQuery").process(exchange -> exchange.getIn().setBody("SELECT LastName, Account.Name FROM Contact WHERE Id = '" + contactId + "'"))
                    .to("salesforce:query?sObjectClass=" + QueryRecordsContact.class.getName() + "&format=" + format);

                // testChildRelationshipQuery
                from("direct:childRelationshipQuery")
                    .process(exchange -> exchange.getIn().setBody("SELECT Id, Name, (SELECT Id, LastName FROM Contacts)" + " FROM Account WHERE Id = '" + accountId + "'"))
                    .to("salesforce:query?sObjectClass=" + QueryRecordsAccount.class.getName() + "&format=" + format);

                // testQueryAll
                from("direct:queryAll")
                    .to("salesforce:queryAll?sObjectQuery=SELECT name from Line_Item__c&sObjectClass=" + QueryRecordsLine_Item__c.class.getName() + "&format=" + format);

                // testSearch
                from("direct:search").to("salesforce:search?sObjectSearch=FIND {Wee}&format=" + format);

                // testApexCall
                from("direct:apexCallGet").to("salesforce:apexCall?apexMethod=GET&apexUrl=Merchandise/{id}&sObjectName=Merchandise__c&format=" + format);

                from("direct:apexCallGetWithId")
                    .to("salesforce:apexCall/Merchandise/?apexMethod=GET&id=dummyId&format=" + format + "&sObjectClass=" + Merchandise__c.class.getName());

                from("direct:apexCallPatch").to("salesforce:apexCall/Merchandise/?format=" + format + "&apexMethod=PATCH&sObjectClass=" + MerchandiseResponse.class.getName());
            }
        };
    }

    @Parameters(name = "format = {0}")
    public static Iterable<String> parameters() {
        return Arrays.asList("XML", "JSON");
    }
}
