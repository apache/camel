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
package org.apache.camel.component.cxf.jaxrs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.cxf.NullFaultListener;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.interceptor.AbstractBasicInterceptorProvider;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.apache.cxf.logging.FaultListener;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cxfrs component is used for JAX-RS REST services using Apache CXF.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "cxfrs", title = "CXF-RS", syntax = "cxfrs:beanId:address", consumerClass = CxfRsConsumer.class, label = "rest", lenientProperties = true)
public class CxfRsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware, Service {

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsEndpoint.class);

    @UriParam(label = "advanced")
    protected Bus bus;

    private final InterceptorHolder interceptorHolder = new InterceptorHolder();

    private Map<String, String> parameters;
    private Map<String, Object> properties;

    @UriPath(description = "To lookup an existing configured CxfRsEndpoint. Must used bean: as prefix.")
    private String beanId;
    @UriPath
    private String address;
    @UriParam
    private List<Class<?>> resourceClasses;
    @UriParam
    private String modelRef;
    @UriParam(label = "consumer", defaultValue = "Default")
    private BindingStyle bindingStyle = BindingStyle.Default;
    @UriParam(label = "consumer")
    private String publishedEndpointUrl;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced")
    private CxfRsBinding binding;
    @UriParam(javaType = "java.lang.String")
    private List<Object> providers = new LinkedList<Object>();
    private String providersRef;
    @UriParam
    private List<String> schemaLocations;
    @UriParam
    private List<Feature> features = new ModCountCopyOnWriteArrayList<Feature>();
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean httpClientAPI = true;
    @UriParam(label = "producer,advanced")
    private boolean ignoreDeleteMethodMessageBody;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer,advanced", defaultValue = "10")
    private int maxClientCacheSize = 10;
    @UriParam(label = "producer")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer")
    private HostnameVerifier hostnameVerifier;
    @UriParam
    private boolean loggingFeatureEnabled;
    @UriParam
    private int loggingSizeLimit;
    @UriParam
    private boolean skipFaultLogging;
    @UriParam(label = "advanced", defaultValue = "30000")
    private long continuationTimeout = 30000;
    @UriParam(label = "advanced")
    private boolean defaultBus;
    @UriParam(label = "advanced")
    private boolean performInvocation;
    @UriParam(label = "advanced")
    private boolean propagateContexts;
    @UriParam(label = "advanced")
    private CxfRsEndpointConfigurer cxfRsEndpointConfigurer;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;

    public CxfRsEndpoint() {
    }

    @Deprecated
    public CxfRsEndpoint(String endpointUri, CamelContext camelContext) {
        super(endpointUri, camelContext);
        setAddress(endpointUri);
    }

    public CxfRsEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        setAddress(endpointUri);
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    // This method is for CxfRsComponent setting the EndpointUri
    protected void updateEndpointUri(String endpointUri) {
        super.setEndpointUri(endpointUri);
    }

    public void setParameters(Map<String, String> param) {
        parameters = param;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * If it is true, the CxfRsProducer will use the HttpClientAPI to invoke the service.
     * If it is false, the CxfRsProducer will use the ProxyClientAPI to invoke the service
     */
    public void setHttpClientAPI(boolean clientAPI) {
        httpClientAPI = clientAPI;
    }

    public boolean isHttpClientAPI() {
        return httpClientAPI;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        CxfRsConsumer answer = new CxfRsConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        if (bindingStyle == BindingStyle.SimpleConsumer) {
            throw new IllegalArgumentException("The SimpleConsumer Binding Style cannot be used in a camel-cxfrs producer");
        }
        final CxfRsProducer cxfRsProducer = new CxfRsProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(cxfRsProducer);
        } else {
            return cxfRsProducer;
        }
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * To use a custom CxfBinding to control the binding between Camel Message and CXF Message.
     */
    public void setBinding(CxfRsBinding binding) {
        this.binding = binding;
    }

    public CxfRsBinding getBinding() {
        return binding;
    }

    public boolean isSkipFaultLogging() {
        return skipFaultLogging;
    }

    public CxfRsEndpointConfigurer getChainedCxfRsEndpointConfigurer() {
        return ChainedCxfRsEndpointConfigurer
                .create(getNullSafeCxfRsEndpointConfigurer(),
                        SslCxfRsEndpointConfigurer.create(sslContextParameters, getCamelContext()))
                .addChild(HostnameVerifierCxfRsEndpointConfigurer.create(hostnameVerifier));
    }
    /**
     * This option controls whether the PhaseInterceptorChain skips logging the Fault that it catches.
     */
    public void setSkipFaultLogging(boolean skipFaultLogging) {
        this.skipFaultLogging = skipFaultLogging;
    }

    protected void checkBeanType(Object object, Class<?> clazz) {
        if (!clazz.isAssignableFrom(object.getClass())) {
            throw new IllegalArgumentException("The configure bean is not the instance of " + clazz.getName());
        }
    }

    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {
        // address
        if (getAddress() != null) {
            sfb.setAddress(getAddress());
        }
        processResourceModel(sfb);
        if (getResourceClasses() != null) {
            sfb.setResourceClasses(getResourceClasses());
        }

        // setup the resource providers for interfaces
        List<ClassResourceInfo> cris = sfb.getServiceFactory().getClassResourceInfo();
        for (ClassResourceInfo cri : cris) {
            final Class<?> serviceClass = cri.getServiceClass();
            if (serviceClass.isInterface()) {
                cri.setResourceProvider(new CamelResourceProvider(serviceClass));
            }
        }
        setupCommonFactoryProperties(sfb);
        sfb.setStart(false);
        getNullSafeCxfRsEndpointConfigurer().configure(sfb);
    }

    private CxfRsEndpointConfigurer getNullSafeCxfRsEndpointConfigurer() {
        if (cxfRsEndpointConfigurer == null) {
            return new ChainedCxfRsEndpointConfigurer.NullCxfRsEndpointConfigurer();
        }
        return cxfRsEndpointConfigurer;
    }

    private void processResourceModel(JAXRSServerFactoryBean sfb) {
        // Currently a CXF model document is the only possible source 
        // of the model. Other sources will be supported going forward
        if (modelRef != null) {

            List<UserResource> resources = ResourceUtils.getUserResources(modelRef, sfb.getBus());

            processUserResources(sfb, resources);
        }
    }

    /*
     * Prepare model beans and set them on the factory.
     * The model beans can be created from a variety of sources such as
     * CXF Model extensions but also other documents (to be supported in the future).
     */
    private void processUserResources(JAXRSServerFactoryBean sfb, List<UserResource> resources) {
        for (UserResource resource : resources) {
            if (StringUtils.isEmpty(resource.getName())) {
                resource.setName(DefaultModelResource.class.getName());
            }
        }
        // The CXF to Camel exchange binding may need to be customized 
        // for the operation name, request, response types be derived from
        // the model info (when a given model does provide this info) as opposed
        // to a matched method which is of no real use with a default handler. 
        sfb.setModelBeans(resources);

    }

    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        // address
        if (address != null) {
            cfb.setAddress(address);
        }
        if (modelRef != null) {
            cfb.setModelRef(modelRef);
        }
        if (getResourceClasses() != null && !getResourceClasses().isEmpty()) {
            cfb.setResourceClass(getResourceClasses().get(0));
            cfb.getServiceFactory().setResourceClasses(getResourceClasses());
        }
        setupCommonFactoryProperties(cfb);
        cfb.setThreadSafe(true);
        getNullSafeCxfRsEndpointConfigurer().configure(cfb);
    }

    protected void setupCommonFactoryProperties(AbstractJAXRSFactoryBean factory) {
        // let customer to override the default setting of provider
        if (!getProviders().isEmpty()) {
            factory.setProviders(getProviders());
        }
        // setup the features
        if (!getFeatures().isEmpty()) {
            factory.getFeatures().addAll(getFeatures());
        }

        if (publishedEndpointUrl != null) {
            factory.setPublishedEndpointUrl(publishedEndpointUrl);
        }

        // we need to avoid flushing the setting from spring or blueprint
        if (!interceptorHolder.getInInterceptors().isEmpty()) {
            factory.setInInterceptors(interceptorHolder.getInInterceptors());
        }
        if (!interceptorHolder.getOutInterceptors().isEmpty()) {
            factory.setOutInterceptors(interceptorHolder.getOutInterceptors());
        }
        if (!interceptorHolder.getOutFaultInterceptors().isEmpty()) {
            factory.setOutFaultInterceptors(interceptorHolder.getOutFaultInterceptors());
        }
        if (!interceptorHolder.getInFaultInterceptors().isEmpty()) {
            factory.setInFaultInterceptors(interceptorHolder.getInFaultInterceptors());
        }

        if (getProperties() != null) {
            if (factory.getProperties() != null) {
                // add to existing properties
                factory.getProperties().putAll(getProperties());
            } else {
                factory.setProperties(getProperties());
            }
            LOG.debug("JAXRS FactoryBean: {} added properties: {}", factory, getProperties());
        }

        if (isLoggingFeatureEnabled()) {
            if (getLoggingSizeLimit() > 0) {
                factory.getFeatures().add(new LoggingFeature(getLoggingSizeLimit()));
            } else {
                factory.getFeatures().add(new LoggingFeature());
            }
        }
        if (this.isSkipFaultLogging()) {
            if (factory.getProperties() == null) {
                factory.setProperties(new HashMap<String, Object>());
            }
            factory.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }
    }

    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        return new JAXRSServerFactoryBean() {
            protected boolean isValidClassResourceInfo(ClassResourceInfo cri) {
                // CXF will consider interfaces created for managing model resources
                // invalid - however it is fine with Camel processors if no service invocation
                // is requested.
                return !performInvocation || !cri.getServiceClass().isInterface();
            }
        };
    }

    protected JAXRSClientFactoryBean newJAXRSClientFactoryBean() {
        return new JAXRSClientFactoryBean();
    }

    protected String resolvePropertyPlaceholders(String str) {
        try {
            if (getCamelContext() != null) {
                return getCamelContext().resolvePropertyPlaceholders(str);
            } else {
                return str;
            }
        } catch (Exception ex) {
            throw ObjectHelper.wrapRuntimeCamelException(ex);
        }
    }

    public JAXRSServerFactoryBean createJAXRSServerFactoryBean() {
        JAXRSServerFactoryBean answer = newJAXRSServerFactoryBean();
        setupJAXRSServerFactoryBean(answer);
        return answer;
    }

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean() {
        return createJAXRSClientFactoryBean(getAddress());
    }

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean(String address) {
        JAXRSClientFactoryBean answer = newJAXRSClientFactoryBean();
        setupJAXRSClientFactoryBean(answer, address);
        return answer;
    }

    public List<Class<?>> getResourceClasses() {
        return resourceClasses;
    }

    public void addResourceClass(Class<?> resourceClass) {
        if (resourceClasses == null) {
            resourceClasses = new ArrayList<Class<?>>();
        }
        resourceClasses.add(resourceClass);
    }

    /**
     * The resource classes which you want to export as REST service. Multiple classes can be separated by comma.
     */
    public void setResourceClasses(List<Class<?>> resourceClasses) {
        this.resourceClasses = resourceClasses;
    }

    public void setResourceClasses(Class<?>... classes) {
        setResourceClasses(Arrays.asList(classes));
    }

    /**
     * The service publish address.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * This option is used to specify the model file which is useful for the resource class without annotation.
     * When using this option, then the service class can be omitted, to emulate document-only endpoints
     */
    public void setModelRef(String ref) {
        this.modelRef = ref;
    }

    public String getAddress() {
        return resolvePropertyPlaceholders(address);
    }

    public String getPublishedEndpointUrl() {
        return publishedEndpointUrl;
    }

    /**
     * This option can override the endpointUrl that published from the WADL which can be accessed with resource address url plus ?_wadl
     */
    public void setPublishedEndpointUrl(String publishedEndpointUrl) {
        this.publishedEndpointUrl = publishedEndpointUrl;
    }

    /**
     * This option enables CXF Logging Feature which writes inbound and outbound REST messages to log.
     */
    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public int getLoggingSizeLimit() {
        return loggingSizeLimit;
    }

    /**
     * To limit the total size of number of bytes the logger will output when logging feature has been enabled.
     */
    public void setLoggingSizeLimit(int loggingSizeLimit) {
        this.loggingSizeLimit = loggingSizeLimit;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * This option tells the CxfRsProducer to inspect return codes and will generate an Exception if the return code is larger than 207.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    /**
     * This option allows you to configure the maximum size of the cache.
     * The implementation caches CXF clients or ClientFactoryBean in CxfProvider and CxfRsProvider.
     */
    public void setMaxClientCacheSize(int maxClientCacheSize) {
        this.maxClientCacheSize = maxClientCacheSize;
    }

    public int getMaxClientCacheSize() {
        return maxClientCacheSize;
    }

    /**
     * To use a custom configured CXF Bus.
     */
    public void setBus(Bus bus) {
        this.bus = bus;
        if (defaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
    }

    public Bus getBus() {
        return bus;
    }

    /**
     * Will set the default bus when CXF endpoint create a bus by itself
     */
    public void setDefaultBus(boolean isSetDefaultBus) {
        this.defaultBus = isSetDefaultBus;
    }

    public boolean isDefaultBus() {
        return defaultBus;
    }

    public boolean isIgnoreDeleteMethodMessageBody() {
        return ignoreDeleteMethodMessageBody;
    }

    /**
     * This option is used to tell CxfRsProducer to ignore the message body of the DELETE method when using HTTP API.
     */
    public void setIgnoreDeleteMethodMessageBody(boolean ignoreDeleteMethodMessageBody) {
        this.ignoreDeleteMethodMessageBody = ignoreDeleteMethodMessageBody;
    }

    public BindingStyle getBindingStyle() {
        return bindingStyle;
    }

    public List<?> getProviders() {
        return providers;
    }

    /**
     * Set custom JAX-RS provider(s) list to the CxfRs endpoint.
     * You can specify a string with a list of providers to lookup in the registy separated by comma.
     */
    public void setProviders(List<?> providers) {
        this.providers.addAll(providers);
    }

    /**
     * Set custom JAX-RS provider(s) list which is looked up in the registry. Multiple entries can be separated by comma.
     */
    public void setProviders(String providers) {
        this.providersRef = providers;
    }

    /**
     * Set custom JAX-RS provider to the CxfRs endpoint.
     */
    public void setProvider(Object provider) {
        providers.add(provider);
    }

    /**
     * Sets the locations of the schema(s) which can be used to validate the incoming XML or JAXB-driven JSON.
     */
    public void setSchemaLocation(String schema) {
        setSchemaLocations(Collections.singletonList(schema));
    }

    /**
     * Sets the locations of the schema(s) which can be used to validate the incoming XML or JAXB-driven JSON.
     */
    public void setSchemaLocations(List<String> schemas) {
        this.schemaLocations = schemas;
    }

    public List<String> getSchemaLocations() {
        return schemaLocations;
    }

    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return interceptorHolder.getOutFaultInterceptors();
    }

    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return interceptorHolder.getInFaultInterceptors();
    }

    public List<Interceptor<? extends Message>> getInInterceptors() {
        return interceptorHolder.getInInterceptors();
    }

    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return interceptorHolder.getOutInterceptors();
    }

    /**
     * Set the inInterceptors to the CxfRs endpoint.
     */
    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInInterceptors(interceptors);
    }

    /**
     * Set the inFaultInterceptors to the CxfRs endpoint.
     */
    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInFaultInterceptors(interceptors);
    }

    /**
     * Set the outInterceptor to the CxfRs endpoint.
     */
    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutInterceptors(interceptors);
    }

    /**
     * Set the outFaultInterceptors to the CxfRs endpoint.
     */
    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutFaultInterceptors(interceptors);
    }

    public List<Feature> getFeatures() {
        return features;
    }

    /**
     * Set the feature list to the CxfRs endpoint.
     */
    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties.putAll(properties);
        }
    }

    /**
     *  Sets how requests and responses will be mapped to/from Camel. Two values are possible:
     *  <ul>
     *      <li>SimpleConsumer: This binding style processes request parameters, multiparts, etc. and maps them to IN headers, IN attachments and to the message body.
     *                          It aims to eliminate low-level processing of {@link org.apache.cxf.message.MessageContentsList}.
     *                          It also also adds more flexibility and simplicity to the response mapping.
     *                          Only available for consumers.
     *      </li>
     *      <li>Default: The default style. For consumers this passes on a MessageContentsList to the route, requiring low-level processing in the route.
     *                   This is the traditional binding style, which simply dumps the {@link org.apache.cxf.message.MessageContentsList} coming in from the CXF stack
     *                   onto the IN message body. The user is then responsible for processing it according to the contract defined by the JAX-RS method signature.
     *      </li>
     *      <li>Custom: allows you to specify a custom binding through the binding option.</li>
     *  </ul>
     */
    public void setBindingStyle(BindingStyle bindingStyle) {
        this.bindingStyle = bindingStyle;
    }

    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String beanId) {
        this.beanId = beanId;
    }

    @Override
    protected void doStart() throws Exception {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfRsHeaderFilterStrategy();
        }

        // if the user explicitly selected the Custom binding style, he must provide a binding
        if (bindingStyle == BindingStyle.Custom && binding == null) {
            throw new IllegalArgumentException("Custom binding style selected, but no binding was supplied");
        }

        // if the user has set a binding, do nothing, just make sure that BindingStyle = Custom for coherency purposes
        if (binding != null) {
            bindingStyle = BindingStyle.Custom;
        }

        // set the right binding based on the binding style
        if (bindingStyle == BindingStyle.SimpleConsumer) {
            binding = new SimpleCxfRsBinding();
        } else if (bindingStyle == BindingStyle.Custom) {
            // do nothing
        } else {
            binding = new DefaultCxfRsBinding();
        }

        if (binding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) binding).setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        if (providersRef != null) {
            String[] names = providersRef.split(",");
            for (String name : names) {
                Object provider = EndpointHelper.resolveReferenceParameter(getCamelContext(), name, Object.class, true);
                setProvider(provider);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
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

    public boolean isPerformInvocation() {
        return performInvocation;
    }

    /**
     * When the option is true, Camel will perform the invocation of the resource class instance and put the response object into the exchange for further processing.
     */
    public void setPerformInvocation(boolean performInvocation) {
        this.performInvocation = performInvocation;
    }

    public boolean isPropagateContexts() {
        return propagateContexts;
    }

    /**
     * When the option is true, JAXRS UriInfo, HttpHeaders, Request and SecurityContext contexts will be available to
     * custom CXFRS processors as typed Camel exchange properties.
     * These contexts can be used to analyze the current requests using JAX-RS API.
     */
    public void setPropagateContexts(boolean propagateContexts) {
        this.propagateContexts = propagateContexts;
    }

    private static class InterceptorHolder extends AbstractBasicInterceptorProvider {
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

    public CxfRsEndpointConfigurer getCxfRsEndpointConfigurer() {
        return cxfRsEndpointConfigurer;
    }

    /**
     * This option could apply the implementation of org.apache.camel.component.cxf.jaxrs.CxfRsEndpointConfigurer which supports to configure the CXF endpoint
     * in  programmatic way. User can configure the CXF server and client by implementing configure{Server/Client} method of CxfEndpointConfigurer.
     */
    public void setCxfRsEndpointConfigurer(CxfRsEndpointConfigurer configurer) {
        this.cxfRsEndpointConfigurer = configurer;
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
}
