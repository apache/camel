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
package org.apache.camel.component.mina;

import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.CamelException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;

/**
 * A {@link Producer} implementation for MINA
 *
 * @version $Revision$
 */
public class MinaProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(MinaProducer.class);
    // TODO: The max wait response should be configurable
    // The URI parameter could be a option
    private static final long MAX_WAIT_RESPONSE = 30000;
    private IoSession session;
    private MinaEndpoint endpoint;
    private CountDownLatch latch;
    private boolean lazySessionCreation;

    public MinaProducer(MinaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.lazySessionCreation = this.endpoint.getLazySessionCreation();
    }

    public void process(Exchange exchange) throws Exception {
        if (session == null && !lazySessionCreation) {
            throw new IllegalStateException("Not started yet!");
        }
        if (session == null || !session.isConnected()) {
            openConnection();
        }

        Object body = exchange.getIn().getBody();
        if (body == null) {
            LOG.warn("No payload for exchange: " + exchange);
        } else {
            if (ExchangeHelper.isOutCapable(exchange)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Writing body: " + body);
                }

                // write the body
                latch = new CountDownLatch(1);
                WriteFuture future = session.write(body);
                future.join();
                if (!future.isWritten()) {
                    throw new ExchangeTimedOutException(exchange, MAX_WAIT_RESPONSE);
                }

                // wait for response, consider timeout
                latch.await(MAX_WAIT_RESPONSE, TimeUnit.MILLISECONDS);
                if (latch.getCount() == 1) {
                    throw new ExchangeTimedOutException(exchange, MAX_WAIT_RESPONSE);
                }

                // did we get a response
                ResponseHandler handler = (ResponseHandler) session.getHandler();
                if (handler.getCause() != null) {
                    throw new CamelException("Response Handler had an exception", handler.getCause());
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Handler message: " + handler.getMessage());
                    }
                    exchange.getOut().setBody(handler.getMessage());
                }
            } else {
                session.write(body);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (!lazySessionCreation) {
            openConnection();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (session != null) {
            session.close().join(2000);
        }
    }

    private void openConnection() {
        SocketAddress address = endpoint.getAddress();
        IoConnector connector = endpoint.getConnector();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: " + address + " using connector: " + connector);
        }
        IoHandler ioHandler = new ResponseHandler(endpoint);
        ConnectFuture future = connector.connect(address, ioHandler, endpoint.getConnectorConfig());
        future.join();
        session = future.getSession();
    }

    /**
     * Handles response from session writes
     *
     * @author <a href="mailto:karajdaar@gmail.com">nsandhu</a>
     */
    private final class ResponseHandler extends IoHandlerAdapter {
        private MinaEndpoint endpoint;
        private Object message;
        private Throwable cause;

        private ResponseHandler(MinaEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Message received: " + message);
            }
            cause = null;
            this.message = message;
            countDown();
        }

        protected void countDown() {
            CountDownLatch downLatch = latch;
            if (downLatch != null) {
                downLatch.countDown();
            }
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Session closed");
            }

            if (message == null) {
                // session was closed but no message received. This is because the remote server had an internal error
                // and could not return a proper response. We should count down to stop waiting for a response
                countDown();
            }
        }

        @Override
        public void exceptionCaught(IoSession ioSession, Throwable cause) {
            LOG.error("Exception on receiving message from address: " + this.endpoint.getAddress()
                    + " using connector: " + this.endpoint.getConnector(), cause);
            this.message = null;
            this.cause = cause;
            if (ioSession != null) {
                ioSession.close();
            }
            countDown();
        }

        public Throwable getCause() {
            return this.cause;
        }

        public Object getMessage() {
            return this.message;
        }
    }

}
