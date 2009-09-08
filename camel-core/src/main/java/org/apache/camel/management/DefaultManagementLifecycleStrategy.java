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
import java.util.List;
import java.util.Map;
import javax.management.JMException;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.management.mbean.ManagedBrowsableEndpoint;
import org.apache.camel.management.mbean.ManagedCamelContext;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedDelayer;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedErrorHandler;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedProducer;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedScheduledPollConsumer;
import org.apache.camel.management.mbean.ManagedSendProcessor;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedThrottler;
import org.apache.camel.management.mbean.ManagedTracer;
import org.apache.camel.model.AOPDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.Delayer;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementAware;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default JMX managed lifecycle strategy that registered objects using the configured
 * {@link org.apache.camel.spi.ManagementStrategy}.
 *
 * @see org.apache.camel.spi.ManagementStrategy
 * @version $Revision$
 */
public class DefaultManagementLifecycleStrategy implements LifecycleStrategy, Service {

    private static final Log LOG = LogFactory.getLog(DefaultManagementLifecycleStrategy.class);
    private final Map<Processor, KeyValueHolder<ProcessorDefinition, InstrumentationProcessor>> wrappedProcessors =
            new HashMap<Processor, KeyValueHolder<ProcessorDefinition, InstrumentationProcessor>>();
    private final CamelContext context;
    private boolean initialized;

    public DefaultManagementLifecycleStrategy(CamelContext context) {
        this.context = context;
    }

    public void onContextStart(CamelContext context) {
        try {
            initialized = true;

            // call addService so that context will handle lifecycle on the strategy
            context.addService(getStrategy());

            ManagedCamelContext mc = new ManagedCamelContext(context);
            getStrategy().manageObject(mc);

        } catch (Exception e) {
            // must rethrow to allow CamelContext fallback to non JMX agent to allow
            // Camel to continue to run
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void onContextStop(CamelContext context) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }
        try {
            ManagedCamelContext mc = new ManagedCamelContext(context);
            // the context could have been removed already
            if (getStrategy().isManaged(null, mc)) {
                getStrategy().unmanageObject(mc);
            }
        } catch (Exception e) {
            LOG.warn("Could not unregister CamelContext MBean", e);
        }
    }

