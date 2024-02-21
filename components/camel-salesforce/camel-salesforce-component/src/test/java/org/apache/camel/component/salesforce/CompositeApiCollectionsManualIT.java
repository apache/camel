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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.DeleteSObjectResult;
import org.apache.camel.component.salesforce.api.dto.SaveSObjectResult;
import org.apache.camel.component.salesforce.api.dto.UpsertSObjectResult;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.apache.camel.test.junit5.params.Parameter;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Parameters;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.AfterEach;

import static org.apache.camel.language.constant.ConstantLanguage.constant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
@Parameterized
public class CompositeApiCollectionsManualIT extends AbstractSalesforceTestBase {

    private static final Set<String> VERSIONS = new HashSet<>(Arrays.asList("46.0", SalesforceEndpointConfig.DEFAULT_VERSION));

    @Parameter
    private String version;

    @AfterEach
    public void removeRecords() {
        template.request("direct:deleteCompositeAccounts", null);
    }

    @Test
    public void retrieve() {
        final String accountId = createAccount();

        List<AbstractDescribedSObjectBase> result
                = (List<AbstractDescribedSObjectBase>) fluentTemplate.to("salesforce:compositeRetrieveSObjectCollections")
                        .withHeader("sObjectIds", Collections.singletonList(accountId))
                        .withHeader("sObjectFields", Arrays.asList("Id", "Name"))
                        .withHeader("sObjectName", "Account")
                        .request();
        assertNotNull(result, "Response was null.");
        assertEquals(1, result.size());
    }

    @Test
    public void delete() {
        final String accountId = createAccount();
        final String account2Id = createAccount();
        final List<String> ids = Arrays.asList(accountId, account2Id, "001000000000000000");
        List<DeleteSObjectResult> result
                = (List<DeleteSObjectResult>) fluentTemplate.to("salesforce:compositeDeleteSObjectCollections")
                        .withHeader("sObjectIds", ids)
                        .request();
        assertNotNull(result, "Response was null.");
        assertEquals(3, result.size());
        assertTrue(result.get(0).getSuccess());
        assertTrue(result.get(1).getSuccess());
        assertFalse(result.get(2).getSuccess());
    }

    @Test
    public void deleteAllOrNone() {
        final String accountId = createAccount();
        final List<String> ids = Arrays.asList(accountId, "001000000000000000");
        List<DeleteSObjectResult> result
                = (List<DeleteSObjectResult>) fluentTemplate.to("salesforce:compositeDeleteSObjectCollections")
                        .withHeader("sObjectIds", ids)
                        .withHeader("allOrNone", constant(true))
                        .request();
        assertNotNull(result, "Response was null.");
        assertEquals(2, result.size());
        assertFalse(result.get(0).getSuccess());
        assertFalse(result.get(1).getSuccess());
    }

    @Test
    public void create() {
        Account account1 = new Account();
        account1.setName("Account created from Composite Collections API");

        Account account2 = new Account();
        final List<Account> accounts = Arrays.asList(account1, account2);

        List<SaveSObjectResult> result
                = (List<SaveSObjectResult>) template.requestBody(
                        "salesforce:compositeCreateSObjectCollections", accounts);
        assertNotNull(result, "Response was null.");
        assertEquals(2, result.size());
        assertTrue(result.get(0).getSuccess());
        assertFalse(result.get(1).getSuccess());
    }

    @Test
    public void createAllOrNone() {
        Account account1 = new Account();
        account1.setName("Account created from Composite Collections API");

        Account account2 = new Account();
        final List<Account> accounts = Arrays.asList(account1, account2);

        List<SaveSObjectResult> result
                = (List<SaveSObjectResult>) template.requestBody(
                        "salesforce:compositeCreateSObjectCollections", accounts);
        assertNotNull(result, "Response was null.");
        assertEquals(2, result.size());
        assertTrue(result.get(0).getSuccess());
        assertFalse(result.get(1).getSuccess());
    }

    @Test
    public void update() {
        final String accountId = createAccount();
        Account account = new Account();
        account.setId(accountId);
        final List<Account> accounts = Collections.singletonList(account);

        List<SaveSObjectResult> result = (List<SaveSObjectResult>) template.requestBody(
                "salesforce:compositeUpdateSObjectCollections", accounts);
        assertNotNull(result, "Response was null.");
        assertTrue(result.get(0).getSuccess());
    }

    @Test
    public void upsert() {
        Account account = new Account();
        account.setName("Account created from Composite Collections API");
        account.setExternal_Id__c("AAA");
        final List<Account> accounts = Collections.singletonList(account);

        List<UpsertSObjectResult> result = (List<UpsertSObjectResult>) template.requestBody(
                "salesforce:compositeUpsertSObjectCollections?sObjectName=Account&sObjectIdName=External_Id__c",
                accounts);
        assertNotNull(result, "Response was null.");
        assertTrue(result.get(0).getSuccess());
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:deleteCompositeAccounts")
                        .to("salesforce:query?sObjectClass=" + Account.class.getName()
                                + "&sObjectQuery=SELECT Id FROM Account WHERE Name = 'Account created from Composite Collections API'")
                        .split(simple("${body.records}")).setHeader("sObjectId", simple("${body.id}"))
                        .to("salesforce:deleteSObject?sObjectName=Account").end();
            }
        };
    }

    @Parameters(name = "version = {0}")
    public static Iterable<Object[]> versions() {
        return VERSIONS.stream().map(v -> new Object[] { v }).collect(Collectors.toList());
    }

    private String createAccount() {
        Account account = new Account();
        account.setName("Account created from Composite Collections API");
        final CreateSObjectResult createResult
                = template.requestBody("salesforce:createSObject", account, CreateSObjectResult.class);
        return createResult.getId();
    }
}
