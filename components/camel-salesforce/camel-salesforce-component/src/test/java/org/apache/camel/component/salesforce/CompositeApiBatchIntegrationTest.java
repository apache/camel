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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.googlecode.junittoolbox.ParallelParameterized;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch.Method;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResult;
import org.apache.camel.component.salesforce.api.utils.Version;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;

@RunWith(ParallelParameterized.class)
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

    private static final Set<String> VERSIONS = new HashSet<>(Arrays.asList(SalesforceEndpointConfig.DEFAULT_VERSION, "34.0", "36.0", "37.0", "39.0"));

    private String accountId;

    private final String batchuri;

    private final String version;

    public CompositeApiBatchIntegrationTest(final String format, final String version) {
        this.version = version;
        batchuri = "salesforce:composite-batch?format=" + format;
    }

    @After
    public void removeRecords() {
        try {
            template.sendBody("salesforce:deleteSObject?sObjectName=Account&sObjectId=" + accountId, null);
        } catch (final CamelExecutionException ignored) {
            // other tests run in parallel could have deleted the Account
        }

        template.request("direct:deleteBatchAccounts", null);
    }

    @Before
    public void setupRecords() {
        final Account account = new Account();
        account.setName("Composite API Batch");

        final CreateSObjectResult result = template.requestBody("salesforce:createSObject", account, CreateSObjectResult.class);

        accountId = result.getId();
    }

    @Test
    public void shouldSubmitBatchUsingCompositeApi() {
        final SObjectBatch batch = new SObjectBatch(version);

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
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addGeneric(Method.GET, "/sobjects/Account/" + accountId);

        testBatch(batch);
    }

    /**
     * The XML format fails, as Salesforce API wrongly includes whitespaces
     * inside tag names. E.g. <Ant Migration Tool>
     * https://www.w3.org/TR/2008/REC-xml-20081126/#NT-NameChar
     */
    @Test
    public void shouldSupportLimits() {
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addLimits();

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `LimitsSnapshot` node,
        // JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, Object> limits = (Map<String, Object>)result.getOrDefault("LimitsSnapshot", result);

        @SuppressWarnings("unchecked")
        final Map<String, String> apiRequests = (Map<String, String>)limits.get("DailyApiRequests");

        // for JSON value will be Integer, for XML (no type information) it will
        // be String
        // This number can be different per org, and future releases,
        // so let's just make sure it's greater than zero
        assertTrue(Integer.valueOf(String.valueOf(apiRequests.get("Max"))) > 0);
    }

    @Test
    public void shouldSupportObjectCreation() {
        final SObjectBatch batch = new SObjectBatch(version);

        final Account newAccount = new Account();
        newAccount.setName("Account created from Composite batch API");
        batch.addCreate(newAccount);

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();

        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `Result` node, JSON
        // does not
        @SuppressWarnings("unchecked")
        final Map<String, Object> creationOutcome = (Map<String, Object>)result.getOrDefault("Result", result);

        assertNotNull(creationOutcome.get("id"));
    }

    @Test
    public void shouldSupportObjectDeletion() {
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addDelete("Account", accountId);

        testBatch(batch);
    }

    @Test
    public void shouldSupportObjectRetrieval() {
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addGet("Account", accountId, "Name");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `Account` node, JSON
        // does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>)result.getOrDefault("Account", result);

        assertEquals("Composite API Batch", data.get("Name"));
    }

    @Test
    public void shouldSupportObjectUpdates() {
        final SObjectBatch batch = new SObjectBatch(version);

        final Account updates = new Account();
        updates.setName("NewName");
        updates.setAccountNumber("AC12345");
        batch.addUpdate("Account", accountId, updates);

        testBatch(batch);
    }

    @Test
    public void shouldSupportQuery() {
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addQuery("SELECT Id, Name FROM Account");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `QueryResult` node,
        // JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>)result.getOrDefault("QueryResult", result);

        assertNotNull(data.get("totalSize"));
    }

    @Test
    public void shouldSupportQueryAll() {
        final SObjectBatch batch = new SObjectBatch(version);

        batch.addQueryAll("SELECT Id, Name FROM Account");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `QueryResult` node,
        // JSON does not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>)result.getOrDefault("QueryResult", result);

        assertNotNull(data.get("totalSize"));
    }

    @Test
    public void shouldSupportRelatedObjectRetrieval() throws IOException {
        if (Version.create(version).compareTo(Version.create("36.0")) < 0) {
            return;
        }

        final SObjectBatch batch = new SObjectBatch("36.0");

        batch.addGetRelated("Account", accountId, "CreatedBy");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>)batchResult.getResult();

        // JSON and XML structure are different, XML has `User` node, JSON does
        // not
        @SuppressWarnings("unchecked")
        final Map<String, String> data = (Map<String, String>)result.getOrDefault("User", result);

        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        assertEquals(loginConfig.getUserName(), data.get("Username"));
    }

    @Test
    public void shouldSupportSearch() {
        final SObjectBatch batch = new SObjectBatch(version);

        // we cannot rely on search returning the `Composite API Batch` account
        // as the search indexer runs
        // asynchronously to object creation, so that account might not be
        // indexed at this time, so we search for
        // `United` Account that should be created with developer instance
        batch.addSearch("FIND {United} IN Name Fields RETURNING Account (Name)");

        final SObjectBatchResponse response = testBatch(batch);

        final List<SObjectBatchResult> results = response.getResults();
        final SObjectBatchResult batchResult = results.get(0);

        final Object firstBatchResult = batchResult.getResult();

        final Object searchResult;
        if (firstBatchResult instanceof Map) {
            // the JSON and XML responses differ, XML has a root node which can
            // be either SearchResults or
            // SearchResultWithMetadata
            // furthermore version 37.0 search results are no longer array, but
            // dictionary of {
            // "searchRecords": [<array>] } and the XML output changed to
            // <SearchResultWithMetadata><searchRecords>, so
            // we have:
            // @formatter:off
            // | version | format | response syntax |
            // | 34 | JSON | {attributes={type=Account... |
            // | 34 | XML | {SearchResults={attributes={type=Account... |
            // | 37 | JSON | {searchRecords=[{attributes={type=Account... |
            // | 37 | XML |
            // {SearchResultWithMetadata={searchRecords={attributes={type=Account...
            // |
            // @formatter:on
            @SuppressWarnings("unchecked")
            final Map<String, Object> tmp = (Map<String, Object>)firstBatchResult;

            @SuppressWarnings("unchecked")
            final Map<String, Object> nested = (Map<String, Object>)tmp.getOrDefault("SearchResultWithMetadata", tmp);

            // JSON and XML structure are different, XML has `SearchResults`
            // node, JSON does not
            searchResult = nested.getOrDefault("searchRecords", nested.getOrDefault("SearchResults", nested));
        } else {
            searchResult = firstBatchResult;
        }

        final Map<String, Object> result;
        if (searchResult instanceof List) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> tmp = (Map<String, Object>)((List)searchResult).get(0);
            result = tmp;
        } else {
            @SuppressWarnings("unchecked")
            final Map<String, Object> tmp = (Map<String, Object>)searchResult;
            result = tmp;
        }

        assertNotNull(result.get("Name"));
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:deleteBatchAccounts")
                    .to("salesforce:query?sObjectClass=" + Accounts.class.getName()
                        + "&sObjectQuery=SELECT Id FROM Account WHERE Name = 'Account created from Composite batch API'")
                    .split(simple("${body.records}")).setHeader("sObjectId", simple("${body.id}")).to("salesforce:deleteSObject?sObjectName=Account").end();
            }
        };
    }

    @Override
    protected String salesforceApiVersionToUse() {
        return version;
    }

    SObjectBatchResponse testBatch(final SObjectBatch batch) {
        final SObjectBatchResponse response = template.requestBody(batchuri, batch, SObjectBatchResponse.class);

        assertNotNull("Response should be provided", response);

        assertFalse("Received errors in: " + response, response.hasErrors());

        return response;
    }

    @Parameters(name = "format = {0}, version = {1}")
    public static Iterable<Object[]> formats() {
        return VERSIONS.stream().map(v -> new Object[] {"JSON", v}).collect(Collectors.toList());
    }
}
