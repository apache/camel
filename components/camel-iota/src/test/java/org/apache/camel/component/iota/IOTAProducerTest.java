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
package org.apache.camel.component.iota;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class IOTAProducerTest extends CamelTestSupport {

    private static final String ADDRESS_WITH_CHECKSUM = "RRMKENGN9DIFIKXSVGWGSMFJLHC9DHZURZAIBAPDH9DRRUFFUVLQHHFORX9WAWMBGRJULRESRCKCF9PVYLEIZWCHVC";
    private static final String TAG = "EHCCHECAMELTEST999999999999";

    private static final String IOTA_NODE_URL = "https://nodes.thetangle.org:443";

    @Test
    public void getTransactionByAddressTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:iota-get-transaction-by-address");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:iota-get-transaction-by-address", new String());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void getTransactionByTAG() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:iota-get-transaction-by-tag");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:iota-get-transaction-by-tag", new String());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:iota-get-transaction-by-address").setHeader(IOTAConstants.ADDRESS_HEADER, constant(ADDRESS_WITH_CHECKSUM))
                    .to("iota://test?url=" + IOTA_NODE_URL + "&operation=" + IOTAOperation.FIND_TRANSACTION_BY_ADDRESS).to("mock:iota-get-transaction-by-address");

                from("direct:iota-get-transaction-by-tag").setHeader(IOTAConstants.TAG_HEADER, constant(TAG))
                    .to("iota://test?url=" + IOTA_NODE_URL + "&operation=" + IOTAOperation.FIND_TRANSACTION_BY_TAG).to("mock:iota-get-transaction-by-tag");
            }
        };
    }
}
