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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.component.mllp.internal.TcpServerAcceptThread;
import org.apache.camel.component.mllp.internal.TcpServerBindThread;
import org.apache.camel.component.mllp.internal.TcpServerConsumerValidationRunnable;
import org.apache.camel.component.mllp.internal.TcpSocketConsumerRunnable;
import org.apache.camel.impl.DefaultConsumer;

/**
 * The MLLP consumer.
 */
@ManagedResource(description = "MLLP Producer")
public class MllpTcpServerConsumer extends DefaultConsumer {
    final ExecutorService validationExecutor;
    final ExecutorService consumerExecutor;

    TcpServerBindThread bindThread;
    TcpServerAcceptThread acceptThread;

    Map<TcpSocketConsumerRunnable, Long> consumerRunnables = new ConcurrentHashMap<>();


    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");

        validationExecutor = Executors.newCachedThreadPool();
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

        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }

        if (bindThread != null) {
            bindThread.interrupt();
            bindThread = null;
        }

        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        if (bindThread == null || !bindThread.isAlive()) {
            bindThread = new TcpServerBindThread(this);

            if (getConfiguration().isLenientBind()) {
                log.debug("doStart() - starting bind thread");
                bindThread.start();
            } else {
                log.debug("doStart() - attempting to bind to port {}", getEndpoint().getPort());
                bindThread.run();

                if (this.acceptThread == null) {
                    throw new BindException("Failed to bind to port " + getEndpoint().getPort());
                }
            }
        }

        super.doStart();
    }

    @Override
    public void handleException(Throwable t) {
        super.handleException(t);
    }

    @Override
    public void handleException(String message, Throwable t) {
        super.handleException(message, t);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        consumerExecutor.shutdownNow();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        validationExecutor.shutdownNow();
    }

    public MllpConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    public Map<TcpSocketConsumerRunnable, Long> getConsumerRunnables() {
        return consumerRunnables;
    }

    public void validateConsumer(Socket clientSocket) {
        MllpSocketBuffer mllpBuffer = new MllpSocketBuffer(getEndpoint());
        TcpServerConsumerValidationRunnable client = new TcpServerConsumerValidationRunnable(this, clientSocket, mllpBuffer);

        try {
            log.info("Validating consumer for Socket {}", clientSocket);
            validationExecutor.submit(client);
        } catch (RejectedExecutionException rejectedExecutionEx) {
            log.warn("Cannot validate consumer - max validations already active");
            mllpBuffer.resetSocket(clientSocket);
        }
    }

    public void startAcceptThread(ServerSocket serverSocket) {
        acceptThread = new TcpServerAcceptThread(this, serverSocket);
        acceptThread.start();
    }

    public void startConsumer(Socket clientSocket, MllpSocketBuffer mllpBuffer) {
        TcpSocketConsumerRunnable client = new TcpSocketConsumerRunnable(this, clientSocket, mllpBuffer);

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
