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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    // the delay before subscribing again after a subscription ended with an error
    private static final long DEFAULT_RESUBSCRIBE_DELAY = 15000;
    private final Web3jConfiguration configuration;
    private Web3j web3j;
    private volatile Disposable subscription;
    private Web3jEndpoint endpoint;
    private long resubscribeDelay = DEFAULT_RESUBSCRIBE_DELAY;
    private volatile ScheduledExecutorService resubscribeExecutor;
    // true from a subscription error until the next item, so the failures while subscribing again are routed once
    private volatile boolean failing;

    public Web3jConsumer(Web3jEndpoint endpoint, Processor processor, Web3jConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    public Web3jEndpoint getEndpoint() {
        return (Web3jEndpoint) super.getEndpoint();
    }

    /**
     * Sets the delay in millis before subscribing again after a subscription ended with an error. Only for tests.
     */
    void setResubscribeDelay(long resubscribeDelay) {
        this.resubscribeDelay = resubscribeDelay;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.web3j = getEndpoint().getWeb3j();
        failing = false;
        resubscribeExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "Web3jConsumerResubscribe");

        subscribe();
    }

    private void subscribe() {
        if (failing) {
            LOG.debug("Subscribing again to: {}", endpoint.getNodeAddress());
        } else {
            LOG.info("Subscribing to: {}", endpoint.getNodeAddress());
        }
        switch (configuration.getOperation()) {
            case Web3jConstants.ETH_LOG_OBSERVABLE:
                EthFilter ethFilter = Web3jEndpoint.buildEthFilter(toDefaultBlockParameter(configuration.getFromBlock()),
                        toDefaultBlockParameter(configuration.getToBlock()), configuration.getAddressesAsList(),
                        configuration.getTopicsAsList());
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

        if (failing) {
            LOG.debug("Subscribed again: {}", this.configuration);
        } else {
            LOG.info("Subscribed: {}", this.configuration);
        }
    }

    /**
     * An item was received, so the subscription works: an error after this one is routed and logged again.
     */
    private void received() {
        if (failing) {
            failing = false;
            LOG.info("Receiving again from: {}", endpoint.getNodeAddress());
        }
    }

    private void ethBlockHashObservable(String x) {
        received();
        LOG.debug("processEthBlock {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void ethPendingTransactionHashObservable(String x) {
        received();
        LOG.debug("processEthBlock {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void blockObservable(EthBlock x) {
        received();
        EthBlock.Block block = x.getBlock();
        LOG.debug("processEthBlock {}", block);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(block);
        processEvent(exchange);
    }

    private void processTransaction(Transaction x) {
        received();
        LOG.debug("processTransaction {}", x);
        Exchange exchange = this.getEndpoint().createExchange();
        exchange.getIn().setBody(x);
        processEvent(exchange);
    }

    private void ethLogObservable(Log x) {
        received();
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
        boolean again = resubscribes(operation) && isRunAllowed();
        if (!failing) {
            failing = true;
            if (again) {
                LOG.warn("The {} subscription to {} ended with an error, subscribing again every {} ms: {}", operation,
                        endpoint.getNodeAddress(), resubscribeDelay, throwable.getMessage());
            }
            Exchange exchange = this.getEndpoint().createExchange();
            exchange.setException(throwable);
            processEvent(exchange);
        } else {
            LOG.debug("Subscribing again to {} for operation {} failed: {}", endpoint.getNodeAddress(), operation,
                    throwable.getMessage());
        }
        ScheduledExecutorService executor = resubscribeExecutor;
        if (again && executor != null) {
            try {
                executor.schedule(() -> resubscribe(operation), resubscribeDelay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // the consumer is stopping
                LOG.debug("Not subscribing again to {} as the consumer is stopping", endpoint.getNodeAddress());
            }
        }
    }

    /**
     * Whether a subscription that ended with an error is subscribed again: the live operations, which start again at
     * the latest block. Not the replay of a block range, nor a log filter from a given block, which would route the
     * blocks or logs from the start of the range again.
     */
    private boolean resubscribes(String operation) {
        if (Web3jConstants.REPLAY_BLOCKS_OBSERVABLE.equals(operation)
                || Web3jConstants.REPLAY_TRANSACTIONS_OBSERVABLE.equals(operation)) {
            return false;
        }
        if (Web3jConstants.ETH_LOG_OBSERVABLE.equals(operation)) {
            String fromBlock = configuration.getFromBlock();
            return fromBlock == null || "latest".equalsIgnoreCase(fromBlock) || "pending".equalsIgnoreCase(fromBlock);
        }
        return true;
    }

    private void resubscribe(String operation) {
        if (!isRunAllowed()) {
            return;
        }
        Disposable old = subscription;
        if (old != null && !old.isDisposed()) {
            old.dispose();
        }
        try {
            subscribe();
        } catch (Exception e) {
            processError(e, operation);
            return;
        }
        // doStop may have run while subscribing (it does not wait for this task), and disposed the previous
        // subscription only
        Disposable current = subscription;
        if (!isRunAllowed() && current != null && !current.isDisposed()) {
            current.dispose();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (resubscribeExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(resubscribeExecutor);
            resubscribeExecutor = null;
        }
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        super.doStop();
    }
}
