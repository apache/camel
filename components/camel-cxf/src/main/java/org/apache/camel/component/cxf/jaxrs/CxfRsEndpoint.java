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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.cxf.NullFaultListener;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.util.ModCountCopyOnWriteArrayList;
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

@UriEndpoint(scheme = "cxfrs", title = "CXF-RS", syntax = "cxfrs:beanId:address", consumerClass = CxfRsConsumer.class, label = "rest")
public class CxfRsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware, Service {

    public enum BindingStyle {
        /**
         * <i>Only available for consumers.</i>
         * This binding style processes request parameters, multiparts, etc. and maps them to IN headers, IN attachments and to the message body.
         * It aims to eliminate low-level processing of {@link org.apache.cxf.message.MessageContentsList}.
         * It also also adds more flexibility and simplicity to the response mapping.
         */
        SimpleConsumer,

        /**
         * This is the traditional binding style, which simply dumps the {@link org.apache.cxf.message.MessageContentsList} coming in from the CXF stack
         * onto the IN message body. The user is then responsible for processing it according to the contract defined by the JAX-RS method signature.
         */
        Default,

        /**
         * A custom binding set by the user.
         */
        Custom
    }

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsEndpoint.class);

    protected Bus bus;

    protected List<Object> entityProviders = new LinkedList<Object>();

    protected List<String> schemaLocations;

    @UriPath(description = "To lookup an existing configured CxfRsEndpoint. Must used bean: as prefix.")
    private String beanId;
    @UriPath
    private String address;

    private Map<String, String> parameters;
    private List<Class<?>> resourceClasses;
    private HeaderFilterStrategy headerFilterStrategy;
    private CxfRsBinding binding;
    @UriParam(defaultValue = "true")
    private boolean httpClientAPI = true;
    @UriParam
    private boolean ignoreDeleteMethodMessageBody;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(defaultValue = "10")
    private int maxClientCacheSize = 10;
    @UriParam
    private boolean loggingFeatureEnabled;
    @UriParam
    private int loggingSizeLimit;
    @UriParam
    private boolean skipFaultLogging;
    @UriParam(defaultValue = "Default")
    private BindingStyle bindingStyle = BindingStyle.Default;
    // The continuation timeout value for CXF continuation to use
    @UriParam(defaultValue = "30000")
    private long continuationTimeout = 30000;
    @UriParam
    private boolean isSetDefaultBus;
    @UriParam
    private boolean performInvocation;
    @UriParam
    private boolean propagateContexts;
    @UriParam
    private String modelRef;
    private List<Feature> features = new ModCountCopyOnWriteArrayList<Feature>();
    private InterceptorHolder interceptorHolder = new InterceptorHolder();
    private Map<String, Object> properties;

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

    public void setHttpClientAPI(boolean clientAPI) {
        httpClientAPI = clientAPI;
    }

    public boolean isHttpClientAPI() {
        return httpClientAPI;
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

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
        return new CxfRsProducer(this);
    }

    public boolean isSingleton() {
        return false;
    }

    public void setBinding(CxfRsBinding binding) {
        this.binding = binding;
    }

    public CxfRsBinding getBinding() {
        return binding;
    }

    public boolean isSkipFaultLogging() {
        return skipFaultLogging;
    }

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
            if (resource.getName() == null) {
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

    public void setResourceClasses(List<Class<?>> resourceClasses) {
        this.resourceClasses = resourceClasses;
    }

    public void setResourceClasses(Class<?>... classes) {
        setResourceClasses(Arrays.asList(classes));
    }

    public void setAddress(String address) {
        this.address = address;
    }
    public void setModelRef(String ref) {
        this.modelRef = ref;
    }
    public String getAddress() {
        return resolvePropertyPlaceholders(address);
    }

    public boolean isLoggingFeatureEnabled() {
        return loggingFeatureEnabled;
    }

    public void setLoggingFeatureEnabled(boolean loggingFeatureEnabled) {
        this.loggingFeatureEnabled = loggingFeatureEnabled;
    }

    public int getLoggingSizeLimit() {
        return loggingSizeLimit;
    }

    public void setLoggingSizeLimit(int loggingSizeLimit) {
        this.loggingSizeLimit = loggingSizeLimit;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    /**
     * @param maxClientCacheSize the maxClientCacheSize to set
     */
    public void setMaxClientCacheSize(int maxClientCacheSize) {
        this.maxClientCacheSize = maxClientCacheSize;
    }

    /**
     * @return the maxClientCacheSize
     */
    public int getMaxClientCacheSize() {
        return maxClientCacheSize;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
        if (isSetDefaultBus) {
            BusFactory.setDefaultBus(bus);
            LOG.debug("Set bus {} as thread default bus", bus);
        }
    }

    public Bus getBus() {
        return bus;
    }

    public void setSetDefaultBus(boolean isSetDefaultBus) {
        this.isSetDefaultBus = isSetDefaultBus;
    }

    public boolean isSetDefaultBus() {
        return isSetDefaultBus;
    }

    public boolean isIgnoreDeleteMethodMessageBody() {
        return ignoreDeleteMethodMessageBody;
    }

    public void setIgnoreDeleteMethodMessageBody(boolean ignoreDeleteMethodMessageBody) {
        this.ignoreDeleteMethodMessageBody = ignoreDeleteMethodMessageBody;
    }

    public BindingStyle getBindingStyle() {
        return bindingStyle;
    }

    public List<?> getProviders() {
        return entityProviders;
    }

    public void setProviders(List<?> providers) {
        this.entityProviders.addAll(providers);
    }

    public void setProvider(Object provider) {
        entityProviders.add(provider);
    }

    public void setSchemaLocation(String schema) {
        setSchemaLocations(Collections.singletonList(schema));
    }

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

    public void setInInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInInterceptors(interceptors);
    }

    public void setInFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setInFaultInterceptors(interceptors);
    }

    public void setOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutInterceptors(interceptors);
    }

    public void setOutFaultInterceptors(List<Interceptor<? extends Message>> interceptors) {
        interceptorHolder.setOutFaultInterceptors(interceptors);
    }

    public List<Feature> getFeatures() {
        return features;
    }

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
     * See documentation of {@link BindingStyle}.
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
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }


    public long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }


    private static class InterceptorHolder extends AbstractBasicInterceptorProvider {
    }


    public boolean isPerformInvocation() {
        return performInvocation;
    }

    public void setPerformInvocation(boolean performInvocation) {
        this.performInvocation = performInvocation;
    }

    public boolean isPropagateContexts() {
        return propagateContexts;
    }

    public void setPropagateContexts(boolean propagateContexts) {
        this.propagateContexts = propagateContexts;
    }
}
