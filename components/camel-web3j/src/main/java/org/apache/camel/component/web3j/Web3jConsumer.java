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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import rx.Subscription;

import static org.apache.camel.component.web3j.Web3jHelper.toDefaultBlockParameter;

/**
 * The web3j consumer.
 */
public class Web3jConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(Web3jConsumer.class);
    private final Web3j web3j;
    private final Web3jConfiguration configuration;
    private Subscription subscription;
    private Web3jEndpoint endpoint;

    public Web3jConsumer(Web3jEndpoint endpoint, Processor processor, Web3jConfiguration configuration) {
        super(endpoint, processor);
        this.web3j = endpoint.getWeb3j();
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    public Web3jEndpoint getEndpoint() {
        return (Web3jEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Subscribing to: {}", endpoint.getNodeAddress());
        switch (configuration.getOperation()) {
            case Web3jConstants.ETH_LOG_OBSERVABLE:
                EthFilter ethFilter = Web3jEndpoint.buildEthFilter(toDefaultBlockParameter(configuration.getFromBlock()), toDefaultBlockParameter(configuration.getToBlock()), configuration.getAddresses(), configuration.getTopics());
                subscription = web3j.ethLogObservable(ethFilter).subscribe(
                    x -> ethLogObservable(x),
                    t -> processError(t, Web3jConstants.ETH_LOG_OBSERVABLE),
                    () -> processDone(Web3jConstants.ETH_LOG_OBSERVABLE)
                );
                break;

            case Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE:
                subscription = web3j.ethBlockHashObservable().subscribe(
                    x -> ethBlockHashObservable(x),
                    t -> processError(t, Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE),
                    () -> processDone(Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE)
                );
                break;

            case Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE:
                subscription = web3j.ethPendingTransactionHashObservable().subscribe(
                    x -> ethPendingTransactionHashObservable(x),
                    t -> processError(t, Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE),
                    () -> processDone(Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE)
                );
                break;

            case Web3jConstants.TRANSACTION_OBSERVABLE:
                subscription = web3j.transactionObservable().subscribe(
                    x -> processTransaction(x),
                    t -> processError(t, Web3jConstants.TRANSACTION_OBSERVABLE),
                    () -> processDone(Web3jConstants.TRANSACTION_OBSERVABLE)
                );
                break;

            case Web3jConstants.PENDING_TRANSACTION_OBSERVABLE:
                subscription = web3j.pendingTransactionObservable().subscribe(
                    x -> processTransaction(x),
                    t -> processError(t, Web3jConstants.PENDING_TRANSACTION_OBSERVABLE),
                    () -> processDone(Web3jConstants.PENDING_TRANSACTION_OBSERVABLE)
                );
                break;

            case Web3jConstants.BLOCK_OBSERVABLE:
                subscription = web3j.blockObservable(configuration.isFullTransactionObjects()).subscribe(
                    x -> blockObservable(x),
                    t -> processError(t, Web3jConstants.BLOCK_OBSERVABLE),
                    () -> processDone(Web3jConstants.BLOCK_OBSERVABLE)
                );
                break;

            case Web3jConstants.REPLAY_BLOCKS_OBSERVABLE:
                subscription = web3j.replayBlocksObservable(toDefaultBlockParameter(configuration.getFromBlock()), toDefaultBlockParameter(configuration.getToBlock()), configuration.isFullTransactionObjects()).subscribe(
                    x -> blockObservable(x),
                    t -> processError(t, Web3jConstants.REPLAY_BLOCKS_OBSERVABLE),
                    () -> processDone(Web3jConstants.REPLAY_BLOCKS_OBSERVABLE)
                );
                break;

            case Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE:
                subscription = web3j.replayTransactionsObservable(toDefaultBlockParameter(configuration.getFromBlock()), toDefaultBlockParameter(configuration.getToBlock())).subscribe(
                    x -> processTransaction(x),
                    t -> processError(t, Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE),
                    () -> processDone(Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE)
                );
                break;

            case Web3jConstants.CATCH_UP_TO_LATEST_BLOCK_OBSERVABLE:
                subscription = web3j.catchUpToLatestBlockObservable(toDefaultBlockParameter(configuration.getFromBlock()), configuration.isFullTransactionObjects()).subscribe(
                    x -> blockObservable(x),
                    t -> processError(t, Web3jConstants.CATCH_UP_TO_LATEST_BLOCK_OBSERVABLE),
                    () -> processDone(Web3jConstants.CATCH_UP_TO_LATEST_BLOCK_OBSERVABLE)
                );
                break;

            case Web3jConstants.CATCH_UP_TO_LATEST_TRANSACTION_OBSERVABLE:
                subscription = web3j.catchUpToLatestTransactionObservable(toDefaultBlockParameter(configuration.getFromBlock())).subscribe(
                    x -> processTransaction(x),
                    t -> processError(t, Web3jConstants.CATCH_UP_TO_LATEST_TRANSACTION_OBSERVABLE),
                    () -> processDone(Web3jConstants.CATCH_UP_TO_LATEST_TRANSACTION_OBSERVABLE)
                );
                break;

            case Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_BLOCKS_OBSERVABLE:
                subscription = web3j.catchUpToLatestAndSubscribeToNewBlocksObservable(toDefaultBlockParameter(configuration.getFromBlock()), configuration.isFullTransactionObjects()).subscribe(
                    x -> blockObservable(x),
                    t -> processError(t, Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_BLOCKS_OBSERVABLE),
                    () -> processDone(Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_BLOCKS_OBSERVABLE)
                );
                break;

            case Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_TRANSACTIONS_OBSERVABLE:
                subscription = web3j.catchUpToLatestAndSubscribeToNewTransactionsObservable(toDefaultBlockParameter(configuration.getFromBlock())).subscribe(
                    x -> processTransaction(x),
                    t -> processError(t, Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_TRANSACTIONS_OBSERVABLE),
                    () -> processDone(Web3jConstants.CATCH_UP_TO_LATEST_AND_SUBSCRIBE_TO_NEW_TRANSACTIONS_OBSERVABLE)
                );
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + configuration.getOperation());
        }

        LOG.info("Subscribed: {}", this.configuration);
    }

    private EthFilter buildEthFilter() {
        EthFilter ethFilter = new EthFilter(toDefaultBlockParameter(configuration.getFromBlock()), toDefaultBlockParameter(configuration.getToBlock()), configuration.getAddresses());
        if (configuration.getTopics() != null) {
            for (String topic : configuration.getTopics()) {
                if (topic != null && topic.length() > 0) {
                    ethFilter.addSingleTopic(topic);
                } else {
                    ethFilter.addNullTopic();
                }
            }
        }
        return ethFilter;
    }

    private void ethBlockHashObservable(String x) {
        LOG.debug("processEthBlock {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void ethPendingTransactionHashObservable(String x) {
        LOG.debug("processEthBlock {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void blockObservable(EthBlock x) {
        EthBlock.Block block = x.getBlock();
        LOG.debug("processEthBlock {}", block);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(block);
        processEvent(exchange);
    }

    private void processTransaction(Transaction x) {
        LOG.debug("processTransaction {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void ethLogObservable(Log x) {
        LOG.debug("processLogObservable {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    public void processEvent(Exchange exchange) {
        LOG.debug("processEvent {}", exchange);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Error processing event ", e);
        }
    }

    private void processDone(String operation) {
        LOG.debug("processDone for operation: {}", operation);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setHeader("status", "done");
        exchange.getIn().setHeader("operation", operation);
        processEvent(exchange);
    }

    private void processError(Throwable throwable, String operation) {
        LOG.debug("processError for operation: " + operation + " " + throwable);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.setException(throwable);
        processEvent(exchange);
    }

    @Override
    protected void doStop() throws Exception {
        if (subscription != null) {
            subscription.unsubscribe();
        }
        super.doStop();
    }
}
