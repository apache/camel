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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.SocketConnector;

/**
 * A {@link Producer} implementation for MINA
 *
 * @version $Revision$
 */
public class MinaProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(MinaProducer.class);
    private IoSession session;
    private MinaEndpoint endpoint;
    private CountDownLatch latch;
    private boolean lazySessionCreation;
    private long timeout;
    private IoConnector connector;
    private boolean sync;

    public MinaProducer(MinaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.lazySessionCreation = endpoint.isLazySessionCreation();
        this.timeout = endpoint.getTimeout();
        this.sync = endpoint.isSync();
    }

    public void process(Exchange exchange) throws Exception {
        if (session == null && !lazySessionCreation) {
            throw new IllegalStateException("Not started yet!");
        }
        if (session == null || !session.isConnected()) {
            openConnection();
        }

        // set the exchange encoding property
        if (endpoint.getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, endpoint.getCharsetName());
        }

        Object body = MinaPayloadHelper.getIn(endpoint, exchange);
        if (body == null) {
            LOG.warn("No payload to send for exchange: " + exchange);
            return; // exit early since nothing to write
        }

        // if sync is true then we should also wait for a response (synchronous mode)
        if (sync) {
            // only initialize latch if we should get a response
            latch = new CountDownLatch(1);
            // reset handler if we expect a response
            ResponseHandler handler = (ResponseHandler) session.getHandler();
            handler.reset();
        }

        // write the body
        if (LOG.isDebugEnabled()) {
            LOG.debug("Writing body: " + body);
        }
        MinaHelper.writeBody(session, body, exchange);

        if (sync) {
            // wait for response, consider timeout
            LOG.debug("Waiting for response");
            latch.await(timeout, TimeUnit.MILLISECONDS);
            if (latch.getCount() == 1) {
                throw new ExchangeTimedOutException(exchange, timeout);
            }

            // did we get a response
            ResponseHandler handler = (ResponseHandler) session.getHandler();
            if (handler.getCause() != null) {
                throw new CamelExchangeException("Response Handler had an exception", exchange, handler.getCause());
            } else if (!handler.isMessageRecieved()) {
                // no message received
                throw new CamelExchangeException("No response received from remote server: " + endpoint.getEndpointUri(), exchange);
            } else {
                // set the result on either IN or OUT on the original exchange depending on its pattern
                if (ExchangeHelper.isOutCapable(exchange)) {
                    MinaPayloadHelper.setOut(exchange, handler.getMessage());
                } else {
                    MinaPayloadHelper.setIn(exchange, handler.getMessage());
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!lazySessionCreation) {
            openConnection();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping connector: " + connector + " at address: " + endpoint.getAddress());
        }

        if (connector instanceof SocketConnector) {
            // Change the worker timeout to 0 second to make the I/O thread quit soon when there's no connection to manage.
            // Default worker timeout is 60 sec and therefore the client using MinaProducer can not terminate the JVM
            // asap but must wait for the timeout to happend, so to speed this up we set the timeout to 0.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting SocketConnector WorkerTimeout=0 to force MINA stopping its resources faster");
            }
            ((SocketConnector) connector).setWorkerTimeout(0);
        }

        if (session != null) {
            session.close();
        }

        super.doStop();
    }

    private void openConnection() {
        SocketAddress address = endpoint.getAddress();
        connector = endpoint.getConnector();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: " + address + " using connector: " + connector + " timeout: " + timeout + " millis.");
        }
        IoHandler ioHandler = new ResponseHandler(endpoint);
        // connect and wait until the connection is established
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
        private boolean messageRecieved;

        private ResponseHandler(MinaEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        public void reset() {
            this.message = null;
            this.cause = null;
            this.messageRecieved = false;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Message received: " + message);
            }
            this.message = message;
            messageRecieved = true;
            cause = null;
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
            if (sync && message == null) {
                // sync=true (InOut mode) so we expected a message as reply but did not get one before the session is closed
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Session closed but no message received from address: " + this.endpoint.getAddress());
                }
                // session was closed but no message received. This could be because the remote server had an internal error
                // and could not return a response. We should count down to stop waiting for a response
                countDown();
            }
        }

        @Override
        public void exceptionCaught(IoSession ioSession, Throwable cause) {
            LOG.error("Exception on receiving message from address: " + this.endpoint.getAddress()
                    + " using connector: " + this.endpoint.getConnector(), cause);
            this.message = null;
            this.messageRecieved = false;
            this.cause = cause;
            if (ioSession != null) {
                ioSession.close();
            }
        }

        public Throwable getCause() {
            return this.cause;
        }

        public Object getMessage() {
            return this.message;
        }

        public boolean isMessageRecieved() {
            return messageRecieved;
        }
    }

}
