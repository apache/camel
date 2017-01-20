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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch.Method;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResult;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompositeApiBatchIntegrationTest extends AbstractSalesforceTestBase {

    public static class Accounts extends AbstractQueryRecordsBase {
        @XStreamImplicit
        private List<Account> records;

        public List<Account> getRecords() {
            return records;
        }

        public void setRecords(final List<Account> records) {
            this.records = records;
        }

    }

    private static final String V34 = "34.0";

    private String accountId;

    private final String batchuri;

    public CompositeApiBatchIntegrationTest(final String format) {
        this.batchuri = "salesforce:composite-batch?format=" + format;
    }

    @Parameters(name = "format = {0}")
    public static Iterable<String> formats() {
        return Arrays.asList("JSON", "XML");
    }

    @After
    public void removeRecords() {
        template.sendBody("salesforce:deleteSObject?sObjectName=Account&sObjectId=" + accountId, null);

        template.request("direct:deleteBatchAccounts", null);
    }

    @Before
    public void setupRecords() {
        final Account account = new Account();
        account.setName("Composite API Batch");

        final CreateSObjectResult result = template.requestBody("salesforce:createSObject", account,
            CreateSObjectResult.class);

        accountId = result.getId();
    }

    @Test
    public void shouldSubmitBatchUsingCompositeApi() {
        final SObjectBatch batch = new SObjectBatch(V34);

        final Account updates = new Account();
        updates.setName("NewName");
        batch.addUpdate("Account", accountId, updates);

        final Account newAccount = new Account();
        newAccount.setName("Account created from Composite batch API");
        batch.addCreate(newAccount);

        batch.addGet("Account", accountId, "Name", "BillingPostalCode");

        batch.addDelete("Account", accountId);

        final SObjectBatchResponse response = template.requestBody(batchuri, batch, SObjectBatchResponse.class);

        assertNotNull("Response should be provided", response);

        assertFalse(response.hasErrors());
    }

    @Test
    public void shouldSupportGenericBatchRequests() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addGeneric(Method.GET, "/sobjects/Account/" + accountId);

        testBatch(batch);
    }

    @Test
    public void shouldSupportLimits() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addLimits();

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `LimitsSnapshot` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, Object> limits = (Map<String, Object>) result.getOrDefault("LimitsSnapshot", result);

        @SuppressWarnings("unchecked")
        final Map<String, String> apiRequests = (Map<String, String>) limits.get("DailyApiRequests");

        // for JSON value will be Integer, for XML (no type information) it will be String
        assertEquals("15000", String.valueOf(apiRequests.get("Max")));
    }

    @Test
    public void shouldSupportObjectCreation() {
        final SObjectBatch batch = new SObjectBatch(V34);

        final Account newAccount = new Account();
        newAccount.setName("Account created from Composite batch API");
        batch.addCreate(newAccount);

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();

        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `Result` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, Object> creationOutcome = (Map<String, Object>) result.getOrDefault("Result", result);

        assertNotNull(creationOutcome.get("id"));
    }

    @Test
    public void shouldSupportObjectDeletion() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addDelete("Account", accountId);

        testBatch(batch);
    }

    @Test
    public void shouldSupportObjectRetrieval() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addGet("Account", accountId, "Name");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `Account` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>) result.getOrDefault("Account", result);

        assertEquals("Composite API Batch", data.get("Name"));
    }

    @Test
    public void shouldSupportObjectUpdates() {
        final SObjectBatch batch = new SObjectBatch(V34);

        final Account updates = new Account();
        updates.setName("NewName");
        updates.setAccountNumber("AC12345");
        batch.addUpdate("Account", accountId, updates);

        testBatch(batch);
    }

    @Test
    public void shouldSupportQuery() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addQuery("SELECT Id, Name FROM Account");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `QueryResult` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>) result.getOrDefault("QueryResult", result);

        assertNotNull(data.get("totalSize"));
    }

    @Test
    public void shouldSupportQueryAll() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addQueryAll("SELECT Id, Name FROM Account");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `QueryResult` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>) result.getOrDefault("QueryResult", result);

        assertNotNull(data.get("totalSize"));
    }

    @Test
    public void shouldSupportRelatedObjectRetrieval() throws IOException {
        final SObjectBatch batch = new SObjectBatch("36.0");

        batch.addGetRelated("Account", accountId, "CreatedBy");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) batchResult.getResult();

        // JSON and XML structure are different, XML has `User` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>) result.getOrDefault("User", result);

        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        assertEquals(loginConfig.getUserName(), data.get("Username"));
    }

    @Test
    public void shouldSupportSearch() {
        final SObjectBatch batch = new SObjectBatch(V34);

        batch.addSearch("FIND {Batch} IN Name Fields RETURNING Account (Name) ");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        final Object firstBatchResult = batchResult.getResult();

        final Map<String, Object> result;
        if (firstBatchResult instanceof List) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> tmp = (Map<String, Object>) ((List) firstBatchResult).get(0);
            result = tmp;
        } else {
            @SuppressWarnings("unchecked")
            final Map<String, Object> tmp = (Map<String, Object>) firstBatchResult;
            result = tmp;
        }

        // JSON and XML structure are different, XML has `SearchResults` node, JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>) result.getOrDefault("SearchResults", result);

        assertNotNull(data.get("Name"));
    }

    SObjectBatchResponse testBatch(final SObjectBatch batch) {
        final SObjectBatchResponse response = template.requestBody(batchuri, batch, SObjectBatchResponse.class);

        assertNotNull("Response should be provided", response);

        assertFalse("Received errors in: " + response, response.hasErrors());

        return response;
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:deleteBatchAccounts")
                    .to("salesforce:query?sObjectClass=" + Accounts.class.getName()
                        + "&sObjectQuery=SELECT Id FROM Account WHERE Name = 'Account created from Composite batch API'")
                    .split(simple("${body.records}")).setHeader("sObjectId", simple("${body.id}"))
                    .to("salesforce:deleteSObject?sObjectName=Account").end();
            }
        };
    }

    @Override
    protected String salesforceApiVersionToUse() {
        return "37.0";
    }
}
