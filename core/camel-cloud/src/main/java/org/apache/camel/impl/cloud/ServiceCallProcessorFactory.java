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
package org.apache.camel.impl.cloud;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceChooserAware;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryAware;
import org.apache.camel.cloud.ServiceExpressionFactory;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterAware;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.model.Model;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.cloud.ServiceCallDefinition;
import org.apache.camel.model.cloud.ServiceCallDefinitionConstants;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.TypedProcessorFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.apache.camel.util.function.ThrowingHelper;

import static org.apache.camel.support.CamelContextHelper.findSingleByType;
import static org.apache.camel.support.CamelContextHelper.lookup;

@Deprecated
public class ServiceCallProcessorFactory extends TypedProcessorFactory<ServiceCallDefinition> {

    private volatile ServiceCallDefinition definition;

    public ServiceCallProcessorFactory() {
        super(ServiceCallDefinition.class);
    }

    // *****************************
    // Processor Factory
    // *****************************

    @Override
    public Processor doCreateProcessor(Route route, ServiceCallDefinition definition) throws Exception {
        this.definition = definition;

        final CamelContext camelContext = route.getCamelContext();
        final ServiceDiscovery serviceDiscovery = retrieveServiceDiscovery(camelContext);
        final ServiceFilter serviceFilter = retrieveServiceFilter(camelContext);
        final ServiceChooser serviceChooser = retrieveServiceChooser(camelContext);
        final ServiceLoadBalancer loadBalancer = retrieveLoadBalancer(camelContext);

        CamelContextAware.trySetCamelContext(serviceDiscovery, camelContext);
        CamelContextAware.trySetCamelContext(serviceFilter, camelContext);
        CamelContextAware.trySetCamelContext(serviceChooser, camelContext);
        CamelContextAware.trySetCamelContext(loadBalancer, camelContext);

        if (loadBalancer instanceof ServiceDiscoveryAware) {
            ((ServiceDiscoveryAware) loadBalancer).setServiceDiscovery(serviceDiscovery);
        }
        if (loadBalancer instanceof ServiceFilterAware) {
            ((ServiceFilterAware) loadBalancer).setServiceFilter(serviceFilter);
        }
        if (loadBalancer instanceof ServiceChooserAware) {
            ((ServiceChooserAware) loadBalancer).setServiceChooser(serviceChooser);
        }

        // The component is used to configure the default scheme to use (eg
        // camel component name).
        // The component configured on EIP takes precedence vs configured on
        // configuration.
        String endpointScheme = definition.getComponent();
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
        // The uri configured on EIP takes precedence vs configured on
        // configuration.
        String endpointUri = definition.getUri();
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
        ObjectHelper.notNull(definition.getName(), "Service name");

        endpointScheme = ThrowingHelper.applyIfNotEmpty(endpointScheme, camelContext::resolvePropertyPlaceholders,
                () -> ServiceCallDefinitionConstants.DEFAULT_COMPONENT);
        endpointUri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, endpointUri);
        ExchangePattern pattern = CamelContextHelper.parse(camelContext, ExchangePattern.class, definition.getPattern());

