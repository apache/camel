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
package org.apache.camel.component.ribbon.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.netflix.loadbalancer.IRule;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.impl.remote.DefaultServiceCallProcessorFactory;
import org.apache.camel.model.remote.RibbonConfigurationDefinition;
import org.apache.camel.model.remote.ServiceCallConfigurationDefinition;
import org.apache.camel.model.remote.ServiceCallDefinition;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * {@link ProcessorFactory} that creates the Ribbon implementation of the ServiceCall EIP.
 */
public class RibbonProcessorFactory extends DefaultServiceCallProcessorFactory<RibbonConfiguration, RibbonServer> {


    @Override
    protected Processor createProcessor(RouteContext routeContext, ServiceCallDefinition definition, RibbonConfiguration cfg) throws Exception {
        String name = definition.getName();
        String uri = definition.getUri();
        ExchangePattern mep = definition.getPattern();

        RibbonConfigurationDefinition config = (RibbonConfigurationDefinition) definition.getServiceCallConfiguration();
        RibbonConfigurationDefinition configRef = null;
        if (definition.getServiceCallConfigurationRef() != null) {
            // lookup in registry first
            configRef = CamelContextHelper.lookup(routeContext.getCamelContext(), definition.getServiceCallConfigurationRef(), RibbonConfigurationDefinition.class);
            if (configRef == null) {
                // and fallback as service configuration
                routeContext.getCamelContext().getServiceCallConfiguration(definition.getServiceCallConfigurationRef(), RibbonConfigurationDefinition.class);
            }
        }

        // if no configuration explicit configured then use default
        if (config == null && configRef == null) {
            config = routeContext.getCamelContext().getServiceCallConfiguration(null, RibbonConfigurationDefinition.class);
        }
        if (config == null) {
            // if no default then try to find if there configuration in the registry of the given type
            Set<RibbonConfigurationDefinition> set = routeContext.getCamelContext().getRegistry().findByType(RibbonConfigurationDefinition.class);
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

        // and set them on the kubernetes configuration class
        IntrospectionSupport.setProperties(cfg, parameters);

        // lookup the load balancer to use (configured on EIP takes precedence vs configured on configuration)
        Object lb = configureLoadBalancer(routeContext, definition);
        if (lb == null && config != null) {
            lb = configureLoadBalancer(routeContext, config);
        }
        if (lb == null && configRef != null) {
            lb = configureLoadBalancer(routeContext, configRef);
        }

        // lookup the server list strategy to use (configured on EIP takes precedence vs configured on configuration)
        ServiceCallServerListStrategy sl = configureServerListStrategy(routeContext, definition);
        if (sl == null && config != null) {
            sl = configureServerListStrategy(routeContext, config);
        }
        if (sl == null && configRef != null) {
            sl = configureServerListStrategy(routeContext, configRef);
        }

        // must be a ribbon load balancer
        if (lb != null && !(lb instanceof IRule)) {
            throw new IllegalArgumentException("Load balancer must be of type: " + IRule.class + " but is of type: " + lb.getClass().getName());
        }

        // the component is used to configure what the default scheme to use (eg camel component name)
        String component = config != null ? config.getComponent() : null;
        if (component == null && configRef != null) {
            component = configRef.getComponent();
        }

        Map<String, String> properties = configureProperties(routeContext, config, configRef);

        RibbonServiceCallProcessor processor = new RibbonServiceCallProcessor(name, uri, component, mep, cfg);
        processor.setRule((IRule) lb);
        processor.setServerListStrategy(sl);
        processor.setRibbonClientConfig(properties);
        return processor;
    }

    @Override
    protected RibbonConfiguration createConfiguration(RouteContext routeContext) throws Exception {
        return new RibbonConfiguration();
    }

    private Object configureLoadBalancer(RouteContext routeContext, ServiceCallDefinition sd) {
        Object lb = null;

        if (sd != null) {
            lb = sd.getLoadBalancer();
            if (lb == null && sd.getLoadBalancerRef() != null) {
                String ref = sd.getLoadBalancerRef();
                lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref);
            }
        }

        return lb;
    }

    private Object configureLoadBalancer(RouteContext routeContext, ServiceCallConfigurationDefinition config) {
        Object lb = config.getLoadBalancer();
        if (lb == null && config.getLoadBalancerRef() != null) {
            String ref = config.getLoadBalancerRef();
            lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref);
        }
        return lb;
    }

    private ServiceCallServerListStrategy configureServerListStrategy(RouteContext routeContext, ServiceCallDefinition sd) {
        ServiceCallServerListStrategy lb = null;

        if (sd != null) {
            lb = sd.getServerListStrategy();
            if (lb == null && sd.getServerListStrategyRef() != null) {
                lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), sd.getServerListStrategyRef(), ServiceCallServerListStrategy.class);
            }
        }

        return lb;
    }

    private ServiceCallServerListStrategy configureServerListStrategy(RouteContext routeContext, ServiceCallConfigurationDefinition config) {
        ServiceCallServerListStrategy lb = config.getServerListStrategy();
        if (lb == null && config.getServerListStrategyRef() != null) {
            String ref = config.getServerListStrategyRef();
            lb = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref, ServiceCallServerListStrategy.class);
        }
        return lb;
    }

}

