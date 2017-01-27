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
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-filter")
@XmlRootElement(name = "multiServiceFilter")
@XmlAccessorType(XmlAccessType.FIELD)
public class ChainedServiceCallServiceFilterConfiguration extends ServiceCallServiceFilterConfiguration {
    @XmlElements({
        @XmlElement(name = "blacklistServiceFilter", type = BlacklistServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "customServiceFilter", type = CustomServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "healthyServiceFilter", type = HealthyServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "passThroughServiceFilter", type = PassThroughServiceCallServiceFilterConfiguration.class) }
    )
    private List<ServiceCallServiceFilterConfiguration> serviceFilterConfigurations;

    public ChainedServiceCallServiceFilterConfiguration() {
        this(null);
    }

    public ChainedServiceCallServiceFilterConfiguration(ServiceCallDefinition parent) {
        super(parent, "chained-service-filter");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public List<ServiceCallServiceFilterConfiguration> getServiceFilterConfigurations() {
        return serviceFilterConfigurations;
    }

    /**
     * List of ServiceFilter configuration to use
     * @param serviceFilterConfigurations
     */
    public void setServiceFilterConfigurations(List<ServiceCallServiceFilterConfiguration> serviceFilterConfigurations) {
        this.serviceFilterConfigurations = serviceFilterConfigurations;
    }

    /**
     *  Add a ServiceFilter configuration
     */
    public void addServiceFilterConfiguration(ServiceCallServiceFilterConfiguration serviceFilterConfiguration) {
        if (serviceFilterConfigurations == null) {
            serviceFilterConfigurations = new ArrayList<>();
        }

        serviceFilterConfigurations.add(serviceFilterConfiguration);
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     *  List of ServiceFilter configuration to use
     */
    public ChainedServiceCallServiceFilterConfiguration serviceFilterConfigurations(List<ServiceCallServiceFilterConfiguration> serviceFilterConfigurations) {
        setServiceFilterConfigurations(serviceFilterConfigurations);
        return this;
    }

    /**
     *  Add a ServiceFilter configuration
     */
    public ChainedServiceCallServiceFilterConfiguration serviceFilterConfiguration(ServiceCallServiceFilterConfiguration serviceFilterConfiguration) {
        addServiceFilterConfiguration(serviceFilterConfiguration);
        return this;
    }

    // *****************************
    // Shortcuts - ServiceFilter
    // *****************************

    public ChainedServiceCallServiceFilterConfiguration healthy() {
        addServiceFilterConfiguration(new HealthyServiceCallServiceFilterConfiguration());
        return this;
    }

    public ChainedServiceCallServiceFilterConfiguration passThrough() {
        addServiceFilterConfiguration(new PassThroughServiceCallServiceFilterConfiguration());
        return this;
    }

    public ChainedServiceCallServiceFilterConfiguration custom(String serviceFilter) {
        addServiceFilterConfiguration(new CustomServiceCallServiceFilterConfiguration().serviceFilter(serviceFilter));
        return this;
    }

    public ChainedServiceCallServiceFilterConfiguration custom(ServiceFilter serviceFilter) {
        addServiceFilterConfiguration(new CustomServiceCallServiceFilterConfiguration().serviceFilter(serviceFilter));
        return this;
    }

    // *************************************************************************
    // Utilities
    // *************************************************************************

    @Override
    protected void postProcessFactoryParameters(final CamelContext camelContext, final Map<String, Object> parameters) throws Exception {
        if (serviceFilterConfigurations != null && !serviceFilterConfigurations.isEmpty()) {
            List<ServiceFilter> discoveries = new ArrayList<>(serviceFilterConfigurations.size());
            for (ServiceCallServiceFilterConfiguration conf : serviceFilterConfigurations) {
                discoveries.add(conf.newInstance(camelContext));
            }

            parameters.put("serviceFilterList", discoveries);
        }
    }
}
