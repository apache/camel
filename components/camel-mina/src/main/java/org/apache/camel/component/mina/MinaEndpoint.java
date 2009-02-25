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
import org.apache.camel.util.ObjectHelper;
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

    private SocketAddress address;
    private IoAcceptor acceptor;
    private IoConnector connector;
    private IoAcceptorConfig acceptorConfig;
    private IoConnectorConfig connectorConfig;
    private MinaConfiguration configuration;

    public MinaEndpoint() {
    }

    public MinaEndpoint(String endpointUri, MinaComponent component) {
        super(endpointUri, component);
    }


    @SuppressWarnings({"unchecked"})
    public Producer<MinaExchange> createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration"); 
        ObjectHelper.notNull(address, "address");
        ObjectHelper.notNull(connector, "connector");
        // wm protocol does not have config
        if (!configuration.getProtocol().equalsIgnoreCase("vm")) {
            ObjectHelper.notNull(connectorConfig, "connectorConfig");
        }
        return new MinaProducer(this);
    }

    public Consumer<MinaExchange> createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(address, "address");
        ObjectHelper.notNull(acceptor, "acceptor");
        // wm protocol does not have config
        if (!configuration.getProtocol().equalsIgnoreCase("vm")) {
            ObjectHelper.notNull(acceptorConfig, "acceptorConfig");
        }
        return new MinaConsumer(this, processor);
    }

    
    public MinaExchange createExchange(ExchangePattern pattern) {
        return new MinaExchange(getCamelContext(), pattern, null);
    }

    public MinaExchange createExchange(IoSession session, Object payload) {
        MinaExchange exchange = new MinaExchange(getCamelContext(), getExchangePattern(), session);
        MinaPayloadHelper.setIn(exchange, payload);
        return exchange;
    }

    public boolean isSingleton() {
        return true;
    }

    // Properties
    // -------------------------------------------------------------------------

    public MinaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public void setAddress(SocketAddress address) {
        this.address = address;
    }

    public IoAcceptor getAcceptor() {
        return acceptor;
    }

    public void setAcceptor(IoAcceptor acceptor) {
        this.acceptor = acceptor;
    }

    public IoConnector getConnector() {
        return connector;
    }

    public void setConnector(IoConnector connector) {
        this.connector = connector;
    }

    public IoAcceptorConfig getAcceptorConfig() {
        return acceptorConfig;
    }

    public void setAcceptorConfig(IoAcceptorConfig acceptorConfig) {
        this.acceptorConfig = acceptorConfig;
    }

    public IoConnectorConfig getConnectorConfig() {
        return connectorConfig;
    }

    public void setConnectorConfig(IoConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }
}
