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
package org.apache.camel.component.cxf.cxfbean;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.cxf.CxfHeaderFilterStrategy;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.CamelContextHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;

/**
 * CXF Bean Endpoint is a {@link ProcessorEndpoint} which associated with 
 * a {@link CxfBeanDestination}.  It delegates the processing of Camel 
 * Exchanges to the associated CxfBeanDestination.
 *  
 * @version $Revision$
 */
public class CxfBeanEndpoint extends ProcessorEndpoint implements HeaderFilterStrategyAware {
    private static final String URI_PREFIX = "cxfbean";
    private Server server;
    private Bus bus;
    private boolean isSetDefaultBus;
    private CxfBeanBinding cxfBeanBinding = new DefaultCxfBeanBinding();
    private HeaderFilterStrategy headerFilterStrategy = new CxfHeaderFilterStrategy();
    
    public CxfBeanEndpoint(String remaining, CxfBeanComponent component) {
        super(remaining, component);
    }
    
    public void stop() {
        server.stop();
    }
    
    public void start() {
        server.start();
    }

    @SuppressWarnings("unchecked")
    public void init() {
        Object obj = CamelContextHelper.mandatoryLookup(getCamelContext(), getEndpointUri());
        
        List<Object> serviceBeans;
        if (obj instanceof List) {
            serviceBeans = (List)obj;
        } else {
            serviceBeans = new ArrayList<Object>();
            serviceBeans.add(obj);
        }
        
        if (bus == null) {
            bus = BusFactory.getDefaultBus();
        }
        
        if (isSetDefaultBus) {
            BusFactory.setDefaultBus(bus);
        }
        
        registerTransportFactory((CxfBeanComponent)this.getComponent());
        server = createServerFactoryBean(serviceBeans).create();
        
    }
    
    @Override
    protected String createEndpointUri() {
        return URI_PREFIX + ":" + getEndpointUri();
    }
    
    private JAXRSServerFactoryBean createServerFactoryBean(List<Object> serviceBeans) {
        JAXRSServerFactoryBean answer = new JAXRSServerFactoryBean();
        answer.setServiceBeans(serviceBeans);
        answer.setAddress("camel://" + createEndpointUri());
        answer.setStart(true);
        answer.setTransportId(CxfBeanTransportFactory.TRANSPORT_ID);
        answer.setBus(bus);
        return answer;
        
    }
    
    /**
     * @param cxfBeanComponent 
     * 
     */
    private void registerTransportFactory(CxfBeanComponent cxfBeanComponent) {
        
        CxfBeanTransportFactory transportFactory = new CxfBeanTransportFactory();
        transportFactory.setCxfBeanComponent(cxfBeanComponent);
        transportFactory.setBus(bus);
        
        // register the conduit initiator
        ConduitInitiatorManager cim = bus.getExtension(ConduitInitiatorManager.class);
        cim.registerConduitInitiator(CxfBeanTransportFactory.TRANSPORT_ID, transportFactory);
        
        // register the destination factory
        DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
        dfm.registerDestinationFactory(CxfBeanTransportFactory.TRANSPORT_ID, transportFactory);    
        
    }

    // Properties
    // -------------------------------------------------------------------------

    public Bus getBus() {
        return bus;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public void setSetDefaultBus(boolean isSetDefaultBus) {
        this.isSetDefaultBus = isSetDefaultBus;
    }

    public boolean isSetDefaultBus() {
        return isSetDefaultBus;
    }

    public void setCxfBeanBinding(CxfBeanBinding cxfBeanBinding) {
        this.cxfBeanBinding = cxfBeanBinding;
    }

    public CxfBeanBinding getCxfBeanBinding() {
        return cxfBeanBinding;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

}
