/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mina;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.support.BaseIoConnector;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.SocketAddress;
import java.io.IOException;

/**
 * @version $Revision$
 */
public class MinaEndpoint extends DefaultEndpoint<MinaExchange> {
    private static final transient Log log = LogFactory.getLog(MinaEndpoint.class);

    private IoSession session;
    private IoHandler serverHandler;
    private IoHandler clientHandler;
    private final IoAcceptor acceptor;
    private final SocketAddress address;
    private final IoConnector connector;


    public MinaEndpoint(String endpointUri, CamelContext container, SocketAddress address, IoAcceptor acceptor, IoConnector connector) {
        super(endpointUri, container);
        this.address = address;
        this.acceptor = acceptor;
        this.connector = connector;
    }

    public void onExchange(MinaExchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            System.out.println("#### No payload for exchange: " + exchange);
        }
        getSession().write(body);
    }

    public MinaExchange createExchange() {
        return new MinaExchange(getContext());
    }

    public MinaExchange createExchange(IoSession session, Object object) {
        MinaExchange exchange = new MinaExchange(getContext());
        exchange.getIn().setBody(object);
        // TODO store session in exchange?
        return exchange;
    }

    public IoHandler getServerHandler() {
        if (serverHandler == null) {
            serverHandler = createServerHandler();
        }
        return serverHandler;
    }

    public IoHandler getClientHandler() {
        if (clientHandler == null) {
            clientHandler = createClientHandler();
        }
        return clientHandler;
    }

    public IoSession getSession() {
        // TODO lazy create if no inbound processor attached?
        return session;
    }

    public void setSession(IoSession session) {
        this.session = session;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void doActivate() throws Exception {
        super.doActivate();

        if (getInboundProcessor() != null) {
            // lets initiate the server

            if (log.isDebugEnabled()) {
                log.debug("Binding to server address: " + address + " using acceptor: " + acceptor);            
            }

            acceptor.bind(address, getServerHandler());
        }
        setSession(createSession());
    }

    /**
     * Initiates the client connection for outbound communication
     */
    protected IoSession createSession() {
        if (log.isDebugEnabled()) {
            log.debug("Creating connector to address: " + address + " using connector: " + connector);
        }
        ConnectFuture future = connector.connect(address, getClientHandler());
        future.join();
        return future.getSession();
    }


    @Override
    protected void doDeactivate() {
        acceptor.unbindAll();
    }

    protected IoHandler createClientHandler() {
        return new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession ioSession, Object object) throws Exception {
                super.messageReceived(ioSession, object);    /** TODO */
            }
        };
    }

    protected IoHandler createServerHandler() {
        return new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object object) throws Exception {
                processInboundMessage(session, object);
            }
        };
    }

    private void processInboundMessage(IoSession session, Object object) {
        getInboundProcessor().onExchange(createExchange(session, object));
    }
}
