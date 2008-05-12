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

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoAcceptorConfig;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoConnectorConfig;
import org.apache.mina.common.IoSession;

/**
 * Endpoint for Camel MINA.
 *
 * @version $Revision$
 */
public class MinaEndpoint extends DefaultEndpoint<MinaExchange> {

    private static final long DEFAULT_TIMEOUT = 30000;
    private long timeout = DEFAULT_TIMEOUT;

    private final IoAcceptor acceptor;
    private final SocketAddress address;
    private final IoConnector connector;
    private final IoAcceptorConfig acceptorConfig;
    private final IoConnectorConfig connectorConfig;
    private final boolean lazySessionCreation;
    private final boolean transferExchange;

    public MinaEndpoint(String endpointUri, MinaComponent component, SocketAddress address,
                        IoAcceptor acceptor, IoAcceptorConfig acceptorConfig, IoConnector connector,
                        IoConnectorConfig connectorConfig, boolean lazySessionCreation, long timeout,
                        boolean transferExchange) {
        super(endpointUri, component);
        this.address = address;
        this.acceptor = acceptor;
        this.acceptorConfig = acceptorConfig;
        this.connectorConfig = connectorConfig;
        this.connector = connector;
        this.lazySessionCreation = lazySessionCreation;
        if (timeout > 0) {
            // override default timeout if provided
            this.timeout = timeout;
        }
        this.transferExchange = transferExchange;
    }

    @SuppressWarnings({"unchecked"})
    public Producer<MinaExchange> createProducer() throws Exception {
        return new MinaProducer(this);
    }

    public Consumer<MinaExchange> createConsumer(Processor processor) throws Exception {
        return new MinaConsumer(this, processor);
    }

    @Override
    public MinaExchange createExchange(ExchangePattern pattern) {
        return new MinaExchange(getCamelContext(), pattern, null);
    }

    public MinaExchange createExchange(IoSession session, Object payload) {
        MinaExchange exchange = new MinaExchange(getCamelContext(), getExchangePattern(), session);
        MinaPayloadHelper.setIn(exchange, payload);
        return exchange;
    }

    // Properties
    // -------------------------------------------------------------------------
    public IoAcceptor getAcceptor() {
        return acceptor;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public IoConnector getConnector() {
        return connector;
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    public IoAcceptorConfig getAcceptorConfig() {
        return acceptorConfig;
    }

    public IoConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public boolean isSingleton() {
        return true;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

}
