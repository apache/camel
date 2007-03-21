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
import org.apache.camel.impl.DefaultComponent;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @version $Revision$
 */
public class MinaComponent extends DefaultComponent<MinaExchange> {
    private Map<String, MinaEndpoint> map = new HashMap<String, MinaEndpoint>();

    public MinaComponent() {
    }

    public MinaComponent(CamelContext context) {
        super(context);
    }

    public synchronized MinaEndpoint createEndpoint(String uri, String[] urlParts) throws IOException {
        MinaEndpoint endpoint = map.get(uri);
        if (endpoint == null) {
            IoAcceptor acceptor = new VmPipeAcceptor();
            endpoint = new MinaEndpoint(uri, getContext(), acceptor);

            VmPipeAddress address = new VmPipeAddress(8080);

            // Set up server
            acceptor.bind(address, endpoint.getServerHandler());

            // Connect to the server.
            VmPipeConnector connector = new VmPipeConnector();
            ConnectFuture future = connector.connect(address, endpoint.getClientHandler());
            future.join();
            IoSession session = future.getSession();

            endpoint.setSession(session);
            map.put(uri, endpoint);
        }

        return endpoint;
    }
}
