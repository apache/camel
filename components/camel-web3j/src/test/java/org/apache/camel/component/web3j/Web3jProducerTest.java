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
package org.apache.camel.component.web3j;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.DbGetHex;
import org.web3j.protocol.core.methods.response.DbGetString;
import org.web3j.protocol.core.methods.response.DbPutHex;
import org.web3j.protocol.core.methods.response.DbPutString;
import org.web3j.protocol.core.methods.response.EthAccounts;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthCoinbase;
import org.web3j.protocol.core.methods.response.EthCompileLLL;
import org.web3j.protocol.core.methods.response.EthCompileSerpent;
import org.web3j.protocol.core.methods.response.EthCompileSolidity;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthFilter;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetBlockTransactionCountByHash;
import org.web3j.protocol.core.methods.response.EthGetBlockTransactionCountByNumber;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetCompilers;
import org.web3j.protocol.core.methods.response.EthGetStorageAt;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthGetUncleCountByBlockHash;
import org.web3j.protocol.core.methods.response.EthGetUncleCountByBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetWork;
import org.web3j.protocol.core.methods.response.EthHashrate;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthMining;
import org.web3j.protocol.core.methods.response.EthProtocolVersion;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthSign;
import org.web3j.protocol.core.methods.response.EthSubmitHashrate;
import org.web3j.protocol.core.methods.response.EthSubmitWork;
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.EthUninstallFilter;
import org.web3j.protocol.core.methods.response.NetListening;
import org.web3j.protocol.core.methods.response.NetPeerCount;
import org.web3j.protocol.core.methods.response.NetVersion;
import org.web3j.protocol.core.methods.response.ShhAddToGroup;
import org.web3j.protocol.core.methods.response.ShhHasIdentity;
import org.web3j.protocol.core.methods.response.ShhMessages;
import org.web3j.protocol.core.methods.response.ShhNewFilter;
import org.web3j.protocol.core.methods.response.ShhNewGroup;
import org.web3j.protocol.core.methods.response.ShhNewIdentity;
import org.web3j.protocol.core.methods.response.ShhPost;
import org.web3j.protocol.core.methods.response.ShhUninstallFilter;
import org.web3j.protocol.core.methods.response.ShhVersion;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.core.methods.response.Web3Sha3;

import static org.apache.camel.component.web3j.Web3jConstants.OPERATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

