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

import java.math.BigInteger;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;

@UriParams
public class Web3jConfiguration implements Cloneable {
    @UriParam(label = "producer", defaultValue = "transaction")
    private String operation = Web3jConstants.TRANSACTION.toLowerCase();
    private DefaultBlockParameter fromBlock;
    private DefaultBlockParameter toBlock;
    private DefaultBlockParameter atBlock;
    private List<String> addresses;
    private String address;
    private List<String> topics; //TODO doesn't support list of lists
    private BigInteger position;
    private String blockHash;
    private String sha3HashOfDataToSign;
    private String signedTransactionData;
    private BigInteger transactionIndex;
    private BigInteger filterId;
    private String databaseName;
    private String keyName;
    private String transactionHash;
    private String sourceCode;
    private String nonce;
    private String headerPowHash;
    private String mixDigest;
    private String hashrate;
    private String clientId;
    private String fromAddress;
    private String toAddress;
    private BigInteger gasPrice;
    private BigInteger gasLimit;
    private BigInteger value;
    private String data;
    private BigInteger priority;
    private BigInteger ttl;
    private Web3j web3j;
    private boolean fullTransactionObjects;

    public Web3j getWeb3j() {
        return web3j;
    }

    public void setWeb3j(Web3j web3j) {
        this.web3j = web3j;
    }

    public BigInteger getPriority() {
        return priority;
    }

    public void setPriority(BigInteger priority) {
        this.priority = priority;
    }

    public BigInteger getTtl() {
        return ttl;
    }

    public void setTtl(BigInteger ttl) {
        this.ttl = ttl;
    }

    public BigInteger getGasPrice() {
        return gasPrice;
    }

    public void setGasPrice(BigInteger gasPrice) {
        this.gasPrice = gasPrice;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
    }

    public BigInteger getValue() {
        return value;
    }

    public void setValue(BigInteger value) {
        this.value = value;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getHashrate() {
        return hashrate;
    }

    public void setHashrate(String hashrate) {
        this.hashrate = hashrate;
    }

    public String getMixDigest() {
        return mixDigest;
    }

    public void setMixDigest(String mixDigest) {
        this.mixDigest = mixDigest;
    }

    public String getHeaderPowHash() {
        return headerPowHash;
    }

    public void setHeaderPowHash(String headerPowHash) {
        this.headerPowHash = headerPowHash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public BigInteger getFilterId() {
        return filterId;
    }

    public void setFilterId(BigInteger filterId) {
        this.filterId = filterId;
    }

    public BigInteger getTransactionIndex() {
        return transactionIndex;
    }

    public void setTransactionIndex(BigInteger transactionIndex) {
        this.transactionIndex = transactionIndex;
    }

    public String getSignedTransactionData() {
        return signedTransactionData;
    }

    public void setSignedTransactionData(String signedTransactionData) {
        this.signedTransactionData = signedTransactionData;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getSha3HashOfDataToSign() {
        return sha3HashOfDataToSign;
    }

    public void setSha3HashOfDataToSign(String sha3HashOfDataToSign) {
        this.sha3HashOfDataToSign = sha3HashOfDataToSign;
    }

    public BigInteger getPosition() {
        return position;
    }

    public void setPosition(BigInteger position) {
        this.position = position;
    }

    public DefaultBlockParameter getFromBlock() {
        return fromBlock;
    }

    public void setFromBlock(String block) {
        this.fromBlock = toDefaultBlockParameter(block);
    }

    public void setToBlock(String block) {
        this.toBlock = toDefaultBlockParameter(block);
    }

    private DefaultBlockParameter toDefaultBlockParameter(String block) {
        DefaultBlockParameter defaultBlockParameter = null;
        if (block != null) {
            for (DefaultBlockParameterName defaultBlockParameterName: DefaultBlockParameterName.values()) {
                if (block.equalsIgnoreCase(defaultBlockParameterName.getValue())) {
                    defaultBlockParameter = defaultBlockParameterName;
                }
            }

            if (defaultBlockParameter == null) {
                defaultBlockParameter = DefaultBlockParameter.valueOf(new BigInteger(block));
            }
        }

        return defaultBlockParameter;
    }

    public DefaultBlockParameter getToBlock() {
        return toBlock;
    }

    public DefaultBlockParameter getAtBlock() {
        return atBlock;
    }

    public void setAtBlock(String block) {
        this.atBlock = toDefaultBlockParameter(block);
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isFullTransactionObjects() {
        return fullTransactionObjects;
    }

    public void setFullTransactionObjects(boolean fullTransactionObjects) {
        this.fullTransactionObjects = fullTransactionObjects;
    }

    public String getOperation() {
        return operation;
    }

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
