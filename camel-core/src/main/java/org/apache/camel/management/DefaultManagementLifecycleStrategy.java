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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.NonManagedService;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.StartupListener;
import org.apache.camel.TimerListener;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.api.management.PerformanceCounter;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.ConsumerCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultEndpointRegistry;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ThrottlingExceptionRoutePolicy;
import org.apache.camel.impl.ThrottlingInflightRoutePolicy;
import org.apache.camel.management.mbean.ManagedAsyncProcessorAwaitManager;
import org.apache.camel.management.mbean.ManagedBacklogDebugger;
import org.apache.camel.management.mbean.ManagedBacklogTracer;
import org.apache.camel.management.mbean.ManagedCamelContext;
import org.apache.camel.management.mbean.ManagedConsumerCache;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedEndpointRegistry;
import org.apache.camel.management.mbean.ManagedInflightRepository;
import org.apache.camel.management.mbean.ManagedProducerCache;
import org.apache.camel.management.mbean.ManagedRestRegistry;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedRuntimeCamelCatalog;
import org.apache.camel.management.mbean.ManagedRuntimeEndpointRegistry;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedStreamCachingStrategy;
import org.apache.camel.management.mbean.ManagedThrottlingExceptionRoutePolicy;
import org.apache.camel.management.mbean.ManagedThrottlingInflightRoutePolicy;
import org.apache.camel.management.mbean.ManagedTracer;
import org.apache.camel.management.mbean.ManagedTransformerRegistry;
import org.apache.camel.management.mbean.ManagedTypeConverterRegistry;
import org.apache.camel.management.mbean.ManagedValidatorRegistry;
import org.apache.camel.model.AOPDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.interceptor.BacklogDebugger;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementAgent;
import org.apache.camel.spi.ManagementAware;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.support.TimerListenerManager;
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
public class DefaultManagementLifecycleStrategy extends ServiceSupport implements LifecycleStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementLifecycleStrategy.class);
    // the wrapped processors is for performance counters, which are in use for the created routes
    // when a route is removed, we should remove the associated processors from this map
    private final Map<Processor, KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor>> wrappedProcessors =
            new HashMap<Processor, KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor>>();
    private final List<PreRegisterService> preServices = new ArrayList<PreRegisterService>();
    private final TimerListenerManager loadTimer = new ManagedLoadTimer();
    private final TimerListenerManagerStartupListener loadTimerStartupListener = new TimerListenerManagerStartupListener();
    private volatile CamelContext camelContext;
    private volatile ManagedCamelContext camelContextMBean;
    private volatile boolean initialized;
    private final Set<String> knowRouteIds = new HashSet<String>();
    private final Map<Tracer, ManagedTracer> managedTracers = new HashMap<Tracer, ManagedTracer>();
    private final Map<BacklogTracer, ManagedBacklogTracer> managedBacklogTracers = new HashMap<BacklogTracer, ManagedBacklogTracer>();
    private final Map<BacklogDebugger, ManagedBacklogDebugger> managedBacklogDebuggers = new HashMap<BacklogDebugger, ManagedBacklogDebugger>();
    private final Map<ThreadPoolExecutor, Object> managedThreadPools = new HashMap<ThreadPoolExecutor, Object>();

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

        String name = context.getName();
        String managementName = context.getManagementNameStrategy().getName();

        try {
            boolean done = false;
            while (!done) {
                ObjectName on = getManagementStrategy().getManagementNamingStrategy().getObjectNameForCamelContext(managementName, name);
                boolean exists = getManagementStrategy().isManaged(mc, on);
                if (!exists) {
                    done = true;
                } else {
                    // okay there exists already a CamelContext with this name, we can try to fix it by finding a free name
                    boolean fixed = false;
                    // if we use the default name strategy we can find a free name to use
                    String newName = findFreeName(mc, context.getManagementNameStrategy(), name);
                    if (newName != null) {
                        // use this as the fixed name
                        fixed = true;
                        done = true;
                        managementName = newName;
                    }
                    // we could not fix it so veto starting camel
                    if (!fixed) {
                        throw new VetoCamelContextStartException("CamelContext (" + context.getName() + ") with ObjectName[" + on + "] is already registered."
                            + " Make sure to use unique names on CamelContext when using multiple CamelContexts in the same MBeanServer.", context);
                    } else {
                        LOG.warn("This CamelContext(" + context.getName() + ") will be registered using the name: " + managementName
                            + " due to clash with an existing name already registered in MBeanServer.");
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
        if (context instanceof DefaultCamelContext) {
            ((DefaultCamelContext) context).setManagementName(managementName);
        }

        try {
            manageObject(mc);
        } catch (Exception e) {
            // must rethrow to allow CamelContext fallback to non JMX agent to allow
            // Camel to continue to run
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        // yes we made it and are initialized
        initialized = true;

        if (mc instanceof ManagedCamelContext) {
            camelContextMBean = (ManagedCamelContext) mc;
        }

        // register any pre registered now that we are initialized
        enlistPreRegisteredServices();

        try {
            Object me = getManagementObjectStrategy().getManagedObjectForCamelHealth(camelContext);
            if (me == null) {
                // endpoint should not be managed
                return;
            }
            manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register CamelHealth MBean. This exception will be ignored.", e);
        }

        try {
            Object me = getManagementObjectStrategy().getManagedObjectForRouteController(camelContext);
            if (me == null) {
                // endpoint should not be managed
                return;
            }
            manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register RouteController MBean. This exception will be ignored.", e);
        }
    }

    private String findFreeName(Object mc, ManagementNameStrategy strategy, String name) throws MalformedObjectNameException {
        // we cannot find a free name for fixed named strategies
        if (strategy.isFixedName()) {
            return null;
        }

        // okay try to find a free name
        boolean done = false;
        String newName = null;
        while (!done) {
            // compute the next name
            newName = strategy.getNextName();
            ObjectName on = getManagementStrategy().getManagementNamingStrategy().getObjectNameForCamelContext(newName, name);
            done = !getManagementStrategy().isManaged(mc, on);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using name: {} in ObjectName[{}] exists? {}", new Object[]{name, on, done});
            }
        }
        return newName;
    }

    /**
     * After {@link CamelContext} has been enlisted in JMX using {@link #onContextStart(org.apache.camel.CamelContext)}
     * then we can enlist any pre registered services as well, as we had to wait for {@link CamelContext} to be
     * enlisted first.
     * <p/>
     * A component/endpoint/service etc. can be pre registered when using dependency injection and annotations such as
     * {@link org.apache.camel.Produce}, {@link org.apache.camel.EndpointInject}. Therefore we need to capture those
     * registrations up front, and then afterwards enlist in JMX when {@link CamelContext} is being started.
     */
    private void enlistPreRegisteredServices() {
        if (preServices.isEmpty()) {
            return;
        }

        LOG.debug("Registering {} pre registered services", preServices.size());
        for (PreRegisterService pre : preServices) {
            if (pre.getComponent() != null) {
                onComponentAdd(pre.getName(), pre.getComponent());
            } else if (pre.getEndpoint() != null) {
                onEndpointAdd(pre.getEndpoint());
            } else if (pre.getService() != null) {
                onServiceAdd(pre.getCamelContext(), pre.getService(), pre.getRoute());
            }
        }

        // we are done so clear the list
        preServices.clear();
    }

    public void onContextStop(CamelContext context) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForRouteController(context);
            // the context could have been removed already
            if (getManagementStrategy().isManaged(mc, null)) {
                unmanageObject(mc);
            }
        } catch (Exception e) {
            LOG.warn("Could not unregister RouteController MBean", e);
        }

        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForCamelHealth(context);
            // the context could have been removed already
            if (getManagementStrategy().isManaged(mc, null)) {
                unmanageObject(mc);
            }
        } catch (Exception e) {
            LOG.warn("Could not unregister CamelHealth MBean", e);
        }

        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForCamelContext(context);
            // the context could have been removed already
            if (getManagementStrategy().isManaged(mc, null)) {
                unmanageObject(mc);
            }
        } catch (Exception e) {
            LOG.warn("Could not unregister CamelContext MBean", e);
        }

        camelContextMBean = null;
    }

    public void onComponentAdd(String name, Component component) {
        // always register components as there are only a few of those
        if (!initialized) {
            // pre register so we can register later when we have been initialized
            PreRegisterService pre = new PreRegisterService();
            pre.onComponentAdd(name, component);
            preServices.add(pre);
            return;
        }
        try {
            Object mc = getManagementObjectStrategy().getManagedObjectForComponent(camelContext, component, name);
            manageObject(mc);
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
            Object mc = getManagementObjectStrategy().getManagedObjectForComponent(camelContext, component, name);
            unmanageObject(mc);
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
        if (!initialized) {
            // pre register so we can register later when we have been initialized
            PreRegisterService pre = new PreRegisterService();
            pre.onEndpointAdd(endpoint);
            preServices.add(pre);
            return;
        }

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
            manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register Endpoint MBean for endpoint: " + endpoint + ". This exception will be ignored.", e);
        }
    }

    public void onEndpointRemove(Endpoint endpoint) {
        // the agent hasn't been started
        if (!initialized) {
            return;
        }

        try {
            Object me = getManagementObjectStrategy().getManagedObjectForEndpoint(camelContext, endpoint);
            unmanageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not unregister Endpoint MBean for endpoint: " + endpoint + ". This exception will be ignored.", e);
        }
    }

    public void onServiceAdd(CamelContext context, Service service, Route route) {
        if (!initialized) {
            // pre register so we can register later when we have been initialized
            PreRegisterService pre = new PreRegisterService();
            pre.onServiceAdd(context, service, route);
            preServices.add(pre);
            return;
        }

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
            manageObject(managedObject);
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
                unmanageObject(managedObject);
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

        // skip non managed services
        if (service instanceof NonManagedService) {
            return null;
        }

        Object answer = null;

        if (service instanceof ManagementAware) {
            return ((ManagementAware<Service>) service).getManagedObject(service);
        } else if (service instanceof Tracer) {
            // special for tracer
            Tracer tracer = (Tracer) service;
            ManagedTracer mt = managedTracers.get(tracer);
            if (mt == null) {
                mt = new ManagedTracer(context, tracer);
                mt.init(getManagementStrategy());
                managedTracers.put(tracer, mt);
            }
            return mt;
        } else if (service instanceof BacklogTracer) {
            // special for backlog tracer
            BacklogTracer backlogTracer = (BacklogTracer) service;
            ManagedBacklogTracer mt = managedBacklogTracers.get(backlogTracer);
            if (mt == null) {
                mt = new ManagedBacklogTracer(context, backlogTracer);
                mt.init(getManagementStrategy());
                managedBacklogTracers.put(backlogTracer, mt);
            }
            return mt;
        } else if (service instanceof BacklogDebugger) {
            // special for backlog debugger
            BacklogDebugger backlogDebugger = (BacklogDebugger) service;
            ManagedBacklogDebugger md = managedBacklogDebuggers.get(backlogDebugger);
            if (md == null) {
                md = new ManagedBacklogDebugger(context, backlogDebugger);
                md.init(getManagementStrategy());
                managedBacklogDebuggers.put(backlogDebugger, md);
            }
            return md;
        } else if (service instanceof DataFormat) {
            answer = getManagementObjectStrategy().getManagedObjectForDataFormat(context, (DataFormat) service);
        } else if (service instanceof Producer) {
            answer = getManagementObjectStrategy().getManagedObjectForProducer(context, (Producer) service);
        } else if (service instanceof Consumer) {
            answer = getManagementObjectStrategy().getManagedObjectForConsumer(context, (Consumer) service);
        } else if (service instanceof Processor) {
            // special for processors as we need to do some extra work
            return getManagedObjectForProcessor(context, (Processor) service, route);
        } else if (service instanceof ThrottlingInflightRoutePolicy) {
            answer = new ManagedThrottlingInflightRoutePolicy(context, (ThrottlingInflightRoutePolicy) service);
        } else if (service instanceof ThrottlingExceptionRoutePolicy) {
            answer = new ManagedThrottlingExceptionRoutePolicy(context, (ThrottlingExceptionRoutePolicy) service);
        } else if (service instanceof ConsumerCache) {
            answer = new ManagedConsumerCache(context, (ConsumerCache) service);
        } else if (service instanceof ProducerCache) {
            answer = new ManagedProducerCache(context, (ProducerCache) service);
        } else if (service instanceof DefaultEndpointRegistry) {
            answer = new ManagedEndpointRegistry(context, (DefaultEndpointRegistry) service);
        } else if (service instanceof TypeConverterRegistry) {
            answer = new ManagedTypeConverterRegistry(context, (TypeConverterRegistry) service);
        } else if (service instanceof RestRegistry) {
            answer = new ManagedRestRegistry(context, (RestRegistry) service);
        } else if (service instanceof InflightRepository) {
            answer = new ManagedInflightRepository(context, (InflightRepository) service);
        } else if (service instanceof AsyncProcessorAwaitManager) {
            answer = new ManagedAsyncProcessorAwaitManager(context, (AsyncProcessorAwaitManager) service);
        } else if (service instanceof RuntimeEndpointRegistry) {
            answer = new ManagedRuntimeEndpointRegistry(context, (RuntimeEndpointRegistry) service);
        } else if (service instanceof StreamCachingStrategy) {
            answer = new ManagedStreamCachingStrategy(context, (StreamCachingStrategy) service);
        } else if (service instanceof EventNotifier) {
            answer = getManagementObjectStrategy().getManagedObjectForEventNotifier(context, (EventNotifier) service);
        } else if (service instanceof TransformerRegistry) {
            answer = new ManagedTransformerRegistry(context, (TransformerRegistry)service);
        } else if (service instanceof ValidatorRegistry) {
            answer = new ManagedValidatorRegistry(context, (ValidatorRegistry)service);
        } else if (service instanceof RuntimeCamelCatalog) {
            answer = new ManagedRuntimeCamelCatalog(context, (RuntimeCamelCatalog) service);
        } else if (service instanceof CamelClusterService) {
            answer = getManagementObjectStrategy().getManagedObjectForClusterService(context, (CamelClusterService)service);
        } else if (service != null) {
            // fallback as generic service
            answer = getManagementObjectStrategy().getManagedObjectForService(context, service);
        }

        if (answer instanceof ManagedService) {
            ManagedService ms = (ManagedService) answer;
            ms.setRoute(route);
            ms.init(getManagementStrategy());
        }

        return answer;
    }

    private Object getManagedObjectForProcessor(CamelContext context, Processor processor, Route route) {
        // a bit of magic here as the processors we want to manage have already been registered
        // in the wrapped processors map when Camel have instrumented the route on route initialization
        // so the idea is now to only manage the processors from the map
        KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor> holder = wrappedProcessors.get(processor);
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
                if (processor instanceof CamelInternalProcessor && mr instanceof ManagedRoute) {
                    CamelInternalProcessor internal = (CamelInternalProcessor) processor;
                    ManagedRoute routeMBean = (ManagedRoute) mr;

                    CamelInternalProcessor.InstrumentationAdvice task = internal.getAdvice(CamelInternalProcessor.InstrumentationAdvice.class);
                    if (task != null) {
                        // we need to wrap the counter with the camel context so we get stats updated on the context as well
                        if (camelContextMBean != null) {
                            CompositePerformanceCounter wrapper = new CompositePerformanceCounter(routeMBean, camelContextMBean);
                            task.setCounter(wrapper);
                        } else {
                            task.setCounter(routeMBean);
                        }
                    }
                }
            }

            try {
                manageObject(mr);
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
                unmanageObject(mr);
            } catch (Exception e) {
                LOG.warn("Could not unregister Route MBean", e);
            }

            // remove from known routes ids, as the route has been removed
            knowRouteIds.remove(route.getId());
        }

        // after the routes has been removed, we should clear the wrapped processors as we no longer need them
        // as they were just a provisional map used during creation of routes
        removeWrappedProcessorsForRoutes(routes);
    }

    public void onErrorHandlerAdd(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
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
            manageObject(me);
        } catch (Exception e) {
            LOG.warn("Could not register error handler builder: " + errorHandlerBuilder + " as ErrorHandler MBean.", e);
        }
    }

    public void onErrorHandlerRemove(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        if (!initialized) {
            return;
        }

        Object me = getManagementObjectStrategy().getManagedObjectForErrorHandler(camelContext, routeContext, errorHandler, errorHandlerBuilder);
        if (me != null) {
            try {
                unmanageObject(me);
            } catch (Exception e) {
                LOG.warn("Could not unregister error handler: " + me + " as ErrorHandler MBean.", e);
            }
        }
    }

    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                                String sourceId, String routeId, String threadPoolProfileId) {

        if (!shouldRegister(threadPool, null)) {
            // avoid registering if not needed
            return;
        }

        Object mtp = getManagementObjectStrategy().getManagedObjectForThreadPool(camelContext, threadPool, id, sourceId, routeId, threadPoolProfileId);

        // skip already managed services, for example if a route has been restarted
        if (getManagementStrategy().isManaged(mtp, null)) {
            LOG.trace("The thread pool is already managed: {}", threadPool);
            return;
        }

        try {
            manageObject(mtp);
            // store a reference so we can unmanage from JMX when the thread pool is removed
            // we need to keep track here, as we cannot re-construct the thread pool ObjectName when removing the thread pool
            managedThreadPools.put(threadPool, mtp);
        } catch (Exception e) {
            LOG.warn("Could not register thread pool: " + threadPool + " as ThreadPool MBean.", e);
        }
    }

    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        if (!initialized) {
            return;
        }

        // lookup the thread pool and remove it from JMX
        Object mtp = managedThreadPools.remove(threadPool);
        if (mtp != null) {
            // skip unmanaged routes
            if (!getManagementStrategy().isManaged(mtp, null)) {
                LOG.trace("The thread pool is not managed: {}", threadPool);
                return;
            }

            try {
                unmanageObject(mtp);
            } catch (Exception e) {
                LOG.warn("Could not unregister ThreadPool MBean", e);
            }
        }
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        if (!initialized) {
            return;
        }

        // Create a map (ProcessorType -> PerformanceCounter)
        // to be passed to InstrumentationInterceptStrategy.
        Map<ProcessorDefinition<?>, PerformanceCounter> registeredCounters =
                new HashMap<ProcessorDefinition<?>, PerformanceCounter>();

        // Each processor in a route will have its own performance counter.
        // These performance counter will be embedded to InstrumentationProcessor
        // and wrap the appropriate processor by InstrumentationInterceptStrategy.
        RouteDefinition route = routeContext.getRoute();

        // register performance counters for all processors and its children
        for (ProcessorDefinition<?> processor : route.getOutputs()) {
            registerPerformanceCounters(routeContext, processor, registeredCounters);
        }

        // set this managed intercept strategy that executes the JMX instrumentation for performance metrics
        // so our registered counters can be used for fine grained performance instrumentation
        routeContext.setManagedInterceptStrategy(new InstrumentationInterceptStrategy(registeredCounters, wrappedProcessors));
    }

    /**
     * Removes the wrapped processors for the given routes, as they are no longer in use.
     * <p/>
     * This is needed to avoid accumulating memory, if a lot of routes is being added and removed.
     *
     * @param routes the routes
     */
    private void removeWrappedProcessorsForRoutes(Collection<Route> routes) {
        // loop the routes, and remove the route associated wrapped processors, as they are no longer in use
        for (Route route : routes) {
            String id = route.getId();

            Iterator<KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor>> it = wrappedProcessors.values().iterator();
            while (it.hasNext()) {
                KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor> holder = it.next();
                RouteDefinition def = ProcessorDefinitionHelper.getRoute(holder.getKey());
                if (def != null && id.equals(def.getId())) {
                    it.remove();
                }
            }
        }
        
    }

    private void registerPerformanceCounters(RouteContext routeContext, ProcessorDefinition<?> processor,
                                             Map<ProcessorDefinition<?>, PerformanceCounter> registeredCounters) {

        // traverse children if any exists
        List<ProcessorDefinition<?>> children = processor.getOutputs();
        for (ProcessorDefinition<?> child : children) {
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
        boolean enabled = camelContext.getManagementStrategy().getManagementAgent().getStatisticsLevel().isDefaultOrExtended();
        pc.setStatisticsEnabled(enabled);

        // and add it as a a registered counter that will be used lazy when Camel
        // does the instrumentation of the route and adds the InstrumentationProcessor
        // that does the actual performance metrics gatherings at runtime
        registeredCounters.put(processor, pc);
    }

    /**
     * Should the given processor be registered.
     */
    protected boolean registerProcessor(ProcessorDefinition<?> processor) {
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
        boolean only = getManagementStrategy().getManagementAgent().getOnlyRegisterProcessorWithCustomId() != null
                && getManagementStrategy().getManagementAgent().getOnlyRegisterProcessorWithCustomId();
        if (only) {
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

    /**
     * Strategy for managing the object
     *
     * @param me the managed object
     * @throws Exception is thrown if error registering the object for management
     */
    protected void manageObject(Object me) throws Exception {
        getManagementStrategy().manageObject(me);
        if (me instanceof TimerListener) {
            TimerListener timer = (TimerListener) me;
            loadTimer.addTimerListener(timer);
        }
    }

    /**
     * Un-manages the object.
     *
     * @param me the managed object
     * @throws Exception is thrown if error unregistering the managed object
     */
    protected void unmanageObject(Object me) throws Exception {
        if (me instanceof TimerListener) {
            TimerListener timer = (TimerListener) me;
            loadTimer.removeTimerListener(timer);
        }
        getManagementStrategy().unmanageObject(me);
    }

    /**
     * Whether or not to register the mbean.
     * <p/>
     * The {@link ManagementAgent} has options which controls when to register.
     * This allows us to only register mbeans accordingly. For example by default any
     * dynamic endpoints is not registered. This avoids to register excessive mbeans, which
     * most often is not desired.
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

        ManagementAgent agent = getManagementStrategy().getManagementAgent();
        if (agent == null) {
            // do not register if no agent
            return false;
        }

        // always register if we are starting CamelContext
        if (getCamelContext().getStatus().isStarting()) {
            return true;
        }

        // always register if we are setting up routes
        if (getCamelContext().isSetupRoutes()) {
            return true;
        }

        // register if always is enabled
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

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        // defer starting the timer manager until CamelContext has been fully started
        camelContext.addStartupListener(loadTimerStartupListener);
    }

    private final class TimerListenerManagerStartupListener implements StartupListener {

        @Override
        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            // we are disabled either if configured explicit, or if level is off
            boolean load = camelContext.getManagementStrategy().getManagementAgent().getLoadStatisticsEnabled() != null
                    && camelContext.getManagementStrategy().getManagementAgent().getLoadStatisticsEnabled();
            boolean disabled = !load || camelContext.getManagementStrategy().getStatisticsLevel() == ManagementStatisticsLevel.Off;

            LOG.debug("Load performance statistics {}", disabled ? "disabled" : "enabled");
            if (!disabled) {
                // must use 1 sec interval as the load statistics is based on 1 sec calculations
                loadTimer.setInterval(1000);
                // we have to defer enlisting timer lister manager as a service until CamelContext has been started
                getCamelContext().addService(loadTimer);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        initialized = false;
        knowRouteIds.clear();
        preServices.clear();
        wrappedProcessors.clear();
        managedTracers.clear();
        managedBacklogTracers.clear();
        managedBacklogDebuggers.clear();
        managedThreadPools.clear();
    }

    /**
     * Class which holds any pre registration details.
     *
     * @see org.apache.camel.management.DefaultManagementLifecycleStrategy#enlistPreRegisteredServices()
     */
    private static final class PreRegisterService {

        private String name;
        private Component component;
        private Endpoint endpoint;
        private CamelContext camelContext;
        private Service service;
        private Route route;

        public void onComponentAdd(String name, Component component) {
            this.name = name;
            this.component = component;
        }

        public void onEndpointAdd(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public void onServiceAdd(CamelContext camelContext, Service service, Route route) {
            this.camelContext = camelContext;
            this.service = service;
            this.route = route;
        }

        public String getName() {
            return name;
        }

        public Component getComponent() {
            return component;
        }

        public Endpoint getEndpoint() {
            return endpoint;
        }

        public CamelContext getCamelContext() {
            return camelContext;
        }

        public Service getService() {
            return service;
        }

        public Route getRoute() {
            return route;
        }
    }

}

