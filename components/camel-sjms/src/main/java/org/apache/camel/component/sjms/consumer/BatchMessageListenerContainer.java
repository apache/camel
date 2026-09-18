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
package org.apache.camel.component.sjms.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchMessageListenerContainer extends SimpleMessageListenerContainer {

    private static final Logger LOG = LoggerFactory.getLogger(BatchMessageListenerContainer.class);

    private final SjmsEndpoint endpoint;
    private BatchEndpointMessageListener batchListener;
    private ExecutorService workerExecutor;
    private final ReentrantLock workersLock = new ReentrantLock();
    private final List<BatchConsumerWorker> workers = new ArrayList<>();
    private final AggregationStrategy aggregationStrategy;

    public BatchMessageListenerContainer(SjmsEndpoint endpoint, AggregationStrategy aggregationStrategy) {
        super(endpoint);
        this.endpoint = endpoint;
        this.aggregationStrategy = aggregationStrategy;
    }

    public void setBatchListener(BatchEndpointMessageListener batchListener) {
        this.batchListener = batchListener;
    }

    @Override
    protected void doStart() throws Exception {
        workerExecutor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(
                this, "SjmsBatchConsumer[" + endpoint.getDestinationName() + "]",
                Math.max(1, this.getConcurrentConsumers()));

        CamelContextAware.trySetCamelContext(
                aggregationStrategy, endpoint.getCamelContext());

        ServiceHelper.initService(aggregationStrategy);
        ServiceHelper.startService(aggregationStrategy);

        // triggers connection + session/consumer creation, calling configureConsumer() below
        // for each session per concurrentConsumers, and re-invokes it again on reconnection
        super.doStart();
    }

    @Override
    protected void configureConsumer(MessageConsumer consumer, Session session) {
        BatchConsumerWorker worker = new BatchConsumerWorker(
                endpoint, batchListener, consumer, session);
        workersLock.lock();
        try {
            workers.add(worker);
        } finally {
            workersLock.unlock();
        }
        CompletableFuture.runAsync(worker, workerExecutor)
                .whenComplete((v, ex) -> onWorkerExit(worker, ex));
    }

    @Override
    protected void doStop() throws Exception {
        invalidateBatchWorkers();
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.warn("Batch consumer workers for {} did not stop within 30s; forcing shutdown",
                        endpoint.getEndpointUri());
                workerExecutor.shutdownNow();
            }
        }

        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        try {
            super.doShutdown();
        } finally {
            ServiceHelper.stopAndShutdownService(aggregationStrategy);
        }
    }

    private void invalidateBatchWorkers() {
        workersLock.lock();
        try {
            for (BatchConsumerWorker worker : workers) {
                worker.shutdown();
            }
            workers.clear();
        } finally {
            workersLock.unlock();
        }
    }

    private void onWorkerExit(BatchConsumerWorker worker, Throwable ex) {

        workersLock.lock();
        try {
            workers.remove(worker);
        } finally {
            workersLock.unlock();
        }

        if (worker.isShutdownRequested()) {
            return;
        }

        Throwable cause = ex != null && ex.getCause() != null ? ex.getCause() : ex;
        LOG.warn("Batch consumer worker for {} exited unexpectedly, triggering recovery",
                endpoint.getEndpointUri(), cause);

        invalidateBatchWorkers();
        invalidateConsumers();
        scheduleConnectionRecovery();
    }

    @Override
    public void onException(JMSException exception) {
        invalidateBatchWorkers();
        super.onException(exception);
    }
}
