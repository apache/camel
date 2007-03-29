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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.xmlsoap.schemas.wsdl.http.AddressType;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @version $Revision$
 */
public class CxfComponent extends DefaultComponent<CxfExchange> {
    private LocalTransportFactory localTransportFactory = new LocalTransportFactory();

    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }

    public synchronized CxfEndpoint createEndpoint(String uri, String[] urlParts) throws IOException, URISyntaxException {
        String remainingUrl = uri.substring("cxf:".length());
        URI u = new URI(remainingUrl);

        // TODO this is a hack!!!
        EndpointInfo endpointInfo = new EndpointInfo(null, "http://schemas.xmlsoap.org/soap/http");
        AddressType a = new AddressType();
        a.setLocation(remainingUrl);
        endpointInfo.addExtensor(a);

        return new CxfEndpoint(uri, this, endpointInfo);
    }

    public LocalTransportFactory getLocalTransportFactory() {
        return localTransportFactory;
    }

    public void setLocalTransportFactory(LocalTransportFactory localTransportFactory) {
        this.localTransportFactory = localTransportFactory;
    }
}
