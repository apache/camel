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
import org.junit.Ignore;
import org.junit.Test;
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

@Ignore("Requires a locally running Ganache instance")
public class Web3jProducerGanacheTest extends Web3jIntegrationTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Test
    public void ethClientVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_CLIENT_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethNetWeb3Sha3Test() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, WEB3_SHA3);
        exchange.getIn().setBody("0x68656c6c6f20776f726c64");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad"));
    }

    @Test
    public void ethNetVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethNetListeningTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_LISTENING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    //a bug in Ganache returns wrong formatted data
    @Ignore
    @Test
    public void ethNetPeerCountTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, NET_PEER_COUNT);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethProtocolVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_PROTOCOL_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethSyncingTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SYNCING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void ethCoinbaseTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COINBASE);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethMiningTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_MINING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void ethHashrateTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_HASHRATE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGasPriceTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GAS_PRICE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethAccountsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_ACCOUNTS);
        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void ethBlockNumberTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_BLOCK_NUMBER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetBalanceTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BALANCE);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(AT_BLOCK, "0");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
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
    public void ethGetStorageAtTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_STORAGE_AT);
        exchange.getIn().setHeader(ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(AT_BLOCK, "6");
        exchange.getIn().setHeader(POSITION, BigInteger.ZERO);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionCountTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_COUNT);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    //a bug in Ganache returns wrong formatted data
    @Ignore
    @Test
    public void ethGetBlockTransactionCountByHashTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a"); // Ganache block 0
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    //a bug in Ganache returns wrong formatted data
    @Ignore
    @Test
    public void ethGetBlockTransactionCountByNumberTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethGetUncleCountByBlockHashTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a"); // Ganache block 0
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethGetUncleCountByBlockNumberTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetCodeTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_CODE);
        exchange.getIn().setHeader(ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethSignTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SIGN);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(SHA3_HASH_OF_DATA_TO_SIGN, "hello");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethSendTransactionTest() throws Exception {
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
        assertTrue(body != null);
    }

    //wrong reminder
    @Ignore
    @Test
    public void ethSendRawTransactionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SEND_RAW_TRANSACTION);
        exchange.getIn().setHeader(SIGNED_TRANSACTION_DATA, "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethCallTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_CALL);
        exchange.getIn().setHeader(FROM_ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TO_ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(NONCE, BigInteger.valueOf(9L));
        exchange.getIn().setHeader(GAS_PRICE, BigInteger.valueOf(10000000000000L));
        exchange.getIn().setHeader(GAS_LIMIT, BigInteger.valueOf(30400L));
        exchange.getIn().setHeader(VALUE, BigInteger.valueOf(50000000000000L));

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethEstimateGasTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_ESTIMATE_GAS);
        exchange.getIn().setHeader(FROM_ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TO_ADDRESS, "0x3B558E3a9ae7944FEe7a3A1010DD10f05a01034B");
        exchange.getIn().setHeader(NONCE, BigInteger.valueOf(9L));
        exchange.getIn().setHeader(GAS_PRICE, BigInteger.valueOf(10000000000000L));
        exchange.getIn().setHeader(GAS_LIMIT, BigInteger.valueOf(30400L));
        exchange.getIn().setHeader(VALUE, BigInteger.valueOf(50000000000000L));

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetBlockByHashTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_BY_HASH);
        exchange.getIn().setHeader(FULL_TRANSACTION_OBJECTS, true);
        exchange.getIn().setHeader(BLOCK_HASH, "0x1fab3a1cc7f016029e41e72363362caf9bd09388ba94070d6ada37b8757ab19a");

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetBlockByNumberTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_BLOCK_BY_NUMBER);
        exchange.getIn().setHeader(FULL_TRANSACTION_OBJECTS, true);
        exchange.getIn().setHeader(AT_BLOCK, "latest");

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByHashTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_HASH);
        exchange.getIn().setHeader(TRANSACTION_HASH, "0xb082f44cb2faa0f33056d5a341d1a7be73ecfcc6eb3bcb643ab03016ce4b6772");

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByBlockHashAndIndexTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX);
        exchange.getIn().setHeader(BLOCK_HASH, "0x226aa81c5a7c86caff96af0bdb58739491d4730b629932ca80f3530558282e1d");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByBlockNumberAndIndexTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethGetUncleByBlockHashAndIndexTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX);
        exchange.getIn().setHeader(BLOCK_HASH, "0x226aa81c5a7c86caff96af0bdb58739491d4730b629932ca80f3530558282e1d");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethGetUncleByBlockNumberAndIndexTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX);
        exchange.getIn().setHeader(AT_BLOCK, "latest");
        exchange.getIn().setHeader(INDEX, BigInteger.ZERO);

        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetCompilers() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_COMPILERS);

        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethCompileSolidityTest() throws Exception {
        String soliditySample =
                "pragma solidity ^0.4.23;"
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
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethCompileLLLTest() throws Exception {
        String sampleCode = "(returnlll (suicide (caller)))";
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COMPILE_LLL);
        exchange.getIn().setHeader(SOURCE_CODE, sampleCode);
        template.send(exchange);
        Map<String, EthCompileSolidity.Code> body = exchange.getIn().getBody(Map.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void ethCompileSerpentTest() throws Exception {
        String serpentSample = "(returnlll (suicide (caller)))";
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_COMPILE_SERPENT);
        exchange.getIn().setHeader(SOURCE_CODE, serpentSample);
        template.send(exchange);
        Map<String, EthCompileSolidity.Code> body = exchange.getIn().getBody(Map.class);
        assertTrue(body != null);
    }

    @Test
    public void ethNewFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_FILTER);
        exchange.getIn().setHeader(FROM_BLOCK, "earliest");
        exchange.getIn().setHeader(TO_BLOCK, "latest");
        exchange.getIn().setHeader(ADDRESSES, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
        exchange.getIn().setHeader(TOPICS, "0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethNewBlockFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_BLOCK_FILTER);

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethNewPendingTransactionFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_NEW_PENDING_TRANSACTION_FILTER);

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    @Test
    public void ethUninstallFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_UNINSTALL_FILTER);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(8));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetFilterChangesTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_FILTER_CHANGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(7));

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetFilterLogsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_FILTER_LOGS);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(6));

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetLogsTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_LOGS);
        exchange.getIn().setHeader(FROM_BLOCK, "earliest");
        exchange.getIn().setHeader(TO_BLOCK, "latest");
