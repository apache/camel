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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterFactory;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "serviceFilterConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
@Deprecated
public class ServiceCallServiceFilterConfiguration extends ServiceCallConfiguration implements ServiceFilterFactory {
    @XmlTransient
    private final ServiceCallDefinition parent;
    @XmlTransient
    private final String factoryKey;

    public ServiceCallServiceFilterConfiguration() {
        this(null, null);
    }

    public ServiceCallServiceFilterConfiguration(ServiceCallDefinition parent, String factoryKey) {
        this.parent = parent;
        this.factoryKey = factoryKey;
    }

    public ServiceCallDefinition end() {
        return this.parent;
    }

    public ProcessorDefinition<?> endParent() {
        return this.parent.end();
    }

    // *************************************************************************
    //
    // *************************************************************************

    /**
     * Adds a custom property to use.
     * <p/>
     * These properties are specific to what service call implementation are in use. For example if using a different
     * one, then the client properties are defined according to the specific service in use.
     */
    @Override
    public ServiceCallServiceFilterConfiguration property(String key, String value) {
        return (ServiceCallServiceFilterConfiguration) super.property(key, value);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceFilter newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(factoryKey, "ServiceFilter factoryKey");

        ServiceFilter answer;

        // First try to find the factory from the registry.
        ServiceFilterFactory factory = CamelContextHelper.lookup(camelContext, factoryKey, ServiceFilterFactory.class);
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
                if (ServiceFilterFactory.class.isAssignableFrom(type)) {
                    factory = (ServiceFilterFactory) camelContext.getInjector().newInstance(type, false);
                } else {
                    throw new NoFactoryAvailableException(
                            "Resolving ServiceFilter: " + factoryKey
                                                          + " detected type conflict: Not a ServiceFilterFactory implementation. Found: "
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
