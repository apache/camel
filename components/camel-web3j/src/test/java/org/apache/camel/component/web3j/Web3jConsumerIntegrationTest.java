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
package org.apache.camel.component.web3j;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.component.web3j.Web3jConstants.*;

@Ignore("Integration test that requires a locally running synced ethereum node")
public class Web3jConsumerIntegrationTest extends Web3jTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Override
    protected String getUrl() {
        return "web3j://http://127.0.0.1:8545?";
    }

    @Test(timeout = 600000L)
    public void consumerTest() throws InterruptedException {
        mockResult.setResultWaitTime(600000L);
        mockResult.expectedMinimumMessageCount(1);
        mockResult.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(getUrl() + OPERATION.toLowerCase() + "=" + TRANSACTION_OBSERVABLE)
                        .to("mock:result");

                from(getUrl() + OPERATION.toLowerCase() + "=" + ETH_LOG_OBSERVABLE)
                        .to("mock:result");

                from(getUrl() + OPERATION.toLowerCase() + "=" + PENDING_TRANSACTION_OBSERVABLE)
                        .to("mock:result");

                from(getUrl() + OPERATION.toLowerCase() + "=" + BLOCK_OBSERVABLE)
                        .to("mock:result");

            }
        };
    }
}
