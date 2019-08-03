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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Thread to handle the ServerSocket.accept requests, and submit the sockets to the accept executor for validation.
 */
public class TcpServerAcceptThread extends Thread {
    Logger log = LoggerFactory.getLogger(this.getClass());

    MllpTcpServerConsumer consumer;
    ServerSocket serverSocket;
    boolean running;

    public TcpServerAcceptThread(MllpTcpServerConsumer consumer, ServerSocket serverSocket) {
        this.consumer = consumer;
        this.serverSocket = serverSocket;
    }

    /**
     * Derive a thread name from the class name, the component URI and the connection information.
     * <p/>
     * The String will in the format <class name>[endpoint key] - [local socket address]
     *
     * @return String for thread name
     */
    String createThreadName(ServerSocket serverSocket) {
        // Get the classname without the package.  This is a nested class, so we want the parent class name included
        String fullClassName = this.getClass().getName();
        String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

        // Get the URI without options
        String fullEndpointKey = consumer.getEndpoint().getEndpointKey();
        String endpointKey;
        if (fullEndpointKey.contains("?")) {
            endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
        } else {
            endpointKey = fullEndpointKey;
        }

        // Now put it all together
        return String.format("%s[%s] - %s", className, endpointKey, serverSocket.getLocalSocketAddress());
    }

    /**
     * The main ServerSocket.accept() loop
     * <p/>
     * NOTE:  When a connection is received, the Socket is checked after a brief delay in an attempt to determine if this is a load-balancer probe.  The test is done before the
     * ConsumerClientSocketThread is created to avoid creating a large number of short lived threads, which is what can occur if the load balancer polling interval is very short.
     */
    @Override
    public void run() {
        running = true;
        String originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(createThreadName(serverSocket));

        MDC.put(UnitOfWork.MDC_CAMEL_CONTEXT_ID, consumer.getEndpoint().getCamelContext().getName());

        Route route = consumer.getRoute();
        if (route != null) {
            String routeId = route.getId();
            if (routeId != null) {
                MDC.put(UnitOfWork.MDC_ROUTE_ID, route.getId());
            }
        }

        log.info("Starting ServerSocket accept thread for {}", serverSocket);
        try {
            while (running && null != serverSocket && serverSocket.isBound() && !serverSocket.isClosed()) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException timeoutEx) {
                    // Didn't get a new connection - keep waiting for one
                    log.debug("Timeout waiting for client connection - keep listening");
                    continue;
                } catch (SocketException socketEx) {
                    // This should happen if the component is closed while the accept call is blocking
                    if (serverSocket.isBound()) {
                        try {
                            serverSocket.close();
                        } catch (Exception ex) {
                            log.debug("Exception encountered closing ServerSocket after SocketException on accept() - ignoring", ex);
                        }
                    }
                    continue;
                } catch (IOException ioEx) {
                    log.error("Exception encountered accepting connection - closing ServerSocket", ioEx);
                    if (serverSocket.isBound()) {
                        try {
                            serverSocket.close();
                        } catch (Exception ex) {
                            log.debug("Exception encountered closing ServerSocket after exception on accept() - ignoring", ex);
                        }
                    }
                    continue;
                }

                if (MllpSocketBuffer.isConnectionValid(socket)) {
                    // Try and avoid starting client threads for things like security scans and load balancer probes
                    consumer.validateConsumer(socket);
                }
            }
        } finally {
            log.info("ServerSocket.accept loop finished - closing listener");
            if (null != serverSocket && serverSocket.isBound() && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (Exception ex) {
                    log.debug("Exception encountered closing ServerSocket after accept loop had exited - ignoring", ex);
                }
            }
            Thread.currentThread().setName(originalThreadName);
            MDC.remove(UnitOfWork.MDC_ROUTE_ID);
            MDC.remove(UnitOfWork.MDC_CAMEL_CONTEXT_ID);
        }
    }

    @Override
    public void interrupt() {
        this.running = false;
        super.interrupt();
        if (null != serverSocket) {
            if (serverSocket.isBound()) {
                try {
                    serverSocket.close();
                } catch (IOException ioEx) {
                    log.warn("Exception encountered closing ServerSocket in interrupt() method - ignoring", ioEx);
                }
            }
        }
    }
}