    public void onComponentAdd(String name, Component component) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }
        try {
            Object mc = getManagedObjectForComponent(name, component);
            getStrategy().manageObject(mc);
        } catch (Exception e) {
            LOG.warn("Could not register Component MBean", e);
        }
    }

    public void onComponentRemove(String name, Component component) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }
        try {
            Object mc = getManagedObjectForComponent(name, component);
            getStrategy().unmanageObject(mc);
        } catch (Exception e) {
            LOG.warn("Could not unregister Component MBean", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getManagedObjectForComponent(String name, Component component) {
        if (component instanceof ManagementAware) {
            return ((ManagementAware) component).getManagedObject(component);
        } else {
            return new ManagedComponent(name, component);
        }
    }

    /**
     * If the endpoint is an instance of ManagedResource then register it with the
     * mbean server, if it is not then wrap the endpoint in a {@link ManagedEndpoint} and
     * register that with the mbean server.
     *
     * @param endpoint the Endpoint attempted to be added
     */
    @SuppressWarnings("unchecked")
    public void onEndpointAdd(Endpoint endpoint) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        try {
            Object me = getManagedObjectForEndpoint(endpoint);
            getStrategy().manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register Endpoint MBean for uri: " + endpoint.getEndpointUri(), e);
        }
    }

    public void onEndpointRemove(Endpoint endpoint) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        try {
            Object me = getManagedObjectForEndpoint(endpoint);
            getStrategy().unmanageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not unregister Endpoint MBean for uri: " + endpoint.getEndpointUri(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getManagedObjectForEndpoint(Endpoint endpoint) {
        if (endpoint instanceof ManagementAware) {
            return ((ManagementAware) endpoint).getManagedObject(endpoint);
        } else if (endpoint instanceof BrowsableEndpoint) {
            return new ManagedBrowsableEndpoint((BrowsableEndpoint) endpoint);
        } else {
            return new ManagedEndpoint(endpoint);
        }
    }

    public void onServiceAdd(CamelContext context, Service service, Route route) {
        // services can by any kind of misc type but also processors
        // so we have special logic when its a processor

        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        Object managedObject = getManagedObjectForService(context, service, route);
        if (managedObject == null) {
            // service should not be managed
            return;
        }

        // skip already managed services, for example if a route has been restarted
        if (getStrategy().isManaged(managedObject, null)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("The service is already managed: " + service);
            }
            return;
        }

        try {
            getStrategy().manageObject(managedObject);
        } catch (Exception e) {
            LOG.warn("Could not register service: " + service + " as Service MBean.", e);
        }
    }

    public void onServiceRemove(CamelContext context, Service service, Route route) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        Object managedObject = getManagedObjectForService(context, service, route);
        if (managedObject != null) {
            try {
                getStrategy().unmanageObject(managedObject);
            } catch (Exception e) {
                LOG.warn("Could not unregister service: " + service + " as Service MBean.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object getManagedObjectForService(CamelContext context, Service service, Route route) {
        ManagedService answer = null;

        if (service instanceof ManagementAware) {
            return ((ManagementAware) service).getManagedObject(service);
        } else if (service instanceof Tracer) {
            // special for tracer
            return new ManagedTracer(context, (Tracer) service);
        } else if (service instanceof Producer) {
            answer = new ManagedProducer(context, (Producer) service);
        } else if (service instanceof ScheduledPollConsumer) {
            answer = new ManagedScheduledPollConsumer(context, (ScheduledPollConsumer) service);
        } else if (service instanceof Consumer) {
            answer = new ManagedConsumer(context, (Consumer) service);
        } else if (service instanceof Processor) {
            // special for processors
            return getManagedObjectForProcessor(context, (Processor) service, route);
        }

        if (answer != null) {
            answer.setRoute(route);
            return answer;
        } else {
            // not supported
            return null;
        }
    }

    private Object getManagedObjectForProcessor(CamelContext context, Processor processor, Route route) {
        // a bit of magic here as the processors we want to manage have already been registered
        // in the wrapped processors map when Camel have instrumented the route on route initialization
        // so the idea is now to only manage the processors from the map
        KeyValueHolder<ProcessorDefinition, InstrumentationProcessor> holder = wrappedProcessors.get(processor);
        if (holder == null) {
            // skip as its not an well known processor we want to manage anyway, such as Channel/UnitOfWork/Pipeline etc.
            return null;
        }

        // get the managed object as it can be a specialized type such as a Delayer/Throttler etc.
        Object managedObject = createManagedObjectForProcessor(context, processor, holder.getKey(), route);
        // only manage if we have a name for it as otherwise we do not want to manage it anyway
        if (managedObject != null) {
            // is it a performance counter then we need to set our counter
            if (managedObject instanceof ManagedPerformanceCounter) {
                InstrumentationProcessor counter = holder.getValue();
                if (counter != null) {
                    // change counter to us
                    counter.setCounter((ManagedPerformanceCounter) managedObject);
                }
            }
        }

        return managedObject;
    }

    private Object createManagedObjectForProcessor(CamelContext context, Processor processor,
                                                   ProcessorDefinition definition, Route route) {
        // skip error handlers
        if (processor instanceof ErrorHandler) {
            return false;
        }

        ManagedProcessor answer = null;
        if (processor instanceof Delayer) {
            answer = new ManagedDelayer(context, (Delayer) processor, definition);
        } else if (processor instanceof Throttler) {
            answer = new ManagedThrottler(context, (Throttler) processor, definition);
        } else if (processor instanceof SendProcessor) {
            answer = new ManagedSendProcessor(context, (SendProcessor) processor, definition);
        }

        if (answer == null) {
            // fallback to a generic processor
            answer = new ManagedProcessor(context, processor, definition);
        }

        answer.setRoute(route);
        return answer;
    }

    public void onRoutesAdd(Collection<Route> routes) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        for (Route route : routes) {
            ManagedRoute mr = new ManagedRoute(getStrategy(), context, route);

            // skip already managed routes, for example if the route has been restarted
            if (getStrategy().isManaged(mr, null)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("The route is already managed: " + route);
                }
                continue;
            }

            // get the wrapped instrumentation processor from this route
            // and set me as the counter
            if (route instanceof EventDrivenConsumerRoute) {
                EventDrivenConsumerRoute edcr = (EventDrivenConsumerRoute) route;
                Processor processor = edcr.getProcessor();
                if (processor instanceof InstrumentationProcessor) {
                    InstrumentationProcessor ip = (InstrumentationProcessor) processor;
                    ip.setCounter(mr);
                }
            }

            try {
                getStrategy().manageObject(mr);
            } catch (JMException e) {
                LOG.warn("Could not register Route MBean", e);
            } catch (Exception e) {
                LOG.warn("Could not create Route MBean", e);
            }
        }
    }

    public void onRoutesRemove(Collection<Route> routes) {
        // noop - keep the route in the mbean so its still there, it will still be unregistered
        // when camel itself is shutting down
    }

    public void onErrorHandlerAdd(RouteContext routeContext, Processor errorHandler, ErrorHandlerBuilder errorHandlerBuilder) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        Object managedObject = new ManagedErrorHandler(routeContext, errorHandler, errorHandlerBuilder);

        // skip already managed services, for example if a route has been restarted
        if (getStrategy().isManaged(managedObject, null)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("The error handler builder is already managed: " + errorHandlerBuilder);
            }
            return;
        }

        try {
            getStrategy().manageObject(managedObject);
        } catch (Exception e) {
            LOG.warn("Could not register error handler builder: " + errorHandlerBuilder + " as ErrorHandlerMBean.", e);
        }
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        // Create a map (ProcessorType -> PerformanceCounter)
        // to be passed to InstrumentationInterceptStrategy.
        Map<ProcessorDefinition, ManagedPerformanceCounter> registeredCounters =
                new HashMap<ProcessorDefinition, ManagedPerformanceCounter>();

        // Each processor in a route will have its own performance counter
        // The performance counter are MBeans that we register with MBeanServer.
        // These performance counter will be embedded
        // to InstrumentationProcessor and wrap the appropriate processor
        // by InstrumentationInterceptStrategy.
        RouteDefinition route = routeContext.getRoute();

        // register performance counters for all processors and its children
        for (ProcessorDefinition processor : route.getOutputs()) {
            registerPerformanceCounters(routeContext, processor, registeredCounters);
        }

        // set this managed intercept strategy that executes the JMX instrumentation for performance metrics
        // so our registered counters can be used for fine grained performance instrumentation
        routeContext.setManagedInterceptStrategy(new InstrumentationInterceptStrategy(registeredCounters, wrappedProcessors));
    }

    @SuppressWarnings("unchecked")
    private void registerPerformanceCounters(RouteContext routeContext, ProcessorDefinition processor,
                                             Map<ProcessorDefinition, ManagedPerformanceCounter> registeredCounters) {

        // traverse children if any exists
        List<ProcessorDefinition> children = processor.getOutputs();
        for (ProcessorDefinition child : children) {
            registerPerformanceCounters(routeContext, child, registeredCounters);
        }

        // skip processors that should not be registered
        if (!registerProcessor(processor)) {
            return;
        }

        // okay this is a processor we would like to manage so create the
        // performance counter that is the base for processors
        ManagedPerformanceCounter pc = new ManagedPerformanceCounter(getStrategy());
        // set statistics enabled depending on the option
        boolean enabled = context.getManagementStrategy().getStatisticsLevel() == ManagementStatisticsLevel.All;
        pc.setStatisticsEnabled(enabled);

        // and add it as a a registered counter that will be used lazy when Camel
        // does the instrumentation of the route and adds the InstrumentationProcessor
        // that does the actual performance metrics gatherings at runtime
        registeredCounters.put(processor, pc);
    }

    /**
     * Should the given processor be registered.
     */
    protected boolean registerProcessor(ProcessorDefinition processor) {
        // skip on exception
        if (processor instanceof OnExceptionDefinition) {
            return false;
        }
        // skip on completion
        if (processor instanceof OnCompletionDefinition) {
            return false;
        }
        // skip intercept
        if (processor instanceof InterceptDefinition) {
            return false;
        }
        // skip aop
        if (processor instanceof AOPDefinition) {
            return false;
        }
        // skip policy
        if (processor instanceof PolicyDefinition) {
            return false;
        }

        // only if custom id assigned
        if (getStrategy().isOnlyManageProcessorWithCustomId()) {
            return processor.hasCustomIdAssigned();
        }

        // use customer filter
        return getStrategy().manageProcessor(processor);
    }

    private ManagementStrategy getStrategy() {
        return context.getManagementStrategy();
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
        initialized = false;
    }
}

