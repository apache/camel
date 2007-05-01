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

import org.apache.camel.Producer;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import java.net.SocketAddress;

/**
 * A {@link Producer} implementation for MINA
 *
 * @version $Revision$
 */
public class MinaProducer extends DefaultProducer {
    private static final transient Log log = LogFactory.getLog(MinaProducer.class);
    private IoSession session;
    private MinaEndpoint endpoint;

    public MinaProducer(MinaEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) {
        if (session == null) {
            throw new IllegalStateException("Not started yet!");
        }
        Object body = exchange.getIn().getBody();
        if (body == null) {
            log.warn("No payload for exchange: " + exchange);
        }
        else {
            session.write(body);
        }
    }

    @Override
    protected void doStart() throws Exception {
        SocketAddress address = endpoint.getAddress();
        IoConnector connector = endpoint.getConnector();
        if (log.isDebugEnabled()) {
            log.debug("Creating connector to address: " + address + " using connector: " + connector);
        }
        IoHandler ioHandler = new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession ioSession, Object object) throws Exception {
                super.messageReceived(ioSession, object);    /** TODO */
            }
        };
        ConnectFuture future = connector.connect(address, ioHandler, endpoint.getConfig());
        future.join();
        session = future.getSession();
    }

    @Override
    protected void doStop() throws Exception {
        if (session != null) {
            session.close().join(2000);
        }
    }
}
