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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.handler.Handler;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.cxf.common.header.CxfHeaderFilterStrategy;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.feature.MessageDataFormatFeature;
import org.apache.camel.component.cxf.feature.PayLoadDataFormatFeature;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Endpoint</a>.
 * It contains a list of properties for CXF endpoint including {@link DataFormat},
 * {@link CxfBinding}, and {@link HeaderFilterStrategy}.  The default DataFormat
 * mode is {@link DataFormat#POJO}.
 */
public class CxfEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware, Service {

    private static final Logger LOG = LoggerFactory.getLogger(CxfEndpoint.class);

    protected Bus bus;

    private String wsdlURL;
    private Class<?> serviceClass;
    private QName portName;
    private QName serviceName;
    private String defaultOperationName;
    private String defaultOperationNamespace;
    // This is for invoking the CXFClient with wrapped parameters of unwrapped parameters
    private boolean isWrapped;
    // This is for marshal or unmarshal message with the document-literal wrapped or unwrapped style
    private Boolean wrappedStyle;
    private DataFormat dataFormat = DataFormat.POJO;
    private String publishedEndpointUrl;
    private boolean inOut = true;
    private CxfBinding cxfBinding;
    private HeaderFilterStrategy headerFilterStrategy;
    private AtomicBoolean getBusHasBeenCalled = new AtomicBoolean(false);
    private boolean isSetDefaultBus;
    private boolean loggingFeatureEnabled;
    private String address;
    private boolean mtomEnabled;
    private boolean skipPayloadMessagePartCheck;
    private Map<String, Object> properties;
    private List<Interceptor<? extends Message>> in 
        = new ModCountCopyOnWriteArrayList<Interceptor<? extends Message>>();
    private List<Interceptor<? extends Message>> out 
        = new ModCountCopyOnWriteArrayList<Interceptor<? extends Message>>();
    private List<Interceptor<? extends Message>> outFault  
        = new ModCountCopyOnWriteArrayList<Interceptor<? extends Message>>();
    private List<Interceptor<? extends Message>> inFault 
        = new ModCountCopyOnWriteArrayList<Interceptor<? extends Message>>();
    private List<AbstractFeature> features 
        = new ModCountCopyOnWriteArrayList<AbstractFeature>();

    private List<Handler> handlers;
    private List<String> schemaLocations = new ArrayList<String>();
    private String transportId;
    private String bindingId;

    

    public CxfEndpoint(String remaining, CxfComponent cxfComponent) {
        super(remaining, cxfComponent);
        setAddress(remaining);
    }

    public CxfEndpoint(String remaining, CamelContext context) {
        super(remaining, context);
        setAddress(remaining);
    }

    public CxfEndpoint(String remaining) {
        super(remaining);
        setAddress(remaining);
    }
    public CxfEndpoint() {
        super();
    }

    // This method is for CxfComponent setting the EndpointUri
    protected void updateEndpointUri(String endpointUri) {
        super.setEndpointUri(endpointUri);
    }

    public Producer createProducer() throws Exception {
        Producer answer = new CxfProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
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
        sfb.setAddress(getAddress());

        // service class
        sfb.setServiceClass(cls);

        sfb.setInInterceptors(in);
        sfb.setOutInterceptors(out);
        sfb.setOutFaultInterceptors(outFault);
        sfb.setInFaultInterceptors(inFault); 
        sfb.setFeatures(features);
        sfb.setSchemaLocations(schemaLocations);
        
        if (sfb instanceof JaxWsServerFactoryBean && handlers != null) {
            ((JaxWsServerFactoryBean)sfb).setHandlers(handlers);
        }
        sfb.setTransportId(transportId);
        sfb.setBindingId(bindingId);
        
        // wsdl url
        if (getWsdlURL() != null) {
            sfb.setWsdlURL(getWsdlURL());
        }

        // service  name qname
        if (getServiceName() != null) {
            sfb.setServiceName(getServiceName());
        }

        // port qname
        if (getPortName() != null) {
            sfb.setEndpointName(getPortName());
        }

        // apply feature here
        if (!CxfEndpointUtils.hasAnnotation(cls, WebServiceProvider.class)) {
            if (getDataFormat() == DataFormat.PAYLOAD) {
                sfb.getFeatures().add(new PayLoadDataFormatFeature());
            } else if (getDataFormat() == DataFormat.MESSAGE) {
                sfb.getFeatures().add(new MessageDataFormatFeature());
            }
        } else {
            LOG.debug("Ignore DataFormat mode {} since SEI class is annotated with WebServiceProvider", getDataFormat());
        }

        if (loggingFeatureEnabled) {
            sfb.getFeatures().add(new LoggingFeature());
        }

        if (getDataFormat() == DataFormat.PAYLOAD) {
            sfb.setDataBinding(new HybridSourceDataBinding());
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null) {
            sfb.getServiceFactory().setWrapped(getWrappedStyle());
        }

        // any optional properties
        if (getProperties() != null) {
            if (sfb.getProperties() != null) {
                // add to existing properties
                sfb.getProperties().putAll(getProperties());
            } else {
                sfb.setProperties(getProperties());
            }
            LOG.debug("ServerFactoryBean: {} added properties: {}", sfb, properties);
        }

        sfb.setBus(getBus());
        sfb.setStart(false);
    }

    /**
     * Create a client factory bean object.  Notice that the serviceClass <b>must</b> be
     * an interface.
     */
    protected ClientProxyFactoryBean createClientFactoryBean(Class<?> cls) throws CamelException {
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            return new JaxWsProxyFactoryBean(new JaxWsClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            });
        } else {
            return new ClientProxyFactoryBean(new ClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            });
        }
    }

    /**
     * Create a client factory bean object without serviceClass interface.
     */
    protected ClientFactoryBean createClientFactoryBean() {
        return new ClientFactoryBean(new WSDLServiceFactoryBean()) {

            @Override
            protected Client createClient(Endpoint ep) {
                return new CamelCxfClientImpl(getBus(), ep);
            }

            @Override
            protected void initializeAnnotationInterceptors(Endpoint ep, Class<?> cls) {
                // Do nothing here
            }
        };
    }

    protected Bus doGetBus() {
        BusFactory busFactory = BusFactory.newInstance();
        // need to check if the camelContext is SpringCamelContext and
        // update the bus configuration with the applicationContext
        // which SpringCamelContext holds
        if (getCamelContext() instanceof SpringCamelContext) {
            SpringCamelContext springCamelContext = (SpringCamelContext) getCamelContext();
            ApplicationContext applicationContext = springCamelContext.getApplicationContext();
            busFactory = new org.apache.cxf.bus.spring.SpringBusFactory(applicationContext);
        }
        return busFactory.createBus();
    }

    /**
     * Populate a client factory bean
     */
    protected void setupClientFactoryBean(ClientProxyFactoryBean factoryBean, Class<?> cls) {
        // service class
        factoryBean.setServiceClass(cls);

        factoryBean.setInInterceptors(in);
        factoryBean.setOutInterceptors(out);
        factoryBean.setOutFaultInterceptors(outFault);
        factoryBean.setInFaultInterceptors(inFault); 
        factoryBean.setFeatures(features);
        
        if (factoryBean instanceof JaxWsProxyFactoryBean && handlers != null) {
            ((JaxWsProxyFactoryBean)factoryBean).setHandlers(handlers);
        }        
        factoryBean.setTransportId(transportId);
        factoryBean.setBindingId(bindingId);

        // address
        factoryBean.setAddress(getAddress());

        // wsdl url
        if (getWsdlURL() != null) {
            factoryBean.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceName() != null) {
            factoryBean.setServiceName(getServiceName());
        }

        // port name qname
        if (getPortName() != null) {
            factoryBean.setEndpointName(getPortName());
        }

        // apply feature here
        if (getDataFormat() == DataFormat.MESSAGE) {
            factoryBean.getFeatures().add(new MessageDataFormatFeature());
        } else if (getDataFormat() == DataFormat.PAYLOAD) {
            factoryBean.getFeatures().add(new PayLoadDataFormatFeature());
            factoryBean.setDataBinding(new HybridSourceDataBinding());
        }

        if (loggingFeatureEnabled) {
            factoryBean.getFeatures().add(new LoggingFeature());
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null) {
            factoryBean.getServiceFactory().setWrapped(getWrappedStyle());
        }

        // set the properties on CxfProxyFactoryBean
        if (getProperties() != null) {
            if (factoryBean.getProperties() != null) {
                // add to existing properties
                factoryBean.getProperties().putAll(getProperties());
            } else {
                factoryBean.setProperties(getProperties());
            }
            LOG.debug("ClientProxyFactoryBean: {} added properties: {}", factoryBean, properties);
        }

        factoryBean.setBus(getBus());
    }

    protected void setupClientFactoryBean(ClientFactoryBean factoryBean) {
        factoryBean.setInInterceptors(in);
        factoryBean.setOutInterceptors(out);
        factoryBean.setOutFaultInterceptors(outFault);
        factoryBean.setInFaultInterceptors(inFault); 
        factoryBean.setFeatures(features);
        factoryBean.setTransportId(transportId);
        factoryBean.setBindingId(bindingId);

        // address
        factoryBean.setAddress(getAddress());

        // wsdl url
        if (getWsdlURL() != null) {
            factoryBean.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceName() != null) {
            factoryBean.setServiceName(getServiceName());
        }

        // port name qname
        if (getPortName() != null) {
            factoryBean.setEndpointName(getPortName());
        }

        // apply feature here
        if (getDataFormat() == DataFormat.MESSAGE) {
            factoryBean.getFeatures().add(new MessageDataFormatFeature());
        } else if (getDataFormat() == DataFormat.PAYLOAD) {
            factoryBean.getFeatures().add(new PayLoadDataFormatFeature());
            factoryBean.setDataBinding(new HybridSourceDataBinding());
        }

        if (loggingFeatureEnabled) {
            factoryBean.getFeatures().add(new LoggingFeature());
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null) {
            factoryBean.getServiceFactory().setWrapped(getWrappedStyle());
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
        if (getDataFormat().equals(DataFormat.POJO)) {
            ObjectHelper.notNull(getServiceClass(), CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && getServiceClass() == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            setServiceClass(org.apache.camel.component.cxf.DefaultSEI.class.getName());
            setDefaultOperationNamespace(CxfConstants.DISPATCH_NAMESPACE);
            setDefaultOperationName(CxfConstants.DISPATCH_DEFAULT_OPERATION_NAMESPACE);
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setSkipPayloadMessagePartCheck(true);
            }
        }

        Class<?> cls = null;
        if (getServiceClass() != null) {
            cls = getServiceClass();
            // create client factory bean
            ClientProxyFactoryBean factoryBean = createClientFactoryBean(cls);
            // setup client factory bean
            setupClientFactoryBean(factoryBean, cls);
            return ((ClientProxy) Proxy.getInvocationHandler(factoryBean.create())).getClient();
        } else {
            checkName(portName, "endpoint/port name");
            checkName(serviceName, "service name");

            ClientFactoryBean factoryBean = createClientFactoryBean();
            // setup client factory bean
            setupClientFactoryBean(factoryBean);
            return factoryBean.create();
        }
    }

    void checkName(Object value, String name) {
        if (ObjectHelper.isEmpty(value)) {
            LOG.warn("The " + name + " of " + this.getEndpointUri() + " is empty, cxf will try to load the first one in wsdl for you.");
        }
    }

    /**
     * Create a CXF server factory bean
     */
    ServerFactoryBean createServerFactoryBean() throws Exception {

        Class<?> cls = null;
        if (getDataFormat() == DataFormat.POJO || getServiceClass() != null) {
            // get service class
            ObjectHelper.notNull(getServiceClass(), CxfConstants.SERVICE_CLASS);
            cls = getServiceClass();
        }

        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            checkName(portName, " endpoint/port name");
            checkName(serviceName, " service name");
            answer = new ServerFactoryBean(new WSDLServiceFactoryBean());
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
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

    public String getPublishedEndpointUrl() {
        return publishedEndpointUrl;
    }

    public void setPublishedEndpointUrl(String url) {
        publishedEndpointUrl = url;
    }

    public String getWsdlURL() {
        return wsdlURL;
    }

    public void setWsdlURL(String url) {
        wsdlURL = url;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class<?> cls) {
        serviceClass = cls;
    }

    public void setServiceClass(Object instance) {
        serviceClass = ClassHelper.getRealClass(instance);
    }
    
    public void setServiceClass(String type) throws ClassNotFoundException {
        serviceClass = ClassLoaderUtils.loadClass(type, getClass());
    }
    
    public void setServiceName(QName service) {
        serviceName = service;
    }

    public QName getServiceName() {
        return serviceName;
    }

    public QName getPortName() {
        return portName;
    }

    public void setPortName(QName port) {
        portName = port;
    }
    public void setEndpointName(QName port) {
        portName = port;
    }

    public String getDefaultOperationName() {
        return defaultOperationName;
    }

    public void setDefaultOperationName(String name) {
        defaultOperationName = name;
    }

    public String getDefaultOperationNamespace() {
        return defaultOperationNamespace;
    }

    public void setDefaultOperationNamespace(String namespace) {
        defaultOperationNamespace = namespace;
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

    public Boolean getWrappedStyle() {
        return wrappedStyle;
    }

    public void setWrappedStyle(Boolean wrapped) {
        wrappedStyle = wrapped;
    }

    public void setCxfBinding(CxfBinding cxfBinding) {
        this.cxfBinding = cxfBinding;
    }

    public CxfBinding getCxfBinding() {
        return cxfBinding;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        if (cxfBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) cxfBinding).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public Bus getBus() {
        if (bus == null) {
            bus = doGetBus();
            LOG.debug("Using DefaultBus {}", bus);
        }

        if (!getBusHasBeenCalled.getAndSet(true) && isSetDefaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
        return bus;
    }

    public void setSetDefaultBus(boolean isSetDefaultBus) {
        this.isSetDefaultBus = isSetDefaultBus;
    }

    public boolean isSetDefaultBus() {
        return isSetDefaultBus;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }

    protected boolean isSkipPayloadMessagePartCheck() {
        return skipPayloadMessagePartCheck;
    }

    protected void setSkipPayloadMessagePartCheck(boolean skipPayloadMessagePartCheck) {
        this.skipPayloadMessagePartCheck = skipPayloadMessagePartCheck;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setCamelContext(CamelContext c) {
        super.setCamelContext(c);
        if (this.properties != null) {
            try {
                EndpointHelper.setReferenceProperties(getCamelContext(),
                                             this,
                                             this.properties);
                EndpointHelper.setProperties(getCamelContext(),
                                             this,
                                             this.properties);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties.putAll(properties);
        }
        if (getCamelContext() != null && this.properties != null) {
            try {
                EndpointHelper.setReferenceProperties(getCamelContext(),
                                             this,
                                             this.properties);
                EndpointHelper.setProperties(getCamelContext(),
                                             this,
                                             this.properties);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfHeaderFilterStrategy();
        }
        if (cxfBinding == null) {
            cxfBinding = new DefaultCxfBinding();
        }
        if (cxfBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) cxfBinding).setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public void setAddress(String address) {
        super.setEndpointUri(address);
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setMtomEnabled(boolean mtomEnabled) {
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() {
        return mtomEnabled;
    }

    /**
     * We need to override the {@link ClientImpl#setParameters} method
     * to insert parameters into CXF Message for {@link DataFormat#PAYLOAD} mode.
     */
    class CamelCxfClientImpl extends ClientImpl {

        public CamelCxfClientImpl(Bus bus, Endpoint ep) {
            super(bus, ep);
        }

        public Bus getBus() {
            return bus;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void setParameters(Object[] params, Message message) {

            Object attachements = message.get(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            if (attachements != null) {
                message.setAttachments((Collection<Attachment>) attachements);
                message.remove(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            }

            if (DataFormat.PAYLOAD == message.get(DataFormat.class)) {

                CxfPayload<?> payload = (CxfPayload<?>) params[0];
                List<Element> elements = payload.getBody();

                BindingOperationInfo boi = message.get(BindingOperationInfo.class);
                MessageContentsList content = new MessageContentsList();
                int i = 0;

                for (MessagePartInfo partInfo : boi.getInput().getMessageParts()) {
                    if (elements.size() > i && (isSkipPayloadMessagePartCheck() || partInfo.getConcreteName().getLocalPart().equals(elements.get(i)
                                                                                                                                            .getLocalName()))) {
                        content.put(partInfo, elements.get(i++));
                    }
                }

                if (content.size() < elements.size()) {
                    LOG.warn("Cannot set right payload paremeters. Please check the BindingOperation and PayLoadMessage.");
                    throw new IllegalArgumentException(
                        "The PayLoad elements cannot fit with the message parts of the BindingOperation. Please check the BindingOperation and PayLoadMessage.");
                }

                message.setContent(List.class, content);
                message.put(Header.HEADER_LIST, payload.getHeaders());
            } else {
                super.setParameters(params, message);
            }

            message.remove(DataFormat.class);
        }
    }
    

    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return outFault;
    }

    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return inFault;
    }

    public List<Interceptor<? extends Message>> getInInterceptors() {
        return in;
    }

    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return out;
    }

    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        in = interceptors;
    }

    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        inFault = interceptors;
    }

    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        out = interceptors;
    }

    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        outFault = interceptors;
    }
    
    public void setFeatures(List<AbstractFeature> f) {
        features = f;
    }
    public List<AbstractFeature> getFeatures() {
        return features;
    }
    
    public void setHandlers(List<Handler> h) {
        handlers = h;
    }
    public List<Handler> getHandlers() {
        return handlers;
    }
    
    public void setSchemaLocations(List<String> sc) {
        schemaLocations = sc;
    }
    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public String getTransportId() {
        return transportId;
    }

    public void setTransportId(String transportId) {
        this.transportId = transportId;
    }
    
    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }
    
}
