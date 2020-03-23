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
package org.apache.camel.spring.remoting;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.remoting.support.RemoteExporter;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A {@link FactoryBean} to create a proxy to a service exposing a given {@link #getServiceInterface()}
 */
public class CamelServiceExporter extends RemoteExporter implements InitializingBean, DisposableBean, ApplicationContextAware, CamelContextAware {
    private String uri;
    private CamelContext camelContext;
    private String camelContextId;
    private Consumer consumer;
    private String serviceRef;
    private String method;
    private ApplicationContext applicationContext;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
    
    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // lets bind the URI to a pojo
        notNull(uri, "uri");
        // Always resolve the camel context by using the camelContextID
        if (ObjectHelper.isNotEmpty(camelContextId)) {
            camelContext = CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
        }
        notNull(camelContext, "camelContext");
        if (serviceRef != null && getService() == null && applicationContext != null) {
            setService(applicationContext.getBean(serviceRef));
        }

        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
        notNull(getService(), "service");
        Object proxy = getProxyForService();

        try {
            // need to start endpoint before we create consumer
            ServiceHelper.initService(endpoint);
            BeanProcessor processor = new BeanProcessor(proxy, camelContext);
            processor.setMethod(method);
            consumer = endpoint.createConsumer(processor);
            // add and start consumer
            camelContext.addService(consumer, true, false);
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(endpoint, e);
        }
    }

    @Override
    public void destroy() throws Exception {
        // we let CamelContext manage the lifecycle of the consumer and shut it down when Camel stops
    }

}
