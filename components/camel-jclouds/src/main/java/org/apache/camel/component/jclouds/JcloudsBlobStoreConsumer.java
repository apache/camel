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

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

import com.google.common.base.Strings;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsBlobStoreConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreConsumer.class);
    private final JcloudsBlobStoreEndpoint endpoint;
    private final String container;
    private final BlobStore blobStore;
    private int maxMessagesPerPoll = 10;

    public JcloudsBlobStoreConsumer(JcloudsBlobStoreEndpoint endpoint, Processor processor, BlobStore blobStore) {
        super(endpoint, processor);
        this.blobStore = blobStore;
        this.endpoint = endpoint;
        this.container = endpoint.getContainer();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String container = endpoint.getContainer();
        String locationId = endpoint.getLocationId();
        JcloudsBlobStoreHelper.ensureContainerExists(blobStore, container, locationId);
    }

    @Override
    protected int poll() throws Exception {
        shutdownRunningTask = null;
        pendingExchanges = 0;

        Queue<Exchange> queue = new LinkedList<Exchange>();
        String directory = endpoint.getDirectory();

        ListContainerOptions opt = new ListContainerOptions();

        if (!Strings.isNullOrEmpty(directory)) {
            opt = opt.inDirectory(directory);
        }

        for (StorageMetadata md : blobStore.list(container, opt.maxResults(maxMessagesPerPoll).recursive())) {
            String blobName = md.getName();
            if (md.getType().equals(StorageType.BLOB)) {
                if (!Strings.isNullOrEmpty(blobName)) {
                    InputStream body = JcloudsBlobStoreHelper.readBlob(blobStore, container, blobName);
                    if (body != null) {
                        Exchange exchange = endpoint.createExchange();
                        CachedOutputStream cos = new CachedOutputStream(exchange);
                        IOHelper.copy(body, cos);
                        exchange.getIn().setBody(cos.newStreamCache());
                        exchange.setProperty(JcloudsConstants.BLOB_NAME, blobName);
                        queue.add(exchange);
                    }
                }
            }
        }
        return queue.isEmpty() ? 0 : processBatch(CastUtils.cast(queue));
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

            LOG.trace("Processing exchange [{}]...", exchange);
            getProcessor().process(exchange);
            if (exchange.getException() != null) {
                // if we failed then throw exception
                throw exchange.getException();
            }

            blobStore.removeBlob(container, exchange.getProperty(JcloudsConstants.BLOB_NAME, String.class));
        }

        return total;
    }
}
