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

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * @deprecated As of version 2.22.0, replaced by {@link CombinedServiceCallServiceDiscoveryConfiguration}
 */
@Deprecated
@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "multiServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregatingServiceCallServiceDiscoveryConfiguration extends CombinedServiceCallServiceDiscoveryConfiguration {

    public AggregatingServiceCallServiceDiscoveryConfiguration() {
        super(null);
    }

    public AggregatingServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent);
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     *  List of ServiceDiscovery configuration to use
     */
    public AggregatingServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfigurations(List<ServiceCallServiceDiscoveryConfiguration> serviceDiscoveryConfigurations) {
        setServiceDiscoveryConfigurations(serviceDiscoveryConfigurations);
        return this;
    }

    /**
     *  Add a ServiceDiscovery configuration
     */
    public AggregatingServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        addServiceDiscoveryConfigurations(serviceDiscoveryConfiguration);
        return this;
    }
}