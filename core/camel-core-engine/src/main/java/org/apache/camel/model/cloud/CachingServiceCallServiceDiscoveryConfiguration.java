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
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "cachingServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
public class CachingServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute
    @Metadata(defaultValue = "60", javaType = "java.lang.Integer")
    private String timeout = Integer.toString(60);
    @XmlAttribute
    @Metadata(javaType = "java.util.concurrent.TimeUnit", defaultValue = "SECONDS",
            enums = "NANOSECONDS,MICROSECONDS,MILLISECONDS,SECONDS,MINUTES,HOURS,DAYS")
    private String units = TimeUnit.SECONDS.name();
    @XmlElements({@XmlElement(name = "consulServiceDiscovery", type = ConsulServiceCallServiceDiscoveryConfiguration.class),
                  @XmlElement(name = "dnsServiceDiscovery", type = DnsServiceCallServiceDiscoveryConfiguration.class),
                  @XmlElement(name = "etcdServiceDiscovery", type = EtcdServiceCallServiceDiscoveryConfiguration.class),
                  @XmlElement(name = "kubernetesServiceDiscovery", type = KubernetesServiceCallServiceDiscoveryConfiguration.class),
                  @XmlElement(name = "combinedServiceDiscovery", type = CombinedServiceCallServiceDiscoveryConfiguration.class),
                  @XmlElement(name = "staticServiceDiscovery", type = StaticServiceCallServiceDiscoveryConfiguration.class)})
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

    public String getTimeout() {
        return timeout;
    }

    /**
     * Set the time the services will be retained.
     */
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getUnits() {
        return units;
    }

    /**
     * Set the time unit for the timeout.
     */
    public void setUnits(String units) {
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
    public CachingServiceCallServiceDiscoveryConfiguration timeout(int timeout) {
        return timeout(Integer.toString(timeout));
    }

    /**
     * Set the time the services will be retained.
     */
    public CachingServiceCallServiceDiscoveryConfiguration timeout(String timeout) {
        setTimeout(timeout);
        return this;
    }

    /**
     * Set the time unit for the timeout.
     */
    public CachingServiceCallServiceDiscoveryConfiguration units(TimeUnit units) {
        return units(units.name());
    }

    /**
     * Set the time unit for the timeout.
     */
    public CachingServiceCallServiceDiscoveryConfiguration units(String units) {
        setUnits(units);
        return this;
    }

    /**
     * Set the service-call configuration to use
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

    public CombinedServiceCallServiceDiscoveryConfiguration combinedServiceDiscovery() {
        CombinedServiceCallServiceDiscoveryConfiguration conf = new CombinedServiceCallServiceDiscoveryConfiguration();
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
