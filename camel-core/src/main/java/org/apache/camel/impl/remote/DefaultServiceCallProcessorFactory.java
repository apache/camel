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
package org.apache.camel.impl.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.CamelContextAware;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.remote.ServiceCallConfigurationDefinition;
import org.apache.camel.model.remote.ServiceCallDefinition;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

public abstract class DefaultServiceCallProcessorFactory<C, S extends ServiceCallServer> implements ProcessorFactory {
    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        // not in use
        return null;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception {
        return definition instanceof ServiceCallDefinition
            ? createProcessor(routeContext, (ServiceCallDefinition) definition, createConfiguration(routeContext))
            : null;
    }

    protected Processor createProcessor(RouteContext routeContext, ServiceCallDefinition definition, C cfg) throws Exception {
        String name = definition.getName();
        String uri = definition.getUri();
        ExchangePattern mep = definition.getPattern();

        ServiceCallConfigurationDefinition config = definition.getServiceCallConfiguration();
        ServiceCallConfigurationDefinition configRef = null;
        if (definition.getServiceCallConfigurationRef() != null) {
            // lookup in registry first
            configRef = CamelContextHelper.lookup(routeContext.getCamelContext(), definition.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
            if (configRef == null) {
                // and fallback as service configuration
                routeContext.getCamelContext().getServiceCallConfiguration(definition.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
            }
        }

        // if no configuration explicit configured then use default
        if (config == null && configRef == null) {
            config = routeContext.getCamelContext().getServiceCallConfiguration(null, ServiceCallConfigurationDefinition.class);
        }
        if (config == null) {
            // if no default then try to find if there configuration in the registry of the given type
            Set<ServiceCallConfigurationDefinition> set = routeContext.getCamelContext().getRegistry().findByType(ServiceCallConfigurationDefinition.class);
            if (set.size() == 1) {
                config = set.iterator().next();
            }
        }

        if (config == null && configRef == null) {
            throw new IllegalStateException("The ServiceCall: " + definition + " must be configured before it can be used.");
        }

        if (cfg != null) {
            // extract the properties from the configuration from the model
            Map<String, Object> parameters = new HashMap<>();
            if (configRef != null) {
                IntrospectionSupport.getProperties(configRef, parameters, null);
            }
            if (config != null) {
                IntrospectionSupport.getProperties(config, parameters, null);
            }

            IntrospectionSupport.setProperties(cfg, parameters);
        }

        // lookup the load balancer to use (configured on EIP takes precedence vs configured on configuration)
        ServiceCallLoadBalancer lb = configureLoadBalancer(cfg, routeContext, definition);
        if (lb == null && config != null) {
            lb = configureLoadBalancer(cfg, routeContext, config);
        }
        if (lb == null && configRef != null) {
            lb = configureLoadBalancer(cfg, routeContext, configRef);
        }

        // lookup the server list strategy to use (configured on EIP takes precedence vs configured on configuration)
        ServiceCallServerListStrategy sl = configureServerListStrategy(cfg, routeContext, definition);
        if (sl == null && config != null) {
            sl = configureServerListStrategy(cfg, routeContext, config);
        }
        if (sl == null && configRef != null) {
            sl = configureServerListStrategy(cfg, routeContext, configRef);
        }

        // the component is used to configure what the default scheme to use (eg camel component name)
        String component = config != null ? config.getComponent() : null;
        if (component == null && configRef != null) {
            component = configRef.getComponent();
        }

        if (ObjectHelper.isNotEmpty(lb) && lb instanceof CamelContextAware) {
            ((CamelContextAware)lb).setCamelContext(routeContext.getCamelContext());
        }

        if (ObjectHelper.isNotEmpty(sl) && sl instanceof CamelContextAware) {
            ((CamelContextAware)sl).setCamelContext(routeContext.getCamelContext());
        }

        if (sl == null) {
            sl = createDefaultServerListStrategy(cfg);
        }
        if (lb == null) {
            lb = createDefaultLoadBalancer(cfg);
        }

        Map<String, String> properties = configureProperties(routeContext, config, configRef);

        DefaultServiceCallProcessor processor = createProcessor(name, component, uri, mep, cfg, properties);
        if (sl != null && processor.getServerListStrategy() == null) {
            processor.setServerListStrategy(sl);
        }
        if (lb != null && processor.getLoadBalancer() == null) {
            processor.setLoadBalancer(lb);
        }

        return processor;
    }

    protected Map<String, String> configureProperties(RouteContext routeContext, ServiceCallConfigurationDefinition config, ServiceCallConfigurationDefinition configRef) throws Exception {
        Map<String, String> answer = new HashMap<>();
        if (config != null && config.getProperties() != null) {
            for (PropertyDefinition prop : config.getProperties()) {
                // support property placeholders
                String key = CamelContextHelper.parseText(routeContext.getCamelContext(), prop.getKey());
                String value = CamelContextHelper.parseText(routeContext.getCamelContext(), prop.getValue());
                answer.put(key, value);
            }
        }
        if (configRef != null && configRef.getProperties() != null) {
            for (PropertyDefinition prop : configRef.getProperties()) {
                // support property placeholders
                String key = CamelContextHelper.parseText(routeContext.getCamelContext(), prop.getKey());
                String value = CamelContextHelper.parseText(routeContext.getCamelContext(), prop.getValue());
                answer.put(key, value);
            }
        }
        return answer;
    }

    protected ServiceCallLoadBalancer configureLoadBalancer(C conf, RouteContext routeContext, ServiceCallDefinition sd)  throws Exception {
        ServiceCallLoadBalancer lb = null;
        String ref;

        if (sd != null) {
            lb = sd.getLoadBalancer();
            ref = sd.getLoadBalancerRef();
            if (lb == null && ref != null) {
                lb = builtInLoadBalancer(
                        conf,
                        ref)
                    .orElseGet(() -> CamelContextHelper.mandatoryLookup(
                        routeContext.getCamelContext(),
                        ref,
                        ServiceCallLoadBalancer.class)
                );
            }
        }

        return lb;
    }

    protected ServiceCallLoadBalancer configureLoadBalancer(C conf, RouteContext routeContext, ServiceCallConfigurationDefinition config)  throws Exception {
        ServiceCallLoadBalancer lb = config.getLoadBalancer();
        String ref = config.getLoadBalancerRef();
        if (lb == null && ref != null) {
            lb = builtInLoadBalancer(
                    conf,
                    ref)
                .orElseGet(() ->CamelContextHelper.mandatoryLookup(
                    routeContext.getCamelContext(),
                    ref,
                    ServiceCallLoadBalancer.class)
            );
        }
        return lb;
    }

    protected ServiceCallServerListStrategy configureServerListStrategy(C conf, RouteContext routeContext, ServiceCallDefinition sd)  throws Exception {
        ServiceCallServerListStrategy sl = null;
        String ref;
        if (sd != null) {
            sl = sd.getServerListStrategy();
            ref = sd.getServerListStrategyRef();
            if (sl == null && ref != null) {
                sl = builtInServerListStrategy(
                        conf,
                        ref)
                    .orElseGet(() -> CamelContextHelper.mandatoryLookup(
                        routeContext.getCamelContext(),
                        ref,
                        ServiceCallServerListStrategy.class)
                );
            }
        }

        return sl;
    }

    protected ServiceCallServerListStrategy configureServerListStrategy(C conf, RouteContext routeContext, ServiceCallConfigurationDefinition config)  throws Exception {
        ServiceCallServerListStrategy sl = config.getServerListStrategy();
        String ref = config.getServerListStrategyRef();
        if (sl == null && ref != null) {
            sl = builtInServerListStrategy(
                    conf,
                    ref)
                .orElseGet(() -> CamelContextHelper.mandatoryLookup(
                    routeContext.getCamelContext(),
                    ref,
                    ServiceCallServerListStrategy.class)
            );
        }

        return sl;
    }

    // special for ref is referring to built-in load balancers
    protected Optional<ServiceCallLoadBalancer> builtInLoadBalancer(C conf, String name)  throws Exception {
        ServiceCallLoadBalancer lb = null;
        if (ObjectHelper.equal(name, "random", true)) {
            lb = new RandomServiceCallLoadBalancer();
        } else if (ObjectHelper.equal(name, "roundrobin", true)) {
            lb = new RoundRobinServiceCallLoadBalancer();
        }

        return Optional.ofNullable(lb);
    }

    // special for ref is referring to built-in server list strategies
    protected Optional<ServiceCallServerListStrategy> builtInServerListStrategy(C conf, String name)  throws Exception {
        return Optional.empty();
    }

    protected DefaultServiceCallProcessor createProcessor(
            String name,
            String component,
            String uri,
            ExchangePattern mep,
            C conf,
            Map<String, String> properties) throws Exception {

        return new DefaultServiceCallProcessor(name, component, uri, mep);
    }

    // TODO: rename
    protected abstract C createConfiguration(RouteContext routeContext) throws Exception;


    protected ServiceCallLoadBalancer<S> createDefaultLoadBalancer(C conf) throws Exception {
        return new RoundRobinServiceCallLoadBalancer<>();
    }

    protected ServiceCallServerListStrategy<S> createDefaultServerListStrategy(C conf) throws Exception {
        return null;
    }
}
