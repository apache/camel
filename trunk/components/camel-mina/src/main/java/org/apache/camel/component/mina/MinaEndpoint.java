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
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
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
 * @version 
 */
public class MinaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    /** The key of the IoSession which is stored in the message header*/
    @Deprecated
    public static final transient String HEADER_MINA_IOSESSION = "CamelMinaIoSession";
    /** The socket address of local machine that received the message. */
    @Deprecated
    public static final transient String HEADER_LOCAL_ADDRESS = "CamelMinaLocalAddress";
    /** The socket address of the remote machine that send the message. */
    @Deprecated
    public static final transient String HEADER_REMOTE_ADDRESS = "CamelMinaRemoteAddress";

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

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration"); 
        ObjectHelper.notNull(address, "address");
        ObjectHelper.notNull(connector, "connector");
        // wm protocol does not have config
        if (!configuration.getProtocol().equalsIgnoreCase("vm")) {
            ObjectHelper.notNull(connectorConfig, "connectorConfig");
        }
        return new MinaProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(address, "address");
        ObjectHelper.notNull(acceptor, "acceptor");
        // wm protocol does not have config
        if (!configuration.getProtocol().equalsIgnoreCase("vm")) {
            ObjectHelper.notNull(acceptorConfig, "acceptorConfig");
        }
        return new MinaConsumer(this, processor);
    }

    public Exchange createExchange(IoSession session, Object payload) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(MinaConstants.MINA_IOSESSION, session);
        exchange.getIn().setHeader(MinaConstants.MINA_LOCAL_ADDRESS, session.getLocalAddress());
        exchange.getIn().setHeader(MinaConstants.MINA_REMOTE_ADDRESS, session.getRemoteAddress());
        MinaPayloadHelper.setIn(exchange, payload);
        return exchange;
    }

    public boolean isSingleton() {
        return true;
    }

    public boolean isMultipleConsumersSupported() {
        // only datagram should allow multiple consumers
        return configuration.isDatagramProtocol();
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
