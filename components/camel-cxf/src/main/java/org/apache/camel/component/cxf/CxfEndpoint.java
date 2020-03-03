/*
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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.handler.Handler;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.component.cxf.common.header.CxfHeaderFilterStrategy;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.feature.CXFMessageDataFormatFeature;
import org.apache.camel.component.cxf.feature.PayLoadDataFormatFeature;
import org.apache.camel.component.cxf.feature.RAWDataFormatFeature;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.BindingConfiguration;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.databinding.DataBinding;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.ext.logging.AbstractLoggingInterceptor;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.jaxws.context.WebServiceContextResourceResolver;
import org.apache.cxf.jaxws.handler.AnnotationHandlerChainBuilder;
import org.apache.cxf.jaxws.support.JaxWsEndpointImpl;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.logging.FaultListener;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.resource.DefaultResourceManager;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.resource.ResourceResolver;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.wsdl.WSDLManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cxf component is used for SOAP WebServices using Apache CXF.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "cxf", title = "CXF", syntax = "cxf:beanId:address", label = "soap,webservice")
public class CxfEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware, Service, Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(CxfEndpoint.class);

    @UriParam(label = "advanced")
    protected Bus bus;

    protected volatile boolean createBus;
    private final AtomicBoolean getBusHasBeenCalled = new AtomicBoolean(false);

    private BindingConfiguration bindingConfig;
    private DataBinding dataBinding;
    private Object serviceFactoryBean;
    private List<Interceptor<? extends Message>> in = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> out = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> outFault = new ModCountCopyOnWriteArrayList<>();
    private List<Interceptor<? extends Message>> inFault = new ModCountCopyOnWriteArrayList<>();
    private List<Feature> features = new ModCountCopyOnWriteArrayList<>();
    private List<Handler> handlers;
    private List<String> schemaLocations;
    private String transportId;

    @UriPath(description = "To lookup an existing configured CxfEndpoint. Must used bean: as prefix.")
    private String beanId;
    @UriParam(defaultValue = "POJO")
    private DataFormat dataFormat = DataFormat.POJO;
    @UriPath(label = "service")
    private String address;
    @UriParam(label = "service")
    private String wsdlURL;
    @UriParam(label = "service")
    private Class<?> serviceClass;
    @UriParam(label = "service")
    private String portName;
    private transient QName portNameQName;
    @UriParam(label = "service")
    private String serviceName;
    private transient QName serviceNameQName;
    @UriParam(label = "service")
    private String bindingId;
    @UriParam(label = "service")
    private String publishedEndpointUrl;
    @UriParam(label = "producer")
    private String defaultOperationName;
    @UriParam(label = "producer")
    private String defaultOperationNamespace;
    @UriParam(label = "producer")
    private boolean wrapped;
    @UriParam(label = "producer")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer")
    private HostnameVerifier hostnameVerifier;
    @UriParam
    private Boolean wrappedStyle;
    @UriParam(label = "advanced")
    private Boolean allowStreaming;
    @UriParam(label = "advanced")
    private CxfBinding cxfBinding;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced")
    private boolean defaultBus;
    @UriParam(label = "logging")
    private boolean loggingFeatureEnabled;
    @UriParam(label = "logging", defaultValue = "" + AbstractLoggingInterceptor.DEFAULT_LIMIT)
    private int loggingSizeLimit;
    @UriParam(label = "advanced")
    private boolean mtomEnabled;
    @UriParam(label = "advanced")
    private boolean skipPayloadMessagePartCheck;
    @UriParam(label = "logging")
    private boolean skipFaultLogging;
    @UriParam(label = "advanced")
    private boolean mergeProtocolHeaders;
    @UriParam(label = "advanced")
    private CxfConfigurer cxfConfigurer;
    @UriParam(label = "advanced", defaultValue = "30000")
    private long continuationTimeout = 30000;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "advanced", prefix = "properties.", multiValue = true)
    private Map<String, Object> properties;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;

    public CxfEndpoint() {
        setExchangePattern(ExchangePattern.InOut);
    }

    public CxfEndpoint(String remaining, CxfComponent cxfComponent) {
        super(remaining, cxfComponent);
        setAddress(remaining);
        setExchangePattern(ExchangePattern.InOut);
    }

    public CxfEndpoint copy() {
        try {
            return (CxfEndpoint)this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    // This method is for CxfComponent setting the EndpointUri
    protected void updateEndpointUri(String endpointUri) {
        super.setEndpointUri(UnsafeUriCharactersEncoder.encodeHttpURI(endpointUri));
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer answer = new CxfProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CxfConsumer answer = new CxfConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

/**
     * Populate server factory bean
     */
    protected void setupServerFactoryBean(ServerFactoryBean sfb, Class<?> cls) {

        // address
        sfb.setAddress(getAddress());

        sfb.setServiceClass(cls);

        sfb.setInInterceptors(in);
        sfb.setOutInterceptors(out);
        sfb.setOutFaultInterceptors(outFault);
        sfb.setInFaultInterceptors(inFault);
        sfb.setFeatures(features);
        if (schemaLocations != null) {
            sfb.setSchemaLocations(schemaLocations);
        }
        if (bindingConfig != null) {
            sfb.setBindingConfig(bindingConfig);
        }

        if (dataBinding != null) {
            sfb.setDataBinding(dataBinding);
        }

        if (serviceFactoryBean != null) {
            setServiceFactory(sfb, serviceFactoryBean);
        }

        if (sfb instanceof JaxWsServerFactoryBean && handlers != null) {
            ((JaxWsServerFactoryBean)sfb).setHandlers(handlers);
        }
        if (getTransportId() != null) {
            sfb.setTransportId(getTransportId());
        }
        if (getBindingId() != null) {
            sfb.setBindingId(getBindingId());
        }

        // wsdl url
        if (getWsdlURL() != null) {
            sfb.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceNameAsQName() != null) {
            sfb.setServiceName(getServiceNameAsQName());
        }

        // port qname
        if (getPortNameAsQName() != null) {
            sfb.setEndpointName(getPortNameAsQName());
        }

        // apply feature here
        if (!CxfEndpointUtils.hasAnnotation(cls, WebServiceProvider.class)) {
            if (getDataFormat() == DataFormat.PAYLOAD) {
                sfb.getFeatures().add(new PayLoadDataFormatFeature(allowStreaming));
            } else if (getDataFormat().dealias() == DataFormat.CXF_MESSAGE) {
                sfb.getFeatures().add(new CXFMessageDataFormatFeature());
                sfb.setDataBinding(new SourceDataBinding());
            } else if (getDataFormat().dealias() == DataFormat.RAW) {
                RAWDataFormatFeature feature = new RAWDataFormatFeature();
                if (this.getExchangePattern().equals(ExchangePattern.InOnly)) {
                    //if DataFormat is RAW|MESSAGE, can't read message so can't
                    //determine it's oneway so need get the MEP from URI explicitly
                    feature.setOneway(true);
                }
                feature.addInIntercepters(getInInterceptors());
                feature.addOutInterceptors(getOutInterceptors());
                sfb.getFeatures().add(feature);
            }
        } else {
            LOG.debug("Ignore DataFormat mode {} since SEI class is annotated with WebServiceProvider", getDataFormat());
        }

        if (isLoggingFeatureEnabled()) {
            LoggingFeature loggingFeature = new LoggingFeature();
            if (getLoggingSizeLimit() >= -1) {
                loggingFeature.setLimit(getLoggingSizeLimit());
            }
            sfb.getFeatures().add(loggingFeature);
        }

        if (getDataFormat() == DataFormat.PAYLOAD) {
            sfb.setDataBinding(new HybridSourceDataBinding());
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null && getDataFormat().dealias() != DataFormat.CXF_MESSAGE) {
            setWrapped(sfb, getWrappedStyle());
        }

        // any optional properties
        if (getProperties() != null) {
            if (sfb.getProperties() != null) {
                // add to existing properties
                sfb.getProperties().putAll(getProperties());
            } else {
                sfb.setProperties(getProperties());
            }
            LOG.debug("ServerFactoryBean: {} added properties: {}", sfb, getProperties());
        }
        if (this.isSkipPayloadMessagePartCheck()) {
            if (sfb.getProperties() == null) {
                sfb.setProperties(new HashMap<String, Object>());
            }
            sfb.getProperties().put("soap.no.validate.parts", Boolean.TRUE);
        }

        if (this.isSkipFaultLogging()) {
            if (sfb.getProperties() == null) {
                sfb.setProperties(new HashMap<String, Object>());
            }
            sfb.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }

        sfb.setBus(getBus());
        sfb.setStart(false);
        getNullSafeCxfConfigurer().configure(sfb);
    }

    /**
     * Create a client factory bean object.  Notice that the serviceClass <b>must</b> be
     * an interface.
     */
    protected ClientFactoryBean createClientFactoryBean(Class<?> cls) throws CamelException {
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            return new JaxWsClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            };
        } else {
            return new ClientFactoryBean() {
                @Override
                protected Client createClient(Endpoint ep) {
                    return new CamelCxfClientImpl(getBus(), ep);
                }
            };
        }
    }

    /**
     * Create a client factory bean object without serviceClass interface.
     */
    protected ClientFactoryBean createClientFactoryBean() {
        ClientFactoryBean cf = new ClientFactoryBean() {
            @Override
            protected Client createClient(Endpoint ep) {
                return new CamelCxfClientImpl(getBus(), ep);
            }

            @Override
            protected void initializeAnnotationInterceptors(Endpoint ep, Class<?> cls) {
                // Do nothing here
            }
        };
        for (Method m : cf.getClass().getMethods()) {
            if ("setServiceFactory".equals(m.getName())) {
                try {
                    // Set Object class as the service class of WSDLServiceFactoryBean
                    ReflectionUtil.setAccessible(m).invoke(cf, new WSDLServiceFactoryBean(Object.class));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return cf;
    }

    protected void setupHandlers(ClientFactoryBean factoryBean, Client client)
        throws Exception {

        if (handlers != null) {
            AnnotationHandlerChainBuilder
                builder = new AnnotationHandlerChainBuilder();
            Method m = factoryBean.getClass().getMethod("getServiceFactory");
            JaxWsServiceFactoryBean sf = (JaxWsServiceFactoryBean)m.invoke(factoryBean);
            @SuppressWarnings("rawtypes")
            List<Handler> chain = new ArrayList<>(handlers);

            chain.addAll(builder.buildHandlerChainFromClass(sf.getServiceClass(),
                                                            sf.getEndpointInfo().getName(),
                                                            sf.getServiceQName(),
                                                            factoryBean.getBindingId()));

            if (!chain.isEmpty()) {
                ResourceManager resourceManager = getBus().getExtension(ResourceManager.class);
                List<ResourceResolver> resolvers = resourceManager.getResourceResolvers();
                resourceManager = new DefaultResourceManager(resolvers);
                resourceManager.addResourceResolver(new WebServiceContextResourceResolver());
                ResourceInjector injector = new ResourceInjector(resourceManager);
                for (Handler<?> h : chain) {
                    if (Proxy.isProxyClass(h.getClass()) && getServiceClass() != null) {
                        injector.inject(h, getServiceClass());
                        injector.construct(h, getServiceClass());
                    } else {
                        injector.inject(h);
                        injector.construct(h);
                    }
                }
            }

            ((JaxWsEndpointImpl)client.getEndpoint()).getJaxwsBinding().setHandlerChain(chain);
        }
    }

    protected void setupClientFactoryBean(ClientFactoryBean factoryBean, Class<?> cls) {
        if (cls != null) {
            factoryBean.setServiceClass(cls);
        }
        factoryBean.setInInterceptors(in);
        factoryBean.setOutInterceptors(out);
        factoryBean.setOutFaultInterceptors(outFault);
        factoryBean.setInFaultInterceptors(inFault);
        factoryBean.setFeatures(features);
        factoryBean.setTransportId(transportId);
        factoryBean.setBindingId(bindingId);

        if (bindingConfig != null) {
            factoryBean.setBindingConfig(bindingConfig);
        }

        if (dataBinding != null) {
            factoryBean.setDataBinding(dataBinding);
        }

        if (serviceFactoryBean != null) {
            setServiceFactory(factoryBean, serviceFactoryBean);
        }

        // address
        factoryBean.setAddress(getAddress());

        // wsdl url
        if (getWsdlURL() != null) {
            factoryBean.setWsdlURL(getWsdlURL());
        }

        // service name qname
        if (getServiceNameAsQName() != null) {
            factoryBean.setServiceName(getServiceNameAsQName());
        }

        // port name qname
        if (getPortNameAsQName() != null) {
            factoryBean.setEndpointName(getPortNameAsQName());
        }

        // apply feature here
        if (getDataFormat().dealias() == DataFormat.RAW) {
            RAWDataFormatFeature feature = new RAWDataFormatFeature();
            feature.addInIntercepters(getInInterceptors());
            feature.addOutInterceptors(getOutInterceptors());
            factoryBean.getFeatures().add(feature);
        } else if (getDataFormat().dealias() == DataFormat.CXF_MESSAGE) {
            factoryBean.getFeatures().add(new CXFMessageDataFormatFeature());
            factoryBean.setDataBinding(new SourceDataBinding());
        } else if (getDataFormat() == DataFormat.PAYLOAD) {
            factoryBean.getFeatures().add(new PayLoadDataFormatFeature(allowStreaming));
            factoryBean.setDataBinding(new HybridSourceDataBinding());
        }

        if (isLoggingFeatureEnabled()) {
            LoggingFeature loggingFeature = new LoggingFeature();
            if (getLoggingSizeLimit() >= -1) {
                loggingFeature.setLimit(getLoggingSizeLimit());

            }
            factoryBean.getFeatures().add(loggingFeature);
        }

        // set the document-literal wrapped style
        if (getWrappedStyle() != null) {
            setWrapped(factoryBean, getWrappedStyle());
        }

        // any optional properties
        if (getProperties() != null) {
            if (factoryBean.getProperties() != null) {
                // add to existing properties
                factoryBean.getProperties().putAll(getProperties());
            } else {
                factoryBean.setProperties(getProperties());
            }
            LOG.debug("ClientFactoryBean: {} added properties: {}", factoryBean, getProperties());
        }

        // setup the basic authentication property
        if (ObjectHelper.isNotEmpty(username)) {
            AuthorizationPolicy authPolicy = new AuthorizationPolicy();
            authPolicy.setUserName(username);
            authPolicy.setPassword(password);
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<String, Object>());
            }
            factoryBean.getProperties().put(AuthorizationPolicy.class.getName(), authPolicy);
        }

        if (this.isSkipPayloadMessagePartCheck()) {
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<String, Object>());
            }
            factoryBean.getProperties().put("soap.no.validate.parts", Boolean.TRUE);
        }

        if (this.isSkipFaultLogging()) {
            if (factoryBean.getProperties() == null) {
                factoryBean.setProperties(new HashMap<String, Object>());
            }
            factoryBean.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }

        factoryBean.setBus(getBus());

        getNullSafeCxfConfigurer().configure(factoryBean);
    }

    // Package private methods
    // -------------------------------------------------------------------------

    private void setWrapped(Object factoryBean, boolean wrapped) {
        try {
            Object sf = factoryBean.getClass().getMethod("getServiceFactory").invoke(factoryBean);
            sf.getClass().getMethod("setWrapped", Boolean.TYPE).invoke(sf, wrapped);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void setServiceFactory(Object factoryBean, Object serviceFactoryBean2) {
        for (Method m : factoryBean.getClass().getMethods()) {
            if ("setServiceFactory".equals(m.getName())
                && m.getParameterTypes()[0].isInstance(serviceFactoryBean2)) {
                try {
                    ReflectionUtil.setAccessible(m).invoke(factoryBean, serviceFactoryBean2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

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

        Class<?> cls = getServiceClass();
        ClientFactoryBean factoryBean;
        if (cls != null) {
            // create client factory bean
            factoryBean = createClientFactoryBean(cls);
        } else {
            factoryBean = createClientFactoryBean();
        }
        
        // setup client factory bean
        setupClientFactoryBean(factoryBean, cls);
        
        if (cls == null) {
            checkName(factoryBean.getEndpointName(), "endpoint/port name");
            checkName(factoryBean.getServiceName(), "service name");
        }
        
        Client client = factoryBean.create();

        // setup the handlers
        setupHandlers(factoryBean, client);
        return client;
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
        if (getDataFormat() == DataFormat.POJO) {
            ObjectHelper.notNull(getServiceClass(), CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && getServiceClass() == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setServiceClass(org.apache.camel.component.cxf.DefaultPayloadProviderSEI.class.getName());
            }
        }

        if (getServiceClass() != null) {
            cls = getServiceClass();
        }

        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            checkName(portName, " endpoint/port name");
            checkName(serviceName, " service name");
            answer = new JaxWsServerFactoryBean(new WSDLServiceFactoryBean());
            cls = Provider.class;
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }

        // setup server factory bean
        setupServerFactoryBean(answer, cls);
        return answer;
    }

    protected String resolvePropertyPlaceholders(String str) {
        try {
            if (getCamelContext() != null) {
                return getCamelContext().resolvePropertyPlaceholders(str);
            } else {
                return str;
            }
        } catch (Exception ex) {
            throw RuntimeCamelException.wrapRuntimeCamelException(ex);
        }
    }

    // Properties
    // -------------------------------------------------------------------------


    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String beanId) {
        this.beanId = beanId;
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    /**
     * The data type messages supported by the CXF endpoint.
     */
    public void setDataFormat(DataFormat format) {
        dataFormat = format;
    }

    public String getPublishedEndpointUrl() {
        return resolvePropertyPlaceholders(publishedEndpointUrl);
    }

    /**
     * This option can override the endpointUrl that published from the WSDL which can be accessed with service address url plus ?wsd
     */
    public void setPublishedEndpointUrl(String url) {
        publishedEndpointUrl = url;
    }

    public String getWsdlURL() {
        return resolvePropertyPlaceholders(wsdlURL);
    }

    /**
     * The location of the WSDL. Can be on the classpath, file system, or be hosted remotely.
     */
    public void setWsdlURL(String url) {
        wsdlURL = url;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }

    /**
     * The class name of the SEI (Service Endpoint Interface) class which could have JSR181 annotation or not.
     */
    public void setServiceClass(Class<?> cls) {
        serviceClass = cls;
    }

    /**
     * The class name of the SEI (Service Endpoint Interface) class which could have JSR181 annotation or not.
     */
    public void setServiceClass(Object instance) {
        serviceClass = ClassHelper.getRealClass(instance);
    }

    /**
     * The class name of the SEI (Service Endpoint Interface) class which could have JSR181 annotation or not.
     */
    public void setServiceClass(String type) throws ClassNotFoundException {
        if (ObjectHelper.isEmpty(type)) {
            throw new IllegalArgumentException("The serviceClass option can neither be null nor an empty String.");
        }
        serviceClass = ClassLoaderUtils.loadClass(resolvePropertyPlaceholders(type), getClass());
    }

    /**
     * The service name this service is implementing, it maps to the wsdl:service@name.
     */
    public void setServiceName(String service) {
        serviceName = service;
    }

    public String getServiceName() {
        return serviceName;
    }

    public QName getServiceNameAsQName() {
        if (serviceNameQName == null && serviceName != null) {
            serviceNameQName = QName.valueOf(resolvePropertyPlaceholders(serviceName));
        }
        //if not specify the service name and if the wsdlUrl is available,
        //parse the wsdl to see if only one service in it, if so set the only service
        //from wsdl to avoid ambiguity
        if (serviceNameQName == null && getWsdlURL() != null) {
            // use wsdl manager to parse wsdl or get cached
            // definition
            try {
                Definition definition = getBus().getExtension(WSDLManager.class)
                        .getDefinition(getWsdlURL());
                if (definition.getServices().size() == 1) {
                    serviceNameQName = (QName) definition.getServices().keySet()
                        .iterator().next();

                }
            } catch (WSDLException e) {
                throw new RuntimeException(e);
            }
        }
        return serviceNameQName;
    }

    public void setServiceNameAsQName(QName qName) {
        this.serviceNameQName = qName;
    }

    public QName getPortNameAsQName() {
        if (portNameQName == null && portName != null) {
            portNameQName = QName.valueOf(resolvePropertyPlaceholders(portName));
        }
        return portNameQName;
    }

    public void setPortNameAsQName(QName qName) {
        this.portNameQName = qName;
    }

    public String getPortName() {
        return portName;
    }

    /**
     * The endpoint name this service is implementing, it maps to the wsdl:port@name. In the format of ns:PORT_NAME where ns is a namespace prefix valid at this scope.
     */
    public void setPortName(String port) {
        portName = port;
    }

    public void setEndpointName(String name) {
        // this is on purpose as camel-cxf in xml-dsl uses endpoint-name as port-name
        portName = name;
    }

    public void setEndpointNameAsQName(QName qName) {
        // this is on purpose as camel-cxf in xml-dsl uses endpoint-name as port-name
        portNameQName = qName;
    }

    public String getDefaultOperationName() {
        return resolvePropertyPlaceholders(defaultOperationName);
    }

    /**
     * This option will set the default operationName that will be used by the CxfProducer which invokes the remote service.
     */
    public void setDefaultOperationName(String name) {
        defaultOperationName = name;
    }

    public String getDefaultOperationNamespace() {
        return resolvePropertyPlaceholders(defaultOperationNamespace);
    }

    /**
     * This option will set the default operationNamespace that will be used by the CxfProducer which invokes the remote service.
     */
    public void setDefaultOperationNamespace(String namespace) {
        defaultOperationNamespace = namespace;
    }

    public boolean isWrapped() {
        return wrapped;
    }

    /**
     * Which kind of operation that CXF endpoint producer will invoke
     */
    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    public Boolean getWrappedStyle() {
        return wrappedStyle;
    }

    /**
     * The WSDL style that describes how parameters are represented in the SOAP body.
     * If the value is false, CXF will chose the document-literal unwrapped style,
     * If the value is true, CXF will chose the document-literal wrapped style
     */
    public void setWrappedStyle(Boolean wrapped) {
        wrappedStyle = wrapped;
    }

    /**
     * This option controls whether the CXF component, when running in PAYLOAD mode, will DOM parse the incoming messages
     * into DOM Elements or keep the payload as a javax.xml.transform.Source object that would allow streaming in some cases.
     */
    public void setAllowStreaming(Boolean allowStreaming) {
        this.allowStreaming = allowStreaming;
    }

    public Boolean getAllowStreaming() {
        return allowStreaming;
    }

    /**
     * To use a custom CxfBinding to control the binding between Camel Message and CXF Message.
     */
    public void setCxfBinding(CxfBinding cxfBinding) {
        this.cxfBinding = cxfBinding;
    }

    public CxfBinding getCxfBinding() {
        return cxfBinding;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        if (cxfBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) cxfBinding).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom configured CXF Bus.
     */
    public void setBus(Bus bus) {
        this.bus = bus;
        this.createBus = false;
    }

    public Bus getBus() {
        if (bus == null) {
            bus = CxfEndpointUtils.createBus(getCamelContext());
            this.createBus = true;
            LOG.debug("Using DefaultBus {}", bus);
        }

        if (!getBusHasBeenCalled.getAndSet(true) && defaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
        return bus;
    }

    /**
     * Will set the default bus when CXF endpoint create a bus by itself
     */
    public void setDefaultBus(boolean defaultBus) {
        this.defaultBus = defaultBus;
    }

    public boolean isDefaultBus() {
        return defaultBus;
    }

    /**
     * This option enables CXF Logging Feature which writes inbound and outbound SOAP messages to log.
     */
    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }

    public int getLoggingSizeLimit() {
        return loggingSizeLimit;
    }

    /**
     * To limit the total size of number of bytes the logger will output when logging feature has been enabled and -1 for no limit.
     */
    public void setLoggingSizeLimit(int loggingSizeLimit) {
        if (loggingSizeLimit < -1) {
            throw new IllegalArgumentException("LoggingSizeLimit must be greater or equal to -1.");
        }
        this.loggingSizeLimit = loggingSizeLimit;
    }

    public boolean isSkipPayloadMessagePartCheck() {
        return skipPayloadMessagePartCheck;
    }

    /**
     * Sets whether SOAP message validation should be disabled.
     */
    public void setSkipPayloadMessagePartCheck(boolean skipPayloadMessagePartCheck) {
        this.skipPayloadMessagePartCheck = skipPayloadMessagePartCheck;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public void setCamelContext(CamelContext c) {
        super.setCamelContext(c);
        if (this.properties != null) {
            try {
                PropertyBindingSupport.bindProperties(getCamelContext(),
                                             this,
                                             this.properties);
            } catch (Throwable e) {
                // TODO: Why dont't we rethrown this exception
                LOG.warn("Error setting CamelContext. This exception will be ignored.", e);
            }
        }
    }

    /**
     * To set additional CXF options using the key/value pairs from the Map.
     * For example to turn on stacktraces in SOAP faults, <tt>properties.faultStackTraceEnabled=true</tt>
     */
    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties.putAll(properties);
        }
        if (getCamelContext() != null && this.properties != null) {
            try {
                PropertyBindingSupport.bindProperties(getCamelContext(),
                                             this,
                                             this.properties);
            } catch (Throwable e) {
                // TODO: Why dont't we rethrown this exception
                LOG.warn("Error setting properties. This exception will be ignored.", e);
            }
        }
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
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
        // we should consider to shutdown the bus if the bus is created by cxfEndpoint
        if (createBus && bus != null) {
            LOG.info("shutdown the bus ... {}", bus);
            getBus().shutdown(false);
            // clean up the bus to create a new one if the endpoint is started again
            bus = null;
        }
    }

    /**
     * The service publish address.
     */
    public void setAddress(String address) {
        super.setEndpointUri(UnsafeUriCharactersEncoder.encodeHttpURI(address));
        this.address = address;
    }

    public String getAddress() {
        return resolvePropertyPlaceholders(address);
    }

    /**
     * To enable MTOM (attachments). This requires to use POJO or PAYLOAD data format mode.
     */
    public void setMtomEnabled(boolean mtomEnabled) {
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() {
        return mtomEnabled;
    }

    public String getPassword() {
        return password;
    }

    /**
     * This option is used to set the basic authentication information of password for the CXF client.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    /**
     * This option is used to set the basic authentication information of username for the CXF client.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public CxfConfigurer getChainedCxfConfigurer() {
        return ChainedCxfConfigurer
                .create(getNullSafeCxfConfigurer(),
                        SslCxfConfigurer.create(sslContextParameters, getCamelContext()))
                .addChild(HostnameVerifierCxfConfigurer.create(hostnameVerifier));
    }

    private CxfConfigurer getNullSafeCxfConfigurer() {
        if (cxfConfigurer == null) {
            return new ChainedCxfConfigurer.NullCxfConfigurer();
        } else {
            return cxfConfigurer;
        }
    }

    /**
     * We need to override the {@link ClientImpl#setParameters} method
     * to insert parameters into CXF Message for {@link DataFormat#PAYLOAD} mode.
     */
    class CamelCxfClientImpl extends ClientImpl {

        CamelCxfClientImpl(Bus bus, Endpoint ep) {
            super(bus, ep);
        }
  
        @Override
        protected Object[] processResult(Message message, org.apache.cxf.message.Exchange exchange,
                                         BindingOperationInfo oi, Map<String, Object> resContext)
                                             throws Exception {
            try {
                return super.processResult(message, exchange, oi, resContext);
            } catch (IllegalEmptyResponseException ex) {
                //Camel does not strickly enforce returning a value when a value is required from the WSDL/contract
                //Thus, we'll capture the exception raised and return a null
                return null;
            }
        }
        
        @SuppressWarnings("unchecked")
        @Override
        protected void setParameters(Object[] params, Message message) {

            Object attachments = message.get(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            if (attachments != null) {
                message.setAttachments((Collection<Attachment>) attachments);
                message.remove(CxfConstants.CAMEL_CXF_ATTACHMENTS);
            }

            // Don't try to reset the parameters if the parameter is not CxfPayload instance
            // as the setParameter will be called more than once when using the fail over feature
            if (DataFormat.PAYLOAD == message.get(DataFormat.class) && params[0] instanceof CxfPayload) {

                CxfPayload<?> payload = (CxfPayload<?>) params[0];
                List<Source> elements = payload.getBodySources();

                BindingOperationInfo boi = message.get(BindingOperationInfo.class);
                MessageContentsList content = new MessageContentsList();
                int i = 0;

                for (MessagePartInfo partInfo : boi.getInput().getMessageParts()) {
                    if (elements.size() > i) {
                        if (isSkipPayloadMessagePartCheck()) {
                            content.put(partInfo, elements.get(i++));
                        } else {
                            String name = findName(elements, i);
                            if (partInfo.getConcreteName().getLocalPart().equals(name)) {
                                content.put(partInfo, elements.get(i++));
                            }
                        }
                    }
                }

                if (elements != null && content.size() < elements.size()) {
                    throw new IllegalArgumentException("The PayLoad elements cannot fit with the message parts of the BindingOperation. Please check the BindingOperation and PayLoadMessage.");
                }

                message.setContent(List.class, content);
                // merge header list from request context with header list from CXF payload
                List<Object> headerListOfRequestContxt = (List<Object>)message.get(Header.HEADER_LIST);
                List<Object> headerListOfPayload = CastUtils.cast(payload.getHeaders());
                if (headerListOfRequestContxt == headerListOfPayload) {
                     // == is correct, we want to compare the object instances
                    // nothing to do, this can happen when the CXF payload is already created in the from-cxf-endpoint and then forwarded to a to-cxf-endpoint
                } else {
                    if (headerListOfRequestContxt == null) {
                        message.put(Header.HEADER_LIST, payload.getHeaders());
                    } else {
                        headerListOfRequestContxt.addAll(headerListOfPayload);
                    }
                }
            } else {
                super.setParameters(params, message);
            }

            message.remove(DataFormat.class.getName());
        }

        private String findName(List<Source> sources, int i) {
            Source source = sources.get(i);
            XMLStreamReader r = null;
            if (source instanceof DOMSource) {
                Node nd = ((DOMSource)source).getNode();
                if (nd instanceof Document) {
                    nd = ((Document)nd).getDocumentElement();
                }
                return nd.getLocalName();
            } else if (source instanceof StaxSource) {
                StaxSource s = (StaxSource)source;
                r = s.getXMLStreamReader();
            } else if (source instanceof StAXSource) {
                StAXSource s = (StAXSource)source;
                r = s.getXMLStreamReader();
            } else if (source instanceof StreamSource || source instanceof SAXSource) {
                //flip to stax so we can get the name
                r = StaxUtils.createXMLStreamReader(source);
                StaxSource src2 = new StaxSource(r);
                sources.set(i, src2);
            }
            if (r != null) {
                try {
                    if (r.getEventType() == XMLStreamConstants.START_DOCUMENT) {
                        r.next();
                    }
                    if (r.getEventType() != XMLStreamConstants.START_ELEMENT) {
                        r.nextTag();
                    }
                } catch (XMLStreamException e) {
                    //ignore
                    LOG.warn("Error finding the start element.", e);
                    return null;
                }
                return r.getLocalName();
            }
            return null;
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

    public void setFeatures(List<Feature> f) {
        features = f;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    @SuppressWarnings("rawtypes")
    public void setHandlers(List<Handler> h) {
        handlers = h;
    }

    @SuppressWarnings("rawtypes")
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
        return resolvePropertyPlaceholders(transportId);
    }

    public void setTransportId(String transportId) {
        this.transportId = transportId;
    }

    public String getBindingId() {
        return resolvePropertyPlaceholders(bindingId);
    }

    /**
     * The bindingId for the service model to use.
     */
    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public BindingConfiguration getBindingConfig() {
        return bindingConfig;
    }

    public boolean isSkipFaultLogging() {
        return skipFaultLogging;
    }

    /**
     * This option controls whether the PhaseInterceptorChain skips logging the Fault that it catches.
     */
    public void setSkipFaultLogging(boolean skipFaultLogging) {
        this.skipFaultLogging = skipFaultLogging;
    }

    public boolean isMergeProtocolHeaders() {
        return mergeProtocolHeaders;
    }

    /**
     * Whether to merge protocol headers. If enabled then propagating headers between Camel and CXF becomes more consistent and similar. For more details see CAMEL-6393.
     */
    public void setMergeProtocolHeaders(boolean mergeProtocolHeaders) {
        this.mergeProtocolHeaders = mergeProtocolHeaders;
    }

    public void setBindingConfig(BindingConfiguration bindingConfig) {
        this.bindingConfig = bindingConfig;
    }

    public DataBinding getDataBinding() {
        return dataBinding;
    }

    public void setDataBinding(DataBinding dataBinding) {
        this.dataBinding = dataBinding;
    }

    public Object getServiceFactoryBean() {
        return serviceFactoryBean;
    }

    public void setServiceFactoryBean(Object serviceFactoryBean) {
        this.serviceFactoryBean = serviceFactoryBean;
    }

    public void setServiceFactory(Object serviceFactoryBean) {
        // needed a setter with this name as the cxf namespace parser expects this name
        this.serviceFactoryBean = serviceFactoryBean;
    }

    public CxfConfigurer getCxfConfigurer() {
        return cxfConfigurer;
    }

    /**
     * This option could apply the implementation of org.apache.camel.component.cxf.CxfEndpointConfigurer which supports to configure the CXF endpoint
     * in  programmatic way. User can configure the CXF server and client by implementing configure{Server|Client} method of CxfEndpointConfigurer.
     */
    public void setCxfConfigurer(CxfConfigurer configurer) {
        this.cxfConfigurer = configurer;
    }

    public long getContinuationTimeout() {
        return continuationTimeout;
    }

    /**
     * This option is used to set the CXF continuation timeout which could be used in CxfConsumer by default when the CXF server is using Jetty or Servlet transport.
     */
    public void setContinuationTimeout(long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * The Camel SSL setting reference. Use the # notation to reference the SSL Context.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * The hostname verifier to be used. Use the # notation to reference a HostnameVerifier
     * from the registry.
     */
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    /**
     * get the request uri for a given exchange.
     */
    URI getRequestUri(Exchange camelExchange) {
        String uriString = camelExchange.getIn().getHeader(Exchange.DESTINATION_OVERRIDE_URL, String.class);
        if (uriString == null) {
            uriString = getAddress();
        }
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            LOG.error("cannot determine request URI", e);
            return null;
        }
    }
}
