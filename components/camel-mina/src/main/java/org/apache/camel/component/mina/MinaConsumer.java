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
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

/**
 * A {@link org.apache.camel.Consumer Consumer} implementation for Apache MINA.
 *
 * @version $Revision$
 */
public class MinaConsumer extends DefaultConsumer<MinaExchange> {
    public static final transient String HEADER_CLOSE_SESSION_WHEN_COMPLETE = "CamelMinaCloseSessionWhenComplete";

    private static final transient Log LOG = LogFactory.getLog(MinaConsumer.class);

    private final MinaEndpoint endpoint;
    private final SocketAddress address;
    private final IoAcceptor acceptor;
    private boolean sync;

    public MinaConsumer(final MinaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.address = endpoint.getAddress();
        this.acceptor = endpoint.getAcceptor();
        this.sync = endpoint.getConfiguration().isSync();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (LOG.isInfoEnabled()) {
            LOG.info("Binding to server address: " + address + " using acceptor: " + acceptor);
        }

        IoHandler handler = new ReceiveHandler();
        acceptor.bind(address, handler, endpoint.getAcceptorConfig());
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Unbinding from server address: " + address + " using acceptor: " + acceptor);
        }
        acceptor.unbind(address);
        super.doStop();
    }

    /**
     * Handles comsuming messages and replying if the exchange is out capable.
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
                    in = endpoint.getCamelContext().getTypeConverter().convertTo(String.class, in);
                }
                LOG.debug("Received body: " + in);
            }

            MinaExchange exchange = endpoint.createExchange(session, object);
            //Set the exchange charset property for converting
            if (endpoint.getConfiguration().getCharsetName() != null) {
                exchange.setProperty(Exchange.CHARSET_NAME, endpoint.getConfiguration().getCharsetName());
            }
            getProcessor().process(exchange);

            // if sync then we should return a response
            if (sync) {
                Object body;
                if (ExchangeHelper.isOutCapable(exchange)) {
                    body = MinaPayloadHelper.getOut(endpoint, exchange);
                } else {
                    body = MinaPayloadHelper.getIn(endpoint, exchange);
                }

                boolean failed = exchange.isFailed();
                if (failed && !endpoint.getConfiguration().isTransferExchange()) {
                    if (exchange.getException() != null) {
                        body = exchange.getException();
                    } else {
                        body = exchange.getFault().getBody();
                    }
                }

                if (body == null) {
                    // must close session if no data to write otherwise client will never receive a response
                    // and wait forever (if not timing out)
                    LOG.warn("Can not write body since its null, closing session: " + exchange);
                    session.close();
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
                close = exchange.getOut().getHeader(HEADER_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
            } else {
                close = exchange.getIn().getHeader(HEADER_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
            }

            if (close != null && close) {
                LOG.debug("Closing session when complete");
                session.close();
            }
        }

    }

}
