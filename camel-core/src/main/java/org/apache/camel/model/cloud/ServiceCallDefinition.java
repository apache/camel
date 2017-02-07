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

import java.util.Set;
import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.cloud.LoadBalancer;
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceChooserAware;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryAware;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterAware;
import org.apache.camel.impl.cloud.DefaultLoadBalancer;
import org.apache.camel.impl.cloud.DefaultServiceCallExpression;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.apache.camel.impl.cloud.HealthyServiceFilter;
import org.apache.camel.impl.cloud.PassThroughServiceFilter;
import org.apache.camel.impl.cloud.RandomServiceChooser;
import org.apache.camel.impl.cloud.RoundRobinServiceChooser;
import org.apache.camel.model.NoOutputDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Remote service call definition
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "serviceCall")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceCallDefinition extends NoOutputDefinition<ServiceCallDefinition> {
    @XmlAttribute @Metadata(required = "true")
    private String name;
    @XmlAttribute @Metadata(defaultValue = "http")
    private String uri;
    @XmlAttribute
    private String component;
    @XmlAttribute
    private ExchangePattern pattern;
    @XmlAttribute
    private String configurationRef;
    @XmlAttribute
    private String serviceDiscoveryRef;
    @XmlTransient
    private ServiceDiscovery serviceDiscovery;
    @XmlAttribute
    private String serviceFilterRef;
    @XmlTransient
    private ServiceFilter serviceFilter;
    @XmlAttribute
    private String serviceChooserRef;
    @XmlTransient
    private ServiceChooser serviceChooser;
    @XmlAttribute
    private String loadBalancerRef;
    @XmlTransient
    private LoadBalancer loadBalancer;
    @XmlAttribute
    private String expressionRef;
    @XmlTransient
    private Expression expression;

    @XmlElements({
        @XmlElement(name = "cachingServiceDiscovery", type = CachingServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "chainedServiceDiscovery", type = ChainedServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "consulServiceDiscovery", type = ConsulServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "dnsServiceDiscovery", type = DnsServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "etcdServiceDiscovery", type = EtcdServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "kubernetesServiceDiscovery", type = KubernetesServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "staticServiceDiscovery", type = StaticServiceCallServiceDiscoveryConfiguration.class)}
    )
    private ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration;

    @XmlElements({
        @XmlElement(name = "blacklistServiceFilter", type = BlacklistServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "chainedServiceFilter", type = ChainedServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "customServiceFilter", type = CustomServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "healthyServiceFilter", type = HealthyServiceCallServiceFilterConfiguration.class),
        @XmlElement(name = "passThroughServiceFilter", type = PassThroughServiceCallServiceFilterConfiguration.class)}
    )
    private ServiceCallServiceFilterConfiguration serviceFilterConfiguration;

    @XmlElements({
        @XmlElement(name = "ribbonLoadBalancer", type = RibbonServiceCallLoadBalancerConfiguration.class),
        @XmlElement(name = "defaultLoadBalancer", type = DefaultServiceCallLoadBalancerConfiguration.class) }
    )
    private ServiceCallLoadBalancerConfiguration loadBalancerConfiguration;

    @XmlElements({
        @XmlElement(name = "expressionConfiguration", type = ServiceCallExpressionConfiguration.class)}
    )
    private ServiceCallExpressionConfiguration expressionConfiguration;

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

    // *****************************
    // Properties
    // *****************************

    public String getName() {
        return name;
    }

    /**
     * Sets the name of the service to use
     */
    public void setName(String name) {
        this.name = name;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public String getConfigurationRef() {
        return configurationRef;
    }

    /**
     * Refers to a ServiceCall configuration to use
     */
    public void setConfigurationRef(String configurationRef) {
        this.configurationRef = configurationRef;
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

    public String getComponent() {
        return component;
    }

    /**
     * The component to use.
     */
    public void setComponent(String component) {
        this.component = component;
    }

    public String getServiceDiscoveryRef() {
        return serviceDiscoveryRef;
    }

    /**
     * Sets a reference to a custom {@link ServiceDiscovery} to use.
     */
    public void setServiceDiscoveryRef(String serviceDiscoveryRef) {
        this.serviceDiscoveryRef = serviceDiscoveryRef;
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    /**
     * Sets a custom {@link ServiceDiscovery} to use.
     */
    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    public String getServiceFilterRef() {
        return serviceFilterRef;
    }

    /**
     * Sets a reference to a custom {@link ServiceFilter} to use.
     */
    public void setServiceFilterRef(String serviceFilterRef) {
        this.serviceFilterRef = serviceFilterRef;
    }

    public ServiceFilter getServiceFilter() {
        return serviceFilter;
    }

    /**
     * Sets a custom {@link ServiceFilter} to use.
     */
    public void setServiceFilter(ServiceFilter serviceFilter) {
        this.serviceFilter = serviceFilter;
    }

    public String getServiceChooserRef() {
        return serviceChooserRef;
    }

    /**
     * Sets a reference to a custom {@link ServiceChooser} to use.
     */
    public void setServiceChooserRef(String serviceChooserRef) {
        this.serviceChooserRef = serviceChooserRef;
    }

    public ServiceChooser getServiceChooser() {
        return serviceChooser;
    }

    /**
     * Sets a custom {@link ServiceChooser} to use.
     */
    public void setServiceChooser(ServiceChooser serviceChooser) {
        this.serviceChooser = serviceChooser;
    }

    public String getLoadBalancerRef() {
        return loadBalancerRef;
    }

    /**
     * Sets a reference to a custom {@link LoadBalancer} to use.
     */
    public void setLoadBalancerRef(String loadBalancerRef) {
        this.loadBalancerRef = loadBalancerRef;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Sets a custom {@link LoadBalancer} to use.
     */
    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public String getExpressionRef() {
        return expressionRef;
    }

    /**
     * Set a reference to a custom {@link Expression} to use.
     */
    public void setExpressionRef(String expressionRef) {
        this.expressionRef = expressionRef;
    }

    public Expression getExpression() {
        return expression;
    }

    /**
     * Set a custom {@link Expression} to use.
     */
    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public ServiceCallServiceDiscoveryConfiguration getServiceDiscoveryConfiguration() {
        return serviceDiscoveryConfiguration;
    }

    /**
     * Configures the ServiceDiscovery using the given configuration.
     */
    public void setServiceDiscoveryConfiguration(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        this.serviceDiscoveryConfiguration = serviceDiscoveryConfiguration;
    }

    public ServiceCallServiceFilterConfiguration getServiceFilterConfiguration() {
        return serviceFilterConfiguration;
    }

    /**
     * Configures the ServiceFilter using the given configuration.
     */
    public void setServiceFilterConfiguration(ServiceCallServiceFilterConfiguration serviceFilterConfiguration) {
        this.serviceFilterConfiguration = serviceFilterConfiguration;
    }

    public ServiceCallLoadBalancerConfiguration getLoadBalancerConfiguration() {
        return loadBalancerConfiguration;
    }

    /**
     * Configures the LoadBalancer using the given configuration.
     */
    public void setLoadBalancerConfiguration(ServiceCallLoadBalancerConfiguration loadBalancerConfiguration) {
        this.loadBalancerConfiguration = loadBalancerConfiguration;
    }

    public ServiceCallExpressionConfiguration getExpressionConfiguration() {
        return expressionConfiguration;
    }

    /**
     * Configures the Expression using the given configuration.
     */
    public void setExpressionConfiguration(ServiceCallExpressionConfiguration expressionConfiguration) {
        this.expressionConfiguration = expressionConfiguration;
    }

    // *****************************
    // Fluent API
    // *****************************

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
     * Sets the component to use
     */
    public ServiceCallDefinition component(String component) {
        setComponent(component);
        return this;
    }

    /**
     * Refers to a ServiceCall configuration to use
     */
    public ServiceCallDefinition serviceCallConfiguration(String ref) {
        configurationRef = ref;
        return this;
    }

    /**
     * Sets a reference to a custom {@link ServiceDiscovery} to use.
     */
    public ServiceCallDefinition serviceDiscovery(String serviceDiscoveryRef) {
        setServiceDiscoveryRef(serviceDiscoveryRef);
        return this;
    }

    /**
     * Sets a custom {@link ServiceDiscovery} to use.
     */
    public ServiceCallDefinition serviceDiscovery(ServiceDiscovery serviceDiscovery) {
        setServiceDiscovery(serviceDiscovery);
        return this;
    }

    /**
     * Sets a reference to a custom {@link ServiceFilter} to use.
     */
    public ServiceCallDefinition serviceFilter(String serviceFilterRef) {
        setServiceDiscoveryRef(serviceDiscoveryRef);
        return this;
    }

    /**
     * Sets a custom {@link ServiceFilter} to use.
     */
    public ServiceCallDefinition serviceFilter(ServiceFilter serviceFilter) {
        setServiceFilter(serviceFilter);
        return this;
    }

    /**
     * Sets a reference to a custom {@link ServiceChooser} to use.
     */
    public ServiceCallDefinition serviceChooser(String serviceChooserRef) {
        setServiceChooserRef(serviceChooserRef);
        return this;
    }

    /**
     * Sets a custom {@link ServiceChooser} to use.
     */
    public ServiceCallDefinition serviceChooser(ServiceChooser serviceChooser) {
        setServiceChooser(serviceChooser);
        return this;
    }

    /**
     * Sets a reference to a custom {@link LoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(String loadBalancerRef) {
        setLoadBalancerRef(loadBalancerRef);
        return this;
    }

    /**
     * Sets a custom {@link LoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(LoadBalancer loadBalancer) {
        setLoadBalancer(loadBalancer);
        return this;
    }

    /**
     * Sets a reference to a custom {@link Expression} to use.
     */
    public ServiceCallDefinition expression(String expressionRef) {
        setExpressionRef(loadBalancerRef);
        return this;
    }

    /**
     * Sets a custom {@link Expression} to use.
     */
    public ServiceCallDefinition expression(Expression expression) {
        setExpression(expression);
        return this;
    }

    /**
     * Configures the ServiceDiscovery using the given configuration.
     */
    public ServiceCallDefinition serviceDiscoveryConfiguration(ServiceCallServiceDiscoveryConfiguration serviceDiscoveryConfiguration) {
        setServiceDiscoveryConfiguration(serviceDiscoveryConfiguration);
        return this;
    }

    /**
     * Configures the ServiceFilter using the given configuration.
     */
    public ServiceCallDefinition serviceFilterConfiguration(ServiceCallServiceFilterConfiguration serviceFilterConfiguration) {
        setServiceFilterConfiguration(serviceFilterConfiguration);
        return this;
    }

    /**
     * Configures the LoadBalancer using the given configuration.
     */
    public ServiceCallDefinition loadBalancerConfiguration(ServiceCallLoadBalancerConfiguration loadBalancerConfiguration) {
        setLoadBalancerConfiguration(loadBalancerConfiguration);
        return this;
    }

    /**
     * Configures the Expression using the given configuration.
     */
    public ServiceCallDefinition expressionConfiguration(ServiceCallExpressionConfiguration expressionConfiguration) {
        setExpressionConfiguration(expressionConfiguration);
        return this;
    }

    // *****************************
    // Shortcuts - ServiceDiscovery
    // *****************************

    public CachingServiceCallServiceDiscoveryConfiguration cachingServiceDiscovery() {
        CachingServiceCallServiceDiscoveryConfiguration conf = new CachingServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public ConsulServiceCallServiceDiscoveryConfiguration consulServiceDiscovery() {
        ConsulServiceCallServiceDiscoveryConfiguration conf = new ConsulServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public DnsServiceCallServiceDiscoveryConfiguration dnsServiceDiscovery() {
        DnsServiceCallServiceDiscoveryConfiguration conf = new DnsServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public ServiceCallDefinition dnsServiceDiscovery(String domain) {
        DnsServiceCallServiceDiscoveryConfiguration conf = new DnsServiceCallServiceDiscoveryConfiguration(this);
        conf.setDomain(domain);

        setServiceDiscoveryConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition dnsServiceDiscovery(String domain, String protocol) {
        DnsServiceCallServiceDiscoveryConfiguration conf = new DnsServiceCallServiceDiscoveryConfiguration(this);
        conf.setDomain(domain);
        conf.setProto(protocol);

        setServiceDiscoveryConfiguration(conf);

        return this;
    }

    public EtcdServiceCallServiceDiscoveryConfiguration etcdServiceDiscovery() {
        EtcdServiceCallServiceDiscoveryConfiguration conf = new EtcdServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public KubernetesServiceCallServiceDiscoveryConfiguration kubernetesServiceDiscovery() {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public KubernetesServiceCallServiceDiscoveryConfiguration kubernetesClientServiceDiscovery() {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration(this);
        conf.setLookup("client");

        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public ServiceCallDefinition kubernetesEnvServiceDiscovery() {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration(this);
        conf.setLookup("environment");

        setServiceDiscoveryConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition kubernetesDnsServiceDiscovery(String namespace, String domain) {
        KubernetesServiceCallServiceDiscoveryConfiguration conf = new KubernetesServiceCallServiceDiscoveryConfiguration(this);
        conf.setLookup("dns");
        conf.setNamespace(namespace);
        conf.setDnsDomain(domain);

        setServiceDiscoveryConfiguration(conf);

        return this;
    }

    public ChainedServiceCallServiceDiscoveryConfiguration multiServiceDiscovery() {
        ChainedServiceCallServiceDiscoveryConfiguration conf = new ChainedServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public StaticServiceCallServiceDiscoveryConfiguration staticServiceDiscovery() {
        StaticServiceCallServiceDiscoveryConfiguration conf = new StaticServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    // *****************************
    // Shortcuts - ServiceFilter
    // *****************************

    public ServiceCallDefinition healthyFilter() {
        HealthyServiceCallServiceFilterConfiguration conf = new HealthyServiceCallServiceFilterConfiguration(this);
        setServiceFilterConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition passThroughFilter() {
        PassThroughServiceCallServiceFilterConfiguration conf = new PassThroughServiceCallServiceFilterConfiguration(this);
        setServiceFilterConfiguration(conf);

        return this;
    }

    public ChainedServiceCallServiceFilterConfiguration multiFilter() {
        ChainedServiceCallServiceFilterConfiguration conf = new ChainedServiceCallServiceFilterConfiguration(this);
        setServiceFilterConfiguration(conf);

        return conf;
    }

    public ServiceCallDefinition customFilter(String serviceFilter) {
        CustomServiceCallServiceFilterConfiguration conf = new CustomServiceCallServiceFilterConfiguration();
        conf.setServiceFilterRef(serviceFilter);

        setServiceFilterConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition customFilter(ServiceFilter serviceFilter) {
        CustomServiceCallServiceFilterConfiguration conf = new CustomServiceCallServiceFilterConfiguration();
        conf.setServiceFilter(serviceFilter);

        setServiceFilterConfiguration(conf);

        return this;
    }

    // *****************************
    // Shortcuts - LoadBalancer
    // *****************************

    public ServiceCallDefinition defaultLoadBalancer() {
        DefaultServiceCallLoadBalancerConfiguration conf = new DefaultServiceCallLoadBalancerConfiguration();
        setLoadBalancerConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition ribbonLoadBalancer() {
        RibbonServiceCallLoadBalancerConfiguration conf = new RibbonServiceCallLoadBalancerConfiguration(this);
        setLoadBalancerConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition ribbonLoadBalancer(String clientName) {
        RibbonServiceCallLoadBalancerConfiguration conf = new RibbonServiceCallLoadBalancerConfiguration(this);
        conf.setClientName(clientName);

        setLoadBalancerConfiguration(conf);

        return this;
    }

    // *****************************
    // Processor Factory
    // *****************************

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        final CamelContext camelContext = routeContext.getCamelContext();
        final ServiceCallConfigurationDefinition config = retrieveConfig(camelContext);

        ServiceDiscovery serviceDiscovery = retrieveServiceDiscovery(camelContext, config);
        ServiceFilter serviceFilter = retrieveServiceFilter(camelContext, config);
        ServiceChooser serviceChooser = retrieveServiceChooser(camelContext, config);
        LoadBalancer loadBalancer = retrieveLoadBalancer(camelContext, config);
        Expression expression = retrieveExpression(camelContext, config);

        if (loadBalancer instanceof CamelContextAware) {
            ((CamelContextAware) loadBalancer).setCamelContext(camelContext);
        }
        if (loadBalancer instanceof ServiceDiscoveryAware) {
            ((ServiceDiscoveryAware) loadBalancer).setServiceDiscovery(serviceDiscovery);
        }
        if (loadBalancer instanceof ServiceFilterAware) {
            ((ServiceFilterAware) loadBalancer).setServiceFilter(serviceFilter);
        }
        if (loadBalancer instanceof ServiceChooserAware) {
            ((ServiceChooserAware) loadBalancer).setServiceChooser(serviceChooser);
        }

        // The component is used to configure what the default scheme to use (eg camel component name).
        // The component configured on EIP takes precedence vs configured on configuration.
        String component = this.component;
        if (component == null) {
            component = config != null ? config.getComponent() : null;
        }

        return new DefaultServiceCallProcessor(camelContext, name, component, uri, pattern, loadBalancer, expression);
    }

    // *****************************
    // Helpers
    // *****************************

    private ServiceCallConfigurationDefinition retrieveConfig(CamelContext camelContext) {
        ServiceCallConfigurationDefinition config = null;
        if (configurationRef != null) {
            // lookup in registry first
            config = CamelContextHelper.lookup(camelContext, configurationRef, ServiceCallConfigurationDefinition.class);
            if (config == null) {
                // and fallback as service configuration
                config = camelContext.getServiceCallConfiguration(configurationRef);
            }
        }

        if (config == null) {
            config = camelContext.getServiceCallConfiguration(null);
        }
        if (config == null) {
            // if no default then try to find if there configuration in the registry of the given type
            Set<ServiceCallConfigurationDefinition> set = camelContext.getRegistry().findByType(ServiceCallConfigurationDefinition.class);
            if (set.size() == 1) {
                config = set.iterator().next();
            }
        }

        return config;
    }

    private ServiceDiscovery retrieveServiceDiscovery(CamelContext camelContext, ServiceCallConfigurationDefinition config) throws Exception {
        ServiceDiscovery answer;
        if (serviceDiscoveryConfiguration != null) {
            answer = serviceDiscoveryConfiguration.newInstance(camelContext);
        } else if (config != null && config.getServiceDiscoveryConfiguration() != null) {
            answer = config.getServiceDiscoveryConfiguration().newInstance(camelContext);
        } else {
            answer = retrieve(ServiceDiscovery.class, camelContext, this::getServiceDiscovery, this::getServiceDiscoveryRef);
            if (answer == null && config != null) {
                answer = retrieve(ServiceDiscovery.class, camelContext, config::getServiceDiscovery, config::getServiceDiscoveryRef);
            }
            if (answer == null) {
                answer = findByType(camelContext, ServiceDiscovery.class);
            }
        }

        // If there's no configuration, let's try to find a suitable implementation
        if (answer == null) {
            for (ServiceCallServiceDiscoveryConfiguration configuration : ServiceCallConstants.SERVICE_DISCOVERY_CONFIGURATIONS) {
                try {
                    answer = configuration.newInstance(camelContext);

                    if (answer != null) {
                        break;
                    }
                } catch (NoFactoryAvailableException e) {
                    // skip
                }
            }
        }

        return answer;
    }

    private ServiceFilter retrieveServiceFilter(CamelContext camelContext, ServiceCallConfigurationDefinition config) throws Exception {
        ServiceFilter answer;

        if (serviceFilterConfiguration != null) {
            answer = serviceFilterConfiguration.newInstance(camelContext);
        } else if (config != null && config.getServiceFilterConfiguration() != null) {
            answer = config.getServiceFilterConfiguration().newInstance(camelContext);
        } else {
            answer = retrieve(ServiceFilter.class, camelContext, this::getServiceFilter, this::getServiceFilterRef);
            if (answer == null && config != null) {
                answer = retrieve(ServiceFilter.class, camelContext, config::getServiceFilter, config::getServiceFilterRef);

                // If the ServiceFilter is not found but a ref is set, try to determine
                // the implementation according to the ref name.
                if (answer == null) {
                    String ref = config.getServiceFilterRef();
                    if (ObjectHelper.equal("healthy", ref, true)) {
                        answer = new HealthyServiceFilter();
                    } else if (ObjectHelper.equal("pass-through", ref, true)) {
                        answer = new PassThroughServiceFilter();
                    } else if (ObjectHelper.equal("passthrough", ref, true)) {
                        answer = new PassThroughServiceFilter();
                    }
                }
            }
        }

        if (answer == null) {
            answer = findByType(camelContext, ServiceFilter.class);
        }

        // If there's no configuration, let's use the healthy strategy
        if (answer == null) {
            answer = new HealthyServiceFilter();
        }

        return answer;
    }

    private ServiceChooser retrieveServiceChooser(CamelContext camelContext, ServiceCallConfigurationDefinition config) {
        ServiceChooser answer = retrieve(ServiceChooser.class, camelContext, this::getServiceChooser, this::getServiceChooserRef);
        if (answer == null && config != null) {
            answer = retrieve(ServiceChooser.class, camelContext, config::getServiceChooser, config::getServiceChooserRef);

            // If the ServiceChooser is not found but a ref is set, try to determine
            // the implementation according to the ref name.
            if (answer == null) {
                String ref = config.getServiceChooserRef();
                if (ObjectHelper.equal("roundrobin", ref, true)) {
                    answer = new RoundRobinServiceChooser();
                } else if (ObjectHelper.equal("round-robin", ref, true)) {
                    answer = new RoundRobinServiceChooser();
                } else if (ObjectHelper.equal("random", ref, true)) {
                    answer = new RandomServiceChooser();
                }
            }
        }
        if (answer == null) {
            answer = findByType(camelContext, ServiceChooser.class);
        }

        // If there's no configuration, let's use the round-robin strategy
        if (answer == null) {
            answer = new RoundRobinServiceChooser();
        }

        return answer;
    }

    private LoadBalancer retrieveLoadBalancer(CamelContext camelContext, ServiceCallConfigurationDefinition config) throws Exception {
        LoadBalancer answer;
        if (loadBalancerConfiguration != null) {
            answer = loadBalancerConfiguration.newInstance(camelContext);
        } else if (config != null && config.getLoadBalancerConfiguration() != null) {
            answer = config.getLoadBalancerConfiguration().newInstance(camelContext);
        } else {
            answer = retrieve(LoadBalancer.class, camelContext, this::getLoadBalancer, this::getLoadBalancerRef);
            if (answer == null && config != null) {
                answer = retrieve(LoadBalancer.class, camelContext, config::getLoadBalancer, config::getLoadBalancerRef);
            }
            if (answer == null) {
                answer = findByType(camelContext, LoadBalancer.class);
            }
        }

        // If there's no configuration, let's try to find a suitable implementation
        if (answer == null) {
            for (ServiceCallLoadBalancerConfiguration configuration : ServiceCallConstants.LOAD_BALANCER_CONFIGURATIONS) {
                try {
                    answer = configuration.newInstance(camelContext);

                    if (answer != null) {
                        break;
                    }
                } catch (NoFactoryAvailableException e) {
                    // skip
                }
            }
        }

        if (answer == null) {
            answer = new DefaultLoadBalancer();
        }

        return answer;
    }

    private Expression retrieveExpression(CamelContext camelContext, ServiceCallConfigurationDefinition config) throws Exception {
        Expression answer;

        if (expressionConfiguration != null) {
            answer = expressionConfiguration.newInstance(camelContext);
        } else if (config != null && config.getExpressionConfiguration() != null) {
            answer = config.getExpressionConfiguration().newInstance(camelContext);
        } else {
            answer = retrieve(Expression.class, camelContext, this::getExpression, this::getExpressionRef);
            if (answer == null && config != null) {
                answer = retrieve(Expression.class, camelContext, config::getExpression, config::getExpressionRef);
            }
            if (answer == null) {
                answer = findByType(camelContext, Expression.class);
            }
        }

        if (answer == null) {
            answer = new DefaultServiceCallExpression();
        }

        return answer;
    }

    private <T> T retrieve(Class<T> type, CamelContext camelContext, Supplier<T> instanceSupplier, Supplier<String> refSupplier) {
        T answer = null;
        if (instanceSupplier != null) {
            answer = instanceSupplier.get();
        }

        if (answer == null && refSupplier != null) {
            String ref = refSupplier.get();
            if (ref != null) {
                answer = CamelContextHelper.lookup(camelContext, ref, type);
            }
        }

        return answer;
    }

    private <T> T findByType(CamelContext camelContext, Class<T> type) {
        Set<T> set = camelContext.getRegistry().findByType(type);
        if (set.size() == 1) {
            return set.iterator().next();
        }

        return null;
    }
}
