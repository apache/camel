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
package org.apache.camel.component.iggy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.component.iggy.client.IggyClientConnectionPool;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IggyConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IggyConsumer.class);
    private final IggyEndpoint endpoint;
    private IggyClientConnectionPool iggyClientConnectionPool;
    private ExecutorService executor;
    private final List<IggyFetchRecords> tasks = new ArrayList<>();

    public IggyConsumer(IggyEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Starting Iggy consumer for stream {} and topic {}", endpoint.getConfiguration().getStreamId(),
                endpoint.getTopicName());

        iggyClientConnectionPool = new IggyClientConnectionPool(
                endpoint.getConfiguration().getHost(),
                endpoint.getConfiguration().getPort(),
                endpoint.getConfiguration().getUsername(),
                endpoint.getConfiguration().getPassword(),
                endpoint.getConfiguration().getClientTransport());

        IggyBaseClient client = iggyClientConnectionPool.borrowObject();
        endpoint.initializeTopic(client);
        endpoint.initializeConsumerGroup(client);
        iggyClientConnectionPool.returnClient(client);

        executor = endpoint.createExecutor();
        BridgeExceptionHandlerToErrorHandler bridge = new BridgeExceptionHandlerToErrorHandler(this);

        // For now, we'll just have one consumer task. This can be extended later if Iggy supports partitioned consumption.
        // TODO Handle streams, once tehy will be implemented in the java client
        IggyFetchRecords task = new IggyFetchRecords(
                this,
                endpoint,
                endpoint.getConfiguration(),
                iggyClientConnectionPool,
                bridge);
        executor.submit(task);
        tasks.add(task);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping Iggy consumer for stream {} and topic {}", endpoint.getConfiguration().getStreamId(),
                endpoint.getTopicName());

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                for (IggyFetchRecords task : tasks) {
                    task.stop();
                }

                int timeout = endpoint.getConfiguration().getShutdownTimeout();
                LOG.debug("Shutting down Iggy consumer worker threads with timeout {} millis", timeout);
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor, timeout);
            } else {
                executor.shutdown();

                int timeout = endpoint.getConfiguration().getShutdownTimeout();
                LOG.debug("Shutting down Iggy consumer worker threads with timeout {} millis", timeout);
                if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Shutting down Iggy {} consumer worker threads did not finish within {} millis",
                            tasks.size(), timeout);
                }
            }

            if (!executor.isTerminated()) {
                tasks.forEach(IggyFetchRecords::stop);
                executor.shutdownNow();
            }
        }
        tasks.clear();
        executor = null;

        super.doStop();
    }
}
