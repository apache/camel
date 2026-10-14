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
package org.apache.camel.component.zookeeper;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.zookeeper.operations.AnyOfOperations;
import org.apache.camel.component.zookeeper.operations.ChildrenChangedOperation;
import org.apache.camel.component.zookeeper.operations.DataChangedOperation;
import org.apache.camel.component.zookeeper.operations.ExistenceChangedOperation;
import org.apache.camel.component.zookeeper.operations.ExistsOperation;
import org.apache.camel.component.zookeeper.operations.GetChildrenOperation;
import org.apache.camel.component.zookeeper.operations.GetDataOperation;
import org.apache.camel.component.zookeeper.operations.OperationResult;
import org.apache.camel.component.zookeeper.operations.ZooKeeperOperation;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ZooKeeperConsumer</code> uses various {@link ZooKeeperOperation} to interact and consume data from a ZooKeeper
 * cluster.
 */
@SuppressWarnings("rawtypes")
public class ZooKeeperConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperConsumer.class);

    private final ZooKeeperConnectionManager zkm;
    private volatile ZooKeeper connection;
    private ZooKeeperConfiguration configuration;
    private LinkedBlockingQueue<ZooKeeperOperation> operations = new LinkedBlockingQueue<>();
    private ExecutorService executor;
    private ScheduledExecutorService taskExecutor;
    private volatile boolean shuttingDown;

    public ZooKeeperConsumer(ZooKeeperEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.zkm = endpoint.getConnectionManager();
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        connection = zkm.getConnection();
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Connected to Zookeeper cluster %s", configuration.getConnectString()));
        }

        initializeConsumer();
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                "Camel-Zookeeper OperationsExecutor", 1);
        taskExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "ZooKeeperReconnectTask");

        OperationsExecutor opsService = new OperationsExecutor();
        executor.submit(opsService);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        shuttingDown = true;
        if (LOG.isTraceEnabled()) {
            LOG.trace(String.format("Shutting down zookeeper consumer of '%s'", configuration.getPath()));
        }
        if (taskExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(taskExecutor);
            taskExecutor = null;
        }
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(executor);
        zkm.shutdown();
    }

    private void initializeConsumer() {
        String node = configuration.getPath();
        if (configuration.isListChildren()) {
            initializeChildListingConsumer(node);
        } else {
            initializeDataConsumer(node);
        }
    }

    private void initializeDataConsumer(String node) {
        if (!shuttingDown) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Initializing consumption of data on node '%s'", node));
            }
            addBasicDataConsumeSequence(node);
        }
    }

    private void initializeChildListingConsumer(String node) {
        if (!shuttingDown) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Initializing child listing of node '%s'", node));
            }
            addBasicChildListingSequence(node);
        }
    }

    private Exchange createExchange(String path, OperationResult result, WatchedEvent watchedEvent) {
        Exchange exchange = createExchange(true);
        ZooKeeperMessage in = new ZooKeeperMessage(getEndpoint().getCamelContext(), path, result.getStatistics(), watchedEvent);
        exchange.setIn(in);
        if (result.isOk()) {
            in.setBody(result.getResult());
        } else {
            exchange.setException(result.getException());
        }
        return exchange;
    }

    private class OperationsExecutor implements Runnable {

        private ZooKeeperOperation current;

        private WatchedEvent watchedEvent;

        @Override
        public void run() {
            while (isRunAllowed()) {
                try {
                    current = operations.take();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(String.format("Processing '%s' operation", current.getClass().getSimpleName()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                }
                String node = current.getNode();
                boolean restarted = false;
                try {
                    OperationResult result = current.get();
                    if (ZooKeeperUtils.hasWatchedEvent(current)) {
                        watchedEvent = ZooKeeperUtils.getWatchedEvent(current);
                    }
                    if (result != null && current.shouldProduceExchange()) {
                        getProcessor().process(createExchange(node, result, watchedEvent));
                        watchedEvent = null;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handleException(e);
                    restarted = backoffAndThenRestart();
                } catch (Exception e) {
                    handleException(e);
                    restarted = backoffAndThenRestart();
                } finally {
                    // after a restart the queue holds the operations of the new sequence; a copy of the failed one
                    // (which may use a handle whose session expired) must not be added to them
                    if (configuration.isRepeat() && !restarted) {
                        try {
                            operations.offer(current.createCopy());
                        } catch (Exception e) {
                            backoffAndThenRestart();
                        }
                    }
                }
            }
        }

        /**
         * Waits for the backoff, then starts the sequence of operations again on the current connection, which is a new
         * one when the session of the previous one expired.
         *
         * @return true when the sequence was started again
         */
        private boolean backoffAndThenRestart() {
            try {
                if (isRunAllowed()) {
                    // Use Camel's task API for reconnection backoff delay instead of Thread.sleep()
                    // We use initialDelay for the actual delay, and maxIterations(1) to run once
                    Tasks.backgroundTask()
                            .withBudget(Budgets.iterationTimeBudget()
                                    .withMaxIterations(1)
                                    .withInitialDelay(Duration.ofMillis(configuration.getBackoff()))
                                    .withInterval(Duration.ofMillis(1))
                                    .withUnlimitedDuration()
                                    .build())
                            .withScheduledExecutor(taskExecutor)
                            .withName("ZooKeeperReconnectBackoff")
                            .build()
                            .run(getEndpoint().getCamelContext(), () -> true);

                    if (isRunAllowed()) {
                        connection = zkm.getConnection();
                        initializeConsumer();
                        return true;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            return false;
        }
    }

    private void addBasicDataConsumeSequence(String node) {
        operations.clear();
        operations.add(new AnyOfOperations(
                node, new ExistsOperation(connection, node), new ExistenceChangedOperation(connection, node)));
        operations.add(new GetDataOperation(connection, node));
        operations.add(new DataChangedOperation(connection, node, false, configuration.isSendEmptyMessageOnDelete()));
    }

    private void addBasicChildListingSequence(String node) {
        operations.clear();
        operations.add(new AnyOfOperations(
                node, new ExistsOperation(connection, node), new ExistenceChangedOperation(connection, node)));
        operations.add(new GetChildrenOperation(connection, node));
        operations.add(new ChildrenChangedOperation(connection, node, false));
    }
}
