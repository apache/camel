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

import io.reactivex.disposables.Disposable;
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

import static org.apache.camel.component.web3j.Web3jHelper.toDefaultBlockParameter;

/**
 * The web3j consumer.
 */
public class Web3jConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(Web3jConsumer.class);
    private final Web3jConfiguration configuration;
    private Web3j web3j;
    private Disposable subscription;
    private Web3jEndpoint endpoint;

    public Web3jConsumer(Web3jEndpoint endpoint, Processor processor, Web3jConfiguration configuration) {
        super(endpoint, processor);
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

        this.web3j = getEndpoint().getWeb3j();

        LOG.info("Subscribing to: {}", endpoint.getNodeAddress());
        switch (configuration.getOperation()) {
            case Web3jConstants.ETH_LOG_OBSERVABLE:
                EthFilter ethFilter = Web3jEndpoint.buildEthFilter(toDefaultBlockParameter(configuration.getFromBlock()),
                        toDefaultBlockParameter(configuration.getToBlock()), configuration.getAddresses(),
                        configuration.getTopics());
                subscription = web3j.ethLogFlowable(ethFilter).subscribe(
                        this::ethLogObservable,
                        t -> processError(t, Web3jConstants.ETH_LOG_OBSERVABLE),
                        () -> processDone(Web3jConstants.ETH_LOG_OBSERVABLE));
                break;

            case Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE:
                subscription = web3j.ethBlockHashFlowable().subscribe(
                        this::ethBlockHashObservable,
                        t -> processError(t, Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE),
                        () -> processDone(Web3jConstants.ETH_BLOCK_HASH_OBSERVABLE));
                break;

            case Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE:
                subscription = web3j.ethPendingTransactionHashFlowable().subscribe(
                        this::ethPendingTransactionHashObservable,
                        t -> processError(t, Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE),
                        () -> processDone(Web3jConstants.ETH_PENDING_TRANSACTION_HASH_OBSERVABLE));
                break;

            case Web3jConstants.TRANSACTION_OBSERVABLE:
                subscription = web3j.transactionFlowable().subscribe(
                        this::processTransaction,
                        t -> processError(t, Web3jConstants.TRANSACTION_OBSERVABLE),
                        () -> processDone(Web3jConstants.TRANSACTION_OBSERVABLE));
                break;

            case Web3jConstants.PENDING_TRANSACTION_OBSERVABLE:
                subscription = web3j.pendingTransactionFlowable().subscribe(
                        this::processTransaction,
                        t -> processError(t, Web3jConstants.PENDING_TRANSACTION_OBSERVABLE),
                        () -> processDone(Web3jConstants.PENDING_TRANSACTION_OBSERVABLE));
                break;

            case Web3jConstants.BLOCK_OBSERVABLE:
                subscription = web3j.blockFlowable(configuration.isFullTransactionObjects()).subscribe(
                        this::blockObservable,
                        t -> processError(t, Web3jConstants.BLOCK_OBSERVABLE),
                        () -> processDone(Web3jConstants.BLOCK_OBSERVABLE));
                break;

            case Web3jConstants.REPLAY_BLOCKS_OBSERVABLE:
                subscription = web3j
                        .replayPastBlocksFlowable(toDefaultBlockParameter(configuration.getFromBlock()),
                                toDefaultBlockParameter(configuration.getToBlock()), configuration.isFullTransactionObjects())
                        .subscribe(
                                this::blockObservable,
                                t -> processError(t, Web3jConstants.REPLAY_BLOCKS_OBSERVABLE),
                                () -> processDone(Web3jConstants.REPLAY_BLOCKS_OBSERVABLE));
                break;

            case Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE:
                subscription = web3j.replayPastTransactionsFlowable(toDefaultBlockParameter(configuration.getFromBlock()),
                        toDefaultBlockParameter(configuration.getToBlock())).subscribe(
                                this::processTransaction,
                                t -> processError(t, Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE),
                                () -> processDone(Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE));
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation " + configuration.getOperation());
        }

        LOG.info("Subscribed: {}", this.configuration);
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
        exchange.getIn().setHeader(Web3jConstants.HEADER_STATUS, "done");
        exchange.getIn().setHeader(Web3jConstants.HEADER_OPERATION, operation);
        processEvent(exchange);
    }

    private void processError(Throwable throwable, String operation) {
        LOG.debug("processError for operation: {} {}", operation, throwable.getMessage(), throwable);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.setException(throwable);
        processEvent(exchange);
    }

    @Override
    protected void doStop() throws Exception {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        super.doStop();
    }
}
