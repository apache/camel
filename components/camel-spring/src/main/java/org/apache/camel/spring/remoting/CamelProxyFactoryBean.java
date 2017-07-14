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
package org.apache.camel.spring.remoting;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Producer;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.remoting.support.UrlBasedRemoteAccessor;

/**
 * A {@link FactoryBean} to create a Proxy to a a Camel Pojo Endpoint.
 */
public class CamelProxyFactoryBean extends UrlBasedRemoteAccessor implements FactoryBean<Object>, CamelContextAware, DisposableBean, ApplicationContextAware {
    private String serviceRef;
    private CamelContext camelContext;
    private String camelContextId;
    private Boolean binding;
    private ApplicationContext applicationContext;
    private Endpoint endpoint;
    private Object serviceProxy;
    private Producer producer;

    @Override
    public void afterPropertiesSet() {
        if (endpoint == null) {
            if (ObjectHelper.isNotEmpty(camelContextId)) {
                camelContext = CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
            }
            if (camelContext == null) {
                throw new IllegalArgumentException("camelContext or camelContextId must be specified");
            }
            
            if (getServiceUrl() == null && getServiceRef() == null) {
                throw new IllegalArgumentException("serviceUrl or serviceRef must be specified.");
            }

            // lookup endpoint or we have the url for it
            if (getServiceRef() != null) {
                endpoint = camelContext.getRegistry().lookupByNameAndType(getServiceRef(), Endpoint.class);
            } else {
                endpoint = camelContext.getEndpoint(getServiceUrl());
            }

            if (endpoint == null) {
                throw new IllegalArgumentException("Could not resolve endpoint: " + getServiceUrl());
            }
        }

        // binding is enabled by default
        boolean bind = getBinding() != null ? getBinding() : true;

        try {
            // need to start endpoint before we create producer
            ServiceHelper.startService(endpoint);
            producer = endpoint.createProducer();
            // add and start producer
            camelContext.addService(producer, true, true);
            serviceProxy = ProxyHelper.createProxy(endpoint, bind, producer, getServiceInterface());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
    }

    public void destroy() throws Exception {
        // we let CamelContext manage the lifecycle of the producer and shut it down when Camel stops
    }

    public Class<?> getServiceInterface() {
        return super.getServiceInterface();
    }

    public String getServiceUrl() {
        return super.getServiceUrl();
    }

    public Object getObject() throws Exception {
        return serviceProxy;
    }

    public Class<?> getObjectType() {
        return getServiceInterface();
    }

    public boolean isSingleton() {
        return true;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public Boolean getBinding() {
        return binding;
    }

    public void setBinding(Boolean binding) {
        this.binding = binding;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
    
    public void setCamelContextId(String contextId) {
        this.camelContextId = contextId;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