//        exchange.getIn().setHeader(ADDRESSES, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");
//        exchange.getIn().setHeader(TOPICS, "0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        template.send(exchange);
        List<EthLog.LogResult> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetWorkTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_GET_WORK);

        template.send(exchange);
        List<String> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void ethSubmitWorkTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SUBMIT_WORK);
        exchange.getIn().setHeader(NONCE, "0x0000000000000001");
        exchange.getIn().setHeader(HEADER_POW_HASH, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        exchange.getIn().setHeader(MIX_DIGEST, "0xD1FE5700000000000000000000000000D1FE5700000000000000000000000000");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void ethSubmitHashrateTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, ETH_SUBMIT_HASHRATE);
        exchange.getIn().setHeader(ETH_HASHRATE, "0x0000000000000000000000000000000000000000000000000000000000500000");
        exchange.getIn().setHeader(CLIENT_ID, "0x59daa26581d0acd1fce254fb7e85952f4c09d0915afd33d3886cd914bc7d283c");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void shhVersionTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_VERSION);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhPostTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_POST);
        exchange.getIn().setHeader(FROM_ADDRESS, "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1");
        exchange.getIn().setHeader(TO_ADDRESS, "0x3e245533f97284d442460f2998cd41858798ddf04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a0d4d661997d3940272b717b1");
        exchange.getIn().setHeader(TOPICS, "0x776869737065722d636861742d636c69656e74");
        exchange.getIn().setHeader(DATA, "0x7b2274797065223a226d6");
        exchange.getIn().setHeader(PRIORITY, BigInteger.valueOf(64));
        exchange.getIn().setHeader(TTL, BigInteger.valueOf(64));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhNewIdentityTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_IDENTITY);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhHasIdentityTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_HAS_IDENTITY);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhNewGroupTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_GROUP);

        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhAddToGroupTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_ADD_TO_GROUP);
        exchange.getIn().setHeader(ADDRESS, "0xc8CDceCE5d006dAB638029EBCf6Dd666efF5A952");

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhNewFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_NEW_FILTER);
        exchange.getIn().setHeader(DATA, "0x04f96a5e25610293e42a73908e93ccc8c4d4dc0edcfa9fa872f50cb214e08ebf61a03e245533f97284d442460f2998cd41858798ddfd4d661997d3940272b717b1");
        exchange.getIn().setHeader(TOPICS, "0x12341234bf4b564f");

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body != null);
    }

    //not supported operation by Ganache
    @Ignore
    @Test
    public void shhUninstallFilterTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_UNINSTALL_FILTER);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body != null);
    }

    @Test
    public void shhGetFilterChangesTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_GET_FILTER_CHANGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        List<ShhMessages.SshMessage> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
    }

    @Test
    public void shhGetMessagesTest() throws Exception {
        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, SHH_GET_MESSAGES);
        exchange.getIn().setHeader(FILTER_ID, BigInteger.valueOf(123));

        template.send(exchange);
        List<ShhMessages.SshMessage> body = exchange.getIn().getBody(List.class);
        assertTrue(body != null);
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
