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

package org.apache.camel.component.mllp;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.component.mllp.internal.TcpServerAcceptRunnable;
import org.apache.camel.component.mllp.internal.TcpSocketConsumerRunnable;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;

/**
 * The MLLP consumer.
 */
@ManagedResource(description = "MLLP Producer")
public class MllpTcpServerConsumer extends DefaultConsumer {
    final ExecutorService acceptExecutor;
    final ExecutorService consumerExecutor;
    TcpServerAcceptRunnable acceptRunnable;
    Map<TcpSocketConsumerRunnable, Long> consumerRunnables = new ConcurrentHashMap<>();


    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");
        // this.endpoint = endpoint;
        // this.configuration = endpoint.getConfiguration();

        acceptExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        consumerExecutor = new ThreadPoolExecutor(1, getConfiguration().getMaxConcurrentConsumers(), getConfiguration().getAcceptTimeout(), TimeUnit.MILLISECONDS, new SynchronousQueue<>());
    }

    @ManagedAttribute(description = "Last activity time")
    public Map<String, Date> getLastActivityTimes() {
        Map<String, Date> answer = new HashMap<>();

        for (Map.Entry<TcpSocketConsumerRunnable, Long> entry : consumerRunnables.entrySet()) {
            TcpSocketConsumerRunnable consumerRunnable = entry.getKey();
            if (consumerRunnable != null) {
                answer.put(consumerRunnable.getCombinedAddress(), new Date(entry.getValue()));
            }
        }
        return answer;
    }

    @ManagedOperation(description = "Close Connections")
    public void closeConnections() {

        for (TcpSocketConsumerRunnable consumerRunnable : consumerRunnables.keySet()) {
            if (consumerRunnable != null) {
                log.info("Close Connection called via JMX for address {}", consumerRunnable.getCombinedAddress());
                consumerRunnable.closeSocket();
            }
        }
    }

    @ManagedOperation(description = "Reset Connections")
    public void resetConnections() {

        for (TcpSocketConsumerRunnable consumerRunnable : consumerRunnables.keySet()) {
            if (consumerRunnable != null) {
                log.info("Reset Connection called via JMX for address {}", consumerRunnable.getCombinedAddress());
                consumerRunnable.resetSocket();
            }
        }
    }

    @Override
    public MllpEndpoint getEndpoint() {
        return (MllpEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

        // Close any client sockets that are currently open
        for (TcpSocketConsumerRunnable consumerClientSocketThread : consumerRunnables.keySet()) {
            consumerClientSocketThread.stop();
        }

        acceptRunnable.stop();

        acceptRunnable = null;

        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("doStart() - starting acceptor");

        ServerSocket serverSocket = new ServerSocket();
        if (getConfiguration().hasReceiveBufferSize()) {
            serverSocket.setReceiveBufferSize(getConfiguration().getReceiveBufferSize());
        }

        if (getConfiguration().hasReuseAddress()) {
            serverSocket.setReuseAddress(getConfiguration().getReuseAddress());
        }

        // Accept Timeout
        serverSocket.setSoTimeout(getConfiguration().getAcceptTimeout());

        InetSocketAddress socketAddress;
        if (null == getEndpoint().getHostname()) {
            socketAddress = new InetSocketAddress(getEndpoint().getPort());
        } else {
            socketAddress = new InetSocketAddress(getEndpoint().getHostname(), getEndpoint().getPort());
        }
        long startTicks = System.currentTimeMillis();

        // Log usage of deprecated URI options
        if (getConfiguration().hasMaxReceiveTimeouts()) {
            if (getConfiguration().hasIdleTimeout()) {
                log.info("Both maxReceivedTimeouts {} and idleTimeout {} URI options are specified - idleTimeout will be used",
                    getConfiguration().getMaxReceiveTimeouts(), getConfiguration().getIdleTimeout());
            } else {
                getConfiguration().setIdleTimeout(getConfiguration().getMaxReceiveTimeouts() * getConfiguration().getReceiveTimeout());
                log.info("Deprecated URI option maxReceivedTimeouts {} specified - idleTimeout {} will be used", getConfiguration().getMaxReceiveTimeouts(), getConfiguration().getIdleTimeout());
            }
        }

        do {
            try {
                if (getConfiguration().hasBacklog()) {
                    serverSocket.bind(socketAddress, getConfiguration().getBacklog());
                } else {
                    serverSocket.bind(socketAddress);
                }
            } catch (BindException bindException) {
                if (System.currentTimeMillis() > startTicks + getConfiguration().getBindTimeout()) {
                    log.error("Failed to bind to address {} within timeout {}", socketAddress, getConfiguration().getBindTimeout());
                    throw bindException;
                } else {
                    log.warn("Failed to bind to address {} - retrying in {} milliseconds", socketAddress, getConfiguration().getBindRetryInterval());
                    Thread.sleep(getConfiguration().getBindRetryInterval());
                }
            }
        } while (!serverSocket.isBound());

        acceptRunnable = new TcpServerAcceptRunnable(this, serverSocket);
        acceptExecutor.submit(acceptRunnable);

        super.doStart();
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        consumerExecutor.shutdownNow();
        acceptExecutor.shutdownNow();
    }

    public MllpConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    public Map<TcpSocketConsumerRunnable, Long> getConsumerRunnables() {
        return consumerRunnables;
    }

    public void startConsumer(Socket clientSocket) {
        TcpSocketConsumerRunnable client = new TcpSocketConsumerRunnable(this, clientSocket);

        consumerRunnables.put(client, System.currentTimeMillis());
        try {
            log.info("Starting consumer for Socket {}", clientSocket);
            consumerExecutor.submit(client);
        } catch (RejectedExecutionException rejectedExecutionEx) {
            log.warn("Cannot start consumer - max consumers already active");
            getEndpoint().doConnectionClose(clientSocket, true, null);
        }
    }

}

