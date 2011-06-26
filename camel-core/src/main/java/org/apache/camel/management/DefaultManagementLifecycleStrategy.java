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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Channel;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.ConsumerCache;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.EndpointRegistry;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ThrottlingInflightRoutePolicy;
import org.apache.camel.management.mbean.ManagedConsumerCache;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedEndpointRegistry;
import org.apache.camel.management.mbean.ManagedProducerCache;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedThrottlingInflightRoutePolicy;
import org.apache.camel.management.mbean.ManagedTracer;
import org.apache.camel.model.AOPDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementAware;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default JMX managed lifecycle strategy that registered objects using the configured
 * {@link org.apache.camel.spi.ManagementStrategy}.
 *
 * @see org.apache.camel.spi.ManagementStrategy
 * @version 
 */
@SuppressWarnings("deprecation")
public class DefaultManagementLifecycleStrategy implements LifecycleStrategy, Service, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementLifecycleStrategy.class);
    private final Map<Processor, KeyValueHolder<ProcessorDefinition, InstrumentationProcessor>> wrappedProcessors =
            new HashMap<Processor, KeyValueHolder<ProcessorDefinition, InstrumentationProcessor>>();
    private CamelContext camelContext;
    private volatile boolean initialized;
    private final Set<String> knowRouteIds = new HashSet<String>();

    public DefaultManagementLifecycleStrategy() {
    }

    public DefaultManagementLifecycleStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
        Object mc = getManagementObjectStrategy().getManagedObjectForCamelContext(context);

        String managementName = context.getManagementName() != null ? context.getManagementName() : context.getName();

        try {
            boolean done = false;
            while (!done) {
                ObjectName on = getManagementStrategy().getManagementNamingStrategy().getObjectNameForCamelContext(context);
                boolean exists = getManagementStrategy().isManaged(mc, on);
                if (!exists) {
                    done = true;
                } else {
                    // okay there exists already a CamelContext with this name, we can try to fix it by finding a free name
                    boolean fixed = false;
                    // if we use the default name strategy we can find a free name to use
                    String name = findFreeName(mc, context.getNameStrategy(), managementName);
                    if (name != null) {
                        // use this as the fixed name
                        fixed = true;
                        done = true;
                        managementName = name;
                    }
                    // we could not fix it so veto starting camel
                    if (!fixed) {
                        throw new VetoCamelContextStartException("CamelContext (" + context.getName() + ") with ObjectName[" + on + "] is already registered."
                            + " Make sure to use unique names on CamelContext when using multiple CamelContexts in the same MBeanServer.", context);
                    } else {
                        if (context.getNameStrategy() instanceof DefaultCamelContextNameStrategy) {
                            // use this as the fixed name
                            LOG.warn("Reassigned auto assigned name on CamelContext from: " + context.getName()
                                    + " to: " + name + " due to clash with existing name already registered in MBeanServer.");
                            // now set the fixed name we are using onwards
                            context.setNameStrategy(new ExplicitCamelContextNameStrategy(name));
                        } else {
                            LOG.warn("This CamelContext(" + context.getName() + ") will be registered using the name: " + managementName
                                + " due to clash with an existing name already registered in MBeanServer.");
                        }
                    }
                }
            }
        } catch (VetoCamelContextStartException e) {
            // rethrow veto
            throw e;
        } catch (Exception e) {
            // must rethrow to allow CamelContext fallback to non JMX agent to allow
            // Camel to continue to run
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // set the name we are going to use
        context.setManagementName(managementName);

        try {
            getManagementStrategy().manageObject(mc);
        } catch (Exception e) {
            // must rethrow to allow CamelContext fallback to non JMX agent to allow
            // Camel to continue to run
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // yes we made it and are initialized
        initialized = true;
    }

    private String findFreeName(Object mc, CamelContextNameStrategy strategy, String managementName) throws MalformedObjectNameException {
        boolean done = false;
        String name = null;
        // start from 2 as the existing name is considered the 1st
        int counter = 2;
        while (!done) {
            // compute the next name
            if (strategy instanceof DefaultCamelContextNameStrategy) {
                // prefer to use the default naming strategy to compute the next free name
                name = ((DefaultCamelContextNameStrategy) strategy).getNextName();
            } else {
                // if explict name then use a counter prefix
                name = managementName + "-" + counter++;
            }
            ObjectName on = getManagementStrategy().getManagementNamingStrategy().getObjectNameForCamelContext(name);
            done = !getManagementStrategy().isManaged(mc, on);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using name: {} in ObjectName[{}] exists? {}", new Object[]{name, on, done});
            }
        }
        return name;
    }

    public void onContextStop(CamelContext context) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }
        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForCamelContext(context);
            // the context could have been removed already
            if (getManagementStrategy().isManaged(mc, null)) {
                getManagementStrategy().unmanageObject(mc);
            }
        } catch (Exception e) {
            LOG.warn("Could not unregister CamelContext MBean", e);
        }
    }

    public void onComponentAdd(String name, Component component) {
        // always register components as there are only a few of those
        if (!initialized) {
            return;
        }
        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForComponent(camelContext, component, name);;
            getManagementStrategy().manageObject(mc);
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
            Object mc = getManagementObjectStrategy().getManagedObjectForComponent(camelContext, component, name);;
            getManagementStrategy().unmanageObject(mc);
        } catch (Exception e) {
            LOG.warn("Could not unregister Component MBean", e);
        }
    }

    /**
     * If the endpoint is an instance of ManagedResource then register it with the
     * mbean server, if it is not then wrap the endpoint in a {@link ManagedEndpoint} and
     * register that with the mbean server.
     *
     * @param endpoint the Endpoint attempted to be added
     */
    public void onEndpointAdd(Endpoint endpoint) {
        if (!shouldRegister(endpoint, null)) {
            // avoid registering if not needed
            return;
        }

        try {
            Object me = getManagementObjectStrategy().getManagedObjectForEndpoint(camelContext, endpoint);
            if (me == null) {
                // endpoint should not be managed
                return;
            }
            getManagementStrategy().manageObject(me);
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
            Object me = getManagementObjectStrategy().getManagedObjectForEndpoint(camelContext, endpoint);
            getManagementStrategy().unmanageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not unregister Endpoint MBean for uri: " + endpoint.getEndpointUri(), e);
        }
    }

    public void onServiceAdd(CamelContext context, Service service, Route route) {
        // services can by any kind of misc type but also processors
        // so we have special logic when its a processor

        if (!shouldRegister(service, route)) {
            // avoid registering if not needed
            return;
        }

        Object managedObject = getManagedObjectForService(context, service, route);
        if (managedObject == null) {
            // service should not be managed
            return;
        }

        // skip already managed services, for example if a route has been restarted
        if (getManagementStrategy().isManaged(managedObject, null)) {
            LOG.trace("The service is already managed: {}", service);
            return;
        }

        try {
            getManagementStrategy().manageObject(managedObject);
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
                getManagementStrategy().unmanageObject(managedObject);
            } catch (Exception e) {
                LOG.warn("Could not unregister service: " + service + " as Service MBean.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object getManagedObjectForService(CamelContext context, Service service, Route route) {
        // skip channel, UoW and dont double wrap instrumentation
        if (service instanceof Channel || service instanceof UnitOfWork || service instanceof InstrumentationProcessor) {
            return null;
        }

        Object answer = null;

        if (service instanceof ManagementAware) {
            return ((ManagementAware) service).getManagedObject(service);
        } else if (service instanceof Tracer) {
            // special for tracer
            ManagedTracer mt = new ManagedTracer(context, (Tracer) service);
            mt.init(getManagementStrategy());
            return mt;
        } else if (service instanceof EventNotifier) {
            answer = getManagementObjectStrategy().getManagedObjectForEventNotifier(context, (EventNotifier) service);
        } else if (service instanceof Producer) {
            answer = getManagementObjectStrategy().getManagedObjectForProducer(context, (Producer) service);
        } else if (service instanceof Consumer) {
            answer = getManagementObjectStrategy().getManagedObjectForConsumer(context, (Consumer) service);
        } else if (service instanceof Processor) {
            // special for processors as we need to do some extra work
            return getManagedObjectForProcessor(context, (Processor) service, route);
        } else if (service instanceof ThrottlingInflightRoutePolicy) {
            answer = new ManagedThrottlingInflightRoutePolicy(context, (ThrottlingInflightRoutePolicy) service);
        } else if (service instanceof ConsumerCache) {
            answer = new ManagedConsumerCache(context, (ConsumerCache) service);
        } else if (service instanceof ProducerCache) {
            answer = new ManagedProducerCache(context, (ProducerCache) service);
        } else if (service instanceof EndpointRegistry) {
            answer = new ManagedEndpointRegistry(context, (EndpointRegistry) service);
        } else if (service != null) {
            // fallback as generic service
            answer = getManagementObjectStrategy().getManagedObjectForService(context, service);
        }

        if (answer != null && answer instanceof ManagedService) {
            ManagedService ms = (ManagedService) answer;
            ms.setRoute(route);
            ms.init(getManagementStrategy());
            return answer;
        } else {
            return answer;
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
        Object managedObject = getManagementObjectStrategy().getManagedObjectForProcessor(context, processor, holder.getKey(), route);
        // only manage if we have a name for it as otherwise we do not want to manage it anyway
        if (managedObject != null) {
            // is it a performance counter then we need to set our counter
            if (managedObject instanceof PerformanceCounter) {
                InstrumentationProcessor counter = holder.getValue();
                if (counter != null) {
                    // change counter to us
                    counter.setCounter(managedObject);
                }
            }
        }

        return managedObject;
    }

    public void onRoutesAdd(Collection<Route> routes) {
        for (Route route : routes) {

            // if we are starting CamelContext or either of the two options has been
            // enabled, then enlist the route as a known route
            if (getCamelContext().getStatus().isStarting()
                || getManagementStrategy().getManagementAgent().getRegisterAlways()
                || getManagementStrategy().getManagementAgent().getRegisterNewRoutes()) {
                // register as known route id
                knowRouteIds.add(route.getId());
            }

            if (!shouldRegister(route, route)) {
                // avoid registering if not needed, skip to next route
                continue;
            }

            Object mr = getManagementObjectStrategy().getManagedObjectForRoute(camelContext, route);

            // skip already managed routes, for example if the route has been restarted
            if (getManagementStrategy().isManaged(mr, null)) {
                LOG.trace("The route is already managed: {}", route);
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
                getManagementStrategy().manageObject(mr);
            } catch (JMException e) {
                LOG.warn("Could not register Route MBean", e);
            } catch (Exception e) {
                LOG.warn("Could not create Route MBean", e);
            }
        }
    }

    public void onRoutesRemove(Collection<Route> routes) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        for (Route route : routes) {
            Object mr = getManagementObjectStrategy().getManagedObjectForRoute(camelContext, route);

            // skip unmanaged routes
            if (!getManagementStrategy().isManaged(mr, null)) {
                LOG.trace("The route is not managed: {}", route);
                continue;
            }

            try {
                getManagementStrategy().unmanageObject(mr);
            } catch (Exception e) {
                LOG.warn("Could not unregister Route MBean", e);
            }
        }
    }

    public void onErrorHandlerAdd(RouteContext routeContext, Processor errorHandler, ErrorHandlerBuilder errorHandlerBuilder) {
        if (!shouldRegister(errorHandler, null)) {
            // avoid registering if not needed
            return;
        }

        Object me = getManagementObjectStrategy().getManagedObjectForErrorHandler(camelContext, routeContext, errorHandler, errorHandlerBuilder);

        // skip already managed services, for example if a route has been restarted
        if (getManagementStrategy().isManaged(me, null)) {
            LOG.trace("The error handler builder is already managed: {}", errorHandlerBuilder);
            return;
        }

        try {
            getManagementStrategy().manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register error handler builder: " + errorHandlerBuilder + " as ErrorHandler MBean.", e);
        }
    }

    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                                String sourceId, String routeId, String threadPoolProfileId) {

        // always register thread pools as there are only a few of those
        if (!initialized) {
            return;
        }

        Object mtp = getManagementObjectStrategy().getManagedObjectForThreadPool(camelContext, threadPool, id, sourceId, routeId, threadPoolProfileId);

        // skip already managed services, for example if a route has been restarted
        if (getManagementStrategy().isManaged(mtp, null)) {
            LOG.trace("The thread pool is already managed: {}", threadPool);
            return;
        }

        try {
            getManagementStrategy().manageObject(mtp);
        } catch (Exception e) {
            LOG.warn("Could not register thread pool: " + threadPool + " as ThreadPool MBean.", e);
        }
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        if (!initialized) {
            return;
        }

        // Create a map (ProcessorType -> PerformanceCounter)
        // to be passed to InstrumentationInterceptStrategy.
        Map<ProcessorDefinition, PerformanceCounter> registeredCounters =
                new HashMap<ProcessorDefinition, PerformanceCounter>();

        // Each processor in a route will have its own performance counter.
        // These performance counter will be embedded to InstrumentationProcessor
        // and wrap the appropriate processor by InstrumentationInterceptStrategy.
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
                                             Map<ProcessorDefinition, PerformanceCounter> registeredCounters) {

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
        // a delegate performance counter that acts as the placeholder in the interceptor
        // that then delegates to the real mbean which we register later in the onServiceAdd method
        DelegatePerformanceCounter pc = new DelegatePerformanceCounter();
        // set statistics enabled depending on the option
        boolean enabled = camelContext.getManagementStrategy().getStatisticsLevel() == ManagementStatisticsLevel.All;
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
        if (getManagementStrategy().isOnlyManageProcessorWithCustomId()) {
            return processor.hasCustomIdAssigned();
        }

        // use customer filter
        return getManagementStrategy().manageProcessor(processor);
    }

    private ManagementStrategy getManagementStrategy() {
        ObjectHelper.notNull(camelContext, "CamelContext");
        return camelContext.getManagementStrategy();
    }

    private ManagementObjectStrategy getManagementObjectStrategy() {
        ObjectHelper.notNull(camelContext, "CamelContext");
        return camelContext.getManagementStrategy().getManagementObjectStrategy();
    }

    public void start() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
    }

    public void stop() throws Exception {
        initialized = false;
        knowRouteIds.clear();
    }

    /**
     * Whether or not to register the mbean.
     * <p/>
     * The {@link ManagementAgent} has options which controls when to register.
     * This allows us to only register mbeans accordingly. For example by default any
     * dynamic endpoints is not registered. This avoids to register excessive mbeans, which
     * most often is not desired.
     *
     *
     * @param service the object to register
     * @param route   an optional route the mbean is associated with, can be <tt>null</tt>
     * @return <tt>true</tt> to register, <tt>false</tt> to skip registering
     */
    protected boolean shouldRegister(Object service, Route route) {
        // the agent hasn't been started
        if (!initialized) {
            return false;
        }

        LOG.trace("Checking whether to register {} from route: {}", service, route);

        // always register if we are starting CamelContext
        if (getCamelContext().getStatus().isStarting()) {
            return true;
        }

        // register if always is enabled
        ManagementAgent agent = getManagementStrategy().getManagementAgent();
        if (agent.getRegisterAlways()) {
            return true;
        }

        // is it a known route then always accept
        if (route != null && knowRouteIds.contains(route.getId())) {
            return true;
        }

        // only register if we are starting a new route, and current thread is in starting routes mode
        if (agent.getRegisterNewRoutes()) {
            // no specific route, then fallback to see if this thread is starting routes
            // which is kept as state on the camel context
            return getCamelContext().isStartingRoutes();
        }

        return false;
    }

}