        Expression expression = retrieveExpression(camelContext, endpointScheme);
        if (expression instanceof ExpressionFactory) {
            expression = ((ExpressionFactory) expression).createExpression(camelContext);
        }
        return new DefaultServiceCallProcessor(
                camelContext, camelContext.resolvePropertyPlaceholders(definition.getName()), endpointScheme, endpointUri,
                pattern,
                loadBalancer, expression);
    }

    // *****************************
    // Helpers
    // *****************************

    private ServiceCallConfigurationDefinition retrieveDefaultConfig(CamelContext camelContext) {
        // check if a default configuration is bound to the registry
        ServiceCallConfigurationDefinition config
                = camelContext.getCamelContextExtension().getContextPlugin(Model.class).getServiceCallConfiguration(null);

        if (config == null) {
            // Or if it is in the registry
            config = lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_CONFIG_ID,
                    ServiceCallConfigurationDefinition.class);
        }

        if (config == null) {
            // If no default is set either by searching by name or bound to the
            // camel context, assume that if there is a single instance in the
            // registry, that is the default one
            config = findSingleByType(camelContext, ServiceCallConfigurationDefinition.class);
        }

        return config;
    }

    private ServiceCallConfigurationDefinition retrieveConfig(CamelContext camelContext) {
        ServiceCallConfigurationDefinition config = null;
        if (definition.getConfigurationRef() != null) {
            // lookup in registry firstNotNull
            config = lookup(camelContext, definition.getConfigurationRef(), ServiceCallConfigurationDefinition.class);
            if (config == null) {
                // and fallback as service configuration
                config = camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                        .getServiceCallConfiguration(definition.getConfigurationRef());
            }
        }

        return config;
    }

    // ******************************************
    // ServiceDiscovery
    // ******************************************

    private ServiceDiscovery retrieveServiceDiscovery(
            CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function)
            throws Exception {
        ServiceDiscovery answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getServiceDiscoveryConfiguration() != null) {
                answer = config.getServiceDiscoveryConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(ServiceDiscovery.class, camelContext, config::getServiceDiscovery,
                        config::getServiceDiscoveryRef);
            }
        }

        return answer;
    }

    private ServiceDiscovery retrieveServiceDiscovery(CamelContext camelContext) throws Exception {
        return Suppliers
                .firstNotNull(
                        () -> (definition.getServiceDiscoveryConfiguration() != null)
                                ? definition.getServiceDiscoveryConfiguration().newInstance(camelContext) : null,
                        // Local configuration
                        () -> retrieve(ServiceDiscovery.class, camelContext, definition::getServiceDiscovery,
                                definition::getServiceDiscoveryRef),
                        // Linked configuration
                        () -> retrieveServiceDiscovery(camelContext, this::retrieveConfig),
                        // Default configuration
                        () -> retrieveServiceDiscovery(camelContext, this::retrieveDefaultConfig),
                        // Check if there is a single instance in the registry
                        () -> findSingleByType(camelContext, ServiceDiscovery.class),
                        // From registry
                        () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_DISCOVERY_ID,
                                ServiceDiscovery.class))
                .orElseGet(
                        // Default, that's s little ugly but a load balancer may
                        // live without so let's delegate the null check
                        // to the actual impl.
                        () -> null);
    }

    // ******************************************
    // ServiceFilter
    // ******************************************

    private ServiceFilter retrieveServiceFilter(
            CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function)
            throws Exception {
        ServiceFilter answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getServiceFilterConfiguration() != null) {
                answer = config.getServiceFilterConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(ServiceFilter.class, camelContext, config::getServiceFilter, config::getServiceFilterRef);
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
        return Suppliers
                .firstNotNull(
                        () -> (definition.getServiceFilterConfiguration() != null)
                                ? definition.getServiceFilterConfiguration().newInstance(camelContext) : null,
                        // Local configuration
                        () -> retrieve(ServiceFilter.class, camelContext, definition::getServiceFilter,
                                definition::getServiceFilterRef),
                        // Linked configuration
                        () -> retrieveServiceFilter(camelContext, this::retrieveConfig),
                        // Default configuration
                        () -> retrieveServiceFilter(camelContext, this::retrieveDefaultConfig),
                        // Check if there is a single instance in
                        // the registry
                        () -> findSingleByType(camelContext, ServiceFilter.class),
                        // From registry
                        () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_FILTER_ID,
                                ServiceFilter.class))
                .orElseGet(
                        // Default
                        () -> new HealthyServiceFilter());
    }

    // ******************************************
    // ServiceChooser
    // ******************************************

    private ServiceChooser retrieveServiceChooser(
            CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function)
            throws Exception {
        ServiceChooser answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            answer = retrieve(ServiceChooser.class, camelContext, config::getServiceChooser, config::getServiceChooserRef);

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
                () -> retrieve(ServiceChooser.class, camelContext, definition::getServiceChooser,
                        definition::getServiceChooserRef),
                // Linked configuration
                () -> retrieveServiceChooser(camelContext, this::retrieveConfig),
                // Default configuration
                () -> retrieveServiceChooser(camelContext, this::retrieveDefaultConfig),
                // Check if there is a single instance in
                // the registry
                () -> findSingleByType(camelContext, ServiceChooser.class),
                // From registry
                () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_CHOOSER_ID, ServiceChooser.class))
                .orElseGet(
                        // Default
                        () -> new RoundRobinServiceChooser());
    }

    // ******************************************
    // LoadBalancer
    // ******************************************

    private ServiceLoadBalancer retrieveLoadBalancer(
            CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function)
            throws Exception {
        ServiceLoadBalancer answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getLoadBalancerConfiguration() != null) {
                answer = config.getLoadBalancerConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(ServiceLoadBalancer.class, camelContext, config::getLoadBalancer, config::getLoadBalancerRef);
            }
        }

        return answer;
    }

    private ServiceLoadBalancer retrieveLoadBalancer(CamelContext camelContext) throws Exception {
        return Suppliers
                .firstNotNull(
                        () -> (definition.getLoadBalancerConfiguration() != null)
                                ? definition.getLoadBalancerConfiguration().newInstance(camelContext) : null,
                        // Local configuration
                        () -> retrieve(ServiceLoadBalancer.class, camelContext, definition::getLoadBalancer,
                                definition::getLoadBalancerRef),
                        // Linked configuration
                        () -> retrieveLoadBalancer(camelContext, this::retrieveConfig),
                        // Default configuration
                        () -> retrieveLoadBalancer(camelContext, this::retrieveDefaultConfig),
                        // Check if there is a single instance in
                        // the registry
                        () -> findSingleByType(camelContext, ServiceLoadBalancer.class),
                        // From registry
                        () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_LOAD_BALANCER_ID,
                                ServiceLoadBalancer.class))
                .orElseGet(
                        // Default
                        DefaultServiceLoadBalancer::new);
    }

    // ******************************************
    // Expression
    // ******************************************

    private Expression retrieveExpression(
            CamelContext camelContext, Function<CamelContext, ServiceCallConfigurationDefinition> function)
            throws Exception {
        Expression answer = null;

        ServiceCallConfigurationDefinition config = function.apply(camelContext);
        if (config != null) {
            if (config.getExpressionConfiguration() != null) {
                answer = config.getExpressionConfiguration().newInstance(camelContext);
            } else {
                answer = retrieve(Expression.class, camelContext, config::getExpression, config::getExpressionRef);
            }
        }

        return answer;
    }

    private Expression retrieveExpression(CamelContext camelContext, String component) throws Exception {
        Optional<Expression> expression = Suppliers
                .firstNotNull(
                        () -> (definition.getExpressionConfiguration() != null)
                                ? definition.getExpressionConfiguration().newInstance(camelContext) : null,
                        // Local configuration
                        () -> retrieve(Expression.class, camelContext, definition::getExpression, definition::getExpressionRef),
                        // Linked configuration
                        () -> retrieveExpression(camelContext, this::retrieveConfig),
                        // Default configuration
                        () -> retrieveExpression(camelContext, this::retrieveDefaultConfig),
                        // From registry
                        () -> lookup(camelContext, ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_EXPRESSION_ID,
                                Expression.class));

        if (expression.isPresent()) {
            return expression.get();
        } else {
            String lookupName = component + "-service-expression";
            // First try to find the factory from the registry.
            ServiceExpressionFactory factory
                    = CamelContextHelper.lookup(camelContext, lookupName, ServiceExpressionFactory.class);
            if (factory != null) {
                // If a factory is found in the registry do not re-configure it
                // as
                // it should be pre-configured.
                return factory.newInstance(camelContext);
            } else {

                Class<?> type = null;

                try {
                    // Then use Service factory.
                    type = camelContext.getCamelContextExtension()
                            .getFactoryFinder(ServiceCallDefinitionConstants.RESOURCE_PATH).findClass(lookupName).orElse(null);
                } catch (Exception e) {
                }

                if (ObjectHelper.isNotEmpty(type)) {
                    if (ServiceExpressionFactory.class.isAssignableFrom(type)) {
                        factory = (ServiceExpressionFactory) camelContext.getInjector().newInstance(type, false);
                    } else {
                        throw new IllegalArgumentException(
                                "Resolving Expression: " + lookupName
                                                           + " detected type conflict: Not a ServiceExpressionFactory implementation. Found: "
                                                           + type.getName());
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

    private <T> T retrieve(
            Class<T> type, CamelContext camelContext, Supplier<T> instanceSupplier, Supplier<String> refSupplier) {
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
