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

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import java.net.SocketAddress;

/**
 * A @{link Consumer} for MINA
 *
 * @version $Revision$
 */
public class MinaConsumer extends DefaultConsumer<MinaExchange> {
    private static final transient Log log = LogFactory.getLog(MinaConsumer.class);

    private final MinaEndpoint endpoint;
    private final SocketAddress address;
    private final IoAcceptor acceptor;

    public MinaConsumer(final MinaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
         address = endpoint.getAddress();
         acceptor = endpoint.getAcceptor();
    }

    @Override
    protected void doStart() throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Binding to server address: " + address + " using acceptor: " + acceptor);
        }

        IoHandler handler = new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object object) throws Exception {
                getProcessor().process(endpoint.createExchange(session, object));
            }
        };

        acceptor.bind(address, handler, endpoint.getConfig());
    }

    @Override
    protected void doStop() throws Exception {
        acceptor.unbind(address);
        super.doStop();
    }
}
