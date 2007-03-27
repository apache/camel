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
package org.apache.camel.component.cxf;

import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.CamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.bus.CXFBusFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 * @version $Revision$
 */
public class CxfComponent extends DefaultComponent<CxfExchange> {
    private Map<String, CxfEndpoint> map = new HashMap<String, CxfEndpoint>();

    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }

    public synchronized CxfEndpoint createEndpoint(String uri, String[] urlParts) throws IOException, URISyntaxException {
        CxfEndpoint endpoint = map.get(uri);
        if (endpoint == null) {
            String remainingUrl = uri.substring("cxf:".length());
            URI u = new URI(remainingUrl);

            String protocol = u.getScheme();

            map.put(uri, endpoint);
        }
        return endpoint;
    }

    /*
    protected void foo() {
       Bus bus = CXFBusFactory.getDefaultBus();
       ServerRegistry serverRegistry = bus.getExtension(ServerRegistry.class);
       List<Server> servers = serverRegistry.getServers();

       Server targetServer = null;
       for (Server server : servers) {
           targetServer = server;
           EndpointInfo info = server.getEndpoint().getEndpointInfo();
           String address = info.getAddress();

           Message message = new MessageImpl();
           server.getMessageObserver().onMessage(message);
       }
    }
    */
}
