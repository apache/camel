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
package org.apache.camel.model.cloud;

import java.util.Map;
import java.util.Optional;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "serviceDiscoveryConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
@Deprecated
public class ServiceCallServiceDiscoveryConfiguration extends ServiceCallConfiguration implements ServiceDiscoveryFactory {
    @XmlTransient
    private final Optional<ServiceCallDefinition> parent;
    @XmlTransient
    private final String factoryKey;

    public ServiceCallServiceDiscoveryConfiguration() {
        this(null, null);
    }

    public ServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent, String factoryKey) {
        this.parent = Optional.ofNullable(parent);
        this.factoryKey = factoryKey;
    }

    public ServiceCallDefinition end() {
        return this.parent.orElseThrow(() -> new IllegalStateException("Parent definition is not set"));
    }

    public ProcessorDefinition<?> endParent() {
        return this.parent.map(ServiceCallDefinition::end)
                .orElseThrow(() -> new IllegalStateException("Parent definition is not set"));
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    public ServiceCallServiceDiscoveryConfiguration property(String key, String value) {
        return (ServiceCallServiceDiscoveryConfiguration) super.property(key, value);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(factoryKey, "ServiceDiscovery factoryKey");

        ServiceDiscovery answer;

        // First try to find the factory from the registry.
        ServiceDiscoveryFactory factory = CamelContextHelper.lookup(camelContext, factoryKey, ServiceDiscoveryFactory.class);
        if (factory != null) {
            // If a factory is found in the registry do not re-configure it as
            // it should be pre-configured.
            answer = factory.newInstance(camelContext);
        } else {
            Class<?> type;
            try {
                // Then use Service factory.
                type = camelContext.getCamelContextExtension()
                        .getFactoryFinder(ServiceCallDefinitionConstants.RESOURCE_PATH).findClass(factoryKey).orElse(null);
            } catch (Exception e) {
                throw new NoFactoryAvailableException(ServiceCallDefinitionConstants.RESOURCE_PATH + factoryKey, e);
            }

            if (type != null) {
                if (ServiceDiscoveryFactory.class.isAssignableFrom(type)) {
                    factory = (ServiceDiscoveryFactory) camelContext.getInjector().newInstance(type, false);
                } else {
                    throw new IllegalArgumentException(
                            "Resolving ServiceDiscovery: " + factoryKey
                                                       + " detected type conflict: Not a ServiceDiscoveryFactory implementation. Found: "
                                                       + type.getName());
                }
            }

            try {
                Map<String, Object> parameters = getConfiguredOptions(camelContext, this);

                parameters.replaceAll((k, v) -> {
                    if (v instanceof String) {
                        try {
                            v = camelContext.resolvePropertyPlaceholders((String) v);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                    String.format("Exception while resolving %s (%s)", k, v), e);
                        }
                    }

                    return v;
                });

                if (factory != null) {
                    // Convert properties to Map<String, String>
                    Map<String, String> map = getPropertiesAsMap(camelContext);
                    if (map != null && !map.isEmpty()) {
                        parameters.put("properties", map);
                    }

                    postProcessFactoryParameters(camelContext, parameters);

                    PropertyBindingSupport.build().bind(camelContext, factory, parameters);

                    answer = factory.newInstance(camelContext);
                } else {
                    throw new IllegalStateException("factory is null");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        return answer;
    }

}
