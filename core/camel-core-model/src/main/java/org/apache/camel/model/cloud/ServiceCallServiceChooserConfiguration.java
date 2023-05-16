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
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceChooserFactory;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "serviceChooserConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
@Deprecated
public class ServiceCallServiceChooserConfiguration extends ServiceCallConfiguration implements ServiceChooserFactory {
    @XmlTransient
    private final ServiceCallDefinition parent;
    @XmlTransient
    private final String factoryKey;

    public ServiceCallServiceChooserConfiguration() {
        this(null, null);
    }

    public ServiceCallServiceChooserConfiguration(ServiceCallDefinition parent, String factoryKey) {
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

    @Override
    public ServiceCallServiceChooserConfiguration property(String key, String value) {
        return (ServiceCallServiceChooserConfiguration) super.property(key, value);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceChooser newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(factoryKey, "ServiceChooser factoryKey");

        ServiceChooser answer;

        // First try to find the factory from the registry.
        ServiceChooserFactory factory = CamelContextHelper.lookup(camelContext, factoryKey, ServiceChooserFactory.class);
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
                if (ServiceChooserFactory.class.isAssignableFrom(type)) {
                    factory = (ServiceChooserFactory) camelContext.getInjector().newInstance(type, false);
                } else {
                    throw new NoFactoryAvailableException(
                            "Resolving ServiceChooser: " + factoryKey
                                                          + " detected type conflict: Not a ServiceChooserFactory implementation. Found: "
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
