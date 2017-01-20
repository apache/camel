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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.QueryRecordsReport;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractApprovalIntegrationTest extends AbstractSalesforceTestBase {

    protected static final Object NOT_USED = null;

    protected List<String> accountIds = Collections.emptyList();

    protected String userId;

    private final int accountCount;

    AbstractApprovalIntegrationTest(final int accountCount) {
        this.accountCount = accountCount;
    }

    @Before
    public void createAccounts() {
        final List<Account> accountsToCreate = IntStream.range(0, accountCount + 1).mapToObj(idx -> {
            final String name = "test-account-" + idx;
            final Account account = new Account();
            account.setName(name);

            return account;
        }).collect(Collectors.toList());

        accountIds = accountsToCreate.stream().map(account -> template
                .requestBody("salesforce:createSObject?sObjectName=Account", account, CreateSObjectResult.class))
                .map(CreateSObjectResult::getId).collect(Collectors.toList());
    }

    @After
    public void deleteAccounts() {
        accountIds.forEach(id -> template.sendBody("salesforce:deleteSObject?sObjectName=Account", id));
    }

    @Before
    public void setupUserId() throws IOException {
        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        final String userName = loginConfig.getUserName();

        // I happen to have a username (e-mail address) with '+' sign in it,
        // DefaultRestClient#urlEncode would encode '+' as '%20' and the query
        // would not return any result, so replacing '+' with '%' and '=' with
        // 'LIKE' makes sense in my case. It should also work for every other
        // case where '+' is not used as a part of the username.
        final String wildcardUsername = userName.replace('+', '%');

        final QueryRecordsReport results = template.requestBody(
                "salesforce:query?sObjectClass=" + QueryRecordsReport.class.getName()//
                    + "&sObjectQuery=SELECT Id FROM User WHERE Username LIKE '" + wildcardUsername + "'",
                NOT_USED, QueryRecordsReport.class);

        userId = results.getRecords().get(0).getId();
    }
}
