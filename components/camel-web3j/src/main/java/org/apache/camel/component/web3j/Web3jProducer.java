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

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
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
import org.web3j.protocol.core.methods.response.VoidResponse;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.core.methods.response.Web3Sha3;
import org.web3j.quorum.Quorum;
import org.web3j.quorum.methods.request.PrivateTransaction;
import org.web3j.quorum.methods.response.BlockMaker;
import org.web3j.quorum.methods.response.CanonicalHash;
import org.web3j.quorum.methods.response.MakeBlock;
import org.web3j.quorum.methods.response.PrivatePayload;
import org.web3j.quorum.methods.response.QuorumNodeInfo;
import org.web3j.quorum.methods.response.Vote;
import org.web3j.quorum.methods.response.Voter;

import static org.apache.camel.component.web3j.Web3jHelper.toDefaultBlockParameter;

public class Web3jProducer extends HeaderSelectorProducer {
    private static final Logger LOG = LoggerFactory.getLogger(Web3jProducer.class);
    private Web3j web3j;
    private Quorum quorum;
    private Web3jConfiguration configuration;

    public Web3jProducer(Web3jEndpoint endpoint, final Web3jConfiguration configuration) {
        super(endpoint, Web3jConstants.OPERATION, () -> configuration.getOperationOrDefault(), false);
        web3j = endpoint.getWeb3j();
        this.configuration = configuration;
        if (web3j instanceof Quorum) {
            quorum = (Quorum) web3j;
        }
    }

    @Override
    public Web3jEndpoint getEndpoint() {
        return (Web3jEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @InvokeOnHeader(Web3jConstants.WEB3_CLIENT_VERSION)
    void web3ClientVersion(Message message) throws IOException {
        Request<?, Web3ClientVersion> web3ClientVersionRequest = web3j.web3ClientVersion();
        setRequestId(message, web3ClientVersionRequest);
        Web3ClientVersion response = web3ClientVersionRequest.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getWeb3ClientVersion());
        }
    }