public class Web3jProducerTest extends Web3jMockTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Mock
    private Request request;

    @Override
    public boolean isUseAdviceWith() {
        return false;
    }

    @Test
    public void clientVersionTest() throws Exception {
        Web3ClientVersion response = Mockito.mock(Web3ClientVersion.class);
        Mockito.when(mockWeb3j.web3ClientVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getWeb3ClientVersion()).thenReturn("Geth-123");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.WEB3_CLIENT_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.startsWith("Geth"));
    }

    @Test
    public void netVersionTest() throws Exception {
        NetVersion response = Mockito.mock(NetVersion.class);
        Mockito.when(mockWeb3j.netVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getNetVersion()).thenReturn("Net-123");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.NET_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.startsWith("Net"));
    }

    @Test
    public void web3Sha3Test() throws Exception {
        Web3Sha3 response = Mockito.mock(Web3Sha3.class);
        Mockito.when(mockWeb3j.web3Sha3(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getResult()).thenReturn("0x471");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.WEB3_SHA3);
        exchange.getIn().setBody("0x68");
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("0x471"));
    }

    @Test
    public void netListeningTest() throws Exception {
        NetListening response = Mockito.mock(NetListening.class);
        Mockito.when(mockWeb3j.netListening()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.isListening()).thenReturn(true);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.NET_LISTENING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void netPeerCountTest() throws Exception {
        BigInteger peerCount = BigInteger.ONE;
        NetPeerCount response = Mockito.mock(NetPeerCount.class);
        Mockito.when(mockWeb3j.netPeerCount()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getQuantity()).thenReturn(peerCount);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.NET_PEER_COUNT);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(peerCount));
    }

    @Test
    public void ethProtocolVersionTest() throws Exception {
        EthProtocolVersion response = Mockito.mock(EthProtocolVersion.class);
        Mockito.when(mockWeb3j.ethProtocolVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getProtocolVersion()).thenReturn("123");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_PROTOCOL_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("123"));
    }

    @Test
    public void ethCoinbaseTest() throws Exception {
        EthCoinbase response = Mockito.mock(EthCoinbase.class);
        Mockito.when(mockWeb3j.ethCoinbase()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getAddress()).thenReturn("123");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_COINBASE);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("123"));
    }

    @Test
    public void ethSyncingTest() throws Exception {
        EthSyncing response = Mockito.mock(EthSyncing.class);
        Mockito.when(mockWeb3j.ethSyncing()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.isSyncing()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SYNCING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void ethMiningTest() throws Exception {
        EthMining response = Mockito.mock(EthMining.class);
        Mockito.when(mockWeb3j.ethMining()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.isMining()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_MINING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void ethHashrateTest() throws Exception {
        EthHashrate response = Mockito.mock(EthHashrate.class);
        Mockito.when(mockWeb3j.ethHashrate()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getHashrate()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_HASHRATE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGasPriceTest() throws Exception {
        EthGasPrice response = Mockito.mock(EthGasPrice.class);
        Mockito.when(mockWeb3j.ethGasPrice()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getGasPrice()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GAS_PRICE);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethAccountsTest() throws Exception {
        EthAccounts response = Mockito.mock(EthAccounts.class);
        Mockito.when(mockWeb3j.ethAccounts()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getAccounts()).thenReturn(Collections.emptyList());

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_ACCOUNTS);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethBlockNumberTest() throws Exception {
        EthBlockNumber response = Mockito.mock(EthBlockNumber.class);
        Mockito.when(mockWeb3j.ethBlockNumber()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getBlockNumber()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_BLOCK_NUMBER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetBalanceTest() throws Exception {
        EthGetBalance response = Mockito.mock(EthGetBalance.class);
        Mockito.when(mockWeb3j.ethGetBalance(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getBalance()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_BALANCE);
        exchange.getIn().setHeader(Web3jConstants.AT_BLOCK, DefaultBlockParameterName.EARLIEST);

        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetStorageAtTest() throws Exception {
        EthGetStorageAt response = Mockito.mock(EthGetStorageAt.class);
        Mockito.when(mockWeb3j.ethGetStorageAt(any(), any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getData()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_STORAGE_AT);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethGetTransactionCountTest() throws Exception {
        EthGetTransactionCount response = Mockito.mock(EthGetTransactionCount.class);
        Mockito.when(mockWeb3j.ethGetTransactionCount(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionCount()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_TRANSACTION_COUNT);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetBlockTransactionCountByHashTest() throws Exception {
        EthGetBlockTransactionCountByHash response = Mockito.mock(EthGetBlockTransactionCountByHash.class);
        Mockito.when(mockWeb3j.ethGetBlockTransactionCountByHash(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionCount()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetBlockTransactionCountByNumberTest() throws Exception {
        EthGetBlockTransactionCountByNumber response = Mockito.mock(EthGetBlockTransactionCountByNumber.class);
        Mockito.when(mockWeb3j.ethGetBlockTransactionCountByNumber(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionCount()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetUncleCountByBlockHashTest() throws Exception {
        EthGetUncleCountByBlockHash response = Mockito.mock(EthGetUncleCountByBlockHash.class);
        Mockito.when(mockWeb3j.ethGetUncleCountByBlockHash(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getUncleCount()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetUncleCountByBlockNumberTest() throws Exception {
        EthGetUncleCountByBlockNumber response = Mockito.mock(EthGetUncleCountByBlockNumber.class);
        Mockito.when(mockWeb3j.ethGetUncleCountByBlockNumber(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getUncleCount()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetCodeTest() throws Exception {
        EthGetCode response = Mockito.mock(EthGetCode.class);
        Mockito.when(mockWeb3j.ethGetCode(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getCode()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_CODE);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethSignTest() throws Exception {
        EthSign response = Mockito.mock(EthSign.class);
        Mockito.when(mockWeb3j.ethSign(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getSignature()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SIGN);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethSendTransactionTest() throws Exception {
        EthSendTransaction response = Mockito.mock(EthSendTransaction.class);
        Mockito.when(mockWeb3j.ethSendTransaction(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionHash()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SEND_TRANSACTION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethSendRawTransactionTest() throws Exception {
        EthSendTransaction response = Mockito.mock(EthSendTransaction.class);
        Mockito.when(mockWeb3j.ethSendRawTransaction(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionHash()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SEND_RAW_TRANSACTION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethCallTest() throws Exception {
        EthCall response = Mockito.mock(EthCall.class);
        Mockito.when(mockWeb3j.ethCall(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getValue()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_CALL);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethEstimateGasTest() throws Exception {
        EthEstimateGas response = Mockito.mock(EthEstimateGas.class);
        Mockito.when(mockWeb3j.ethEstimateGas(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getAmountUsed()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_ESTIMATE_GAS);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethGetBlockByHashTest() throws Exception {
        EthBlock response = Mockito.mock(EthBlock.class);
        Mockito.when(mockWeb3j.ethGetBlockByHash(any(), anyBoolean())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getBlock()).thenReturn(Mockito.mock(EthBlock.Block.class));

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_BLOCK_BY_HASH);
        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetBlockByNumberTest() throws Exception {
        EthBlock response = Mockito.mock(EthBlock.class);
        Mockito.when(mockWeb3j.ethGetBlockByNumber(any(), anyBoolean())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getBlock()).thenReturn(Mockito.mock(EthBlock.Block.class));

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_BLOCK_BY_NUMBER);
        exchange.getIn().setHeader(Web3jConstants.AT_BLOCK, DefaultBlockParameterName.EARLIEST);
        exchange.getIn().setHeader(Web3jConstants.FULL_TRANSACTION_OBJECTS, true);

        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByHashTest() throws Exception {
        EthTransaction response = Mockito.mock(EthTransaction.class);
        Mockito.when(mockWeb3j.ethGetTransactionByHash(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Transaction transaction = Mockito.mock(Transaction.class);
        Optional<Transaction> optional = Optional.ofNullable(transaction);
        Mockito.when(response.getTransaction()).thenReturn(optional);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_TRANSACTION_BY_HASH);
        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByBlockHashAndIndexTest() throws Exception {
        EthTransaction response = Mockito.mock(EthTransaction.class);
        Mockito.when(mockWeb3j.ethGetTransactionByBlockHashAndIndex(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Transaction transaction = Mockito.mock(Transaction.class);
        Optional<Transaction> optional = Optional.ofNullable(transaction);
        Mockito.when(response.getTransaction()).thenReturn(optional);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX);
        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionByBlockNumberAndIndexTest() throws Exception {
        EthTransaction response = Mockito.mock(EthTransaction.class);
        Mockito.when(mockWeb3j.ethGetTransactionByBlockNumberAndIndex(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Transaction transaction = Mockito.mock(Transaction.class);
        Optional<Transaction> optional = Optional.ofNullable(transaction);
        Mockito.when(response.getTransaction()).thenReturn(optional);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX);
        template.send(exchange);
        Transaction body = exchange.getIn().getBody(Transaction.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetTransactionReceiptTest() throws Exception {
        EthGetTransactionReceipt response = Mockito.mock(EthGetTransactionReceipt.class);
        Mockito.when(mockWeb3j.ethGetTransactionReceipt(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getTransactionReceipt()).thenReturn(Mockito.mock(Optional.class));

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_TRANSACTION_RECEIPT);
        template.send(exchange);
        Optional<Transaction> body = exchange.getIn().getBody(Optional.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetUncleByBlockHashAndIndexTest() throws Exception {
        EthBlock response = Mockito.mock(EthBlock.class);
        Mockito.when(mockWeb3j.ethGetUncleByBlockHashAndIndex(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getBlock()).thenReturn(Mockito.mock(EthBlock.Block.class));

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX);
        template.send(exchange);
        EthBlock.Block body = exchange.getIn().getBody(EthBlock.Block.class);
        assertTrue(body != null);
    }

    @Test
    public void ethGetCompilersTest() throws Exception {
        EthGetCompilers response = Mockito.mock(EthGetCompilers.class);
        Mockito.when(mockWeb3j.ethGetCompilers()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getCompilers()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_COMPILERS);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethCompileLLLTest() throws Exception {
        EthCompileLLL response = Mockito.mock(EthCompileLLL.class);
        Mockito.when(mockWeb3j.ethCompileLLL(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getCompiledSourceCode()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_COMPILE_LLL);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethCompileSolidityTest() throws Exception {
        EthCompileSolidity response = Mockito.mock(EthCompileSolidity.class);
        Mockito.when(mockWeb3j.ethCompileSolidity(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getCompiledSolidity()).thenReturn(Collections.EMPTY_MAP);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_COMPILE_SOLIDITY);
        template.send(exchange);
        Map body = exchange.getIn().getBody(Map.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethCompileSerpentTest() throws Exception {
        EthCompileSerpent response = Mockito.mock(EthCompileSerpent.class);
        Mockito.when(mockWeb3j.ethCompileSerpent(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getCompiledSourceCode()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_COMPILE_SERPENT);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void ethNewFilterTest() throws Exception {
        EthFilter response = Mockito.mock(EthFilter.class);
        Mockito.when(mockWeb3j.ethNewFilter(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getFilterId()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_NEW_FILTER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethNewBlockFilterTest() throws Exception {
        EthFilter response = Mockito.mock(EthFilter.class);
        Mockito.when(mockWeb3j.ethNewBlockFilter()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getFilterId()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_NEW_BLOCK_FILTER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethNewPendingTransactionFilterTest() throws Exception {
        EthFilter response = Mockito.mock(EthFilter.class);
        Mockito.when(mockWeb3j.ethNewPendingTransactionFilter()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getFilterId()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_NEW_PENDING_TRANSACTION_FILTER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void ethUninstallFilterTest() throws Exception {
        EthUninstallFilter response = Mockito.mock(EthUninstallFilter.class);
        Mockito.when(mockWeb3j.ethUninstallFilter(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.isUninstalled()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_UNINSTALL_FILTER);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void ethGetFilterChangesTest() throws Exception {
        EthLog response = Mockito.mock(EthLog.class);
        Mockito.when(mockWeb3j.ethGetFilterChanges(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getLogs()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_FILTER_CHANGES);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethGetFilterLogsTest() throws Exception {
        EthLog response = Mockito.mock(EthLog.class);
        Mockito.when(mockWeb3j.ethGetFilterLogs(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getLogs()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_FILTER_LOGS);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethGetLogsTest() throws Exception {
        EthLog response = Mockito.mock(EthLog.class);
        Mockito.when(mockWeb3j.ethGetLogs(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getLogs()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_LOGS);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void ethGetWorkTest() throws Exception {
        EthGetWork response = Mockito.mock(EthGetWork.class);
        Mockito.when(mockWeb3j.ethGetWork()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getResult()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_GET_WORK);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.equals(Collections.EMPTY_LIST));
    }

    @Test
    public void ethSubmitWorkTest() throws Exception {
        EthSubmitWork response = Mockito.mock(EthSubmitWork.class);
        Mockito.when(mockWeb3j.ethSubmitWork(any(), any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.solutionValid()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SUBMIT_WORK);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void ethSubmitHashrateTest() throws Exception {
        EthSubmitHashrate response = Mockito.mock(EthSubmitHashrate.class);
        Mockito.when(mockWeb3j.ethSubmitHashrate(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.submissionSuccessful()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.ETH_SUBMIT_HASHRATE);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void dbPutStringTest() throws Exception {
        DbPutString response = Mockito.mock(DbPutString.class);
        Mockito.when(mockWeb3j.dbPutString(any(), any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.valueStored()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.DB_PUT_STRING);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void dbGetStringTest() throws Exception {
        DbGetString response = Mockito.mock(DbGetString.class);
        Mockito.when(mockWeb3j.dbGetString(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getStoredValue()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.DB_GET_STRING);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void dbPutHexTest() throws Exception {
        DbPutHex response = Mockito.mock(DbPutHex.class);
        Mockito.when(mockWeb3j.dbPutHex(any(), any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.valueStored()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.DB_PUT_HEX);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void dbGetHexTest() throws Exception {
        DbGetHex response = Mockito.mock(DbGetHex.class);
        Mockito.when(mockWeb3j.dbGetHex(any(), any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getStoredValue()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.DB_GET_HEX);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void shhPostTest() throws Exception {
        ShhPost response = Mockito.mock(ShhPost.class);
        Mockito.when(mockWeb3j.shhPost(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.messageSent()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_POST);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void shhVersionTest() throws Exception {
        ShhVersion response = Mockito.mock(ShhVersion.class);
        Mockito.when(mockWeb3j.shhVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getVersion()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_VERSION);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void shhNewIdentityTest() throws Exception {
        ShhNewIdentity response = Mockito.mock(ShhNewIdentity.class);
        Mockito.when(mockWeb3j.shhNewIdentity()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getAddress()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_NEW_IDENTITY);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void shhHasIdentityTest() throws Exception {
        ShhHasIdentity response = Mockito.mock(ShhHasIdentity.class);
        Mockito.when(mockWeb3j.shhHasIdentity(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.hasPrivateKeyForIdentity()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_HAS_IDENTITY);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void shhNewGroupTest() throws Exception {
        ShhNewGroup response = Mockito.mock(ShhNewGroup.class);
        Mockito.when(mockWeb3j.shhNewGroup()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getAddress()).thenReturn("test");

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_NEW_GROUP);
        template.send(exchange);
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.equals("test"));
    }

    @Test
    public void shhAddToGroupTest() throws Exception {
        ShhAddToGroup response = Mockito.mock(ShhAddToGroup.class);
        Mockito.when(mockWeb3j.shhAddToGroup(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.addedToGroup()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_ADD_TO_GROUP);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void shhNewFilterTest() throws Exception {
        ShhNewFilter response = Mockito.mock(ShhNewFilter.class);
        Mockito.when(mockWeb3j.shhNewFilter(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getFilterId()).thenReturn(BigInteger.ONE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_NEW_FILTER);
        template.send(exchange);
        BigInteger body = exchange.getIn().getBody(BigInteger.class);
        assertTrue(body.equals(BigInteger.ONE));
    }

    @Test
    public void shhUninstallFilterTest() throws Exception {
        ShhUninstallFilter response = Mockito.mock(ShhUninstallFilter.class);
        Mockito.when(mockWeb3j.shhUninstallFilter(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.isUninstalled()).thenReturn(Boolean.TRUE);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_UNINSTALL_FILTER);
        template.send(exchange);
        Boolean body = exchange.getIn().getBody(Boolean.class);
        assertTrue(body);
    }

    @Test
    public void shhGetFilterChangesTest() throws Exception {
        ShhMessages response = Mockito.mock(ShhMessages.class);
        Mockito.when(mockWeb3j.shhGetFilterChanges(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getMessages()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_GET_FILTER_CHANGES);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void shhGetMessagesTest() throws Exception {
        ShhMessages response = Mockito.mock(ShhMessages.class);
        Mockito.when(mockWeb3j.shhGetMessages(any())).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.getMessages()).thenReturn(Collections.EMPTY_LIST);

        Exchange exchange = createExchangeWithBodyAndHeader(null, OPERATION, Web3jConstants.SHH_GET_MESSAGES);
        template.send(exchange);
        List body = exchange.getIn().getBody(List.class);
        assertTrue(body.isEmpty());
    }

    @Test
    public void setRequestIdTest() throws Exception {
        Web3ClientVersion response = Mockito.mock(Web3ClientVersion.class);
        Mockito.when(mockWeb3j.web3ClientVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);

        Exchange exchange = createExchangeWithBodyAndHeader(null, Web3jConstants.ID, Long.valueOf(1));
        template.send(exchange);
        Mockito.verify(request).setId(1L);
    }


    @Test
    public void checkForErrorTest() throws Exception {
        Web3ClientVersion response = Mockito.mock(Web3ClientVersion.class);
        Mockito.when(mockWeb3j.web3ClientVersion()).thenReturn(request);
        Mockito.when(request.send()).thenReturn(response);
        Mockito.when(response.hasError()).thenReturn(true);

        Response.Error error = Mockito.mock(Response.Error.class);
        Mockito.when(response.getError()).thenReturn(error);
        Mockito.when(error.getCode()).thenReturn(1);
        Mockito.when(error.getMessage()).thenReturn("error message");
        Mockito.when(error.getData()).thenReturn("error data");

        Exchange exchange = createExchangeWithBodyAndHeader(null, Web3jConstants.ID, Long.valueOf(2));
        template.send(exchange);
        assertTrue(exchange.getIn().getHeader(Web3jConstants.ERROR_CODE, Integer.class).equals(Integer.valueOf(1)));
        assertTrue(exchange.getIn().getHeader(Web3jConstants.ERROR_MESSAGE, String.class).equals("error message"));
        assertTrue(exchange.getIn().getHeader(Web3jConstants.ERROR_DATA, String.class).equals("error data"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(getUrl() + OPERATION.toLowerCase() + "=" + Web3jConstants.WEB3_CLIENT_VERSION);
            }
        };
    }
}
