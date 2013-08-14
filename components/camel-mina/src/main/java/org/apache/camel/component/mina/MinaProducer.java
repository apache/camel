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
import org.apache.camel.ServicePoolAware;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Producer} implementation for MINA
 *
 * @version 
 */
public class MinaProducer extends DefaultProducer implements ServicePoolAware {
    private static final Logger LOG = LoggerFactory.getLogger(MinaProducer.class);
    private IoSession session;
    private CountDownLatch latch;
    private boolean lazySessionCreation;
    private long timeout;
    private IoConnector connector;
    private boolean sync;
    private CamelLogger noReplyLogger;

    public MinaProducer(MinaEndpoint endpoint) {
        super(endpoint);
        this.lazySessionCreation = endpoint.getConfiguration().isLazySessionCreation();
        this.timeout = endpoint.getConfiguration().getTimeout();
        this.sync = endpoint.getConfiguration().isSync();
        this.noReplyLogger = new CamelLogger(LOG, endpoint.getConfiguration().getNoReplyLogLevel());
    }
    
    @Override
    public MinaEndpoint getEndpoint() {
        return (MinaEndpoint) super.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return false;
    }

    public void process(Exchange exchange) throws Exception {
        try {
            doProcess(exchange);
        } finally {
            // ensure we always disconnect if configured
            maybeDisconnectOnDone(exchange);
        }
    }

    protected void doProcess(Exchange exchange) throws Exception {
        if (session == null && !lazySessionCreation) {
            throw new IllegalStateException("Not started yet!");
        }
        if (session == null || !session.isConnected()) {
            openConnection();
        }

        // set the exchange encoding property
        if (getEndpoint().getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(getEndpoint().getConfiguration().getCharsetName()));
        }

        Object body = MinaPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            noReplyLogger.log("No payload to send for exchange: " + exchange);
            return; // exit early since nothing to write
        }

        // if textline enabled then covert to a String which must be used for textline
        if (getEndpoint().getConfiguration().isTextline()) {
            body = getEndpoint().getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, body);
        }

        // if sync is true then we should also wait for a response (synchronous mode)
        if (sync) {
            // only initialize latch if we should get a response
            latch = new CountDownLatch(1);
            // reset handler if we expect a response
            ResponseHandler handler = (ResponseHandler) session.getHandler();
            handler.reset();
        }

        // log what we are writing
        if (LOG.isDebugEnabled()) {
            Object out = body;
            if (body instanceof byte[]) {
                // byte arrays is not readable so convert to string
                out = exchange.getContext().getTypeConverter().convertTo(String.class, body);
            }
            LOG.debug("Writing body : {}", out);
        }
        // write the body
        MinaHelper.writeBody(session, body, exchange);

        if (sync) {
            // wait for response, consider timeout
            LOG.debug("Waiting for response using timeout {} millis.", timeout);
            boolean done = latch.await(timeout, TimeUnit.MILLISECONDS);
            if (!done) {
                throw new ExchangeTimedOutException(exchange, timeout);
            }

            // did we get a response
            ResponseHandler handler = (ResponseHandler) session.getHandler();
            if (handler.getCause() != null) {
                throw new CamelExchangeException("Error occurred in ResponseHandler", exchange, handler.getCause());
            } else if (!handler.isMessageReceived()) {
                // no message received
                throw new CamelExchangeException("No response received from remote server: " + getEndpoint().getEndpointUri(), exchange);
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

    protected void maybeDisconnectOnDone(Exchange exchange) {
        if (session == null) {
            return;
        }

        // should session be closed after complete?
        Boolean close;
        if (ExchangeHelper.isOutCapable(exchange)) {
            close = exchange.getOut().getHeader(MinaConstants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
        } else {
            close = exchange.getIn().getHeader(MinaConstants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = getEndpoint().getConfiguration().isDisconnect();
        if (close != null) {
            disconnect = close;
        }
        if (disconnect) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing session when complete at address: {}", getEndpoint().getAddress());
            }
            session.close();
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
            LOG.debug("Stopping connector: {} at address: {}", connector, getEndpoint().getAddress());
        }
        closeConnection();
        super.doStop();
    }

    private void closeConnection() {
        if (connector instanceof SocketConnector) {
            // Change the worker timeout to 0 second to make the I/O thread quit soon when there's no connection to manage.
            // Default worker timeout is 60 sec and therefore the client using MinaProducer cannot terminate the JVM
            // asap but must wait for the timeout to happen, so to speed this up we set the timeout to 0.
            LOG.trace("Setting SocketConnector WorkerTimeout=0 to force MINA stopping its resources faster");
            ((SocketConnector) connector).setWorkerTimeout(0);
        }

        if (session != null) {
            session.close();
        }
    }

    private void openConnection() {
        SocketAddress address = getEndpoint().getAddress();
        connector = getEndpoint().getConnector();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: {} using connector: {} timeout: {} millis.", new Object[]{address, connector, timeout});
        }
        IoHandler ioHandler = new ResponseHandler(getEndpoint());
        // connect and wait until the connection is established
        ConnectFuture future = connector.connect(address, ioHandler, getEndpoint().getConnectorConfig());
        future.join();
        session = future.getSession();
    }

    /**
     * Handles response from session writes
     */
    private final class ResponseHandler extends IoHandlerAdapter {
        private MinaEndpoint endpoint;
        private Object message;
        private Throwable cause;
        private boolean messageReceived;

        private ResponseHandler(MinaEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        public void reset() {
            this.message = null;
            this.cause = null;
            this.messageReceived = false;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            LOG.debug("Message received: {}", message);
            this.message = message;
            messageReceived = true;
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
            if (sync && !messageReceived) {
                // sync=true (InOut mode) so we expected a message as reply but did not get one before the session is closed
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Session closed but no message received from address: {}", this.endpoint.getAddress());
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
            this.messageReceived = false;
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

        public boolean isMessageReceived() {
            return messageReceived;
        }
    }

}
