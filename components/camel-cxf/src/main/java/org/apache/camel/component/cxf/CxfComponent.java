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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.xmlsoap.schemas.wsdl.http.AddressType;

/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Component</a>

 * @version $Revision$
 */
public class CxfComponent extends DefaultComponent<CxfExchange> {
    private LocalTransportFactory localTransportFactory;

    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint<CxfExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        URI u = new URI(remaining);

        // TODO this is a hack!!!
        EndpointInfo endpointInfo = new EndpointInfo(null, "http://schemas.xmlsoap.org/soap/http");
        AddressType a = new AddressType();
        a.setLocation(remaining);
        endpointInfo.addExtensor(a);

        return new CxfEndpoint(uri, this, endpointInfo);
    }

    public LocalTransportFactory getLocalTransportFactory() throws BusException {
        if (localTransportFactory == null) {
            localTransportFactory = findLocalTransportFactory();
            if (localTransportFactory == null) {
                localTransportFactory = new LocalTransportFactory();
            }
        }
        return localTransportFactory;
    }

    public void setLocalTransportFactory(LocalTransportFactory localTransportFactory) {
        this.localTransportFactory = localTransportFactory;
    }

    protected LocalTransportFactory findLocalTransportFactory() throws BusException {
        Bus bus = CXFBusFactory.getDefaultBus();
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        return (LocalTransportFactory) dfm.getDestinationFactory(LocalTransportFactory.TRANSPORT_ID);
    }
}
