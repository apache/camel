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

package org.apache.camel.spring.cloud.servicecall;

import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.remote.AbstractServiceCallProcessorFactory;
import org.apache.camel.impl.remote.DefaultServiceCallProcessor;
import org.apache.camel.model.remote.ServiceCallConfigurationDefinition;
import org.apache.camel.model.remote.ServiceCallDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.CamelContextHelper;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

public class CamelCloudServiceCallProcessorFactory extends AbstractServiceCallProcessorFactory {
    @Override
    protected Processor createProcessor(RouteContext routeContext, ServiceCallDefinition definition) throws Exception {
        String name = definition.getName();
        String uri = definition.getUri();
        ExchangePattern mep = definition.getPattern();
        CamelContext camelContext = routeContext.getCamelContext();

        ServiceCallConfigurationDefinition config = definition.getServiceCallConfiguration();
        ServiceCallConfigurationDefinition configRef = null;
        if (definition.getServiceCallConfigurationRef() != null) {
            // lookup in registry first
            configRef = CamelContextHelper.lookup(camelContext, definition.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
            if (configRef == null) {
                // and fallback as service configuration
                camelContext.getServiceCallConfiguration(definition.getServiceCallConfigurationRef(), ServiceCallConfigurationDefinition.class);
            }
        }

        // if no configuration explicit configured then use default
        if (config == null && configRef == null) {
            config = camelContext.getServiceCallConfiguration(null, ServiceCallConfigurationDefinition.class);
        }
        if (config == null) {
            // if no default then try to find if there configuration in the registry of the given type
            Set<ServiceCallConfigurationDefinition> set = camelContext.getRegistry().findByType(ServiceCallConfigurationDefinition.class);
            if (set.size() == 1) {
                config = set.iterator().next();
            }
        }


        //if (config == null && configRef == null) {
        //    throw new IllegalStateException("The ServiceCall: " + definition + " must be configured before it can be used.");
        //}

        String component = definition.getComponent();
        if (component == null) {
            component = config != null ? config.getComponent() : null;
            if (component == null && configRef != null) {
                component = configRef.getComponent();
            }
        }

        // lookup the load balancer to use (configured on EIP takes precedence vs configured on configuration)
        Object lb = retrieveLoadBalancer(camelContext, definition, config, configRef);
        if (lb == null) {
            throw new IllegalArgumentException("Load balancer must be provided");
        }

        if (lb instanceof LoadBalancerClient) {
            return new CamelCloudServiceCallProcessor(name, uri, component, mep, (LoadBalancerClient) lb);
        } else if (lb instanceof ServiceCallLoadBalancer) {

            ServiceCallServerListStrategy<ServiceCallServer> sl = retrieveServerListStrategy(camelContext, definition, config, configRef);
            if (lb == null) {
                throw new IllegalArgumentException("Server list strategy must be provided");
            }

            DefaultServiceCallProcessor<ServiceCallServer> processor = new DefaultServiceCallProcessor<>(name, component, uri, mep);
            processor.setLoadBalancer((ServiceCallLoadBalancer<ServiceCallServer>)lb);
            processor.setServerListStrategy(sl);

            return processor;
        } else {
            throw new IllegalStateException(
                "Unable to configure ServiceCall: LoadBalancer should be an instance of LoadBalancerClient or ServiceCallLoadBalancer, got " + lb.getClass().getName()
            );
        }
    }

    // *************************************************************************
    // Load Balancer
    // *************************************************************************

    private Object retrieveLoadBalancer(
        CamelContext camelContext, ServiceCallDefinition definition, ServiceCallConfigurationDefinition config, ServiceCallConfigurationDefinition configRef) {

        // lookup the load balancer to use (configured on EIP takes precedence vs configured on configuration)
        Object lb = retrieveLoadBalancer(camelContext, definition::getLoadBalancer, definition::getLoadBalancerRef);
        if (lb == null && config != null) {
            lb = retrieveLoadBalancer(camelContext, config::getLoadBalancer, config::getLoadBalancerRef);
        }
        if (lb == null && configRef != null) {
            lb = retrieveLoadBalancer(camelContext, configRef::getLoadBalancer, configRef::getLoadBalancerRef);
        }

        if (lb == null) {
            Set<LoadBalancerClient> set = camelContext.getRegistry().findByType(LoadBalancerClient.class);
            if (set.size() == 1) {
                lb = set.iterator().next();
            }
        }

        return lb;
    }

    private Object retrieveLoadBalancer(
        CamelContext camelContext, Supplier<Object> loadBalancerSupplier, Supplier<String> loadBalancerRefSupplier) {

        Object lb = null;

        if (loadBalancerSupplier != null) {
            lb = loadBalancerSupplier.get();
        }

        if (lb == null && loadBalancerRefSupplier != null) {
            String ref = loadBalancerRefSupplier.get();
            if (ref != null) {
                lb = CamelContextHelper.lookup(camelContext, ref, LoadBalancerClient.class);
            }
            if (ref != null && lb == null) {
                lb = CamelContextHelper.lookup(camelContext, ref, ServiceCallLoadBalancer.class);
            }
        }

        return lb;
    }

    // *************************************************************************
    // Server List
    // *************************************************************************


    private ServiceCallServerListStrategy retrieveServerListStrategy(
        CamelContext camelContext, ServiceCallDefinition definition, ServiceCallConfigurationDefinition config, ServiceCallConfigurationDefinition configRef) {

        // lookup the server list strategy to use (configured on EIP takes precedence vs configured on configuration)
        ServiceCallServerListStrategy sl = retrieveServerListStrategy(camelContext, definition::getServerListStrategy, definition::getServerListStrategyRef);
        if (sl == null && config != null) {
            sl = retrieveServerListStrategy(camelContext, config::getServerListStrategy, config::getServerListStrategyRef);
        }
        if (sl == null && configRef != null) {
            sl = retrieveServerListStrategy(camelContext, configRef::getServerListStrategy, configRef::getServerListStrategyRef);
        }

        if (sl == null) {
            Set<ServiceCallServerListStrategy> set = camelContext.getRegistry().findByType(ServiceCallServerListStrategy.class);
            if (set.size() == 1) {
                sl = set.iterator().next();
            }
        }

        return sl;
    }

    private ServiceCallServerListStrategy retrieveServerListStrategy(
        CamelContext camelContext, Supplier<ServiceCallServerListStrategy> serverListSupplier, Supplier<String> serverListSupplierRef) {

        ServiceCallServerListStrategy sl = null;

        if (serverListSupplier != null) {
            sl = serverListSupplier.get();
        }

        if (sl == null && serverListSupplierRef != null) {
            String ref = serverListSupplierRef.get();
            if (ref != null) {
                sl = CamelContextHelper.lookup(camelContext, ref, ServiceCallServerListStrategy.class);
            }
        }

        return sl;
    }
}
