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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.cxf.CxfEndpointUtils;
import org.apache.camel.component.cxf.NullFaultListener;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.logging.FaultListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Default
    }

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsEndpoint.class);

    protected Bus bus;
    
    protected List<Object> entityProviders = new LinkedList<Object>();
    
    protected List<String> schemaLocations;

    private Map<String, String> parameters;
    private List<Class<?>> resourceClasses;
    private HeaderFilterStrategy headerFilterStrategy;
    private CxfRsBinding binding;
    private boolean httpClientAPI = true;
    private String address;
    private boolean throwExceptionOnFailure = true;
    private int maxClientCacheSize = 10;
    private boolean loggingFeatureEnabled;
    private int loggingSizeLimit;
    private boolean skipFaultLogging;
    private BindingStyle bindingStyle = BindingStyle.Default;
    
    private AtomicBoolean getBusHasBeenCalled = new AtomicBoolean(false);

    private boolean isSetDefaultBus;
    
   

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
        if (getResourceClasses() != null) {
            List<Class<?>> res = getResourceClasses();
            // setup the resource providers
            for (Class<?> clazz : res) {
                sfb.setResourceProvider(clazz, new CamelResourceProvider(clazz));
            }
            sfb.setResourceClasses(res);
        }
        sfb.setStart(false);
    }

    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        // address
        if (address != null) {
            cfb.setAddress(address);
        }
        if (getResourceClasses() != null && !getResourceClasses().isEmpty()) {
            cfb.setResourceClass(getResourceClasses().get(0));
            cfb.getServiceFactory().setResourceClasses(getResourceClasses());
        }
        if (isLoggingFeatureEnabled()) {
            if (getLoggingSizeLimit() > 0) {
                cfb.getFeatures().add(new LoggingFeature(getLoggingSizeLimit()));
            } else {
                cfb.getFeatures().add(new LoggingFeature());
            }
        }
        if (this.isSkipFaultLogging()) {
            if (cfb.getProperties() == null) {
                cfb.setProperties(new HashMap<String, Object>());
            }
            cfb.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }
        cfb.setThreadSafe(true);
    }
    
    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        return new JAXRSServerFactoryBean();
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
        // let customer to override the default setting of provider
        if (!getProviders().isEmpty()) {
            answer.setProviders(getProviders());
        }
        if (schemaLocations != null) {
            answer.setSchemaLocations(schemaLocations);
        }
        if (isLoggingFeatureEnabled()) {
            if (getLoggingSizeLimit() > 0) {
                answer.getFeatures().add(new LoggingFeature(getLoggingSizeLimit()));
            } else {
                answer.getFeatures().add(new LoggingFeature());
            }
        }
        if (this.isSkipFaultLogging()) {
            if (answer.getProperties() == null) {
                answer.setProperties(new HashMap<String, Object>());
            }
            answer.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }
        return answer;
    }
    

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean() {
        return createJAXRSClientFactoryBean(getAddress());
    }
    
    public JAXRSClientFactoryBean createJAXRSClientFactoryBean(String address) {
        
        JAXRSClientFactoryBean answer = newJAXRSClientFactoryBean();
        setupJAXRSClientFactoryBean(answer, address);
        // let customer to override the default setting of provider
        if (!getProviders().isEmpty()) {
            answer.setProviders(getProviders());
        }
        if (schemaLocations != null) {
            answer.setSchemaLocations(schemaLocations);
        }
        if (isLoggingFeatureEnabled()) {
            if (getLoggingSizeLimit() > 0) {
                answer.getFeatures().add(new LoggingFeature(getLoggingSizeLimit()));
            } else {
                answer.getFeatures().add(new LoggingFeature());
            }
        }
        if (this.isSkipFaultLogging()) {
            if (answer.getProperties() == null) {
                answer.setProperties(new HashMap<String, Object>());
            }
            answer.getProperties().put(FaultListener.class.getName(), new NullFaultListener());
        }
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
    }

    public Bus getBus() {
        if (bus == null) {
            bus = CxfEndpointUtils.createBus(getCamelContext());
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
    
    public BindingStyle getBindingStyle() {
        return bindingStyle;
    }
    
    public List<?> getProviders() {
        return entityProviders;
    }
    
    public void setProviders(List<? extends Object> providers) {
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

    /**
     * See documentation of {@link BindingStyle}.
     */
    public void setBindingStyle(BindingStyle bindingStyle) {
        this.bindingStyle = bindingStyle;
    }
    
    @Override
    protected void doStart() throws Exception {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfRsHeaderFilterStrategy();
        }
        
        binding = bindingStyle == null || bindingStyle == BindingStyle.Default ? new DefaultCxfRsBinding() : new SimpleCxfRsBinding();
        
        if (binding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) binding).setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
