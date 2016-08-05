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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.model.NoOutputDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServerListStrategy;

/**
 * Remote service call
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "serviceCall")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceCallDefinition extends NoOutputDefinition<ServiceCallDefinition> {

    @XmlAttribute @Metadata(required = "true")
    private String name;
    @XmlAttribute @Metadata(required = "true")
    private String uri;
    @XmlAttribute
    private ExchangePattern pattern;
    @XmlElement
    private ServiceCallConfigurationDefinition serviceCallConfiguration;
    @XmlAttribute
    private String serviceCallConfigurationRef;
    @XmlAttribute
    private String loadBalancerRef;
    // TODO: allow to use custom type as load balancer
    @XmlTransient
    private ServiceCallLoadBalancer loadBalancer;
    @XmlAttribute
    private String serverListStrategyRef;
    @XmlTransient
    private ServiceCallServerListStrategy serverListStrategy;

    public ServiceCallDefinition() {
    }

    @Override
    public String toString() {
        return "ServiceCall[" + name + "]";
    }

    @Override
    public String getLabel() {
        return "serviceCall";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new IllegalStateException("Cannot find Camel component supporting the ServiceCall EIP such as camel-kubernetes or camel-ribbon.");
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public ServiceCallDefinition pattern(ExchangePattern pattern) {
        setPattern(pattern);
        return this;
    }

    /**
     * Sets the name of the service to use
     */
    public ServiceCallDefinition name(String name) {
        setName(name);
        return this;
    }

    /**
     * Sets the uri of the service to use
     */
    public ServiceCallDefinition uri(String uri) {
        setUri(uri);
        return this;
    }

    /**
     * Configures the Service Call EIP using Kubernetes
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Service Call EIP.
     */
    public KubernetesConfigurationDefinition kubernetesConfiguration() {
        serviceCallConfiguration = new KubernetesConfigurationDefinition(this);
        return (KubernetesConfigurationDefinition) serviceCallConfiguration;
    }

    /**
     * Configures the Service Call EIP using Ribbon
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Service Call EIP.
     */
    public RibbonConfigurationDefinition ribbonConfiguration() {
        serviceCallConfiguration = new RibbonConfigurationDefinition(this);
        return (RibbonConfigurationDefinition) serviceCallConfiguration;
    }

    /**
     * Configures the Service Call EIP using Consul
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Service Call EIP.
     */
    public ConsulConfigurationDefinition consulConfiguration() {
        serviceCallConfiguration = new ConsulConfigurationDefinition(this);
        return (ConsulConfigurationDefinition) serviceCallConfiguration;
    }

    /**
     * Configures the Service Call EIP using Etcd
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Service Call EIP.
     */
    public EtcdConfigurationDefinition etcdConfiguration() {
        serviceCallConfiguration = new EtcdConfigurationDefinition(this);
        return (EtcdConfigurationDefinition) serviceCallConfiguration;
    }

    /**
     * Configures the Service Call EIP using Dns
     * <p/>
     * Use <tt>end</tt> when configuration is complete, to return back to the Service Call EIP.
     */
    public DnsConfigurationDefinition dnsConfiguration() {
        serviceCallConfiguration = new DnsConfigurationDefinition(this);
        return (DnsConfigurationDefinition) serviceCallConfiguration;
    }

    /**
     * Configures the ServiceCall using the given configuration
     */
    public ServiceCallDefinition serviceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        serviceCallConfiguration = configuration;
        return this;
    }

    /**
     * Refers to a ServiceCall configuration to use
     */
    public ServiceCallDefinition serviceCallConfiguration(String ref) {
        serviceCallConfigurationRef = ref;
        return this;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallLoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(String loadBalancerRef) {
        setLoadBalancerRef(loadBalancerRef);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.spi.ServiceCallLoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(ServiceCallLoadBalancer loadBalancer) {
        setLoadBalancer(loadBalancer);
        return this;
    }

    /**
     * Sets a reference to a custom {@link org.apache.camel.spi.ServiceCallServerListStrategy} to use.
     */
    public ServiceCallDefinition serverListStrategy(String serverListStrategyRef) {
        setServerListStrategyRef(serverListStrategyRef);
        return this;
    }

    /**
     * Sets a custom {@link org.apache.camel.spi.ServiceCallServerListStrategy} to use.
     */
    public ServiceCallDefinition serverListStrategy(ServiceCallServerListStrategy serverListStrategy) {
        setServerListStrategy(serverListStrategy);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public ServiceCallConfigurationDefinition getServiceCallConfiguration() {
        return serviceCallConfiguration;
    }

    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition serviceCallConfiguration) {
        this.serviceCallConfiguration = serviceCallConfiguration;
    }

    public String getServiceCallConfigurationRef() {
        return serviceCallConfigurationRef;
    }

    /**
     * Refers to a ServiceCall configuration to use
     */
    public void setServiceCallConfigurationRef(String serviceCallConfigurationRef) {
        this.serviceCallConfigurationRef = serviceCallConfigurationRef;
    }

    public String getUri() {
        return uri;
    }

    /**
     * The uri of the endpoint to send to.
     * The uri can be dynamic computed using the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
     */
    public void setUri(String uri) {
        this.uri = uri;
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
}
