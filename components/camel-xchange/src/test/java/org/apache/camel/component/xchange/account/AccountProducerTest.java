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
package org.apache.camel.component.xchange.account;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.xchange.XChangeComponent;
import org.apache.camel.component.xchange.XChangeTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.Wallet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled("See CAMEL-19751 before enabling")
public class AccountProducerTest extends XChangeTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:balances")
                        .to("xchange:binance?service=account&method=balances");

                from("direct:wallets")
                        .to("xchange:binance?service=account&method=wallets");

                from("direct:fundingHistory")
                        .to("xchange:binance?service=account&method=fundingHistory");
            }
        };
    }

    @Test
    public void testBalances() {
        assumeTrue(useMockedBackend() || hasAPICredentials());

        List<Balance> balances = template.requestBody("direct:balances", null, List.class);
        assertNotNull(balances, "Balances not null");
    }

    @Test
    public void testWallets() {
        assumeTrue(useMockedBackend() || hasAPICredentials());

        List<Wallet> wallets = template.requestBody("direct:wallets", null, List.class);
        assertNotNull(wallets, "Wallets not null");
    }

    @Test
    public void testFundingHistory() {
        //disabled with mocked backend, see https://issues.apache.org/jira/browse/CAMEL-18486 for more details
        assumeTrue(/*useMockedBackend() ||*/ hasAPICredentials());

        List<FundingRecord> records = template.requestBody("direct:fundingHistory", null, List.class);
        assertNotNull(records, "Funding records not null");
    }

    private boolean hasAPICredentials() {
        XChangeComponent component = context().getComponent("xchange", XChangeComponent.class);
        ExchangeSpecification exchangeSpecification = component.getXChange("binance").getExchangeSpecification();
        return exchangeSpecification.getApiKey() != null;
    }
}
