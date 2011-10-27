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
package org.apache.camel.blueprint;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.aries.blueprint.ExtendedBlueprintContainer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Producer;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.util.ServiceHelper;

/**
 * A factory to create a Proxy to a a Camel Pojo Endpoint.
 */
@XmlRootElement(name = "proxy")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelProxyFactoryBean extends AbstractCamelFactoryBean<Object> {

    @XmlAttribute
    private String serviceUrl;
    @XmlAttribute
    private String serviceRef;
    @XmlAttribute
    private String serviceInterface;
    @XmlTransient
    private Endpoint endpoint;
    @XmlTransient
    private Object serviceProxy;
    @XmlTransient
    private Producer producer;
    @XmlTransient
    private ExtendedBlueprintContainer blueprintContainer;

    public Object getObject() {
        return serviceProxy;
    }

    public Class<Object> getObjectType() {
        return Object.class;
    }

    protected CamelContext getCamelContextWithId(String camelContextId) {
        if (blueprintContainer != null) {
            return (CamelContext) blueprintContainer.getComponentInstance(camelContextId);
        }
        return null;
    }

    public void afterPropertiesSet() throws Exception {
        if (endpoint == null) {
            getCamelContext();
            if (getServiceUrl() == null && getServiceRef() == null) {
                throw new IllegalArgumentException("serviceUrl or serviceRef must be specified.");
            }
            if (getServiceInterface() == null) {
                throw new IllegalArgumentException("serviceInterface must be specified.");
            }

            // lookup endpoint or we have the url for it
            if (getServiceRef() != null) {
                endpoint = getCamelContext().getRegistry().lookup(getServiceRef(), Endpoint.class);
            } else {
                endpoint = getCamelContext().getEndpoint(getServiceUrl());
            }

            if (endpoint == null) {
                throw new IllegalArgumentException("Could not resolve endpoint: " + getServiceUrl());
            }
        }

        try {
            producer = endpoint.createProducer();
            ServiceHelper.startService(producer);
            Class clazz = blueprintContainer.loadClass(getServiceInterface());
            serviceProxy = ProxyHelper.createProxy(endpoint, producer, clazz);
        } catch (Exception e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
    }

    public void destroy() throws Exception {
        ServiceHelper.stopService(producer);
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    public ExtendedBlueprintContainer getBlueprintContainer() {
        return blueprintContainer;
    }

    public void setBlueprintContainer(ExtendedBlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

}
