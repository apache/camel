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

import javax.jws.WebService;

import org.apache.camel.component.cxf.common.header.CxfHeaderFilterStrategy;
import org.apache.camel.component.cxf.common.message.CxfMessageMapper;
import org.apache.camel.component.cxf.common.message.DefaultCxfMessageMapper;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.CamelContextHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;


/**
 * CXF Bean Endpoint is a {@link ProcessorEndpoint} which associated with 
 * a {@link CxfBeanDestination}.  It delegates the processing of Camel 
 * Exchanges to the associated CxfBeanDestination.
 *
 * @deprecated
 */
@Deprecated
public class CxfBeanEndpoint extends ProcessorEndpoint implements HeaderFilterStrategyAware {
    private static final String URI_PREFIX = "cxfbean";
    private Server server;
    private Bus bus;
    private boolean isSetDefaultBus;
    private CxfMessageMapper cxfBeanBinding = new DefaultCxfMessageMapper();
    private HeaderFilterStrategy headerFilterStrategy = new CxfHeaderFilterStrategy();
    private boolean loggingFeatureEnabled;
    private boolean populateFromClass = true;
    private List<Object> providers;

    public CxfBeanEndpoint(String remaining, CxfBeanComponent component) {
        super(remaining, component);
    }

    @Override
    protected void doStart() throws Exception {
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        server.stop();
    }

    @SuppressWarnings("unchecked")
    public void init() {
        Object obj = CamelContextHelper.mandatoryLookup(getCamelContext(), getEndpointUri());
        
        List<Object> serviceBeans;
        if (obj instanceof List) {
            serviceBeans = (List<Object>)obj;
        } else {
            serviceBeans = new ArrayList<Object>(1);
            serviceBeans.add(obj);
        }
        
        if (bus == null) {
            ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
            try {
                // Using the class loader of BusFactory to load the Bus
                Thread.currentThread().setContextClassLoader(BusFactory.class.getClassLoader());
                bus = BusFactory.newInstance().createBus();
            } finally {
                Thread.currentThread().setContextClassLoader(oldCL);
            }
        }
        
        if (isSetDefaultBus) {
            BusFactory.setDefaultBus(bus);
        }
        
        registerTransportFactory((CxfBeanComponent)this.getComponent());       
        
        createServer(serviceBeans);
    }
    
    @Override
    protected String createEndpointUri() {
        return URI_PREFIX + ":" + getEndpointUri();
    }
    
    private void createServer(List<Object> serviceBeans) {
        Object obj = serviceBeans.get(0).getClass().getAnnotation(WebService.class);

        if (obj != null) {
            JaxWsServerFactoryBean bean = new JaxWsServerFactoryBean();
            bean.setTransportId(CxfBeanTransportFactory.TRANSPORT_ID);
            bean.setServiceClass(serviceBeans.get(0).getClass());
            // set the bean instance as well, otherwise CXF will re-create a new instance of the class
            bean.setServiceBean(serviceBeans.get(0));
            if (bean.getJaxWsServiceFactory() != null) {
                bean.getJaxWsServiceFactory().setPopulateFromClass(isPopulateFromClass());
            }
            bean.setBus(bus);
            bean.setStart(true);
            bean.setAddress("camel://" + createEndpointUri());
            if (loggingFeatureEnabled) {
                bean.getFeatures().add(new LoggingFeature());
            }            
            server = bean.create();
        } else {
            JAXRSServerFactoryBean bean = new JAXRSServerFactoryBean();
            bean.setServiceBeans(serviceBeans);
            bean.setAddress("camel://" + createEndpointUri());
            bean.setStart(true);
            bean.setTransportId(CxfBeanTransportFactory.TRANSPORT_ID);
            bean.setBus(bus);
            if (loggingFeatureEnabled) {
                bean.getFeatures().add(new LoggingFeature());
            }
            bean.setProviders(providers);
            server = bean.create();
        }
    }
    
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

    public void setCxfBeanBinding(CxfMessageMapper cxfBeanBinding) {
        this.cxfBeanBinding = cxfBeanBinding;
    }

    public CxfMessageMapper getCxfBeanBinding() {
        return cxfBeanBinding;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }
    
    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }     

    public void setPopulateFromClass(boolean populateFromClass) {
        this.populateFromClass = populateFromClass;
    }

    public boolean isPopulateFromClass() {
        return populateFromClass;
    }

    public List<Object> getProviders() {
        return providers;
    }

    public void setProviders(List<Object> providers) {
        this.providers = providers;
    }
}
