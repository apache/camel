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
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "multiServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregatingServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlElements({
        @XmlElement(name = "consulServiceDiscovery", type = ConsulServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "dnsServiceDiscovery", type = DnsServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "etcdServiceDiscovery", type = EtcdServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "kubernetesServiceDiscovery", type = KubernetesServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "staticServiceDiscovery", type = StaticServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "cachingServiceDiscovery", type = CachingServiceCallServiceDiscoveryConfiguration.class)}
    )
    private List<ServiceCallServiceDiscoveryConfiguration> serviceDiscoveryConfigurations;

    public AggregatingServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public AggregatingServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "aggregating-service-discovery");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public List<ServiceCallServiceDiscoveryConfiguration> getServiceDiscoveryConfigurations() {
        return serviceDiscoveryConfigurations;
    }

    /**
     * List of ServiceDiscovery configuration to use
     * @param serviceDiscoveryConfigurations
     */
    public void setServiceDiscoveryConfigurations(List<ServiceCallServiceDiscoveryConfiguration> serviceDiscoveryConfigurations) {
        this.serviceDiscoveryConfigurations = serviceDiscoveryConfigurations;
    }

    /**
     *  Add a ServiceDiscovery configuration
     */
    public void addServiceDiscoveryConfigurations(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        if (serviceDiscoveryConfigurations == null) {
            serviceDiscoveryConfigurations = new ArrayList<>();
        }

        serviceDiscoveryConfigurations.add(serviceDiscoveryConfiguration);
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

    // *****************************
    // Shortcuts - ServiceDiscovery
    // *****************************

    public CachingServiceCallServiceDiscoveryConfiguration cachingServiceDiscovery() {
        CachingServiceCallServiceDiscoveryConfiguration conf = new CachingServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public ConsulServiceCallServiceDiscoveryConfiguration consulServiceDiscovery() {
        ConsulServiceCallServiceDiscoveryConfiguration conf = new ConsulServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public DnsServiceCallServiceDiscoveryConfiguration dnsServiceDiscovery() {
        DnsServiceCallServiceDiscoveryConfiguration conf = new DnsServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public EtcdServiceCallServiceDiscoveryConfiguration etcdServiceDiscovery() {
        EtcdServiceCallServiceDiscoveryConfiguration conf = new EtcdServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public KubernetesServiceCallServiceDiscoveryConfiguration kubernetesServiceDiscovery() {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public AggregatingServiceCallServiceDiscoveryConfiguration multiServiceDiscovery() {
        AggregatingServiceCallServiceDiscoveryConfiguration conf = new AggregatingServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    public StaticServiceCallServiceDiscoveryConfiguration staticServiceDiscovery() {
        StaticServiceCallServiceDiscoveryConfiguration conf = new StaticServiceCallServiceDiscoveryConfiguration();
        addServiceDiscoveryConfigurations(conf);

        return conf;
    }

    // *************************************************************************
    // Utilities
    // *************************************************************************

    @Override
    protected void postProcessFactoryParameters(final CamelContext camelContext, final Map<String, Object> parameters) throws Exception {
        if (serviceDiscoveryConfigurations != null && !serviceDiscoveryConfigurations.isEmpty()) {
            List<ServiceDiscovery> discoveries = new ArrayList<>(serviceDiscoveryConfigurations.size());
            for (ServiceCallServiceDiscoveryConfiguration conf : serviceDiscoveryConfigurations) {
                discoveries.add(conf.newInstance(camelContext));
            }

            parameters.put("serviceDiscoveryList", discoveries);
        }
    }
}