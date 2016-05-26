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
import java.util.Set;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.remote.ServiceCallConfigurationDefinition;
import org.apache.camel.model.remote.ServiceCallDefinition;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;

public class DefaultServiceCallProcessorFactory implements ProcessorFactory {

    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        // not in use
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception {
        if (definition instanceof ServiceCallDefinition) {
            ServiceCallDefinition sc = (ServiceCallDefinition) definition;

            String name = sc.getName();
            String uri = sc.getUri();
            ExchangePattern mep = sc.getPattern();

            ServiceCallConfigurationDefinition config = sc.getServiceCallConfiguration();
            ServiceCallConfigurationDefinition configRef = null;
            if (sc.getServiceCallConfigurationRef() != null) {
                // lookup in registry first
                configRef = CamelContextHelper.lookup(routeContext.getCamelContext(), sc.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
                if (configRef == null) {
                    // and fallback as service configuration
                    routeContext.getCamelContext().getServiceCallConfiguration(sc.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
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

            // extract the properties from the configuration from the model
            Map<String, Object> parameters = new HashMap<>();
            if (configRef != null) {
                IntrospectionSupport.getProperties(configRef, parameters, null);
            }
            if (config != null) {
                IntrospectionSupport.getProperties(config, parameters, null);
            }

            // lookup the load balancer to use (configured on EIP takes precedence vs configured on configuration)
            ServiceCallLoadBalancer lb = configureLoadBalancer(routeContext, sc);
            if (lb == null && config != null) {
                lb = configureLoadBalancer(routeContext, config);
            }
            if (lb == null && configRef != null) {
                lb = configureLoadBalancer(routeContext, configRef);
            }

            // lookup the server list strategy to use (configured on EIP takes precedence vs configured on configuration)
            ServiceCallServerListStrategy sl = configureServerListStrategy(routeContext, sc);
            if (sl == null && config != null) {
                sl = configureServerListStrategy(routeContext, config);
            }
            if (sl == null && configRef != null) {
                sl = configureServerListStrategy(routeContext, configRef);
            }

            // the component is used to configure what the default scheme to use (eg camel component name)
            String component = config != null ? config.getComponent() : null;
            if (component == null && configRef != null) {
                component = configRef.getComponent();
            }

            return createProcessor(name, component, uri, mep, parameters, lb, sl);

        } else {
            return null;
        }
    }

    protected Processor createProcessor(
            String name,
            String component,
            String uri,
            ExchangePattern mep,
            Map<String, Object> parameters,
            ServiceCallLoadBalancer lb, ServiceCallServerListStrategy sl) {

        return new DefaultServiceCallProcessor(name, component, uri, mep).
            loadBalancer(lb)
            .serverListStrategy(sl);
    }


    protected ServiceCallLoadBalancer configureLoadBalancer(RouteContext routeContext, ServiceCallDefinition sd) {
        ServiceCallLoadBalancer lb = null;

        if (sd != null) {
            lb = sd.getLoadBalancer();
            if (lb == null && sd.getLoadBalancerRef() != null) {
                String ref = sd.getLoadBalancerRef();
                // special for ref is referring to built-in
                if ("random".equalsIgnoreCase(ref)) {
                    lb = new RandomServiceCallLoadBalancer();
                } else if ("roundrobin".equalsIgnoreCase(ref)) {
                    lb = new RoundRobinServiceCallLoadBalancer();
                } else {
                    lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref, ServiceCallLoadBalancer.class);
                }
            }
        }

        return lb;
    }

    protected ServiceCallLoadBalancer configureLoadBalancer(RouteContext routeContext, ServiceCallConfigurationDefinition config) {
        ServiceCallLoadBalancer lb = config.getLoadBalancer();
        if (lb == null && config.getLoadBalancerRef() != null) {
            String ref = config.getLoadBalancerRef();
            // special for ref is referring to built-in
            if ("random".equalsIgnoreCase(ref)) {
                lb = new RandomServiceCallLoadBalancer();
            } else if ("roundrobin".equalsIgnoreCase(ref)) {
                lb = new RoundRobinServiceCallLoadBalancer();
            } else {
                lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref, ServiceCallLoadBalancer.class);
            }
        }
        return lb;
    }

    protected ServiceCallServerListStrategy configureServerListStrategy(RouteContext routeContext, ServiceCallDefinition sd) {
        ServiceCallServerListStrategy lb = null;

        if (sd != null) {
            lb = sd.getServerListStrategy();
            if (lb == null && sd.getServerListStrategyRef() != null) {
                lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), sd.getServerListStrategyRef(), ServiceCallServerListStrategy.class);
            }
        }

        return lb;
    }

    protected ServiceCallServerListStrategy configureServerListStrategy(RouteContext routeContext, ServiceCallConfigurationDefinition config) {
        ServiceCallServerListStrategy lb = config.getServerListStrategy();
        if (lb == null && config.getServerListStrategyRef() != null) {
            String ref = config.getServerListStrategyRef();
            lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref, ServiceCallServerListStrategy.class);
        }
        return lb;
    }
}
