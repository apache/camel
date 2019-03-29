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
package org.apache.camel.component.mllp.internal;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runnable to handle the ServerSocket.accept requests
 */
public class TcpServerBindThread extends Thread {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final MllpTcpServerConsumer consumer;

    public TcpServerBindThread(MllpTcpServerConsumer consumer) {
        this.consumer = consumer;

        // Get the URI without options
        String fullEndpointKey = consumer.getEndpoint().getEndpointKey();
        String endpointKey;
        if (fullEndpointKey.contains("?")) {
            endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
        } else {
            endpointKey = fullEndpointKey;
        }

        this.setName(String.format("%s - %s", this.getClass().getSimpleName(), endpointKey));
    }


    /**
     * Bind the TCP ServerSocket within the specified timeout.
     */
    @Override
    public void run() {
        MDC.put(UnitOfWork.MDC_CAMEL_CONTEXT_ID, consumer.getEndpoint().getCamelContext().getName());

        Route route = consumer.getRoute();
        if (route != null) {
            String routeId = route.getId();
            if (routeId != null) {
                MDC.put(UnitOfWork.MDC_ROUTE_ID, route.getId());
            }
        }

        try {
            ServerSocket serverSocket = new ServerSocket();
            if (consumer.getConfiguration().hasReceiveBufferSize()) {
                serverSocket.setReceiveBufferSize(consumer.getConfiguration().getReceiveBufferSize());
            }

            if (consumer.getConfiguration().hasReuseAddress()) {
                serverSocket.setReuseAddress(consumer.getConfiguration().getReuseAddress());
            }

            // Accept Timeout
            serverSocket.setSoTimeout(consumer.getConfiguration().getAcceptTimeout());

            InetSocketAddress socketAddress;
            if (null == consumer.getEndpoint().getHostname()) {
                socketAddress = new InetSocketAddress(consumer.getEndpoint().getPort());
            } else {
                socketAddress = new InetSocketAddress(consumer.getEndpoint().getHostname(), consumer.getEndpoint().getPort());
            }

            log.debug("Attempting to bind to {}", socketAddress);

            long startTicks = System.currentTimeMillis();
            do {
                try {
                    if (consumer.getConfiguration().hasBacklog()) {
                        serverSocket.bind(socketAddress, consumer.getConfiguration().getBacklog());
                    } else {
                        serverSocket.bind(socketAddress);
                    }
                    consumer.startAcceptThread(serverSocket);
                } catch (BindException bindException) {
                    if (System.currentTimeMillis() > startTicks + consumer.getConfiguration().getBindTimeout()) {
                        log.error("Failed to bind to address {} within timeout {}", socketAddress, consumer.getConfiguration().getBindTimeout(), bindException);
                        break;
                    } else {
                        log.warn("Failed to bind to address {} - retrying in {} milliseconds", socketAddress, consumer.getConfiguration().getBindRetryInterval());
                        try {
                            Thread.sleep(consumer.getConfiguration().getBindRetryInterval());
                        } catch (InterruptedException interruptedEx) {
                            log.info("Bind to address {} interrupted", socketAddress, interruptedEx);
                            if (!this.isInterrupted()) {
                                super.interrupt();
                            }
                            break;
                        }
                    }
                } catch (IOException unexpectedEx) {
                    log.error("Unexpected exception encountered binding to address {}", socketAddress, unexpectedEx);
                    break;
                }
            } while (!this.isInterrupted() && !serverSocket.isBound());

        } catch (IOException ioEx) {
            log.error("Unexpected exception encountered initializing ServerSocket before attempting to bind", ioEx);
        }
    }

}
