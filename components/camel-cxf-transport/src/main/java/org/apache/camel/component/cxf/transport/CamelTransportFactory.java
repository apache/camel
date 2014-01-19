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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.cxf.common.header.CxfHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.common.injection.NoJSR250Annotations;
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
 * @version 
 */
@NoJSR250Annotations
public class CamelTransportFactory extends AbstractTransportFactory implements ConduitInitiator, DestinationFactory, CamelContextAware {

    public static final String TRANSPORT_ID = "http://cxf.apache.org/transports/camel";
    public static final List<String> DEFAULT_NAMESPACES = Arrays.asList(TRANSPORT_ID);
    private static final Set<String> URI_PREFIXES = new HashSet<String>();

    private HeaderFilterStrategy headerFilterStrategy;
    private boolean checkException;
    private Bus bus;

    static {
        URI_PREFIXES.add("camel://");        
    }

    private CamelContext camelContext;
    
    public CamelTransportFactory() {
        CxfHeaderFilterStrategy defaultHeaderFilterStrategy = new CxfHeaderFilterStrategy();
        // Doesn't filter the camel relates headers by default
        defaultHeaderFilterStrategy.setOutFilterPattern(null);
        headerFilterStrategy = defaultHeaderFilterStrategy;
    }
    public CamelTransportFactory(Bus b) {
        super(DEFAULT_NAMESPACES);
        bus = b;
        registerFactory();

        CxfHeaderFilterStrategy defaultHeaderFilterStrategy = new CxfHeaderFilterStrategy();
        // Doesn't filter the camel relates headers by default
        defaultHeaderFilterStrategy.setOutFilterPattern(null);
        headerFilterStrategy = defaultHeaderFilterStrategy;
    }

    public void setCheckException(boolean check) {
        checkException = check;
    }
    
    public boolean isCheckException() {
        return checkException;
    }

    public Conduit getConduit(EndpointInfo targetInfo) throws IOException {
        return getConduit(targetInfo, null, bus);
    }

    public Conduit getConduit(EndpointInfo endpointInfo, EndpointReferenceType target) throws IOException {
        return getConduit(endpointInfo, target, bus);
    }

    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        return getDestination(endpointInfo, bus);
    }

    public Set<String> getUriPrefixes() {
        return URI_PREFIXES;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }
    
    public CamelContext getCamelContext() {
        return camelContext;
    }
    public void setCamelContext(CamelContext c) {
        camelContext = c;
    }
    public Destination getDestination(EndpointInfo ei, Bus b) throws IOException {
        return new CamelDestination(camelContext, b, this, ei, headerFilterStrategy, checkException);
    }
    public Conduit getConduit(EndpointInfo targetInfo, Bus b) throws IOException {
        return getConduit(targetInfo, null, b);
    }
    public Conduit getConduit(EndpointInfo localInfo, EndpointReferenceType target, Bus b)
        throws IOException {
        return new CamelConduit(camelContext, b, localInfo, target, headerFilterStrategy);
    }

    // CXF 2.x support methods    
    public void setBus(Bus b) {
        unregisterFactory();
        bus = b;
        registerFactory();
    }
    public final void registerFactory() {
        if (null == bus) {
            return;
        }
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        if (null != dfm && getTransportIds() != null) {
            for (String ns : getTransportIds()) {
                dfm.registerDestinationFactory(ns, this);
            }
        }
        ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
        if (cim != null && getTransportIds() != null) {
            for (String ns : getTransportIds()) {
                cim.registerConduitInitiator(ns, this);
            }
        }
    }
    
    public final void unregisterFactory() {
        if (null == bus) {
            return;
        }
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        if (null != dfm && getTransportIds() != null) {
            for (String ns : getTransportIds()) {
                try {
                    if (dfm.getDestinationFactory(ns) == this) {
                        dfm.deregisterDestinationFactory(ns);
                    }
                } catch (BusException e) {
                    //ignore
                }
            }
        }
        ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
        if (cim != null && getTransportIds() != null) {
            for (String ns : getTransportIds()) {
                try {
                    if (cim.getConduitInitiator(ns) == this) {
                        cim.deregisterConduitInitiator(ns);
                    }
                } catch (BusException e) {
                    //ignore
                }
            }
        }
    }
}


