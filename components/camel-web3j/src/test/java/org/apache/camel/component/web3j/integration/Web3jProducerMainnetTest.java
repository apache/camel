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

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.component.web3j.Web3jConstants.ETH_BLOCK_NUMBER;
import static org.apache.camel.component.web3j.Web3jConstants.NET_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.OPERATION;
import static org.apache.camel.component.web3j.Web3jConstants.TRANSACTION;
import static org.apache.camel.component.web3j.Web3jConstants.WEB3_CLIENT_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.WEB3_SHA3;

@Ignore("Requires a local node or registration at Infura")
public class Web3jProducerMainnetTest extends Web3jIntegrationTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    protected String getUrl() {
        return "https://mainnet.infura.io/YOUR_INFURA_ID?";
    }

    @Test
    public void clientVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_CLIENT_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.startsWith("Geth"));
    }

    @Test
    public void netVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void netWeb3Sha3Test() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_SHA3);
        exchange.getIn().setBody("0x68656c6c6f20776f726c64");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad"));
    }

    @Test
    public void ethBlockNumberTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_BLOCK_NUMBER);
        template.send(exchange);
        Long body = exchange.getIn().getBody(Long.class);
        assertTrue(body.longValue() > 5714225); // latest block at time of writing
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("web3j://" + getUrl() + OPERATION.toLowerCase() + "=" + TRANSACTION);
            }
        };
    }
}
