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
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.DatagramConnector;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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

    public synchronized MinaEndpoint createEndpoint(String uri, String[] urlParts) throws IOException, URISyntaxException {
        MinaEndpoint endpoint = map.get(uri);
        if (endpoint == null) {
            String remainingUrl = uri.substring("mina:".length());
            URI u = new URI(remainingUrl);

            String protocol = u.getScheme();
            if (protocol.equals("tcp")) {
                endpoint = createSocketEndpoint(uri, u);
            }
            else if (protocol.equals("udp") || protocol.equals("mcast") || protocol.equals("multicast")) {
                endpoint = createDatagramEndpoint(uri, u);
            }
            else if (protocol.equals("vm")) {
                endpoint = createVmEndpoint(uri, u);
            }
            else {
                throw new IOException("Unrecognised MINA protocol: " + protocol + " for uri: " + uri);
            }
            map.put(uri, endpoint);
        }

        return endpoint;
    }

    protected MinaEndpoint createVmEndpoint(String uri, URI connectUri) {
        IoAcceptor acceptor = new VmPipeAcceptor();
        SocketAddress address = new VmPipeAddress(connectUri.getPort());
        IoConnector connector = new VmPipeConnector();

        return new MinaEndpoint(uri, getContext(), address, acceptor, connector);
    }

    protected MinaEndpoint createSocketEndpoint(String uri, URI connectUri) {
        IoAcceptor acceptor = new SocketAcceptor();
        SocketAddress address = new InetSocketAddress(connectUri.getHost(), connectUri.getPort());
        IoConnector connector = new SocketConnector();
        return new MinaEndpoint(uri, getContext(), address, acceptor, connector);
    }

    protected MinaEndpoint createDatagramEndpoint(String uri, URI connectUri) {
        IoAcceptor acceptor = new DatagramAcceptor();
        SocketAddress address = new InetSocketAddress(connectUri.getHost(), connectUri.getPort());
        IoConnector connector = new DatagramConnector();
        return new MinaEndpoint(uri, getContext(), address, acceptor, connector);
    }
}
