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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.spring.CxfRsClientFactoryBeanDefinitionParser.SpringJAXRSClientFactoryBean;
import org.apache.camel.component.cxf.spring.CxfRsServerFactoryBeanDefinitionParser.SpringJAXRSServerFactoryBean;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;

public class CxfRsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    private static final Log LOG = LogFactory.getLog(CxfRsEndpoint.class);

    private Map<String, String> parameters;
    private List<Class<?>> resourceClasses;
    private HeaderFilterStrategy headerFilterStrategy;
    private CxfRsBinding binding;
    private boolean httpClientAPI = true;
    private String address;
    private boolean throwExceptionOnFailure = true;
    private int maxClientCacheSize = 10;
    
    private AtomicBoolean bindingInitialized = new AtomicBoolean(false);

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
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new CxfRsHeaderFilterStrategy();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Create default header filter strategy " + headerFilterStrategy);
            }
        }
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
        if (binding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) binding).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CxfRsConsumer(this, processor);
    }

    public Producer createProducer() throws Exception {
        return new CxfRsProducer(this);
    }

    public boolean isSingleton() {
        return false;
    }

    public void setBinding(CxfRsBinding binding) {
        this.binding = binding;
        bindingInitialized.set(false);
    }

    public synchronized CxfRsBinding getBinding() {
        if (binding == null) {
            binding = new DefaultCxfRsBinding();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Create default CXF Binding " + binding);
            }
        }

        if (!bindingInitialized.getAndSet(true) && binding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) binding).setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        return binding;
    }

    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {
        // address
        sfb.setAddress(getAddress());
        sfb.setResourceClasses(CastUtils.cast(getResourceClasses(), Class.class));
        sfb.setStart(false);
    }

    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        // address
        cfb.setAddress(address);
        if (getResourceClasses() != null) {
            cfb.setResourceClass(getResourceClasses().get(0));
        }
    }

    public JAXRSServerFactoryBean createJAXRSServerFactoryBean() {
        JAXRSServerFactoryBean answer = new SpringJAXRSServerFactoryBean();
        setupJAXRSServerFactoryBean(answer);
        return answer;
    }

    public JAXRSClientFactoryBean createJAXRSClientFactoryBean() {
        return createJAXRSClientFactoryBean(getAddress());
    }
    
    public JAXRSClientFactoryBean createJAXRSClientFactoryBean(String address) {
        JAXRSClientFactoryBean answer = new SpringJAXRSClientFactoryBean();
        setupJAXRSClientFactoryBean(answer, address);
        return answer;
    }

    public List<Class<?>> getResourceClasses() {
        return resourceClasses;
    }

    public void setResourceClasses(List<Class<?>> classes) {
        resourceClasses = classes;
    }

    public void setResourceClasses(Class<?>... classes) {
        setResourceClasses(Arrays.asList(classes));
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
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
}
