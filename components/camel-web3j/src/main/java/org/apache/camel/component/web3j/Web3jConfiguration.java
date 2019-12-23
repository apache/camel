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
import java.util.Arrays;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.web3j.protocol.Web3j;

@UriParams
public class Web3jConfiguration implements Cloneable {

    @UriParam(label = "producer", defaultValue = "transaction")
    private String operation = Web3jConstants.TRANSACTION.toLowerCase(); // TODO: Make this an enum

    @UriParam(label = "common", defaultValue = "latest")
    private String fromBlock;

    @UriParam(label = "common", defaultValue = "latest")
    private String toBlock;

    @UriParam(label = "producer", defaultValue = "latest")
    private String atBlock;

    @UriParam(label = "common")
    private List<String> addresses;

    @UriParam(label = "producer")
    private String address;

    @UriParam(label = "common", javaType = "java.lang.String")
    private List<String> topics;

    @UriParam(label = "producer")
    private BigInteger position;

    @UriParam(label = "producer")
    private String blockHash;

    @UriParam(label = "producer")
    private String sha3HashOfDataToSign;

    @UriParam(label = "producer")
    private String signedTransactionData;

    @UriParam(label = "producer")
    private BigInteger index;

    @UriParam(label = "producer")
    private BigInteger filterId;

    @UriParam(label = "producer")
    private String databaseName;

    @UriParam(label = "producer")
    private String keyName;

    @UriParam(label = "producer")
    private String transactionHash;

    @UriParam(label = "producer")
    private String sourceCode;

    @UriParam(label = "producer")
    private String nonce;

    @UriParam(label = "producer")
    private String headerPowHash;

    @UriParam(label = "producer")
    private String mixDigest;

    @UriParam(label = "producer")
    private String hashrate;

    @UriParam(label = "producer")
    private String clientId;

    @UriParam(label = "common")
    private String fromAddress;

    @UriParam(label = "common")
    private String toAddress;

    @UriParam(label = "producer")
    private BigInteger gasPrice;

    @UriParam
    private BigInteger gasLimit;

    @UriParam(label = "producer")
    private BigInteger value;

    @UriParam(label = "producer")
    private String data;

    @UriParam(label = "producer")
    private BigInteger priority;

    @UriParam(label = "producer")
    private BigInteger ttl;

    @UriParam(label = "common")
    private Web3j web3j;

    @UriParam(label = "common")
    private boolean fullTransactionObjects;

    @UriParam(label = "common")
    private boolean quorumAPI;

    @UriParam(label = "common")
    private List<String> privateFor;


    public List<String> getPrivateFor() {
        return privateFor;
    }
    /**
     *  A transaction privateFor nodes with public keys in a Quorum network
     */
    public void setPrivateFor(List<String> privateFor) {
        this.privateFor = privateFor;
    }


