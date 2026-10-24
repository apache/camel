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

import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Filter;
import org.web3j.protocol.core.methods.request.ShhFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.ipc.UnixIpcService;
import org.web3j.protocol.ipc.WindowsIpcService;
import org.web3j.quorum.Quorum;

/**
 * Interact with Ethereum nodes using web3j client API.
 */
@UriEndpoint(firstVersion = "2.22.0", scheme = "web3j", title = "Web3j Ethereum Blockchain", syntax = "web3j:nodeAddress",
             category = { Category.BLOCKCHAIN }, headersClass = Web3jConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.POJO, Carrier.NULL }, dependsOn = "operation",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "ETH_BLOCK_HASH_OBSERVABLE",
                                                          carriers = { Carrier.TEXT, Carrier.NULL },
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_PENDING_TRANSACTION_HASH_OBSERVABLE",
                                                          carriers = { Carrier.TEXT, Carrier.NULL },
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "BLOCK_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation", value = "REPLAY_BLOCKS_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation", value = "TRANSACTION_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation", value = "PENDING_TRANSACTION_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation", value = "REPLAY_TRANSACTIONS_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation", value = "ETH_LOG_OBSERVABLE",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Log") },
                                   description = "One message per item of the subscription of the operation: the hash of a"
                                                 + " block or a pending transaction as a String, an EthBlock.Block, a"
                                                 + " Transaction or a Log of web3j. No body on the message that ends the"
                                                 + " subscription (header CamelWeb3jStatus=done) or carries its error"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The operations take their input from headers and options, except WEB3_SHA3,"
                                                + " DB_PUT_STRING, DB_PUT_HEX and QUORUM_GET_PRIVATE_PAYLOAD, which send the"
                                                + " body converted to a String: a Map or a POJO is sent as its toString"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "WEB3_CLIENT_VERSION",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "WEB3_SHA3", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "NET_VERSION", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_PROTOCOL_VERSION",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_COINBASE", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_STORAGE_AT",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_CODE", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_SIGN", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_SEND_TRANSACTION",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_SEND_RAW_TRANSACTION",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_CALL", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_COMPILE_LLL",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "ETH_COMPILE_SERPENT",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "DB_GET_STRING",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "DB_GET_HEX", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "SHH_VERSION", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "SHH_NEW_IDENTITY",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "SHH_NEW_GROUP",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "QUORUM_ETH_SEND_TRANSACTION",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "QUORUM_GET_PRIVATE_PAYLOAD",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "NET_PEER_COUNT",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_HASHRATE",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_GAS_PRICE",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_BLOCK_NUMBER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_BALANCE",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_TRANSACTION_COUNT",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_ESTIMATE_GAS",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_NEW_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_NEW_BLOCK_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "ETH_NEW_PENDING_TRANSACTION_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "SHH_NEW_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.math.BigInteger"),
                                           @BodyType.When(option = "operation", value = "NET_LISTENING",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_SYNCING", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_MINING", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_UNINSTALL_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_SUBMIT_WORK",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_SUBMIT_HASHRATE",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "DB_PUT_STRING",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "DB_PUT_HEX", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "SHH_POST", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "SHH_HAS_IDENTITY",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "SHH_ADD_TO_GROUP",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "SHH_UNINSTALL_FILTER",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "ETH_ACCOUNTS", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.lang.String>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_COMPILERS",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.lang.String>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_WORK", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.lang.String>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_FILTER_CHANGES",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.web3j.protocol.core.methods.response.EthLog.LogResult<?>>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_FILTER_LOGS",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.web3j.protocol.core.methods.response.EthLog.LogResult<?>>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_LOGS", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.web3j.protocol.core.methods.response.EthLog.LogResult<?>>"),
                                           @BodyType.When(option = "operation", value = "SHH_GET_FILTER_CHANGES",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.web3j.protocol.core.methods.response.ShhMessages.SshMessage>"),
                                           @BodyType.When(option = "operation", value = "SHH_GET_MESSAGES",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.web3j.protocol.core.methods.response.ShhMessages.SshMessage>"),
                                           @BodyType.When(option = "operation", value = "ETH_COMPILE_SOLIDITY",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, org.web3j.protocol.core.methods.response.EthCompileSolidity.Code>"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_BLOCK_BY_HASH",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_BLOCK_BY_NUMBER",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.EthBlock.Block"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_TRANSACTION_BY_HASH",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation",
                                                          value = "ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "org.web3j.protocol.core.methods.response.Transaction"),
                                           @BodyType.When(option = "operation", value = "ETH_GET_TRANSACTION_RECEIPT",
                                                          carriers = Carrier.POJO,
                                                          javaType = "java.util.Optional<org.web3j.protocol.core.methods.response.TransactionReceipt>"),
                                           @BodyType.When(option = "operation", value = "QUORUM_NODE_INFO",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.web3j.protocol.core.methods.response.admin.AdminNodeInfo.NodeInfo") },
                                   description = "The result of the JSON-RPC call of the operation, selected by the"
                                                 + " CamelWeb3jOperation header or the operation option: a String for a"
                                                 + " hash, an address or hex data, a BigInteger for a quantity, a Boolean, a"
                                                 + " List, or a web3j object such as an EthBlock.Block, a Transaction (null"
                                                 + " when not found) or an Optional of a TransactionReceipt. On a JSON-RPC"
                                                 + " error the body is unchanged and the exchange fails, with the"
                                                 + " CamelWeb3jErrorCode, CamelWeb3jErrorData and CamelWeb3jErrorMessage headers"))
public class Web3jEndpoint extends DefaultEndpoint implements EndpointServiceLocation {
    private static final Logger LOG = LoggerFactory.getLogger(Web3jEndpoint.class);

    private Web3j web3j;

    @UriPath
    @Metadata(required = true)
    private String nodeAddress;

    @UriParam
    private final Web3jConfiguration configuration;

    public Web3jEndpoint(String uri, String remaining, Web3jComponent component, Web3jConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.nodeAddress = remaining;
    }

    @Override
    public String getServiceUrl() {
        return nodeAddress;
    }

    @Override
    public String getServiceProtocol() {
        return "json-rpc";
    }

    @Override
    protected void doStart() throws Exception {
        this.web3j = buildService(nodeAddress, configuration);
        super.doStart();
    }

    public Web3jConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Web3jProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Web3jConsumer consumer = new Web3jConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    private Web3j buildService(String clientAddress, Web3jConfiguration configuration) {
        LOG.info("Building service for endpoint: {}, {}", clientAddress, configuration);

        if (configuration.getWeb3j() != null) {
            return configuration.getWeb3j();
        }

        Web3jService web3jService;
        if (clientAddress == null || clientAddress.isEmpty()) {
            web3jService = new HttpService();
        } else if (clientAddress.startsWith("http")) {
            web3jService = new HttpService(clientAddress);
        } else if (System.getProperty("os.name").regionMatches(true, 0, "win", 0, "win".length())) {
            web3jService = new WindowsIpcService(clientAddress);
        } else {
            web3jService = new UnixIpcService(clientAddress);
        }

        if (configuration.isQuorumAPI()) {
            return Quorum.build(web3jService);
        }

        return Web3j.build(web3jService);
    }

    public String getNodeAddress() {
        return nodeAddress;
    }

    /**
     * Sets the node address used to communicate
     */
    public void setNodeAddress(String nodeAddress) {
        this.nodeAddress = nodeAddress;
    }

    public static EthFilter buildEthFilter(
            DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock, List<String> addresses, List<String> topics) {
        EthFilter filter = new EthFilter(fromBlock, toBlock, addresses);
        addTopics(filter, topics);
        return filter;
    }

    public static ShhFilter buildShhFilter(String data, List<String> topics) {
        ShhFilter filter = new ShhFilter(data);
        addTopics(filter, topics);
        return filter;
    }

    private static void addTopics(Filter<?> filter, List<String> topics) {
        if (topics != null) {
            for (String topic : topics) {
                if (topic != null && topic.length() > 0) {
                    filter.addSingleTopic(topic);
                } else {
                    filter.addNullTopic();
                }
            }
        }
    }

}
