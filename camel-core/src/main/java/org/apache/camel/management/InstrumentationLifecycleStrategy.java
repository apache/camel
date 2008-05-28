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
package org.apache.camel.management;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.interceptor.Debugger;
import org.apache.camel.spi.InstrumentationAgent;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JMX agent that registeres Camel lifecycle events in JMX.
 */
public class InstrumentationLifecycleStrategy implements LifecycleStrategy {
    private static final transient Log LOG = LogFactory.getLog(InstrumentationProcessor.class);

    private InstrumentationAgent agent;
    private CamelNamingStrategy namingStrategy;

    // A map (Endpoint -> InstrumentationProcessor) to facilitate
    // adding per-route interceptor and registering ManagedRoute MBean
    private Map<Endpoint, InstrumentationProcessor> interceptorMap =
        new HashMap<Endpoint, InstrumentationProcessor>();

    public InstrumentationLifecycleStrategy(InstrumentationAgent agent,
            CamelNamingStrategy namingStrategy) {
        this.agent = agent;
        this.namingStrategy = namingStrategy;
    }

    public void onContextCreate(CamelContext context) {
        if (context instanceof DefaultCamelContext) {
            try {
                DefaultCamelContext dc = (DefaultCamelContext)context;
                ManagedService ms = new ManagedService(dc);
                agent.register(ms, getNamingStrategy().getObjectName(dc));
            } catch (JMException e) {
                LOG.warn("Could not register CamelContext MBean", e);
            }
        }
    }

    public void onEndpointAdd(Endpoint<? extends Exchange> endpoint) {
        try {
            ManagedEndpoint me = new ManagedEndpoint(endpoint);
            agent.register(me, getNamingStrategy().getObjectName(me));
        } catch (JMException e) {
            LOG.warn("Could not register Endpoint MBean", e);
        }
    }

    public void onRoutesAdd(Collection<Route> routes) {
        for (Route route : routes) {
            try {
                ManagedRoute mr = new ManagedRoute(route);
                // retrieve the per-route intercept for this route
                InstrumentationProcessor interceptor = interceptorMap.get(route.getEndpoint());
                if (interceptor == null) {
                    LOG.warn("Instrumentation processor not found for route endpoint "
                             + route.getEndpoint());
                } else {
                    interceptor.setCounter(mr);
                }
                agent.register(mr, getNamingStrategy().getObjectName(mr));
            } catch (JMException e) {
                LOG.warn("Could not register Route MBean", e);
            }
        }
    }

    public void onServiceAdd(CamelContext context, Service service) {
        if (service instanceof ServiceSupport) {
            try {
                ManagedService ms = new ManagedService((ServiceSupport)service);
                agent.register(ms, getNamingStrategy().getObjectName(context, ms));
            } catch (JMException e) {
                LOG.warn("Could not register Service MBean", e);
            }
        }
    }

    public void onRouteContextCreate(RouteContext routeContext) {

        // Create a map (ProcessorType -> PerformanceCounter)
        // to be passed to InstrumentationInterceptStrategy.
        Map<ProcessorType, PerformanceCounter> counterMap =
            new HashMap<ProcessorType, PerformanceCounter>();

        // Each processor in a route will have its own performance counter
        // The performance counter are MBeans that we register with MBeanServer.
        // These performance counter will be embedded
        // to InstrumentationProcessor and wrap the appropriate processor
        // by InstrumentationInterceptStrategy.
        RouteType route = routeContext.getRoute();
        for (ProcessorType processor : route.getOutputs()) {
            PerformanceCounter pc = new PerformanceCounter();
            try {
                agent.register(pc, getNamingStrategy().getObjectName(
                        routeContext, processor));
            } catch (JMException e) {
                LOG.warn("Could not register Counter MBean", e);
            }
            counterMap.put(processor, pc);
        }

        routeContext.addInterceptStrategy(new InstrumentationInterceptStrategy(counterMap));


        // Add an InstrumentationProcessor at the beginning of each route and
        // set up the interceptorMap for onRoutesAdd() method to register the
        // ManagedRoute MBeans.

        RouteType routeType = routeContext.getRoute();
        if (routeType.getInputs() != null && !routeType.getInputs().isEmpty()) {
            if (routeType.getInputs().size() > 1) {
                LOG.warn("Add InstrumentationProcessor to first input only.");
            }

            Endpoint endpoint  = routeType.getInputs().get(0).getEndpoint();
            ProcessorType<?>[] outputs =
                routeType.getOutputs().toArray(new ProcessorType<?>[0]);

            routeType.clearOutput();
            InstrumentationProcessor processor = new InstrumentationProcessor();
            routeType.intercept(processor);
            for (ProcessorType<?> output : outputs) {
                routeType.addOutput(output);
            }

            interceptorMap.put(endpoint, processor);
        }

    }

    public CamelNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(CamelNamingStrategy strategy) {
        this.namingStrategy = strategy;
    }
}
