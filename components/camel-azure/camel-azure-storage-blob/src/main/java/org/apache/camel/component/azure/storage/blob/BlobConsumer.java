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
package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobContainerOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BlobConsumer.class);

    public BlobConsumer(final BlobEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String containerName = getEndpoint().getConfiguration().getContainerName();
        final String blobName = getEndpoint().getConfiguration().getBlobName();
        final BlobContainerClient blobContainerClient
                = getEndpoint().getBlobServiceClient().getBlobContainerClient(containerName);

        Queue<Exchange> exchanges;

        try {
            if (ObjectHelper.isNotEmpty(blobName)) {
                // here we have a blob which means we just download a single blob
                final Exchange exchange = createExchangeFromBlob(blobName, blobContainerClient);
                exchanges = new LinkedList<>();
                exchanges.add(exchange);
            } else {
                // download multiple blobs since we only have no blobName set
                exchanges = createBatchExchangesFromContainer(blobContainerClient);
            }
            return processBatch(CastUtils.cast(exchanges));
        } catch (BlobStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    private Exchange createExchangeFromBlob(final String blobName, final BlobContainerClient blobContainerClient)
            throws IOException {
        final BlobClientWrapper clientWrapper
                = new BlobClientWrapper(blobContainerClient.getBlobClient(blobName));
        final BlobOperations operations = new BlobOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = createExchange(true);

        BlobOperationResponse response;
        if (!ObjectHelper.isEmpty(getEndpoint().getConfiguration().getFileDir())) {
            // if we have a fileDir set, we download our content
            response = operations.downloadBlobToFile(exchange);
        } else {
            // otherwise, we rely on the outputstream/inputstream
            response = operations.getBlob(exchange);
        }

        getEndpoint().setResponseOnExchange(response, exchange);

        exchange.getIn().setHeader(BlobConstants.BLOB_NAME, blobName);
        return exchange;
    }

    @SuppressWarnings("unchecked")
    private Queue<Exchange> createBatchExchangesFromContainer(final BlobContainerClient blobContainerClient)
            throws IOException {
        final BlobContainerClientWrapper containerClientWrapper = new BlobContainerClientWrapper(blobContainerClient);
        final BlobContainerOperations containerOperations
                = new BlobContainerOperations(getEndpoint().getConfiguration(), containerClientWrapper);

        final List<BlobItem> blobs = (List<BlobItem>) containerOperations.listBlobs(null).getBody();

        // okay we have some response from azure so lets mark the consumer as ready
        forceConsumerAsReady();

        final Queue<Exchange> exchanges = new LinkedList<>();
        for (BlobItem blobItem : blobs) {
            exchanges.add(createExchangeFromBlob(blobItem.getName(), blobContainerClient));
        }
        return exchanges;
    }

    @Override
    public BlobEndpoint getEndpoint() {
        return (BlobEndpoint) super.getEndpoint();
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

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    LOG.trace("Completed from processing all exchanges...");
                }

                @Override
                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }
            });

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }
        return total;
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException(
                    "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }
}
