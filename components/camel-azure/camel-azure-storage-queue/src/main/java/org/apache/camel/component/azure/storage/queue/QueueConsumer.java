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
package org.apache.camel.component.azure.storage.queue;

import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import com.azure.core.util.BinaryData;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.QueueStorageException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(QueueConsumer.class);

    private QueueClientWrapper clientWrapper;
    private QueueOperations queueOperations;

    public QueueConsumer(final QueueEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        clientWrapper = new QueueClientWrapper(getServiceClient().getQueueClient(getConfiguration().getQueueName()));
        queueOperations = new QueueOperations(getConfiguration(), clientWrapper);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        try {
            final List<QueueMessageItem> messageItems = clientWrapper.receiveMessages(getConfiguration().getMaxMessages(),
                    getConfiguration().getVisibilityTimeout(),
                    getConfiguration().getTimeout());

            // okay we have some response from azure so lets mark the consumer as ready
            forceConsumerAsReady();

            LOG.trace("Receiving messages [{}]...", messageItems);

            final Queue<Exchange> exchanges = createExchanges(messageItems);

            return processBatch(CastUtils.cast(exchanges));
        } catch (QueueStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    private Queue<Exchange> createExchanges(final List<QueueMessageItem> messageItems) {
        return messageItems
                .stream()
                .map(this::createExchange)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private QueueServiceClient getServiceClient() {
        return getEndpoint().getQueueServiceClient();
    }

    private QueueConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public QueueEndpoint getEndpoint() {
        return (QueueEndpoint) super.getEndpoint();
    }

    @Override
    public int processBatch(Queue<Object> exchanges) {
        final int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // copy messageId, popReceipt, timeout for fix exchange override case
            // azure storage blob can override this headers
            final String messageId = exchange.getIn().getHeader(QueueConstants.MESSAGE_ID, String.class);
            final String popReceipt = exchange.getIn().getHeader(QueueConstants.POP_RECEIPT, String.class);
            final Duration timeout = exchange.getIn().getHeader(QueueConstants.TIMEOUT, Duration.class);

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    // past messageId, popReceipt, timeout for fix exchange override case
                    exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, messageId);
                    exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, popReceipt);
                    exchange.getIn().setHeader(QueueConstants.TIMEOUT, timeout);

                    processCommit(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }
            });

            LOG.trace("Processing exchange [{}]...", exchange);
            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }
        return total;
    }

    private Exchange createExchange(final QueueMessageItem messageItem) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        BinaryData data = messageItem.getBody();
        InputStream is = data == null ? null : data.toStream();
        message.setBody(is);
        message.setHeaders(QueueExchangeHeaders.createQueueExchangeHeadersFromQueueMessageItem(messageItem).toMap());

        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onDone(Exchange exchange) {
                IOHelper.close(is);
            }
        });

        return exchange;
    }

    /**
     * Strategy to delete the message after being processed.
     *
     * @param exchange the exchange
     */
    private void processCommit(final Exchange exchange) {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Deleting message with pop receipt handle {}...",
                        QueueExchangeHeaders.getPopReceiptFromHeaders(exchange));
            }
            queueOperations.deleteMessage(exchange);
        } catch (QueueStorageException ex) {
            getExceptionHandler().handleException("Error occurred during deleting message. This exception is ignored.",
                    exchange, ex);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    private void processRollback(Exchange exchange) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException(
                    "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }
}
