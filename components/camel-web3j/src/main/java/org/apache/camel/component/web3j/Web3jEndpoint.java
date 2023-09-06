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
public class Web3jEndpoint extends DefaultEndpoint {
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
