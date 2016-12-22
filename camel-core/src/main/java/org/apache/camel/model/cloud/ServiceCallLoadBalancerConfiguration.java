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

package org.apache.camel.model.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.cloud.LoadBalancer;
import org.apache.camel.cloud.LoadBalancerFactory;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "routing,cloud,load-balancing")
@XmlRootElement(name = "loadBalancerConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceCallLoadBalancerConfiguration extends IdentifiedType implements LoadBalancerFactory {
    private static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/cloud/";

    @XmlTransient
    private final Optional<ServiceCallDefinition> parent;
    @XmlTransient
    private final String factoryKey;
    @XmlElement(name = "properties") @Metadata(label = "advanced")
    private List<PropertyDefinition> properties;

    public ServiceCallLoadBalancerConfiguration() {
        this(null, null);
    }

    public ServiceCallLoadBalancerConfiguration(ServiceCallDefinition parent, String factoryKey) {
        this.parent = Optional.ofNullable(parent);
        this.factoryKey = factoryKey;
    }

    public ProcessorDefinition end() {
        // end parent as well so we do not have to use 2x end
        return this.parent.orElseGet(null);
    }

    // *************************************************************************
    //
    // *************************************************************************

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

    /**
     * Adds a custom property to use.
     * <p/>
     * These properties are specific to what service call implementation are in
     * use. For example if using ribbon, then the client properties are define
     * in com.netflix.client.config.CommonClientConfigKey.
     */
    public ServiceCallLoadBalancerConfiguration property(String key, String value) {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        PropertyDefinition prop = new PropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        properties.add(prop);
        return this;
    }

    protected Map<String, String> getPropertiesAsMap(CamelContext camelContext) throws Exception {
        Map<String, String> answer;

        if (properties == null || properties.isEmpty()) {
            answer = Collections.emptyMap();
        } else {
            answer = new HashMap<>();
            for (PropertyDefinition prop : properties) {
                // support property placeholders
                String key = CamelContextHelper.parseText(camelContext, prop.getKey());
                String value = CamelContextHelper.parseText(camelContext, prop.getValue());
                answer.put(key, value);
            }
        }

        return answer;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public LoadBalancer newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(factoryKey, "LoadBalancer factoryKey");

        LoadBalancer answer;

        // First try to find the factory from the registry.
        LoadBalancerFactory factory = CamelContextHelper.lookup(camelContext, factoryKey, LoadBalancerFactory.class);
        if (factory != null) {
            // If a factory is found in the registry do not re-configure it as
            // it should be pre-configured.
            answer = factory.newInstance(camelContext);
        } else {

            Class<?> type;
            try {
                // Then use Service factory.
                type = camelContext.getFactoryFinder(RESOURCE_PATH).findClass(factoryKey);
            } catch (Exception e) {
                throw new NoFactoryAvailableException(RESOURCE_PATH + factoryKey, e);
            }

            if (type != null) {
                if (LoadBalancerFactory.class.isAssignableFrom(type)) {
                    factory = (LoadBalancerFactory) camelContext.getInjector().newInstance(type);
                } else {
                    throw new IllegalArgumentException(
                        "Resolving LoadBalancer: " + factoryKey + " detected type conflict: Not a LoadBalancerFactory implementation. Found: " + type.getName());
                }
            }

            try {
                Map<String, Object> parameters = new HashMap<>();
                IntrospectionSupport.getProperties(this, parameters, null, false);
                parameters.put("properties", getPropertiesAsMap(camelContext));

                IntrospectionSupport.setProperties(factory, parameters);


                answer = factory.newInstance(camelContext);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        return answer;
    }
}