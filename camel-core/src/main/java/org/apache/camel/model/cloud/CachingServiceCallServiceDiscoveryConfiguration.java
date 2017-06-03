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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "cachingServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class CachingServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute @Metadata(defaultValue = "60")
    private Integer timeout = 60;
    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class) @Metadata(defaultValue = "SECONDS")
    private TimeUnit units = TimeUnit.SECONDS;
    @XmlElements({
        @XmlElement(name = "consulServiceDiscovery", type = ConsulServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "dnsServiceDiscovery", type = DnsServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "etcdServiceDiscovery", type = EtcdServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "kubernetesServiceDiscovery", type = KubernetesServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "aggregatingServiceDiscovery", type = AggregatingServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "staticServiceDiscovery", type = StaticServiceCallServiceDiscoveryConfiguration.class)}
    )
    private ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration;

    public CachingServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public CachingServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "caching-service-discovery");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Set the time the services will be retained.
     */
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public TimeUnit getUnits() {
        return units;
    }

    /**
     * Set the time unit for the timeout.
     */
    public void setUnits(TimeUnit units) {
        this.units = units;
    }

    public ServiceCallServiceDiscoveryConfiguration getServiceDiscoveryConfiguration() {
        return serviceDiscoveryConfiguration;
    }

    /**
     * Set the service-call configuration to use
     */
    public void setServiceDiscoveryConfiguration(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        this.serviceDiscoveryConfiguration = serviceDiscoveryConfiguration;
    }


    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * Set the time the services will be retained.
     */
    public CachingServiceCallServiceDiscoveryConfiguration timeout(Integer timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     *  Set the time unit for the timeout.
     */
    public CachingServiceCallServiceDiscoveryConfiguration units(TimeUnit units) {
        setUnits(units);
        return this;
    }

    /**
     *  Set the service-call configuration to use
     */
    public CachingServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        setServiceDiscoveryConfiguration(serviceDiscoveryConfiguration);
        return this;
    }

    // *****************************
    // Shortcuts - ServiceDiscovery
    // *****************************

    public CachingServiceCallServiceDiscoveryConfiguration cachingServiceDiscovery() {
        CachingServiceCallServiceDiscoveryConfiguration conf = new CachingServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return serviceDiscoveryConfiguration(conf);
    }

    public ConsulServiceCallServiceDiscoveryConfiguration consulServiceDiscovery() {
        ConsulServiceCallServiceDiscoveryConfiguration conf = new ConsulServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public DnsServiceCallServiceDiscoveryConfiguration dnsServiceDiscovery() {
        DnsServiceCallServiceDiscoveryConfiguration conf = new DnsServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public EtcdServiceCallServiceDiscoveryConfiguration etcdServiceDiscovery() {
        EtcdServiceCallServiceDiscoveryConfiguration conf = new EtcdServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public KubernetesServiceCallServiceDiscoveryConfiguration kubernetesServiceDiscovery() {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public AggregatingServiceCallServiceDiscoveryConfiguration aggregatingServiceDiscovery() {
        AggregatingServiceCallServiceDiscoveryConfiguration conf = new AggregatingServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public StaticServiceCallServiceDiscoveryConfiguration staticServiceDiscovery() {
        StaticServiceCallServiceDiscoveryConfiguration conf = new StaticServiceCallServiceDiscoveryConfiguration();
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    // *************************************************************************
    // Utilities
    // *************************************************************************

    @Override
    protected void postProcessFactoryParameters(CamelContext camelContext, Map<String, Object> parameters) throws Exception {
        if (serviceDiscoveryConfiguration != null) {
            parameters.put("serviceDiscovery", serviceDiscoveryConfiguration.newInstance(camelContext));
        }
    }
}