    @InvokeOnHeader(Web3jConstants.WEB3_SHA3)
    void web3Sha3(Message message) throws IOException {
        Request<?, Web3Sha3> web3Sha3Request = web3j.web3Sha3(message.getBody(String.class));
        setRequestId(message, web3Sha3Request);
        Web3Sha3 response = web3Sha3Request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getResult());
        }
    }

    @InvokeOnHeader(Web3jConstants.NET_VERSION)
    void netVersion(Message message) throws IOException {
        Request<?, NetVersion> netVersionRequest = web3j.netVersion();
        setRequestId(message, netVersionRequest);
        NetVersion response = netVersionRequest.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getNetVersion());
        }
    }

    @InvokeOnHeader(Web3jConstants.NET_LISTENING)
    void netListening(Message message) throws IOException {
        Request<?, NetListening> netListeningRequest = web3j.netListening();
        setRequestId(message, netListeningRequest);
        NetListening response = netListeningRequest.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isListening());
        }
    }

    @InvokeOnHeader(Web3jConstants.NET_PEER_COUNT)
    void netPeerCount(Message message) throws IOException {
        Request<?, NetPeerCount> request = web3j.netPeerCount();
        setRequestId(message, request);
        NetPeerCount response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getQuantity());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_PROTOCOL_VERSION)
    void ethProtocolVersion(Message message) throws IOException {
        Request<?, EthProtocolVersion> request = web3j.ethProtocolVersion();
        setRequestId(message, request);
        EthProtocolVersion response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getProtocolVersion());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SYNCING)
    void ethSyncing(Message message) throws IOException {
        Request<?, EthSyncing> request = web3j.ethSyncing();
        setRequestId(message, request);
        EthSyncing response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isSyncing());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_COINBASE)
    void ethCoinbase(Message message) throws IOException {
        Request<?, EthCoinbase> request = web3j.ethCoinbase();
        setRequestId(message, request);
        EthCoinbase response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getAddress());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_MINING)
    void ethMining(Message message) throws IOException {
        Request<?, EthMining> request = web3j.ethMining();
        setRequestId(message, request);
        EthMining response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isMining());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_HASHRATE)
    void ethHashrate(Message message) throws IOException {
        Request<?, EthHashrate> request = web3j.ethHashrate();
        setRequestId(message, request);
        EthHashrate response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getHashrate());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GAS_PRICE)
    void ethGasPrice(Message message) throws IOException {
        Request<?, EthGasPrice> request = web3j.ethGasPrice();
        setRequestId(message, request);
        EthGasPrice response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getGasPrice());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_ACCOUNTS)
    void ethAccounts(Message message) throws IOException {
        Request<?, EthAccounts> request = web3j.ethAccounts();
        setRequestId(message, request);
        EthAccounts response = request.send();
        message.setBody(response.getAccounts());
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getAccounts());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_BLOCK_NUMBER)
    void ethBlockNumber(Message message) throws IOException {
        Request<?, EthBlockNumber> request = web3j.ethBlockNumber();
        setRequestId(message, request);
        EthBlockNumber response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlockNumber());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_BALANCE)
    void ethGetBalance(Message message) throws IOException {
        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));

        Request<?, EthGetBalance> request = web3j.ethGetBalance(address, atBlock);
        setRequestId(message, request);
        EthGetBalance response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBalance());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_STORAGE_AT)
    void ethGetStorageAt(Message message) throws IOException {
        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        BigInteger position = message.getHeader(Web3jConstants.POSITION, configuration::getPosition, BigInteger.class);
        Request<?, EthGetStorageAt> request = web3j.ethGetStorageAt(address, position, atBlock);
        setRequestId(message, request);
        EthGetStorageAt response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getData());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_TRANSACTION_COUNT)
    void ethGetTransactionCount(Message message) throws IOException {
        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        Request<?, EthGetTransactionCount> request = web3j.ethGetTransactionCount(address, atBlock);
        setRequestId(message, request);
        EthGetTransactionCount response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionCount());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH)
    void ethGetBlockTransactionCountByHash(Message message) throws IOException {
        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        Request<?, EthGetBlockTransactionCountByHash> request = web3j.ethGetBlockTransactionCountByHash(blockHash);
        setRequestId(message, request);
        EthGetBlockTransactionCountByHash response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionCount());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER)
    void ethGetBlockTransactionCountByNumber(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        Request<?, EthGetBlockTransactionCountByNumber> request = web3j.ethGetBlockTransactionCountByNumber(atBlock);
        setRequestId(message, request);
        EthGetBlockTransactionCountByNumber response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionCount());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH)
    void ethGetUncleCountByBlockHash(Message message) throws IOException {
        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        Request<?, EthGetUncleCountByBlockHash> request = web3j.ethGetUncleCountByBlockHash(blockHash);
        setRequestId(message, request);
        EthGetUncleCountByBlockHash response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getUncleCount());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER)
    void ethGetUncleCountByBlockNumber(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        Request<?, EthGetUncleCountByBlockNumber> request = web3j.ethGetUncleCountByBlockNumber(atBlock);
        setRequestId(message, request);
        EthGetUncleCountByBlockNumber response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getUncleCount());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_CODE)
    void ethGetCode(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        Request<?, EthGetCode> request = web3j.ethGetCode(address, atBlock);
        setRequestId(message, request);
        EthGetCode response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCode());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SIGN)
    void ethSign(Message message) throws IOException {
        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        String sha3HashOfDataToSign = message.getHeader(Web3jConstants.SHA3_HASH_OF_DATA_TO_SIGN, configuration::getSha3HashOfDataToSign, String.class);
        Request<?, EthSign> request = web3j.ethSign(address, sha3HashOfDataToSign);
        setRequestId(message, request);
        EthSign response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getSignature());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SEND_TRANSACTION)
    void ethSendTransaction(Message message) throws IOException {
        String fromAddress = message.getHeader(Web3jConstants.FROM_ADDRESS, configuration::getFromAddress, String.class);
        String toAddress = message.getHeader(Web3jConstants.TO_ADDRESS, configuration::getToAddress, String.class);
        BigInteger nonce = message.getHeader(Web3jConstants.NONCE, configuration::getNonce, BigInteger.class);
        BigInteger gasPrice = message.getHeader(Web3jConstants.GAS_PRICE, configuration::getGasPrice, BigInteger.class);
        BigInteger gasLimit = message.getHeader(Web3jConstants.GAS_LIMIT, configuration::getGasLimit, BigInteger.class);
        BigInteger value = message.getHeader(Web3jConstants.VALUE, configuration::getValue, BigInteger.class);
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);

        org.web3j.protocol.core.methods.request.Transaction transaction = new org.web3j.protocol.core.methods.request.Transaction(
                fromAddress, nonce, gasPrice, gasLimit, toAddress, value, data);

        Request<?, EthSendTransaction> request = web3j.ethSendTransaction(transaction);
        setRequestId(message, request);
        EthSendTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionHash());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SEND_RAW_TRANSACTION)
    void ethSendRawTransaction(Message message) throws IOException {
        String signedTransactionData = message.getHeader(Web3jConstants.SIGNED_TRANSACTION_DATA, configuration::getSignedTransactionData, String.class);
        Request<?, EthSendTransaction> request = web3j.ethSendRawTransaction(signedTransactionData);
        setRequestId(message, request);
        EthSendTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionHash());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_CALL)
    void ethCall(Message message) throws IOException {
        String fromAddress = message.getHeader(Web3jConstants.FROM_ADDRESS, configuration::getFromAddress, String.class);
        String toAddress = message.getHeader(Web3jConstants.TO_ADDRESS, configuration::getToAddress, String.class);
        BigInteger nonce = message.getHeader(Web3jConstants.NONCE, configuration::getNonce, BigInteger.class);
        BigInteger gasPrice = message.getHeader(Web3jConstants.GAS_PRICE, configuration::getGasPrice, BigInteger.class);
        BigInteger gasLimit = message.getHeader(Web3jConstants.GAS_LIMIT, configuration::getGasLimit, BigInteger.class);
        BigInteger value = message.getHeader(Web3jConstants.VALUE, configuration::getValue, BigInteger.class);
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));

        org.web3j.protocol.core.methods.request.Transaction transaction = new org.web3j.protocol.core.methods.request.Transaction(
                fromAddress, nonce, gasPrice, gasLimit, toAddress, value, data);

        Request<?, EthCall> request = web3j.ethCall(transaction, atBlock);
        setRequestId(message, request);
        EthCall response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getValue());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_ESTIMATE_GAS)
    void ethEstimateGas(Message message) throws IOException {
        String fromAddress = message.getHeader(Web3jConstants.FROM_ADDRESS, configuration::getFromAddress, String.class);
        String toAddress = message.getHeader(Web3jConstants.TO_ADDRESS, configuration::getToAddress, String.class);
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);

        org.web3j.protocol.core.methods.request.Transaction transaction = new org.web3j.protocol.core.methods.request.Transaction(
                fromAddress, null, null, null, toAddress, null, data);

        Request<?, EthEstimateGas> request = web3j.ethEstimateGas(transaction);
        setRequestId(message, request);
        EthEstimateGas response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getAmountUsed());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_BLOCK_BY_HASH)
    void ethGetBlockByHash(Message message) throws IOException {
        Boolean fullTransactionObjects = message.getHeader(Web3jConstants.FULL_TRANSACTION_OBJECTS, configuration::isFullTransactionObjects, Boolean.class);
        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        Request<?, EthBlock> request = web3j.ethGetBlockByHash(blockHash, fullTransactionObjects);
        setRequestId(message, request);
        EthBlock response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlock());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_BLOCK_BY_NUMBER)
    void ethGetBlockByNumber(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        Boolean fullTransactionObjects = message.getHeader(Web3jConstants.FULL_TRANSACTION_OBJECTS, configuration::isFullTransactionObjects, Boolean.class);
        Request<?, EthBlock> request = web3j.ethGetBlockByNumber(atBlock, fullTransactionObjects);
        setRequestId(message, request);
        EthBlock response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlock());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_TRANSACTION_BY_HASH)
    void ethGetTransactionByHash(Message message) throws IOException {
        String transactionHash = message.getHeader(Web3jConstants.TRANSACTION_HASH, configuration::getTransactionHash, String.class);
        Request<?, EthTransaction> request = web3j.ethGetTransactionByHash(transactionHash);
        setRequestId(message, request);
        EthTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransaction().isPresent() ? response.getTransaction().get() : null);
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX)
    void ethGetTransactionByBlockHashAndIndex(Message message) throws IOException {
        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        BigInteger transactionIndex = message.getHeader(Web3jConstants.INDEX, configuration::getIndex, BigInteger.class);
        Request<?, EthTransaction> request = web3j.ethGetTransactionByBlockHashAndIndex(blockHash, transactionIndex);
        setRequestId(message, request);
        EthTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransaction().isPresent() ? response.getTransaction().get() : null);
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX)
    void ethGetTransactionByBlockNumberAndIndex(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        BigInteger transactionIndex = message.getHeader(Web3jConstants.INDEX, configuration::getIndex, BigInteger.class);
        Request<?, EthTransaction> request = web3j.ethGetTransactionByBlockNumberAndIndex(atBlock, transactionIndex);
        setRequestId(message, request);
        EthTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransaction().isPresent() ? response.getTransaction().get() : null);
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX)
    void ethGetUncleByBlockHashAndIndex(Message message) throws IOException {
        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        BigInteger uncleIndex = message.getHeader(Web3jConstants.INDEX, configuration::getIndex, BigInteger.class);
        Request<?, EthBlock> request = web3j.ethGetUncleByBlockHashAndIndex(blockHash, uncleIndex);
        setRequestId(message, request);
        EthBlock response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlock());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX)
    void ethGetUncleByBlockNumberAndIndex(Message message) throws IOException {
        DefaultBlockParameter atBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.AT_BLOCK, configuration::getAtBlock, String.class));
        BigInteger uncleIndex = message.getHeader(Web3jConstants.INDEX, configuration::getIndex, BigInteger.class);
        Request<?, EthBlock> request = web3j.ethGetUncleByBlockNumberAndIndex(atBlock, uncleIndex);
        setRequestId(message, request);
        EthBlock response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlock());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_TRANSACTION_RECEIPT)
    void ethGetTransactionReceipt(Message message) throws IOException {
        String transactionHash = message.getHeader(Web3jConstants.TRANSACTION_HASH, configuration::getTransactionHash, String.class);
        Request<?, EthGetTransactionReceipt> request = web3j.ethGetTransactionReceipt(transactionHash);
        setRequestId(message, request);
        EthGetTransactionReceipt response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionReceipt());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_COMPILERS)
    void ethGetCompilers(Message message) throws IOException {
        Request<?, EthGetCompilers> request = web3j.ethGetCompilers();
        setRequestId(message, request);
        EthGetCompilers response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCompilers());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_COMPILE_LLL)
    void ethCompileLLL(Message message) throws IOException {
        String sourceCode = message.getHeader(Web3jConstants.SOURCE_CODE, configuration::getSourceCode, String.class);
        Request<?, EthCompileLLL> request = web3j.ethCompileLLL(sourceCode);
        setRequestId(message, request);
        EthCompileLLL response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCompiledSourceCode());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_COMPILE_SOLIDITY)
    void ethCompileSolidity(Message message) throws IOException {
        String sourceCode = message.getHeader(Web3jConstants.SOURCE_CODE, configuration::getSourceCode, String.class);
        Request<?, EthCompileSolidity> request = web3j.ethCompileSolidity(sourceCode);
        setRequestId(message, request);
        EthCompileSolidity response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCompiledSolidity());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_COMPILE_SERPENT)
    void ethCompileSerpent(Message message) throws IOException {
        String sourceCode = message.getHeader(Web3jConstants.SOURCE_CODE, configuration::getSourceCode, String.class);
        Request<?, EthCompileSerpent> request = web3j.ethCompileSerpent(sourceCode);
        setRequestId(message, request);
        EthCompileSerpent response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCompiledSourceCode());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_NEW_FILTER)
    void ethNewFilter(Message message) throws IOException {
        DefaultBlockParameter fromBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.FROM_BLOCK, configuration::getFromBlock, String.class));
        DefaultBlockParameter toBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.TO_BLOCK, configuration::getToBlock, String.class));
        List<String> addresses = message.getHeader(Web3jConstants.ADDRESSES, configuration::getAddresses, List.class);
        List<String> topics = message.getHeader(Web3jConstants.TOPICS, configuration::getTopics, List.class);

        org.web3j.protocol.core.methods.request.EthFilter ethFilter = Web3jEndpoint.buildEthFilter(fromBlock, toBlock, addresses, topics);

        Request<?, EthFilter> request = web3j.ethNewFilter(ethFilter);
        setRequestId(message, request);
        EthFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getFilterId());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_NEW_BLOCK_FILTER)
    void ethNewBlockFilter(Message message) throws IOException {
        Request<?, EthFilter> request = web3j.ethNewBlockFilter();
        setRequestId(message, request);
        EthFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getFilterId());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_NEW_PENDING_TRANSACTION_FILTER)
    void ethNewPendingTransactionFilter(Message message) throws IOException {
        Request<?, EthFilter> request = web3j.ethNewPendingTransactionFilter();
        setRequestId(message, request);
        EthFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getFilterId());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_UNINSTALL_FILTER)
    void ethUninstallFilter(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, EthUninstallFilter> request = web3j.ethUninstallFilter(filterId);
        setRequestId(message, request);
        EthUninstallFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isUninstalled());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_FILTER_CHANGES)
    void ethGetFilterChanges(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, EthLog> request = web3j.ethGetFilterChanges(filterId);
        setRequestId(message, request);
        EthLog response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getLogs());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_FILTER_LOGS)
    void ethGetFilterLogs(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, EthLog> request = web3j.ethGetFilterLogs(filterId);
        setRequestId(message, request);
        EthLog response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getLogs());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_LOGS)
    void ethGetLogs(Message message) throws IOException {
        DefaultBlockParameter fromBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.FROM_BLOCK, configuration::getFromBlock, String.class));
        DefaultBlockParameter toBlock = toDefaultBlockParameter(message.getHeader(Web3jConstants.TO_BLOCK, configuration::getToBlock, String.class));
        List<String> addresses = message.getHeader(Web3jConstants.ADDRESSES, configuration::getAddresses, List.class);
        List<String> topics = message.getHeader(Web3jConstants.TOPICS, configuration::getTopics, List.class);
        org.web3j.protocol.core.methods.request.EthFilter ethFilter = Web3jEndpoint.buildEthFilter(fromBlock, toBlock, addresses, topics);

        Request<?, EthLog> request = web3j.ethGetLogs(ethFilter);
        setRequestId(message, request);
        EthLog response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getLogs());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_GET_WORK)
    void ethGetWork(Message message) throws IOException {
        Request<?, EthGetWork> request = web3j.ethGetWork();
        setRequestId(message, request);
        EthGetWork response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getResult());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SUBMIT_WORK)
    void ethSubmitWork(Message message) throws IOException {
        String nonce = message.getHeader(Web3jConstants.NONCE, configuration::getNonce, String.class);
        String headerPowHash = message.getHeader(Web3jConstants.HEADER_POW_HASH, configuration::getHeaderPowHash, String.class);
        String mixDigest = message.getHeader(Web3jConstants.MIX_DIGEST, configuration::getHeaderPowHash, String.class);
        Request<?, EthSubmitWork> request = web3j.ethSubmitWork(nonce, headerPowHash, mixDigest);
        setRequestId(message, request);
        EthSubmitWork response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.solutionValid());
        }
    }

    @InvokeOnHeader(Web3jConstants.ETH_SUBMIT_HASHRATE)
    void ethSubmitHashrate(Message message) throws IOException {
        String hashrate = message.getHeader(Web3jConstants.ETH_HASHRATE, configuration::getHashrate, String.class);
        String clientId = message.getHeader(Web3jConstants.CLIENT_ID, configuration::getClientId, String.class);
        Request<?, EthSubmitHashrate> request = web3j.ethSubmitHashrate(hashrate, clientId);
        setRequestId(message, request);
        EthSubmitHashrate response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.submissionSuccessful());
        }
    }

    //deprecated operations
    @InvokeOnHeader(Web3jConstants.DB_PUT_STRING)
    void dbPutString(Message message) throws IOException {
        String databaseName = message.getHeader(Web3jConstants.DATABASE_NAME, configuration::getDatabaseName, String.class);
        String keyName = message.getHeader(Web3jConstants.KEY_NAME, configuration::getKeyName, String.class);
        Request<?, DbPutString> request = web3j.dbPutString(databaseName, keyName, message.getBody(String.class));
        setRequestId(message, request);
        DbPutString response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.valueStored());
        }
    }

    @InvokeOnHeader(Web3jConstants.DB_GET_STRING)
    void dbGetString(Message message) throws IOException {
        String databaseName = message.getHeader(Web3jConstants.DATABASE_NAME, configuration::getDatabaseName, String.class);
        String keyName = message.getHeader(Web3jConstants.KEY_NAME, configuration::getKeyName, String.class);
        Request<?, DbGetString> request = web3j.dbGetString(databaseName, keyName);
        setRequestId(message, request);
        DbGetString response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getStoredValue());
        }
    }

    @InvokeOnHeader(Web3jConstants.DB_PUT_HEX)
    void dbPutHex(Message message) throws IOException {
        String databaseName = message.getHeader(Web3jConstants.DATABASE_NAME, configuration::getDatabaseName, String.class);
        String keyName = message.getHeader(Web3jConstants.KEY_NAME, configuration::getKeyName, String.class);
        Request<?, DbPutHex> request = web3j.dbPutHex(databaseName, keyName, message.getBody(String.class));
        setRequestId(message, request);
        DbPutHex response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.valueStored());
        }
    }

    @InvokeOnHeader(Web3jConstants.DB_GET_HEX)
    void dbGetHex(Message message) throws IOException {
        String databaseName = message.getHeader(Web3jConstants.DATABASE_NAME, configuration::getDatabaseName, String.class);
        String keyName = message.getHeader(Web3jConstants.KEY_NAME, configuration::getKeyName, String.class);
        Request<?, DbGetHex> request = web3j.dbGetHex(databaseName, keyName);
        setRequestId(message, request);
        DbGetHex response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getStoredValue());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_VERSION)
    void shhVersion(Message message) throws IOException {
        Request<?, ShhVersion> request = web3j.shhVersion();
        setRequestId(message, request);
        ShhVersion response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getVersion());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_POST)
    void shhPost(Message message) throws IOException {
        String fromAddress = message.getHeader(Web3jConstants.FROM_ADDRESS, configuration::getFromAddress, String.class);
        String toAddress = message.getHeader(Web3jConstants.TO_ADDRESS, configuration::getToAddress, String.class);
        List<String> topics = message.getHeader(Web3jConstants.TOPICS, configuration::getTopics, List.class);
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);
        BigInteger priority = message.getHeader(Web3jConstants.PRIORITY, configuration::getPriority, BigInteger.class);
        BigInteger ttl = message.getHeader(Web3jConstants.TTL, configuration::getTtl, BigInteger.class);
        org.web3j.protocol.core.methods.request.ShhPost shhPost = new org.web3j.protocol.core.methods.request.ShhPost(fromAddress, toAddress, topics, data, priority, ttl);

        Request<?, ShhPost> request = web3j.shhPost(shhPost);
        setRequestId(message, request);
        ShhPost response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.messageSent());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_NEW_IDENTITY)
    void shhNewIdentity(Message message) throws IOException {
        Request<?, ShhNewIdentity> request = web3j.shhNewIdentity();
        setRequestId(message, request);
        ShhNewIdentity response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getAddress());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_HAS_IDENTITY)
    void shhHasIdentity(Message message) throws IOException {
        String identityAddress = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        Request<?, ShhHasIdentity> request = web3j.shhHasIdentity(identityAddress);
        setRequestId(message, request);
        ShhHasIdentity response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.hasPrivateKeyForIdentity());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_NEW_GROUP)
    void shhNewGroup(Message message) throws IOException {
        Request<?, ShhNewGroup> request = web3j.shhNewGroup();
        setRequestId(message, request);
        ShhNewGroup response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getAddress());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_ADD_TO_GROUP)
    void shhAddToGroup(Message message) throws IOException {
        String identityAddress = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        Request<?, ShhAddToGroup> request = web3j.shhAddToGroup(identityAddress);
        setRequestId(message, request);
        ShhAddToGroup response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.addedToGroup());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_NEW_FILTER)
    void shhNewFilter(Message message) throws IOException {
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);
        List<String> topics = message.getHeader(Web3jConstants.TOPICS, configuration::getTopics, List.class);
        org.web3j.protocol.core.methods.request.ShhFilter shhFilter = Web3jEndpoint.buildShhFilter(data, topics);

        Request<?, ShhNewFilter> request = web3j.shhNewFilter(shhFilter);
        setRequestId(message, request);
        ShhNewFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getFilterId());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_UNINSTALL_FILTER)
    void shhUninstallFilter(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, ShhUninstallFilter> request = web3j.shhUninstallFilter(filterId);
        setRequestId(message, request);
        ShhUninstallFilter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isUninstalled());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_GET_FILTER_CHANGES)
    void shhGetFilterChanges(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, ShhMessages> request = web3j.shhGetFilterChanges(filterId);
        setRequestId(message, request);
        ShhMessages response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getMessages());
        }
    }

    @InvokeOnHeader(Web3jConstants.SHH_GET_MESSAGES)
    void shhGetMessages(Message message) throws IOException {
        BigInteger filterId = message.getHeader(Web3jConstants.FILTER_ID, configuration::getFilterId, BigInteger.class);
        Request<?, ShhMessages> request = web3j.shhGetMessages(filterId);
        setRequestId(message, request);
        ShhMessages response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getMessages());
        }
    }

    // Quorum API Operations
    @InvokeOnHeader(Web3jConstants.QUORUM_NODE_INFO)
    void quorumNodeInfo(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, QuorumNodeInfo> request = quorum.quorumNodeInfo();
        setRequestId(message, request);
        QuorumNodeInfo response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getNodeInfo());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_CANONICAL_HASH)
    void quorumCanonicalHash(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, CanonicalHash> request = quorum.quorumCanonicalHash(message.getBody(BigInteger.class));
        setRequestId(message, request);
        CanonicalHash response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getCanonicalHash());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_VOTE)
    void quorumVote(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        String blockHash = message.getHeader(Web3jConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
        Request<?, Vote> request = quorum.quorumVote(blockHash);
        setRequestId(message, request);
        Vote response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionHash());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_MAKE_BLOCK)
    void quorumMakeBlock(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, MakeBlock> request = quorum.quorumMakeBlock();
        setRequestId(message, request);
        MakeBlock response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getBlockHash());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_PAUSE_BLOCK_MAKER)
    void quorumPauseBlockMaker(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, VoidResponse> request = quorum.quorumPauseBlockMaker();
        setRequestId(message, request);
        VoidResponse response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isValid());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_RESUME_BLOCK_MAKER)
    void quorumResumeBlockMaker(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, VoidResponse> request = quorum.quorumResumeBlockMaker();
        setRequestId(message, request);
        VoidResponse response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isValid());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_IS_BLOCK_MAKER)
    void quorumIsBlockMaker(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        Request<?, BlockMaker> request = quorum.quorumIsBlockMaker(address);
        setRequestId(message, request);
        BlockMaker response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isBlockMaker());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_IS_VOTER)
    void quorumIsVoter(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        String address = message.getHeader(Web3jConstants.ADDRESS, configuration::getAddress, String.class);
        Request<?, Voter> request = quorum.quorumIsVoter(address);
        setRequestId(message, request);
        Voter response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.isVoter());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_GET_PRIVATE_PAYLOAD)
    void quorumGetPrivatePayload(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        Request<?, PrivatePayload> request = quorum.quorumGetPrivatePayload(message.getBody(String.class));
        setRequestId(message, request);
        PrivatePayload response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getPrivatePayload());
        }
    }

    @InvokeOnHeader(Web3jConstants.QUORUM_ETH_SEND_TRANSACTION)
    void quorumEthSendTransaction(Message message) throws IOException {
        if (quorum == null) {
            setQuorumEndpointError(message);
            return;
        }

        //the same as a regular transaction, but there is no gasPrice, instead there is optional privateFor
        String fromAddress = message.getHeader(Web3jConstants.FROM_ADDRESS, configuration::getFromAddress, String.class);
        String toAddress = message.getHeader(Web3jConstants.TO_ADDRESS, configuration::getToAddress, String.class);
        BigInteger nonce = message.getHeader(Web3jConstants.NONCE, configuration::getNonce, BigInteger.class);
        BigInteger gasLimit = message.getHeader(Web3jConstants.GAS_LIMIT, configuration::getGasLimit, BigInteger.class);
        BigInteger value = message.getHeader(Web3jConstants.VALUE, configuration::getValue, BigInteger.class);
        String data = message.getHeader(Web3jConstants.DATA, configuration::getData, String.class);
        List<String> privateFor = message.getHeader(Web3jConstants.PRIVATE_FOR, configuration::getPrivateFor, List.class);
        PrivateTransaction transaction = new PrivateTransaction(fromAddress, nonce, gasLimit, toAddress, value, data, privateFor);

        Request<?, EthSendTransaction> request = quorum.ethSendTransaction(transaction);
        setRequestId(message, request);
        EthSendTransaction response = request.send();
        boolean hasError = checkForError(message, response);
        if (!hasError) {
            message.setBody(response.getTransactionHash());
        }
    }

    private void setRequestId(Message message, Request request) {
        final Long id = message.getHeader(Web3jConstants.ID, Long.class);
        LOG.debug("setRequestId {}", id);
        if (id != null) {
            request.setId(id);
        }
    }

    private boolean checkForError(Message message, Response response) {
        if (response.hasError()) {
            int code = response.getError().getCode();
            String data = response.getError().getData();
            String messages = response.getError().getMessage();
            message.setHeader(Web3jConstants.ERROR_CODE, code);
            message.setHeader(Web3jConstants.ERROR_DATA, data);
            message.setHeader(Web3jConstants.ERROR_MESSAGE, messages);
            message.getExchange().setException(new CamelExchangeException("Web3j failed. Error code: " + code + " data: " + data + " messages: " + messages, message.getExchange()));
            return true;
        } else {
            return false;
        }
    }

    private void setQuorumEndpointError(Message message) {
        message.getExchange().setException(new CamelExchangeException("This is not a Quorum endpoint. Create one by specifying quorumAPI=true", message.getExchange()));
    }
}
