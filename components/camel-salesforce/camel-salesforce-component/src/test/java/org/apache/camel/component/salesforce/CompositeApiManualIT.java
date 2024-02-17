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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite.Method;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResult;
import org.apache.camel.component.salesforce.api.utils.Version;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.component.salesforce.dto.generated.Line_Item__c;
import org.apache.camel.test.junit5.params.Parameter;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Parameters;
import org.apache.camel.test.junit5.params.Test;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

@Parameterized
public class CompositeApiManualIT extends AbstractSalesforceTestBase {

    public static class Accounts extends AbstractQueryRecordsBase<Account> {
    }

    private static final Set<String> VERSIONS = new HashSet<>(Arrays.asList("38.0", SalesforceEndpointConfig.DEFAULT_VERSION));

    @Parameter
    private String format;

    @Parameter(1)
    private String version;

    private String accountId;

    private String compositeUri;

    @AfterEach
    public void removeRecords() {
        try {
            template.sendBody("salesforce:deleteSObject?sObjectName=Account&sObjectId=" + accountId, null);
        } catch (final CamelExecutionException ignored) {
            // other tests run in parallel could have deleted the Account
        }

        template.request("direct:deleteBatchAccounts", null);
    }

    @BeforeEach
    public void setupRecords() {
        compositeUri = "salesforce:composite?format=" + format;

        final Account account = new Account();
        account.setName("Composite API Batch");

        final CreateSObjectResult result = template.requestBody("salesforce:createSObject", account, CreateSObjectResult.class);

        accountId = result.getId();
    }

    @Test
    public void shouldSubmitBatchUsingCompositeApi() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        final Account updates = new Account();
        updates.setName("NewName");
        composite.addUpdate("Account", accountId, updates, "UpdateExistingAccountReferenceId");

        final Account newAccount = new Account();
        newAccount.setName("Account created from Composite batch API");
        composite.addCreate(newAccount, "CreateAccountReferenceId");

        composite.addGet("Account", accountId, "GetAccountReferenceId", "Name", "BillingPostalCode");

        composite.addDelete("Account", accountId, "DeleteAccountReferenceId");

        testComposite(composite);
    }

    @Test
    public void shouldSupportGenericCompositeRequests() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        composite.addGeneric(Method.GET, "/sobjects/Account/" + accountId, "GetExistingAccountReferenceId");

        testComposite(composite);
    }

    @Test
    public void shouldSupportObjectCreation() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        final Account newAccount = new Account();
        newAccount.setName("Account created from Composite batch API");
        composite.addCreate(newAccount, "CreateAccountReferenceId");

        final SObjectCompositeResponse response = testComposite(composite);

        assertResponseContains(response, "id");
    }

    @Test
    public void shouldSupportObjectDeletion() {
        final SObjectComposite composite = new SObjectComposite(version, true);
        composite.addDelete("Account", accountId, "DeleteAccountReferenceId");

        testComposite(composite);
    }

    @Test
    public void shouldSupportObjectRetrieval() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        composite.addGet("Account", accountId, "GetExistingAccountReferenceId", "Name");

        final SObjectCompositeResponse response = testComposite(composite);

        assertResponseContains(response, "Name");
    }

    @Test
    public void shouldSupportObjectUpdates() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        final Account updates = new Account();
        updates.setName("NewName");
        updates.setAccountNumber("AC12345");
        composite.addUpdate("Account", accountId, updates, "UpdateAccountReferenceId");

        testComposite(composite);
    }

    @Test
    public void shouldSupportObjectUpserts() {
        final SObjectComposite composite = new SObjectComposite(version, true);

        final Line_Item__c li = new Line_Item__c();
        composite.addUpsertByExternalId("Line_Item__c", "Name", "AC12345", li,
                "UpsertLineItemReferenceId");
        testComposite(composite);
    }

    @Test
    public void shouldSupportRaw() throws Exception {
        final String rawComposite = "{\n" +
                                    "   \"allOrNone\" : true,\n" +
                                    "   \"compositeRequest\" : [{\n" +
                                    "      \"method\": \"GET\",\n" +
                                    "      \"url\": \"/services/data/v" + version
                                    + "/query/?q=SELECT+Id+FROM+Contact+LIMIT+1\",\n" +
                                    "      \"referenceId\": \"contacts\"\n" +
                                    "    }]\n" +
                                    "}\n";
        final String response = testRawComposite(rawComposite);
        ObjectMapper objectMapper = new ObjectMapper();
        SObjectCompositeResponse sObjectCompositeResponse = objectMapper.readValue(
                response, SObjectCompositeResponse.class);
        assertResponseContains(sObjectCompositeResponse, "done");
    }

    @Test
    public void shouldSupportQuery() {
        final SObjectComposite composite = new SObjectComposite(version, true);
        composite.addQuery("SELECT Id, Name FROM Account", "SelectQueryReferenceId");

        final SObjectCompositeResponse response = testComposite(composite);

        assertResponseContains(response, "totalSize");
    }

    @Test
    public void shouldSupportQueryAll() {
        final SObjectComposite composite = new SObjectComposite(version, true);
        composite.addQueryAll("SELECT Id, Name FROM Account", "SelectQueryReferenceId");

        final SObjectCompositeResponse response = testComposite(composite);

        assertResponseContains(response, "totalSize");
    }

    @Test
    public void shouldSupportRelatedObjectRetrieval() {
        assumeFalse(Version.create(version).compareTo(Version.create("36.0")) < 0,
                "Version must be greater than or equal to 36.0");

        final SObjectComposite composite = new SObjectComposite("36.0", true);
        composite.addGetRelated("Account", accountId, "CreatedBy", "GetRelatedAccountReferenceId");

        final SObjectCompositeResponse response = testComposite(composite);

        assertResponseContains(response, "Username");
    }

    SObjectCompositeResponse testComposite(final SObjectComposite batch) {
        final SObjectCompositeResponse response = template.requestBody(compositeUri, batch, SObjectCompositeResponse.class);

        Assertions.assertThat(response).as("Response should be provided").isNotNull();

        Assertions.assertThat(response.getCompositeResponse()).as("Received errors in: " + response)
                .allMatch(val -> val.getHttpStatusCode() >= 200 && val.getHttpStatusCode() <= 299);

        return response;
    }

    String testRawComposite(final String rawComposite) {
        final String rawCompositeUri = "salesforce:composite?rawPayload=true";
        final String response = template.requestBody(rawCompositeUri, rawComposite, String.class);

        Assertions.assertThat(response).as("Response should be provided").isNotNull();

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

    @Parameters(name = "format = {0}, version = {1}")
    public static Iterable<Object[]> formats() {
        return VERSIONS.stream().map(v -> new Object[] { "JSON", v }).collect(Collectors.toList());
    }

    static void assertResponseContains(final SObjectCompositeResponse response, final String key) {
        Assertions.assertThat(response).isNotNull();

        final List<SObjectCompositeResult> compositeResponse = response.getCompositeResponse();
        Assertions.assertThat(compositeResponse).hasSize(1);

        final SObjectCompositeResult firstCompositeResponse = compositeResponse.get(0);
        Assertions.assertThat(firstCompositeResponse).isNotNull();

        final Object firstCompositeResponseBody = firstCompositeResponse.getBody();
        Assertions.assertThat(firstCompositeResponseBody).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        final Map<String, ?> body = (Map<String, ?>) firstCompositeResponseBody;
        Assertions.assertThat(body).containsKey(key);
        Assertions.assertThat(body.get(key)).isNotNull();
    }
}
