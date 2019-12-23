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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.RecentItem;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(Standalone.class)
public class RecentIntegrationTest extends AbstractSalesforceTestBase {

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

    private static final Object NOT_USED = null;

    @After
    public void deleteRecords() {
        template.sendBody("direct:delete-recent", NOT_USED);
    }

    @Before
    public void setupTenRecentItems() {
        final List<Account> accounts = IntStream.range(0, 10).mapToObj(RecentIntegrationTest::account).collect(Collectors.toList());

        template.sendBody("direct:create-recent", accounts);
    }

    @Test
    public void shouldFetchRecentItems() {
        @SuppressWarnings("unchecked")
        final List<RecentItem> items = template.requestBody("direct:test-recent", NOT_USED, List.class);

        assertRecentItemsSize(items, 10);
    }

    @Test
    public void shouldFetchRecentItemsLimitingByHeaderParam() {
        @SuppressWarnings("unchecked")
        final List<RecentItem> items = template.requestBody("direct:test-recent-with-header-limit-param", NOT_USED, List.class);

        assertRecentItemsSize(items, 5);
    }

    @Test
    public void shouldFetchRecentItemsLimitingByParamInBody() {
        @SuppressWarnings("unchecked")
        final List<RecentItem> items = template.requestBody("direct:test-recent-with-body-limit-param", NOT_USED, List.class);

        assertRecentItemsSize(items, 5);
    }

    @Test
    public void shouldFetchRecentItemsLimitingByUriParam() {
        @SuppressWarnings("unchecked")
        final List<RecentItem> items = template.requestBody("direct:test-recent-with-limit-uri-param", NOT_USED, List.class);

        assertRecentItemsSize(items, 5);
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:create-recent").split().body().to("salesforce:createSObject?sObjectName=Account").end()
                    .to("salesforce:query?sObjectClass=" + Accounts.class.getName() + "&sObjectQuery=SELECT Id FROM Account WHERE Name LIKE 'recent-%' FOR VIEW");

                from("direct:delete-recent").to("salesforce:query?sObjectClass=" + Accounts.class.getName() + "&sObjectQuery=SELECT Id FROM Account WHERE Name LIKE 'recent-%'")
                    .transform(simple("${body.records}")).split().body().setHeader(SalesforceEndpointConfig.SOBJECT_ID).simple("${body.id}")
                    .to("salesforce:deleteSObject?sObjectName=Account");

                from("direct:test-recent").to("salesforce:recent");

                from("direct:test-recent-with-limit-uri-param").to("salesforce:recent?limit=5");

                from("direct:test-recent-with-header-limit-param").setHeader(SalesforceEndpointConfig.LIMIT).constant(5).to("salesforce:recent");

                from("direct:test-recent-with-body-limit-param").setBody(constant(5)).to("salesforce:recent");
            }
        };
    }

    static Account account(final int ord) {
        final Account account = new Account();
        account.setName("recent-" + ord);

        return account;
    }

    static void assertRecentItemsSize(final List<RecentItem> items, final int expected) {
        final List<RecentItem> recentItems = items.stream().filter(i -> i.getName().startsWith("recent-")).collect(Collectors.toList());

        assertListSize("Expected " + expected + " items named `recent-N` in recent items", recentItems, expected);
    }
}
