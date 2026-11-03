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
package org.apache.camel.component.mtconnect;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads one agent through its long-lived streaming sample request on one thread of its own, step after step of the
 * {@link AgentProtocol}, and waits {@code reconnectDelay} after a failure or after the agent ended the stream. A stream
 * without a part for two heartbeats counts as a failure.
 */
public class MTConnectConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MTConnectConsumer.class);

    private final MTConnectEndpoint endpoint;
    private ExecutorService executor;
    private AgentReader reader;
    private AgentProtocol protocol;
    private CountDownLatch stopping;

    public MTConnectConsumer(MTConnectEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        stopping = new CountDownLatch(1);
        reader = AgentReader.open(endpoint, this);
        protocol = new AgentProtocol(endpoint, this, reader, true);
        executor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadExecutor(this, "MTConnectConsumer");
        executor.execute(this::run);
    }

    @Override
    protected void doStop() throws Exception {
        ExecutorServiceManager manager = getEndpoint().getCamelContext().getExecutorServiceManager();
        if (stopping != null) {
            stopping.countDown();
        }
        if (reader != null) {
            // a read waiting for a silent agent returns at once
            reader.abort();
        }
        if (executor != null) {
            // the exchange in flight completes; the loop ends before the next observation
            manager.shutdownGraceful(executor);
            executor = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        protocol = null;
        super.doStop();
    }

    private void run() {
        LOG.debug("Reading the MTConnect agent {}", endpoint.getAgentUrl());
        try {
            while (isRunAllowed()) {
                if (!protocol.step() && !pause()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.debug("Stopped reading the MTConnect agent {}", endpoint.getAgentUrl());
    }

    /** Waits the reconnect delay; false when the consumer is stopping. */
    private boolean pause() {
        try {
            return !stopping.await(endpoint.getReconnectDelay(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
