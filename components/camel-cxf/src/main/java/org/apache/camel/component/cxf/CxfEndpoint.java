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
package org.apache.camel.component.cxf;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.ws.WebServiceProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.feature.MessageDataFormatFeature;
import org.apache.camel.component.cxf.feature.PayLoadDataFormatFeature;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Endpoint</a>.
 * It contains a list of properties for CXF endpoint including {@link DataFormat}, 
 * {@link CxfBinding}, and {@link HeaderFilterStrategy}.  The default DataFormat 
 * mode is {@link DataFormat#POJO}.  
 *
 * @version $Revision$
 */
public class CxfEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    
    private static final Log LOG = LogFactory.getLog(CxfEndpoint.class);

    private String wsdlURL;
    private String serviceClass;
    private String portName;
    private String serviceName;
    private DataFormat dataFormat = DataFormat.POJO;
    private boolean isWrapped;
    private boolean inOut = true;
    private Bus bus;
    private CxfBinding cxfBinding;
    private HeaderFilterStrategy headerFilterStrategy;
    private AtomicBoolean cxfBindingInitialized = new AtomicBoolean(false);
    private AtomicBoolean getBusHasBeenCalled = new AtomicBoolean(false);
    private boolean isSetDefaultBus;

    public CxfEndpoint(String remaining, CxfComponent cxfComponent) {
        super(remaining, cxfComponent);
    }
    
    public CxfEndpoint(String remaining, CamelContext context) {
        super(remaining, context);
    }

    public Producer createProducer() throws Exception {
        return new CxfProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CxfConsumer(this, processor);
    }
    
    public boolean isSingleton() {
        return true;
    }
    
    /**
     * Populate server factory bean
     */
    protected void setupServerFactoryBean(ServerFactoryBean sfb, Class<?> cls) {
        
        // address
        sfb.setAddress(getEndpointUri());
        
        // service class
        sfb.setServiceClass(cls); 

        // wsdl url
        if (getWsdlURL() != null) {
            sfb.setWsdlURL(getWsdlURL());
        }
        
        // service  name qname
        if (getServiceName() != null) {
            sfb.setServiceName(CxfEndpointUtils.getQName(getServiceName()));
        }
        
        // port qname
        if (getPortName() != null) {
            sfb.setEndpointName(CxfEndpointUtils.getQName(getPortName()));
        }

        // apply feature here
        if (!CxfEndpointUtils.hasAnnotation(cls, WebServiceProvider.class)) {
            if (getDataFormat() == DataFormat.PAYLOAD) {
                sfb.getFeatures().add(new PayLoadDataFormatFeature());
            } else if (getDataFormat() == DataFormat.MESSAGE) {
                sfb.getFeatures().add(new MessageDataFormatFeature());
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignore DataFormat mode " + getDataFormat() 
                        + " since SEI class is annotated with WebServiceProvider");
            }
        }
        
        sfb.setBus(getBus());
        sfb.setStart(false);
    }
    
    /**
     * 
     * Create a client factory bean object.  Notice that the serviceClass <b>must</b> be
     * an interface.
     */
    protected ClientProxyFactoryBean createClientFactoryBean(Class<?> cls) throws CamelException {
        // quick null point check for serviceClass
        ObjectHelper.notNull(cls, "Please provide endpoint service interface class");
        
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            return new JaxWsProxyFactoryBean(new JaxWsClientFactoryBean() {
                @Override
                protected void createClient(Endpoint ep) {
                    setClient(new CamelCxfClientImpl(getBus(), ep));
                }
            });
        } else {
            return new ClientProxyFactoryBean(new ClientFactoryBean() {
                @Override
                protected void createClient(Endpoint ep) {
                    setClient(new CamelCxfClientImpl(getBus(), ep));
                }
            });
        }
    }

    protected Bus doGetBus() {
        return BusFactory.getDefaultBus();
    }
    
    /**
     * 
     * Populate a client factory bean
     */
    protected void setupClientFactoryBean(ClientProxyFactoryBean factoryBean, Class<?> cls) {
        // quick null point check for serviceClass
        ObjectHelper.notNull(cls, "Please provide endpoint service interface class");
        
        // service class
        factoryBean.setServiceClass(cls);
        
        // address
        factoryBean.setAddress(getEndpointUri());

        // wsdl url
        if (getWsdlURL() != null) {
            factoryBean.setWsdlURL(getWsdlURL());
        }
        
        // service name qname
        if (getServiceName() != null) {
            factoryBean.setServiceName(CxfEndpointUtils.getQName(getServiceName()));
        }
        
        // port name qname
        if (getPortName() != null) {
            factoryBean.setEndpointName(CxfEndpointUtils.getQName(getPortName()));
        }

        // apply feature here
        if (getDataFormat() == DataFormat.MESSAGE) {
            factoryBean.getFeatures().add(new MessageDataFormatFeature());
        } else if (getDataFormat() == DataFormat.PAYLOAD) {
            factoryBean.getFeatures().add(new PayLoadDataFormatFeature());
        }
        
        factoryBean.setBus(getBus());
        
    }
    
    // Package private methods
    // -------------------------------------------------------------------------
 
    /**
     * Create a CXF client object
     */
    Client createClient() throws Exception {

        // get service class
        ObjectHelper.notEmpty(getServiceClass(), CxfConstants.SERVICE_CLASS);      
        Class<?> cls = ClassLoaderUtils.loadClass(getServiceClass(), getClass());

        // create client factory bean
        ClientProxyFactoryBean factoryBean = createClientFactoryBean(cls);
        
        // setup client factory bean
        setupClientFactoryBean(factoryBean, cls);
        
        return ((ClientProxy)Proxy.getInvocationHandler(factoryBean.create())).getClient();
    }


    /**
     * Create a CXF server factory bean
     */
    ServerFactoryBean createServerFactoryBean() throws Exception {
 
        // get service class
        ObjectHelper.notEmpty(getServiceClass(), CxfConstants.SERVICE_CLASS);      
        Class<?> cls = ClassLoaderUtils.loadClass(getServiceClass(), getClass());
        
        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }
        
        // setup server factory bean
        setupServerFactoryBean(answer, cls);
        return answer;
    }
    
    // Properties
    // -------------------------------------------------------------------------

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(DataFormat format) {
        dataFormat = format;
    }

    public String getWsdlURL() {
        return wsdlURL;
    }

    public void setWsdlURL(String url) {
        wsdlURL = url;
    }
    
    public String getServiceClass() {        
        return serviceClass;
    }
      
    public void setServiceClass(String className) {
        serviceClass = className;
    }
    
    public void setServiceClass(Object instance) {
        serviceClass = ClassHelper.getRealClass(instance).getName();
    }

    public void setServiceName(String service) {
        serviceName = service;
    }

    public String getServiceName() {
        return serviceName;
    }
    
    public String getPortName() {
        return portName;
    }
    
    public void setPortName(String port) {
        portName = port;
    }

    public boolean isInOut() {
        return inOut;
    }

    public void setInOut(boolean inOut) {
        this.inOut = inOut;
    }

    public boolean isWrapped() {
        return isWrapped;
    }

    public void setWrapped(boolean wrapped) {
        isWrapped = wrapped;
    }

    public void setCxfBinding(CxfBinding cxfBinding) {
        this.cxfBinding = cxfBinding;
        cxfBindingInitialized.set(false);
    }

    public CxfBinding getCxfBinding() {
        if (cxfBinding == null) {
            cxfBinding = new DefaultCxfBinding();   
            if (LOG.isDebugEnabled()) {
                LOG.debug("Create default CXF Binding " + cxfBinding);
            }
        }
        
        if (!cxfBindingInitialized.getAndSet(true) 
                && cxfBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware)cxfBinding)
                .setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        return cxfBinding;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfHeaderFilterStrategy();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Create CXF default header filter strategy " 
                        + headerFilterStrategy);
            }
        }
        return headerFilterStrategy;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public Bus getBus() {
        if (bus == null) {
            bus = doGetBus();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using DefaultBus " + bus);
            }
        }
        
        if (!getBusHasBeenCalled.getAndSet(true) && isSetDefaultBus) {
            BusFactory.setDefaultBus(bus);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Set bus " + bus + " as thread default bus");
            }
        }
        return bus;
    }

    public void setSetDefaultBus(boolean isSetDefaultBus) {
        this.isSetDefaultBus = isSetDefaultBus;
    }

    public boolean isSetDefaultBus() {
        return isSetDefaultBus;
    }
    
    /**
     * We need to override the {@link ClientImpl#setParameters} method
     * to insert parameters into CXF Message for {@link DataFormat#PAYLOAD}
     * mode.
     */
    private class CamelCxfClientImpl extends ClientImpl {

        public CamelCxfClientImpl(Bus bus, Endpoint ep) {
            super(bus, ep);
        }
        
        @Override
        protected void setParameters(Object[] params, Message message) {
            if (DataFormat.PAYLOAD == message.get(DataFormat.class)) {
                CxfPayload<?> payload = (CxfPayload<?>)params[0];
                message.put(List.class, payload.getBody());
                message.put(Header.HEADER_LIST, payload.getHeaders());
            } else {
                super.setParameters(params, message);
            }
        }


    }

}
