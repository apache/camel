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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;

/**
 * To proxy a service call using a interface
 *
 * @version 
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "proxy")
public class CamelProxyFactoryDefinition extends IdentifiedType {

    @XmlAttribute
    private String serviceUrl;
    @XmlAttribute @Deprecated
    private String serviceRef;
    @XmlAttribute
    private Class<?> serviceInterface;
    @XmlAttribute
    private String camelContextId;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean binding;

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * The camel endpoint uri used to send the message to when calling the service from the interface.
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    /**
     * The camel endpoint reference used to send the message to when calling the service from the interface.
     *
     * @deprecated use serviceUrl instead with the ref endpoint.
     */
    @Deprecated
    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    /**
     * Java interfaces to use as facade for the service to be proxied
     */
    public void setServiceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getCamelContextId() {
        return camelContextId;
    }

    /**
     * The id of the CamelContext to use, if there is multiple CamelContext in the same JVM.
     */
    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public Boolean getBinding() {
        return binding;
    }

    /**
     * Camel parameter binding is enabled by default, which will use binding information from the method signature
     * parameters to bind to the Exchange/Message with the following annotations.
     * If disabled then a {@link org.apache.camel.component.bean.CamelInvocationHandler} is used.
     */
    public void setBinding(Boolean binding) {
        this.binding = binding;
    }
}
