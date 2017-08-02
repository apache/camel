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

import java.util.Optional;
import java.util.function.Function;
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
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceChooserAware;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryAware;
import org.apache.camel.cloud.ServiceExpressionFactory;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterAware;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.impl.cloud.DefaultServiceCallExpression;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.apache.camel.impl.cloud.DefaultServiceLoadBalancer;
import org.apache.camel.impl.cloud.HealthyServiceFilter;
import org.apache.camel.impl.cloud.PassThroughServiceFilter;
import org.apache.camel.impl.cloud.RandomServiceChooser;
import org.apache.camel.impl.cloud.RoundRobinServiceChooser;
import org.apache.camel.model.NoOutputDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;

import static org.apache.camel.util.CamelContextHelper.findByType;
import static org.apache.camel.util.CamelContextHelper.lookup;

/**
 * To call remote services
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "serviceCall")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceCallDefinition extends NoOutputDefinition<ServiceCallDefinition> {
    @XmlAttribute @Metadata(required = "true")
    private String name;
    @XmlAttribute
    private String uri;
    @XmlAttribute @Metadata(defaultValue = ServiceCallDefinitionConstants.DEFAULT_COMPONENT)
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
    private ServiceLoadBalancer loadBalancer;
    @XmlAttribute
    private String expressionRef;
    @XmlTransient
    private Expression expression;

    @XmlElements({
        @XmlElement(name = "cachingServiceDiscovery", type = CachingServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "aggregatingServiceDiscovery", type = AggregatingServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "consulServiceDiscovery", type = ConsulServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "dnsServiceDiscovery", type = DnsServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "etcdServiceDiscovery", type = EtcdServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "kubernetesServiceDiscovery", type = KubernetesServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "staticServiceDiscovery", type = StaticServiceCallServiceDiscoveryConfiguration.class),
        @XmlElement(name = "zookeeperServiceDiscovery", type = ZooKeeperServiceCallServiceDiscoveryConfiguration.class)}
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
        @XmlElement(name = "ribbonLoadBalancer", type = RibbonServiceCallServiceLoadBalancerConfiguration.class),
        @XmlElement(name = "defaultLoadBalancer", type = DefaultServiceCallServiceLoadBalancerConfiguration.class) }
    )
    private ServiceCallServiceLoadBalancerConfiguration loadBalancerConfiguration;

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
     * Sets a reference to a custom {@link ServiceLoadBalancer} to use.
     */
    public void setLoadBalancerRef(String loadBalancerRef) {
        this.loadBalancerRef = loadBalancerRef;
    }

    public ServiceLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Sets a custom {@link ServiceLoadBalancer} to use.
     */
    public void setLoadBalancer(ServiceLoadBalancer loadBalancer) {
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

    public ServiceCallServiceLoadBalancerConfiguration getLoadBalancerConfiguration() {
        return loadBalancerConfiguration;
    }

    /**
     * Configures the LoadBalancer using the given configuration.
     */
    public void setLoadBalancerConfiguration(ServiceCallServiceLoadBalancerConfiguration loadBalancerConfiguration) {
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
     * Sets a reference to a custom {@link ServiceLoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(String loadBalancerRef) {
        setLoadBalancerRef(loadBalancerRef);
        return this;
    }

    /**
     * Sets a custom {@link ServiceLoadBalancer} to use.
     */
    public ServiceCallDefinition loadBalancer(ServiceLoadBalancer loadBalancer) {
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
     * Sets a custom {@link Expression} to use through an expression builder clause.
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ServiceCallDefinition> expression() {
        ExpressionClause<ServiceCallDefinition> clause = new ExpressionClause<>(this);
        setExpression(clause);

        return clause;
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
    public ServiceCallDefinition loadBalancerConfiguration(ServiceCallServiceLoadBalancerConfiguration loadBalancerConfiguration) {
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

    public ServiceCallDefinition consulServiceDiscovery(String url) {
        ConsulServiceCallServiceDiscoveryConfiguration conf = new ConsulServiceCallServiceDiscoveryConfiguration(this);
        conf.setUrl(url);

        setServiceDiscoveryConfiguration(conf);

        return this;
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

    public ServiceCallDefinition etcdServiceDiscovery(String uris) {
        EtcdServiceCallServiceDiscoveryConfiguration conf = new EtcdServiceCallServiceDiscoveryConfiguration(this);
        conf.setUris(uris);

        setServiceDiscoveryConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition etcdServiceDiscovery(String uris, String servicePath) {
        EtcdServiceCallServiceDiscoveryConfiguration conf = new EtcdServiceCallServiceDiscoveryConfiguration(this);
        conf.setUris(uris);
        conf.setServicePath(servicePath);

        setServiceDiscoveryConfiguration(conf);

        return this;
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

    public AggregatingServiceCallServiceDiscoveryConfiguration multiServiceDiscovery() {
        AggregatingServiceCallServiceDiscoveryConfiguration conf = new AggregatingServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public StaticServiceCallServiceDiscoveryConfiguration staticServiceDiscovery() {
        StaticServiceCallServiceDiscoveryConfiguration conf = new StaticServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public ZooKeeperServiceCallServiceDiscoveryConfiguration zookeeperServiceDiscovery() {
        ZooKeeperServiceCallServiceDiscoveryConfiguration conf = new ZooKeeperServiceCallServiceDiscoveryConfiguration(this);
        setServiceDiscoveryConfiguration(conf);

        return conf;
    }

    public ServiceCallDefinition zookeeperServiceDiscovery(String nodes, String basePath) {
        ZooKeeperServiceCallServiceDiscoveryConfiguration conf = new ZooKeeperServiceCallServiceDiscoveryConfiguration(this);
        conf.setNodes(nodes);
        conf.setBasePath(basePath);

        setServiceDiscoveryConfiguration(conf);

        return this;
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

    public BlacklistServiceCallServiceFilterConfiguration blacklistFilter() {
        BlacklistServiceCallServiceFilterConfiguration conf = new BlacklistServiceCallServiceFilterConfiguration();
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
        DefaultServiceCallServiceLoadBalancerConfiguration conf = new DefaultServiceCallServiceLoadBalancerConfiguration();
        setLoadBalancerConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition ribbonLoadBalancer() {
        RibbonServiceCallServiceLoadBalancerConfiguration conf = new RibbonServiceCallServiceLoadBalancerConfiguration(this);
        setLoadBalancerConfiguration(conf);

        return this;
    }

    public ServiceCallDefinition ribbonLoadBalancer(String clientName) {
        RibbonServiceCallServiceLoadBalancerConfiguration conf = new RibbonServiceCallServiceLoadBalancerConfiguration(this);
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
        final ServiceDiscovery serviceDiscovery = retrieveServiceDiscovery(camelContext);
        final ServiceFilter serviceFilter = retrieveServiceFilter(camelContext);
        final ServiceChooser serviceChooser = retrieveServiceChooser(camelContext);
        final ServiceLoadBalancer loadBalancer = retrieveLoadBalancer(camelContext);

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

        // The component is used to configure the default scheme to use (eg camel component name).
        // The component configured on EIP takes precedence vs configured on configuration.
        String endpointScheme = this.component;
        if (endpointScheme == null) {
            ServiceCallConfigurationDefinition conf = retrieveConfig(camelContext);
            if (conf != null) {
                endpointScheme = conf.getComponent();
            }
        }
        if (endpointScheme == null) {
            ServiceCallConfigurationDefinition conf = retrieveDefaultConfig(camelContext);
            if (conf != null) {
                endpointScheme = conf.getComponent();
            }
        }

        // The uri is used to tweak the uri.
        // The uri configured on EIP takes precedence vs configured on configuration.
        String endpointUri = this.uri;
        if (endpointUri == null) {
            ServiceCallConfigurationDefinition conf = retrieveConfig(camelContext);
            if (conf != null) {
                endpointUri = conf.getUri();
            }
        }
        if (endpointUri == null) {
            ServiceCallConfigurationDefinition conf = retrieveDefaultConfig(camelContext);
            if (conf != null) {
                endpointUri = conf.getUri();
            }
        }

        // Service name is mandatory
        ObjectHelper.notNull(name, "Service name");

        endpointScheme = ObjectHelper.applyIfNotEmpty(endpointScheme, camelContext::resolvePropertyPlaceholders, () -> ServiceCallDefinitionConstants.DEFAULT_COMPONENT);
        endpointUri = ObjectHelper.applyIfNotEmpty(endpointUri, camelContext::resolvePropertyPlaceholders, () -> null);

        return new DefaultServiceCallProcessor(
            camelContext,
            camelContext.resolvePropertyPlaceholders(name),
            endpointScheme,
            endpointUri,
            pattern,
            loadBalancer,
            retrieveExpression(camelContext, endpointScheme));
    }

    // *****************************
    // Helpers
    // *****************************

    private ServiceCallConfigurationDefinition retrieveDefaultConfig(CamelContext camelContext) {
        // check if a default configuration is bound to the registry
        ServiceCallConfigurationDefinition config = camelContext.getServiceCallConfiguration(null);

        if (config == null) {
            // Or if it is in the registry
            config = lookup(
                camelContext,
                ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_CONFIG_ID,
                ServiceCallConfigurationDefinition.class);
        }

        if (config == null) {
            // If no default is set either by searching by name or bound to the
            // camel context, assume that if there is a single instance in the
            // registry, that is the default one
            config = findByType(camelContext, ServiceCallConfigurationDefinition.class);
        }

        return config;
    }

    private ServiceCallConfigurationDefinition retrieveConfig(CamelContext camelContext) {
        ServiceCallConfigurationDefinition config = null;
        if (configurationRef != null) {
            // lookup in registry firstNotNull
            config = lookup(camelContext, configurationRef, ServiceCallConfigurationDefinition.class);
            if (config == null) {
                // and fallback as service configuration
                config = camelContext.getServiceCallConfiguration(configurationRef);
            }
        }

        return config;
    }

    // ******************************************
    // ServiceDiscovery
    // ******************************************

    private ServiceDiscovery retrieveServiceDiscovery(CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function) throws Exception {
        ServiceDiscovery answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getServiceDiscoveryConfiguration() != null) {
                answer = config.getServiceDiscoveryConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(
                    ServiceDiscovery.class,
                    camelContext,
                    config::getServiceDiscovery,
                    config::getServiceDiscoveryRef
                );
            }
        }

        return answer;
    }

    private ServiceDiscovery retrieveServiceDiscovery(CamelContext camelContext) throws Exception {
        return Suppliers.firstNotNull(
            () -> (serviceDiscoveryConfiguration != null) ? serviceDiscoveryConfiguration.newInstance(camelContext) : null,
            // Local configuration
            () -> retrieve(ServiceDiscovery.class, camelContext, this::getServiceDiscovery, this::getServiceDiscoveryRef),
            // Linked configuration
            () -> retrieveServiceDiscovery(camelContext, this::retrieveConfig),
            // Default configuration
            () -> retrieveServiceDiscovery(camelContext, this::retrieveDefaultConfig),
            // Check if there is a single instance in the registry
            () -> findByType(camelContext, ServiceDiscovery.class),
            // From registry
            () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_DISCOVERY_ID, ServiceDiscovery.class)
        ).orElseGet(
            // Default, that's s little ugly but a load balancer may live without
            // (i.e. the Ribbon one) so let's delegate the null check to the actual
            // impl.
            () -> null
        );
    }

    // ******************************************
    // ServiceFilter
    // ******************************************

    private ServiceFilter retrieveServiceFilter(CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function) throws Exception {
        ServiceFilter answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getServiceFilterConfiguration() != null) {
                answer = config.getServiceFilterConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(
                    ServiceFilter.class,
                    camelContext,
                    config::getServiceFilter,
                    config::getServiceFilterRef
                );
            }

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

        return answer;
    }

    private ServiceFilter retrieveServiceFilter(CamelContext camelContext) throws Exception {
        return Suppliers.firstNotNull(
            () -> (serviceFilterConfiguration != null) ? serviceFilterConfiguration.newInstance(camelContext) : null,
            // Local configuration
            () -> retrieve(ServiceFilter.class, camelContext, this::getServiceFilter, this::getServiceFilterRef),
            // Linked configuration
            () -> retrieveServiceFilter(camelContext, this::retrieveConfig),
            // Default configuration
            () -> retrieveServiceFilter(camelContext, this::retrieveDefaultConfig),
            // Check if there is a single instance in the registry
            () -> findByType(camelContext, ServiceFilter.class),
            // From registry
            () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_FILTER_ID, ServiceFilter.class)
        ).orElseGet(
            // Default
            () -> new HealthyServiceFilter()
        );
    }

    // ******************************************
    // ServiceChooser
    // ******************************************

    private ServiceChooser retrieveServiceChooser(CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function) throws Exception {
        ServiceChooser answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            answer = retrieve(
                ServiceChooser.class,
                camelContext,
                config::getServiceChooser,
                config::getServiceChooserRef
            );

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

        return answer;
    }

    private ServiceChooser retrieveServiceChooser(CamelContext camelContext) throws Exception {
        return Suppliers.firstNotNull(
            // Local configuration
            () -> retrieve(ServiceChooser.class, camelContext, this::getServiceChooser, this::getServiceChooserRef),
            // Linked configuration
            () -> retrieveServiceChooser(camelContext, this::retrieveConfig),
            // Default configuration
            () -> retrieveServiceChooser(camelContext, this::retrieveDefaultConfig),
            // Check if there is a single instance in the registry
            () -> findByType(camelContext, ServiceChooser.class),
            // From registry
            () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_CHOOSER_ID, ServiceChooser.class)
        ).orElseGet(
            // Default
            () -> new RoundRobinServiceChooser()
        );
    }

    // ******************************************
    // LoadBalancer
    // ******************************************

    private ServiceLoadBalancer retrieveLoadBalancer(CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function) throws Exception {
        ServiceLoadBalancer answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getLoadBalancerConfiguration() != null) {
                answer = config.getLoadBalancerConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(
                    ServiceLoadBalancer.class,
                    camelContext,
                    config::getLoadBalancer,
                    config::getLoadBalancerRef
                );
            }
        }

        return answer;
    }

    private ServiceLoadBalancer retrieveLoadBalancer(CamelContext camelContext) throws Exception {
        return Suppliers.firstNotNull(
            () -> (loadBalancerConfiguration != null) ? loadBalancerConfiguration.newInstance(camelContext) : null,
            // Local configuration
            () -> retrieve(ServiceLoadBalancer.class, camelContext, this::getLoadBalancer, this::getLoadBalancerRef),
            // Linked configuration
            () -> retrieveLoadBalancer(camelContext, this::retrieveConfig),
            // Default configuration
            () -> retrieveLoadBalancer(camelContext, this::retrieveDefaultConfig),
            // Check if there is a single instance in the registry
            () -> findByType(camelContext, ServiceLoadBalancer.class),
            // From registry
            () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_LOAD_BALANCER_ID, ServiceLoadBalancer.class)
        ).orElseGet(
            // Default
            () -> new DefaultServiceLoadBalancer()
        );
    }

    // ******************************************
    // Expression
    // ******************************************

    private Expression retrieveExpression(CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function) throws Exception {
        Expression answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getExpressionConfiguration() != null) {
                answer = config.getExpressionConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(
                    Expression.class,
                    camelContext,
                    config::getExpression,
                    config::getExpressionRef
                );
            }
        }

        return answer;
    }

    private Expression retrieveExpression(CamelContext camelContext, String component) throws Exception {
        Optional<Expression> expression = Suppliers.firstNotNull(
            () -> (expressionConfiguration != null) ? expressionConfiguration.newInstance(camelContext) : null,
            // Local configuration
            () -> retrieve(Expression.class, camelContext, this::getExpression, this::getExpressionRef),
            // Linked configuration
            () -> retrieveExpression(camelContext, this::retrieveConfig),
            // Default configuration
            () -> retrieveExpression(camelContext, this::retrieveDefaultConfig),
            // From registry
            () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_EXPRESSION_ID, Expression.class)
        );

        if (expression.isPresent()) {
            return expression.get();
        } else {
            String lookupName = component + "-service-expression";
            // First try to find the factory from the registry.
            ServiceExpressionFactory factory = CamelContextHelper.lookup(camelContext, lookupName, ServiceExpressionFactory.class);
            if (factory != null) {
                // If a factory is found in the registry do not re-configure it as
                // it should be pre-configured.
                return factory.newInstance(camelContext);
            } else {

                Class<?> type = null;

                try {
                    // Then use Service factory.
                    type = camelContext.getFactoryFinder(ServiceCallDefinitionConstants.RESOURCE_PATH).findClass(lookupName);
                } catch (Exception e) {
                }

                if (ObjectHelper.isNotEmpty(type)) {
                    if (ServiceExpressionFactory.class.isAssignableFrom(type)) {
                        factory = (ServiceExpressionFactory) camelContext.getInjector().newInstance(type);
                    } else {
                        throw new IllegalArgumentException(
                            "Resolving Expression: " + lookupName + " detected type conflict: Not a ServiceExpressionFactory implementation. Found: " + type.getName());
                    }
                } else {
                    // If no factory is found, returns the default
                    factory = context -> new DefaultServiceCallExpression();
                }

                return factory.newInstance(camelContext);
            }
        }
    }

    // ************************************
    // Helpers
    // ************************************

    private <T> T retrieve(Class<T> type, CamelContext camelContext, Supplier<T> instanceSupplier, Supplier<String> refSupplier) {
        T answer = null;
        if (instanceSupplier != null) {
            answer = instanceSupplier.get();
        }

        if (answer == null && refSupplier != null) {
            String ref = refSupplier.get();
            if (ref != null) {
                answer = lookup(camelContext, ref, type);
            }
        }

        return answer;
    }
}
