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
package org.apache.camel.component.jclouds;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsBlobStoreConsumer extends JcloudsConsumer implements BatchConsumer, ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreConsumer.class);

    private final JcloudsBlobStoreEndpoint endpoint;

    private BlobStoreContext blobStoreContext;
    private String container;
    private BlobStore blobStore;

    private int maxMessagesPerPoll = 10;

    private volatile ShutdownRunningTask shutdownRunningTask;
    private volatile int pendingExchanges;


    public JcloudsBlobStoreConsumer(JcloudsBlobStoreEndpoint endpoint, Processor processor, BlobStoreContext blobStoreContext, String container) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.blobStoreContext = blobStoreContext;
        this.container = container;
        blobStore = blobStoreContext.getBlobStore();
    }

    @Override
    protected int poll() throws Exception {
        int messages = 0;
        shutdownRunningTask = null;
        pendingExchanges = 0;

        Queue<Exchange> queue = new LinkedList<Exchange>();

        ListContainerOptions opt = new ListContainerOptions();

        for (StorageMetadata md : blobStore.list(container, opt.maxResults(maxMessagesPerPoll))) {
            messages++;
            String blobName = md.getName();
            Object body = readBlob(container, blobName);
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(body);
            exchange.setProperty(JcloudsConstants.BLOB_NAME, blobName);
            queue.add(exchange);
        }
        return queue.isEmpty() ? 0 : processBatch(CastUtils.cast(queue));
    }

    @Override
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    String blobName = (String) exchange.getProperty(JcloudsConstants.BLOB_NAME);
                    blobStore.removeBlob(container, blobName);
                }

                public void onFailure(Exchange exchange) {

                }
            });

            LOG.trace("Processing exchange [{}]...", exchange);
            getProcessor().process(exchange);
        }

        return total;
    }

    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    @Override
    public void prepareShutdown() {

    }

    /**
     * Reads from a {@link BlobStore}. It returns an Object.
     *
     * @param container
     * @param blobName
     * @return
     */
    protected Object readBlob(String container, String blobName) {
        Object result = null;
        ObjectInputStream ois = null;
        BlobStore blobStore = blobStoreContext.getBlobStore();
        blobStore.createContainerInLocation(null, container);

        InputStream is = blobStore.getBlob(container, blobName).getPayload().getInput();

        try {
            ois = new ObjectInputStream(is) {
                @Override
                public Class resolveClass(ObjectStreamClass desc) throws IOException,
                        ClassNotFoundException {
                    try {
                        return getEndpoint().getCamelContext().getClassResolver().resolveClass(desc.getName());
                    } catch (Exception e) {
                    }
                    return super.resolveClass(desc);
                }
            };
            result = ois.readObject();
        } catch (IOException
                e) {
            e.printStackTrace();
        } catch (ClassNotFoundException
                e) {
            e.printStackTrace();
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }

        return result;
    }
}
