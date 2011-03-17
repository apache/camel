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

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer Consumer} implementation for Apache MINA.
 *
 * @version 
 */
public class MinaConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(MinaConsumer.class);

    private final SocketAddress address;
    private final IoAcceptor acceptor;
    private boolean sync;
    private CamelLogger noReplyLogger;

    public MinaConsumer(final MinaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.address = endpoint.getAddress();
        this.acceptor = endpoint.getAcceptor();
        this.sync = endpoint.getConfiguration().isSync();
        this.noReplyLogger = new CamelLogger(LOG, endpoint.getConfiguration().getNoReplyLogLevel());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (LOG.isInfoEnabled()) {
            LOG.info("Binding to server address: " + address + " using acceptor: " + acceptor);
        }

        IoHandler handler = new ReceiveHandler();
        acceptor.bind(address, handler, getEndpoint().getAcceptorConfig());
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Unbinding from server address: " + address + " using acceptor: " + acceptor);
        }
        acceptor.unbind(address);
        super.doStop();
    }
    
    @Override
    public MinaEndpoint getEndpoint() {
        return (MinaEndpoint) super.getEndpoint();
    }

    /**
     * Handles consuming messages and replying if the exchange is out capable.
     */
    private final class ReceiveHandler extends IoHandlerAdapter {

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            // close invalid session
            if (session != null) {
                LOG.debug("Closing session as an exception was thrown from MINA");
                session.close();
            }

            // must wrap and rethrow since cause can be of Throwable and we must only throw Exception
            throw new CamelException(cause);
        }

        @Override
        public void messageReceived(IoSession session, Object object) throws Exception {
            // log what we received
            if (LOG.isDebugEnabled()) {
                Object in = object;
                if (in instanceof byte[]) {
                    // byte arrays is not readable so convert to string
                    in = getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, in);
                }
                LOG.debug("Received body: " + in);
            }

            Exchange exchange = getEndpoint().createExchange(session, object);
            //Set the exchange charset property for converting
            if (getEndpoint().getConfiguration().getCharsetName() != null) {
                exchange.setProperty(Exchange.CHARSET_NAME, IOConverter.normalizeCharset(getEndpoint().getConfiguration().getCharsetName()));
            }

            try {
                getProcessor().process(exchange);
            } catch (Throwable e) {
                getExceptionHandler().handleException(e);
            }

            // if sync then we should return a response
            if (sync) {
                Object body;
                if (ExchangeHelper.isOutCapable(exchange)) {
                    body = MinaPayloadHelper.getOut(getEndpoint(), exchange);
                } else {
                    body = MinaPayloadHelper.getIn(getEndpoint(), exchange);
                }

                boolean failed = exchange.isFailed();
                if (failed && !getEndpoint().getConfiguration().isTransferExchange()) {
                    if (exchange.getException() != null) {
                        body = exchange.getException();
                    } else {
                        // failed and no exception, must be a fault
                        body = exchange.getOut().getBody();
                    }
                }

                if (body == null) {
                    noReplyLogger.log("No payload to send as reply for exchange: " + exchange);
                    if (getEndpoint().getConfiguration().isDisconnectOnNoReply()) {
                        // must close session if no data to write otherwise client will never receive a response
                        // and wait forever (if not timing out)
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Closing session as no payload to send as reply at address: " + address);
                        }
                        session.close();
                    }
                } else {
                    // we got a response to write
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Writing body: " + body);
                    }
                    MinaHelper.writeBody(session, body, exchange);
                }
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
                    LOG.debug("Closing session when complete at address: " + address);
                }
                session.close();
            }
        }
    }
}