    public boolean isQuorumAPI() {
        return quorumAPI;
    }
    /**
     *  If true, this will support Quorum API.
     */
    public void setQuorumAPI(boolean quorumAPI) {
        this.quorumAPI = quorumAPI;
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    /**
     * The preconfigured Web3j object.
     */
    public void setWeb3j(Web3j web3j) {
        this.web3j = web3j;
    }

    public BigInteger getPriority() {
        return priority;
    }

    /**
     * The priority of a whisper message.
     */
    public void setPriority(BigInteger priority) {
        this.priority = priority;
    }

    public BigInteger getTtl() {
        return ttl;
    }

    /**
     * The time to live in seconds of a whisper message.
     */
    public void setTtl(BigInteger ttl) {
        this.ttl = ttl;
    }

    public BigInteger getGasPrice() {
        return gasPrice;
    }

    /**
     * Gas price used for each paid gas.
     */
    public void setGasPrice(BigInteger gasPrice) {
        this.gasPrice = gasPrice;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    /**
     * The maximum gas allowed in this block.
     */
    public void setGasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
    }

    public BigInteger getValue() {
        return value;
    }

    /**
     * The value sent within a transaction.
     */
    public void setValue(BigInteger value) {
        this.value = value;
    }

    public String getData() {
        return data;
    }

    /**
     * The compiled code of a contract OR the hash of the invoked method signature and encoded parameters.
     */
    public void setData(String data) {
        this.data = data;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    /**
     * The address the transaction is send from
     */
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    /**
     * The address the transaction is directed to.
     */
    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * A random hexadecimal(32 bytes) ID identifying the client.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getHashrate() {
        return hashrate;
    }

    /**
     * A hexadecimal string representation (32 bytes) of the hash rate.
     */
    public void setHashrate(String hashrate) {
        this.hashrate = hashrate;
    }

    public String getMixDigest() {
        return mixDigest;
    }

    /**
     * The mix digest (256 bits) used for submitting a proof-of-work solution.
     */
    public void setMixDigest(String mixDigest) {
        this.mixDigest = mixDigest;
    }

    public String getHeaderPowHash() {
        return headerPowHash;
    }

    /**
     * The header's pow-hash (256 bits) used for submitting a proof-of-work solution.
     */
    public void setHeaderPowHash(String headerPowHash) {
        this.headerPowHash = headerPowHash;
    }

    public String getNonce() {
        return nonce;
    }

    /**
     * The nonce found (64 bits) used for submitting a proof-of-work solution.
     */
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * The source code to compile.
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    /**
     * The information about a transaction requested by transaction hash.
     */
    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * The local database name.
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getKeyName() {
        return keyName;
    }

    /**
     * The key name in the database.
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public BigInteger getFilterId() {
        return filterId;
    }

    /**
     * The filter id to use.
     */
    public void setFilterId(BigInteger filterId) {
        this.filterId = filterId;
    }

    public BigInteger getIndex() {
        return index;
    }

    /**
     * The transactions/uncle index position in the block.
     */
    public void setIndex(BigInteger index) {
        this.index = index;
    }

    public String getSignedTransactionData() {
        return signedTransactionData;
    }

    /**
     * The signed transaction data for a new message call transaction or a contract creation for signed transactions.
     */
    public void setSignedTransactionData(String signedTransactionData) {
        this.signedTransactionData = signedTransactionData;
    }

    public String getBlockHash() {
        return blockHash;
    }

    /**
     * Hash of the block where this transaction was in.
     */
    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getSha3HashOfDataToSign() {
        return sha3HashOfDataToSign;
    }

    /**
     * Message to sign by calculating an Ethereum specific signature.
     */
    public void setSha3HashOfDataToSign(String sha3HashOfDataToSign) {
        this.sha3HashOfDataToSign = sha3HashOfDataToSign;
    }

    public BigInteger getPosition() {
        return position;
    }

    /**
     * The transaction index position withing a block.
     */
    public void setPosition(BigInteger position) {
        this.position = position;
    }

    public String getFromBlock() {
        return fromBlock;
    }

    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined transactions.
     */
    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined transactions.
     */
    public void setToBlock(String toBlock) {
        this.toBlock = toBlock;
    }

    public String getToBlock() {
        return toBlock;
    }

    public String getAtBlock() {
        return atBlock;
    }

    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined transactions.
     */
    public void setAtBlock(String atBlock) {
        this.atBlock = atBlock;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    /**
     * Contract address or a list of addresses.
     */
    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public List<String> getTopics() {
        return topics;
    }

    /**
     * Topics are order-dependent. Each topic can also be a list of topics.
     * Specify multiple topics separated by comma.
     */
    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public void setTopics(String topics) {
        String[] arr = topics.split(",");
        this.topics = Arrays.asList(arr);
    }

    public String getAddress() {
        return address;
    }

    /**
     * Contract address.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isFullTransactionObjects() {
        return fullTransactionObjects;
    }

    /**
     *  If true it returns the full transaction objects, if false only the hashes of the transactions.
     */
    public void setFullTransactionObjects(boolean fullTransactionObjects) {
        this.fullTransactionObjects = fullTransactionObjects;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Operation to use.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationOrDefault() {
        return this.operation != null ? operation : Web3jConstants.TRANSACTION;
    }

    public Web3jConfiguration copy() {
        try {
            return (Web3jConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
