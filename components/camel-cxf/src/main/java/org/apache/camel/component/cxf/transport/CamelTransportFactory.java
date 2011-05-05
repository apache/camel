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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.cxf.CxfHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.Bus;
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
@NoJSR250Annotations(unlessNull = "bus")
public class CamelTransportFactory extends AbstractTransportFactory implements ConduitInitiator, DestinationFactory, CamelContextAware {

    public static final String TRANSPORT_ID = "http://cxf.apache.org/transports/camel";
    public static final List<String> DEFAULT_NAMESPACES = Arrays.asList(TRANSPORT_ID);
    private static final Set<String> URI_PREFIXES = new HashSet<String>();

    private HeaderFilterStrategy headerFilterStrategy;
    private boolean checkException;

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
        super(DEFAULT_NAMESPACES, b);
        CxfHeaderFilterStrategy defaultHeaderFilterStrategy = new CxfHeaderFilterStrategy();
        // Doesn't filter the camel relates headers by default
        defaultHeaderFilterStrategy.setOutFilterPattern(null);
        headerFilterStrategy = defaultHeaderFilterStrategy;
    }

    @Resource(name = "cxf")
    public void setBus(Bus b) {
        super.setBus(b);
    }
    
    public void setCheckException(boolean check) {
        checkException = check;
    }
    
    public boolean isCheckException() {
        return checkException;
    }

    public Conduit getConduit(EndpointInfo targetInfo) throws IOException {
        return getConduit(targetInfo, null);
    }

    public Conduit getConduit(EndpointInfo endpointInfo, EndpointReferenceType target) throws IOException {
        return new CamelConduit(camelContext, bus, endpointInfo, target, headerFilterStrategy);
    }

    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        return new CamelDestination(camelContext, bus, this, endpointInfo, headerFilterStrategy, checkException);
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

}


