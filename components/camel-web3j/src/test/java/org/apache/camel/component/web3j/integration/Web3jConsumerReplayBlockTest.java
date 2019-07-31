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
package org.apache.camel.component.web3j.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.component.web3j.Web3jConstants.OPERATION;
import static org.apache.camel.component.web3j.Web3jConstants.REPLAY_BLOCKS_OBSERVABLE;

@Ignore("Requires Ganache instance with 10 transactions")
public class Web3jConsumerReplayBlockTest extends Web3jIntegrationTestSupport {

    @Test
    public void consumerTest() throws Exception {
        mockResult.expectedMinimumMessageCount(4); // 3 blocks + DONE exchange
        mockError.expectedMessageCount(0);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("web3j://" + getUrl()
                        + OPERATION.toLowerCase() + "=" + REPLAY_BLOCKS_OBSERVABLE + "&"
                        + "fromBlock=0&"
                        + "toBlock=2&"
                        + "fullTransactionObjects=false")
                        .to("mock:result");
            }
        };
    }

}
