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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCompileSolidity;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.ShhMessages;
import org.web3j.protocol.core.methods.response.Transaction;

import static org.apache.camel.component.web3j.Web3jConstants.ADDRESS;
import static org.apache.camel.component.web3j.Web3jConstants.ADDRESSES;
import static org.apache.camel.component.web3j.Web3jConstants.AT_BLOCK;
import static org.apache.camel.component.web3j.Web3jConstants.BLOCK_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.CLIENT_ID;
import static org.apache.camel.component.web3j.Web3jConstants.DATA;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_ACCOUNTS;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_BLOCK_NUMBER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_CALL;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_COINBASE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_COMPILE_LLL;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_COMPILE_SERPENT;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_COMPILE_SOLIDITY;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_ESTIMATE_GAS;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GAS_PRICE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_BALANCE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_BLOCK_BY_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_BLOCK_BY_NUMBER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_CODE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_COMPILERS;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_FILTER_CHANGES;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_FILTER_LOGS;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_LOGS;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_STORAGE_AT;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_TRANSACTION_BY_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_TRANSACTION_COUNT;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_GET_WORK;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_HASHRATE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_MINING;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_NEW_BLOCK_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_NEW_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_NEW_PENDING_TRANSACTION_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_PROTOCOL_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SEND_RAW_TRANSACTION;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SEND_TRANSACTION;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SIGN;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SUBMIT_HASHRATE;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SUBMIT_WORK;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_SYNCING;
import static org.apache.camel.component.web3j.Web3jConstants.ETH_UNINSTALL_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.FILTER_ID;
import static org.apache.camel.component.web3j.Web3jConstants.FROM_ADDRESS;
import static org.apache.camel.component.web3j.Web3jConstants.FROM_BLOCK;
import static org.apache.camel.component.web3j.Web3jConstants.FULL_TRANSACTION_OBJECTS;
import static org.apache.camel.component.web3j.Web3jConstants.GAS_LIMIT;
import static org.apache.camel.component.web3j.Web3jConstants.GAS_PRICE;
import static org.apache.camel.component.web3j.Web3jConstants.HEADER_POW_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.INDEX;
import static org.apache.camel.component.web3j.Web3jConstants.MIX_DIGEST;
import static org.apache.camel.component.web3j.Web3jConstants.NET_LISTENING;
import static org.apache.camel.component.web3j.Web3jConstants.NET_PEER_COUNT;
import static org.apache.camel.component.web3j.Web3jConstants.NET_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.NONCE;
import static org.apache.camel.component.web3j.Web3jConstants.OPERATION;
import static org.apache.camel.component.web3j.Web3jConstants.POSITION;
import static org.apache.camel.component.web3j.Web3jConstants.PRIORITY;
import static org.apache.camel.component.web3j.Web3jConstants.SHA3_HASH_OF_DATA_TO_SIGN;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_ADD_TO_GROUP;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_GET_FILTER_CHANGES;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_GET_MESSAGES;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_HAS_IDENTITY;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_NEW_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_NEW_GROUP;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_NEW_IDENTITY;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_POST;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_UNINSTALL_FILTER;
import static org.apache.camel.component.web3j.Web3jConstants.SHH_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.SIGNED_TRANSACTION_DATA;
import static org.apache.camel.component.web3j.Web3jConstants.SOURCE_CODE;
import static org.apache.camel.component.web3j.Web3jConstants.TOPICS;
import static org.apache.camel.component.web3j.Web3jConstants.TO_ADDRESS;
import static org.apache.camel.component.web3j.Web3jConstants.TO_BLOCK;
import static org.apache.camel.component.web3j.Web3jConstants.TRANSACTION;
import static org.apache.camel.component.web3j.Web3jConstants.TRANSACTION_HASH;
import static org.apache.camel.component.web3j.Web3jConstants.TTL;
import static org.apache.camel.component.web3j.Web3jConstants.VALUE;
import static org.apache.camel.component.web3j.Web3jConstants.WEB3_CLIENT_VERSION;
import static org.apache.camel.component.web3j.Web3jConstants.WEB3_SHA3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Requires a locally running Ganache instance")
public class Web3jProducerGanacheTest extends Web3jIntegrationTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Test
    public void ethClientVersionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_CLIENT_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethNetWeb3Sha3Test() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_SHA3);
        exchange.getIn().setBody("0x68656c6c6f20776f726c64");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertEquals("0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad", body);
    }

    @Test
    public void ethNetVersionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethNetListeningTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_LISTENING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    //a bug in Ganache returns wrong formatted data
    @Disabled
    @Test
    public void ethNetPeerCountTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_PEER_COUNT);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethProtocolVersionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_PROTOCOL_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethSyncingTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SYNCING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void ethCoinbaseTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COINBASE);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethMiningTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_MINING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void ethHashrateTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_HASHRATE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethGasPriceTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GAS_PRICE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethAccountsTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_ACCOUNTS);
        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void ethBlockNumberTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_BLOCK_NUMBER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetBalanceTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BALANCE);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(AT_BLOCK, "0");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    // Given this contract created at address 0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B:
    //    pragma solidity ^0.4.23;
    //    contract Storage {
    //        uint pos0;
    //        function Storage() {
    //            pos0 = 5;
    //        }
    //    }
    @Test
    public void ethGetStorageAtTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_STORAGE_AT);
        exchange.getIn().setHeader(ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(AT_BLOCK, "6");
        exchange.getIn().setHeader(POSITION, BigInteger.ZERO);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetTransactionCountTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_COUNT);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    //a bug in Ganache returns wrong formatted data
    @Disabled
    @Test
    public void ethGetBlockTransactionCountByHashTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a"); // Ganache block 0
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    //a bug in Ganache returns wrong formatted data
    @Disabled
    @Test
    public void ethGetBlockTransactionCountByNumberTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethGetUncleCountByBlockHashTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a"); // Ganache block 0
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethGetUncleCountByBlockNumberTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetCodeTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_CODE);
        exchange.getIn().setHeader(ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethSignTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SIGN);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(SHA3_HASH_OF_DATA_TO_SIGN, "hello");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethSendTransactionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SEND_TRANSACTION);
        exchange.getIn().setHeader(FROM_ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TO_ADDRESS, "0x883E97F42e3cfC2b233DC684574F33B96a0329C4");
        exchange.getIn().setHeader(NONCE, BigInteger.valueOf(9L));
        exchange.getIn().setHeader(GAS_PRICE, BigInteger.valueOf(10000000000000L));
        exchange.getIn().setHeader(GAS_LIMIT, BigInteger.valueOf(30400L));
        exchange.getIn().setHeader(VALUE, BigInteger.valueOf(50000000000000L));

        //        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);
        //
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    //wrong reminder
    @Disabled
    @Test
    public void ethSendRawTransactionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SEND_RAW_TRANSACTION);
        exchange.getIn().setHeader(SIGNED_TRANSACTION_DATA,
                "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethCallTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_CALL);
        exchange.getIn().setHeader(FROM_ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TO_ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(NONCE, BigInteger.valueOf(9L));
        exchange.getIn().setHeader(GAS_PRICE, BigInteger.valueOf(10000000000000L));
        exchange.getIn().setHeader(GAS_LIMIT, BigInteger.valueOf(30400L));
        exchange.getIn().setHeader(VALUE, BigInteger.valueOf(50000000000000L));

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethEstimateGasTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_ESTIMATE_GAS);
        exchange.getIn().setHeader(FROM_ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TO_ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(NONCE, BigInteger.valueOf(9L));
        exchange.getIn().setHeader(GAS_PRICE, BigInteger.valueOf(10000000000000L));
        exchange.getIn().setHeader(GAS_LIMIT, BigInteger.valueOf(30400L));
        exchange.getIn().setHeader(VALUE, BigInteger.valueOf(50000000000000L));

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetBlockByHashTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_BY_HASH);
        exchange.getIn().setHeader(FULL_TRANSACTION_OBJECTS, true);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a");

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetBlockByNumberTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_BY_NUMBER);
        exchange.getIn().setHeader(FULL_TRANSACTION_OBJECTS, true);
        exchange.getIn().setHeader(AT_BLOCK, "latest");

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetTransactionByHashTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_HASH);
        exchange.getIn().setHeader(TRANSACTION_HASH, "0xb082f44cb2faa0f33056d5a341d1a7be73ecfcc6eb3bcb643ab03016ce4b6772");

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetTransactionByBlockHashAndIndexTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX);
        exchange.getIn().setHeader(BLOCK_HASH, "0x226aa81c5a7c86caff96af0bdb58739491d4730b629932ca80f3530558282e1d");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetTransactionByBlockNumberAndIndexTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethGetUncleByBlockHashAndIndexTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX);
        exchange.getIn().setHeader(BLOCK_HASH, "0x226aa81c5a7c86caff96af0bdb58739491d4730b629932ca80f3530558282e1d");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethGetUncleByBlockNumberAndIndexTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetCompilers() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_COMPILERS);

        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethCompileSolidityTest() {
        String soliditySample = "pragma solidity ^0.4.23;"
                                + "contract Storage {"
                                + "    uint pos0;"
                                + "    function Storage() {"
                                + "        pos0 = 5;"
                                + "    }"
                                + "}";
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COMPILE_SOLIDITY);
        exchange.getIn().setHeader(SOURCE_CODE, soliditySample);
        template.send(exchange);
        Map<String, EthCompileSolidity.Code> body = exchange.getIn().getBody(Map.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethCompileLLLTest() {
        String sampleCode = "(returnlll (suicide (caller)))";
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COMPILE_LLL);
        exchange.getIn().setHeader(SOURCE_CODE, sampleCode);
        template.send(exchange);
        Map<String, EthCompileSolidity.Code> body = exchange.getIn().getBody(Map.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void ethCompileSerpentTest() {
        String serpentSample = "(returnlll (suicide (caller)))";
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COMPILE_SERPENT);
        exchange.getIn().setHeader(SOURCE_CODE, serpentSample);
        template.send(exchange);
        Map<String, EthCompileSolidity.Code> body = exchange.getIn().getBody(Map.class);
        assertNotNull(body);
    }

    @Test
    public void ethNewFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_FILTER);
        exchange.getIn().setHeader(FROM_BLOCK, "earliest");
        exchange.getIn().setHeader(TO_BLOCK, "latest");
        exchange.getIn().setHeader(ADDRESSES, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TOPICS, "0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethNewBlockFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_BLOCK_FILTER);

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethNewPendingTransactionFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_PENDING_TRANSACTION_FILTER);

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    @Test
    public void ethUninstallFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_UNINSTALL_FILTER);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(8));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetFilterChangesTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_FILTER_CHANGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(7));

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetFilterLogsTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_FILTER_LOGS);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(6));

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetLogsTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_LOGS);
        exchange.getIn().setHeader(FROM_BLOCK, "earliest");
        exchange.getIn().setHeader(TO_BLOCK, "latest");
        //        exchange.getIn().setHeader(ADDRESSES, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        //        exchange.getIn().setHeader(TOPICS, "0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void ethGetWorkTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_WORK);

        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void ethSubmitWorkTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SUBMIT_WORK);
        exchange.getIn().setHeader(NONCE, "0x0000000000000001");
        exchange.getIn().setHeader(HEADER_POW_HASH, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        exchange.getIn().setHeader(MIX_DIGEST, "0xD1FE5700000000000000000000000000D1FE5700000000000000000000000000");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void ethSubmitHashrateTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SUBMIT_HASHRATE);
        exchange.getIn().setHeader(ETH_HASHRATE, "0x0000000000000000000000000000000000000000000000000000000000500000");
        exchange.getIn().setHeader(CLIENT_ID, "0x59daa26581d0acd1fce254fb7e85952f4c09d0915afd33d3886cd914bc7d283c");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void shhVersionTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_VERSION);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhPostTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_POST);
        exchange.getIn().setHeader(FROM_ADDRESS,
                "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1");
        exchange.getIn().setHeader(TO_ADDRESS,
                "0x3e245533f97284d442460f2998cd41858798ddf04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a0d4d661997d3940272b717b1");
        exchange.getIn().setHeader(TOPICS, "0x776869737065722d636861742d636c69656e74");
        exchange.getIn().setHeader(DATA, "0x7b2274797065223a226d6");
        exchange.getIn().setHeader(PRIORITY, BigInteger.valueOf(64));
        exchange.getIn().setHeader(TTL, BigInteger.valueOf(64));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhNewIdentityTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_IDENTITY);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhHasIdentityTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_HAS_IDENTITY);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhNewGroupTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_GROUP);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhAddToGroupTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_ADD_TO_GROUP);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhNewFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_FILTER);
        exchange.getIn().setHeader(DATA,
                "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1");
        exchange.getIn().setHeader(TOPICS, "0x12341234bf4b564f");

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertNotNull(body);
    }

    //not supported operation by Ganache
    @Disabled
    @Test
    public void shhUninstallFilterTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_UNINSTALL_FILTER);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertNotNull(body);
    }

    @Test
    public void shhGetFilterChangesTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_GET_FILTER_CHANGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        List<ShhMessages.SshMessage> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Test
    public void shhGetMessagesTest() {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_GET_MESSAGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        List<ShhMessages.SshMessage> body = exchange.getIn().getBody(List.class);
        assertNotNull(body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("web3j://" + getUrl() + OPERATION.toLowerCase() + "=" + TRANSACTION);
            }
        };
    }
}
