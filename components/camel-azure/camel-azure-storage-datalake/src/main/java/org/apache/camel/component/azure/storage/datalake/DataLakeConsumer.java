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
package org.apache.camel.component.azure.storage.datalake;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.PathItem;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileSystemOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeOperationResponse;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLakeConsumer extends ScheduledBatchPollingConsumer {

    public static final int NOT_FOUND = 404;
    private static final Logger LOG = LoggerFactory.getLogger(DataLakeConsumer.class);

    public DataLakeConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String fileSystemName = getEndpoint().getConfiguration().getFileSystemName();
        final String fileName = getEndpoint().getConfiguration().getFileName();
        final DataLakeFileSystemClient dataLakeFileSystemClient
                = getEndpoint().getDataLakeServiceClient().getFileSystemClient(fileSystemName);
        int result;

        try {
            Queue<Exchange> exchanges;
            if (ObjectHelper.isNotEmpty(fileName)) {
                final Exchange exchange = createExchangeFromFile(fileName, dataLakeFileSystemClient);
                exchanges = new LinkedList<>();
                exchanges.add(exchange);
            } else {
                exchanges = createBatchExchangesFromPath(dataLakeFileSystemClient);
            }
            result = processBatch(CastUtils.cast(exchanges));
        } catch (DataLakeStorageException e) {
            if (NOT_FOUND == e.getStatusCode()) {
                result = 0;
            } else {
                throw e;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Queue<Exchange> createBatchExchangesFromPath(final DataLakeFileSystemClient dataLakeFileSystemClient)
            throws IOException {
        final DataLakeFileSystemClientWrapper fileSystemClientWrapper
                = new DataLakeFileSystemClientWrapper(dataLakeFileSystemClient);
        final DataLakeFileSystemOperations fileSystemOperations
                = new DataLakeFileSystemOperations(getEndpoint().getConfiguration(), fileSystemClientWrapper);

        final List<PathItem> items = (List<PathItem>) fileSystemOperations.listPaths(null).getBody();

        // okay we have some response from azure so lets mark the consumer as ready
        forceConsumerAsReady();

        final Queue<Exchange> exchanges = new LinkedList<>();
        // the listed files that could not be got, other than those that no longer exist, by name
        final Map<String, Exception> failures = new LinkedHashMap<>();
        for (PathItem pathItem : items) {
            if (!pathItem.isDirectory()) {
                // a failure to get one listed file (access denied by its ACL, a key that cannot decrypt it) does not
                // fail the poll as long as another listed file could be got: the file is skipped, the other listed
                // files are consumed, and a later poll tries it again
                try {
                    exchanges.add(createExchangeFromFile(pathItem.getName(), dataLakeFileSystemClient));
                } catch (DataLakeStorageException e) {
                    if (NOT_FOUND == e.getStatusCode()) {
                        // deleted (for example by another consumer) after it was listed
                        LOG.debug("Skipping file {} as it no longer exists", pathItem.getName());
                    } else {
                        failures.put(pathItem.getName(), e);
                    }
                } catch (Exception e) {
                    failures.put(pathItem.getName(), e);
                }
            }
        }

        if (!failures.isEmpty()) {
            if (exchanges.isEmpty()) {
                // none of the listed files could be got, so this is not about one file (expired credentials, no
                // permission to read files, no connection): the poll fails, and the backoff of the scheduled consumer
                // applies
                final Map.Entry<String, Exception> first = failures.entrySet().iterator().next();
                LOG.warn("None of the listed files of file system {} could be got ({} failed, the first is {}): the poll fails",
                        getEndpoint().getConfiguration().getFileSystemName(), failures.size(), first.getKey());
                rethrow(first.getValue());
            }
            for (Map.Entry<String, Exception> failure : failures.entrySet()) {
                getExceptionHandler().handleException("Error getting file " + failure.getKey() + " from file system "
                                                      + getEndpoint().getConfiguration().getFileSystemName()
                                                      + ". The file is skipped for this poll.",
                        failure.getValue());
            }
        }
        return exchanges;
    }

    private static void rethrow(final Exception e) throws IOException {
        if (e instanceof IOException ioe) {
            throw ioe;
        }
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new IOException(e);
    }

    private Exchange createExchangeFromFile(final String fileName, final DataLakeFileSystemClient dataLakeFileSystemClient)
            throws IOException {
        final DataLakeFileClientWrapper clientWrapper
                = new DataLakeFileClientWrapper(dataLakeFileSystemClient.getFileClient(fileName));
        final DataLakeFileOperations operations = new DataLakeFileOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = createExchange(true);

        DataLakeOperationResponse response;

        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getFileDir())) {
            response = operations.downloadToFile(exchange);
        } else {
            response = operations.getFile(exchange);
        }

        getEndpoint().setResponseOnExchange(response, exchange);

        exchange.getIn().setHeader(DataLakeConstants.FILE_NAME, fileName);
        return exchange;
    }

    @Override
    public DataLakeEndpoint getEndpoint() {
        return (DataLakeEndpoint) super.getEndpoint();
    }

    @Override
    public int processBatch(Queue<Object> exchanges) {
        final int total = exchanges.size();

        for (int i = 0; i < total && isBatchAllowed(); i++) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, i);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, i == total - 1);

            pendingExchanges = total - i - 1;

            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    LOG.trace("Processing all exchanges completed");
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

    protected void processRollback(Exchange exchange) {
        final Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException(
                    "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }
}
