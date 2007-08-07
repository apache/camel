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
import org.apache.camel.component.bean.ProxyHelper;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.remoting.support.UrlBasedRemoteAccessor;

/**
 * A {@link FactoryBean} to create a Proxy to a a Camel Pojo Endpoint.
 * 
 * @author chirino
 */
public class CamelProxyFactoryBean extends UrlBasedRemoteAccessor implements FactoryBean, CamelContextAware {
    private CamelContext camelContext;
    private Endpoint endpoint;
    private Object serviceProxy;

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        try {
            if (endpoint == null) {
                if (getServiceUrl() == null || camelContext == null) {
                    throw new IllegalArgumentException("If endpoint is not specified, the serviceUrl and camelContext must be specified.");
                }

                endpoint = camelContext.getEndpoint(getServiceUrl());
                if (endpoint == null) {
                    throw new IllegalArgumentException("Could not resolve endpoint: " + getServiceUrl());
                }
            }

            this.serviceProxy = ProxyHelper.createProxy(endpoint, getServiceInterface());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Class getServiceInterface() {
        return super.getServiceInterface();
    }

    public String getServiceUrl() {
        return super.getServiceUrl();
    }

    public Object getObject() throws Exception {
        return serviceProxy;
    }

    public Class getObjectType() {
        return getServiceInterface();
    }

    public boolean isSingleton() {
        return true;
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
}
