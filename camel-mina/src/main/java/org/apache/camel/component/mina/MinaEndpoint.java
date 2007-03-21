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
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoAcceptor;

/**
 * @version $Revision$
 */
public class MinaEndpoint extends DefaultEndpoint<MinaExchange> {
    private IoSession session;
    private IoHandler serverHandler;
    private IoHandler clientHandler;
    private final IoAcceptor acceptor;

    public MinaEndpoint(String endpointUri, CamelContext container, IoAcceptor acceptor) {
        super(endpointUri, container);
        this.acceptor = acceptor;
    }

    public void onExchange(MinaExchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            System.out.println("#### No payload for exchange: " + exchange);
        }
        session.write(body);
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
        return session;
    }

    public void setSession(IoSession session) {
        this.session = session;
    }


    // Implementation methods
    //-------------------------------------------------------------------------

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
