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
package org.apache.camel.model.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.OtherAttributesAware;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServerListStrategy;

/**
 * Remote service call configuration
 */
@Metadata(label = "eip,routing,remote")
@XmlRootElement(name = "serviceCallConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class ServiceCallConfigurationDefinition extends IdentifiedType implements OtherAttributesAware {

    @XmlTransient
    private ServiceCallDefinition parent;
    @XmlAttribute @Metadata(defaultValue = "http")
    private String component;
    @XmlAttribute
    private String loadBalancerRef;
    @XmlTransient
    private ServiceCallLoadBalancer loadBalancer;
    @XmlAttribute
    private String serverListStrategyRef;
    @XmlTransient
    private ServiceCallServerListStrategy serverListStrategy;
    @XmlElement(name = "clientProperty") @Metadata(label = "advanced")
    private List<PropertyDefinition> properties;
    // use xs:any to support optional property placeholders
    @XmlAnyAttribute
    private Map<QName, Object> otherAttributes;

    public ServiceCallConfigurationDefinition() {
    }

    public ServiceCallConfigurationDefinition(ServiceCallDefinition parent) {
        this.parent = parent;
    }

    // Getter/Setter
    // -------------------------------------------------------------------------


    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getLoadBalancerRef() {
        return loadBalancerRef;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallLoadBalancer} to use.
     */
    public void setLoadBalancerRef(String loadBalancerRef) {
        this.loadBalancerRef = loadBalancerRef;
    }

    public ServiceCallLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(ServiceCallLoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public String getServerListStrategyRef() {
        return serverListStrategyRef;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallServerListStrategy} to use.
     */
    public void setServerListStrategyRef(String serverListStrategyRef) {
        this.serverListStrategyRef = serverListStrategyRef;
    }

    public ServiceCallServerListStrategy getServerListStrategy() {
        return serverListStrategy;
    }

    public void setServerListStrategy(ServiceCallServerListStrategy serverListStrategy) {
        this.serverListStrategy = serverListStrategy;
    }

    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * Set client properties to use.
     * <p/>
     * These properties are specific to what service call implementation are in 
     * use. For example if using ribbon, then the client properties are define 
     * in com.netflix.client.config.CommonClientConfigKey.
     */
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }

    @Override
    public Map<QName, Object> getOtherAttributes() {
        return otherAttributes;
    }

    @Override
    public void setOtherAttributes(Map<QName, Object> otherAttributes) {
        this.otherAttributes = otherAttributes;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the default Camel component to use for calling the remote service.
     * <p/>
     * By default the http component is used. You can configure this to use <tt>netty4-http</tt>, <tt>jetty</tt>,
     * <tt>restlet</tt> or some other components of choice. If the service is not HTTP protocol you can use other
     * components such as <tt>mqtt</tt>, <tt>jms</tt>, <tt>amqp</tt> etc.
     * <p/>
     * If the service call has been configured using an uri, then the component from the uri is used instead
     * of this default component.
     */
    public ServiceCallConfigurationDefinition component(String component) {
        setComponent(component);
        return this;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallLoadBalancer} to use.
     */
    public ServiceCallConfigurationDefinition loadBalancer(String loadBalancerRef) {
        setLoadBalancerRef(loadBalancerRef);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.spi.ServiceCallLoadBalancer} to use.
     */
    public ServiceCallConfigurationDefinition loadBalancer(ServiceCallLoadBalancer loadBalancer) {
        setLoadBalancer(loadBalancer);
        return this;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallServerListStrategy} to use.
     */
    public ServiceCallConfigurationDefinition serverListStrategy(String serverListStrategyRef) {
        setServerListStrategyRef(serverListStrategyRef);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.spi.ServiceCallServerListStrategy} to use.
     */
    public ServiceCallConfigurationDefinition serverListStrategy(ServiceCallServerListStrategy serverListStrategy) {
        setServerListStrategy(serverListStrategy);
        return this;
    }

    /**
     * Adds a custom client property to use.
     * <p/>
     * These properties are specific to what service call implementation are in use. For example if using ribbon, then
     * the client properties are define in com.netflix.client.config.CommonClientConfigKey.
     */
    public ServiceCallConfigurationDefinition clientProperty(String key, String value) {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        PropertyDefinition prop = new PropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        properties.add(prop);
        return this;
    }

    /**
     * End of configuration
     */
    public ProcessorDefinition end() {
        // end parent as well so we do not have to use 2x end
        return parent.end();
    }

}
