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
package org.apache.camel.component.cxf.transport;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CxfHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractTransportFactory;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * @version $Revision$
 */
public class CamelTransportFactory extends AbstractTransportFactory implements ConduitInitiator, DestinationFactory {

    public static final String TRANSPORT_ID = "http://cxf.apache.org/transports/camel";

    private static final Set<String> URI_PREFIXES = new HashSet<String>();

    private Collection<String> activationNamespaces;
    
    private HeaderFilterStrategy headerFilterStrategy = new CxfHeaderFilterStrategy();

    static {
        URI_PREFIXES.add("camel://");
    }

    private Bus bus;
    private CamelContext camelContext;

    @Resource(name = "bus")
    public void setBus(Bus b) {
        bus = b;
    }

    public Bus getBus() {
        return bus;
    }

    @Resource
    public void setActivationNamespaces(Collection<String> ans) {
        activationNamespaces = ans;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Resource(name = "camelContext")
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Conduit getConduit(EndpointInfo targetInfo) throws IOException {
        return getConduit(targetInfo, null);
    }

    public Conduit getConduit(EndpointInfo endpointInfo, EndpointReferenceType target) throws IOException {
        return new CamelConduit(camelContext, bus, endpointInfo, target, headerFilterStrategy);
    }

    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        return new CamelDestination(camelContext, bus, this, endpointInfo, headerFilterStrategy);
    }

    public Set<String> getUriPrefixes() {
        return URI_PREFIXES;
    }

    @PostConstruct
    void registerWithBindingManager() {
        if (null == bus) {
            return;
        }
        ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
        if (null != cim && null != activationNamespaces) {
            for (String ns : activationNamespaces) {
                cim.registerConduitInitiator(ns, this);
            }
        }
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        if (null != dfm && null != activationNamespaces) {
            for (String ns : activationNamespaces) {
                dfm.registerDestinationFactory(ns, this);
            }
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }
    
    
}


