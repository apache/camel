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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.ContractAdvice;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The context used to activate new routing rules
 *
 * @version 
 */
public class DefaultRouteContext implements RouteContext {
    private final Map<ProcessorDefinition<?>, AtomicInteger> nodeIndex = new HashMap<ProcessorDefinition<?>, AtomicInteger>();
    private final RouteDefinition route;
    private FromDefinition from;
    private final Collection<Route> routes;
    private Endpoint endpoint;
    private final List<Processor> eventDrivenProcessors = new ArrayList<Processor>();
    private CamelContext camelContext;
    private List<InterceptStrategy> interceptStrategies = new ArrayList<InterceptStrategy>();
    private InterceptStrategy managedInterceptStrategy;
    private boolean routeAdded;
    private Boolean trace;
    private Boolean messageHistory;
    private Boolean logMask;
    private Boolean logExhaustedMessageBody;
    private Boolean streamCache;
    private Boolean handleFault;
    private Long delay;
    private Boolean autoStartup = Boolean.TRUE;
    private List<RoutePolicy> routePolicyList = new ArrayList<RoutePolicy>();
    private ShutdownRoute shutdownRoute;
    private ShutdownRunningTask shutdownRunningTask;
    private RouteError routeError;
    private RouteController routeController;

    public DefaultRouteContext(CamelContext camelContext, RouteDefinition route, FromDefinition from, Collection<Route> routes) {
        this.camelContext = camelContext;
        this.route = route;
        this.from = from;
        this.routes = routes;
    }

    /**
     * Only used for lazy construction from inside ExpressionType
     */
    public DefaultRouteContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.routes = new ArrayList<Route>();
        this.route = new RouteDefinition("temporary");
    }

    public Endpoint getEndpoint() {
        if (endpoint == null) {
            endpoint = from.resolveEndpoint(this);
        }
        return endpoint;
    }

    public FromDefinition getFrom() {
        return from;
    }

    public RouteDefinition getRoute() {
        return route;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Endpoint resolveEndpoint(String uri) {
        return route.resolveEndpoint(getCamelContext(), uri);
    }

    public Endpoint resolveEndpoint(String uri, String ref) {
        Endpoint endpoint = null;
        if (uri != null) {
            endpoint = resolveEndpoint(uri);
            if (endpoint == null) {
                throw new NoSuchEndpointException(uri);
            }
        }
        if (ref != null) {
            endpoint = lookup(ref, Endpoint.class);
            if (endpoint == null) {
                throw new NoSuchEndpointException("ref:" + ref, "check your camel registry with id " + ref);
            }
            // Check the endpoint has the right CamelContext 
            if (!this.getCamelContext().equals(endpoint.getCamelContext())) {
                throw new NoSuchEndpointException("ref:" + ref, "make sure the endpoint has the same camel context as the route does.");
            }
            try {
                // need add the endpoint into service
                getCamelContext().addService(endpoint);
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("Either 'uri' or 'ref' must be specified on: " + this);
        } else {
            return endpoint;
        }
    }

    public <T> T lookup(String name, Class<T> type) {
        return getCamelContext().getRegistry().lookupByNameAndType(name, type);
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        return getCamelContext().getRegistry().findByTypeWithName(type);
    }

    @Override
    public <T> T mandatoryLookup(String name, Class<T> type) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), name, type);
    }

    public void commit() {
        // now lets turn all of the event driven consumer processors into a single route
        if (!eventDrivenProcessors.isEmpty()) {
            Processor target = Pipeline.newInstance(getCamelContext(), eventDrivenProcessors);

            // force creating the route id so its known ahead of the route is started
            String routeId = route.idOrCreate(getCamelContext().getNodeIdFactory());

            // and wrap it in a unit of work so the UoW is on the top, so the entire route will be in the same UoW
            CamelInternalProcessor internal = new CamelInternalProcessor(target);
            internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(this));

            // and then optionally add route policy processor if a custom policy is set
            List<RoutePolicy> routePolicyList = getRoutePolicyList();
            if (routePolicyList != null && !routePolicyList.isEmpty()) {
                for (RoutePolicy policy : routePolicyList) {
                    // add policy as service if we have not already done that (eg possible if two routes have the same service)
                    // this ensures Camel can control the lifecycle of the policy
                    if (!camelContext.hasService(policy)) {
                        try {
                            camelContext.addService(policy);
                        } catch (Exception e) {
                            throw ObjectHelper.wrapRuntimeCamelException(e);
                        }
                    }
                }

                internal.addAdvice(new CamelInternalProcessor.RoutePolicyAdvice(routePolicyList));
            }

            // wrap in route inflight processor to track number of inflight exchanges for the route
            internal.addAdvice(new CamelInternalProcessor.RouteInflightRepositoryAdvice(camelContext.getInflightRepository(), routeId));

            // wrap in JMX instrumentation processor that is used for performance stats
            internal.addAdvice(new CamelInternalProcessor.InstrumentationAdvice("route"));

            // wrap in route lifecycle
            internal.addAdvice(new CamelInternalProcessor.RouteLifecycleAdvice());

            // wrap in REST binding
            if (route.getRestBindingDefinition() != null) {
                try {
                    internal.addAdvice(route.getRestBindingDefinition().createRestBindingAdvice(this));
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            // wrap in contract
            if (route.getInputType() != null || route.getOutputType() != null) {
                Contract contract = new Contract();
                if (route.getInputType() != null) {
                    contract.setInputType(route.getInputType().getUrn());
                    contract.setValidateInput(route.getInputType().isValidate());
                }
                if (route.getOutputType() != null) {
                    contract.setOutputType(route.getOutputType().getUrn());
                    contract.setValidateOutput(route.getOutputType().isValidate());
                }
                internal.addAdvice(new ContractAdvice(contract));
                // make sure to enable data type as its in use when using input/output types on routes
                camelContext.setUseDataType(true);
            }

            // and create the route that wraps the UoW
            Route edcr = new EventDrivenConsumerRoute(this, getEndpoint(), internal);
            edcr.getProperties().put(Route.ID_PROPERTY, routeId);
            edcr.getProperties().put(Route.PARENT_PROPERTY, Integer.toHexString(route.hashCode()));
            edcr.getProperties().put(Route.DESCRIPTION_PROPERTY, route.getDescriptionText());
            if (route.getGroup() != null) {
                edcr.getProperties().put(Route.GROUP_PROPERTY, route.getGroup());
            }
            String rest = "false";
            if (route.isRest() != null && route.isRest()) {
                rest = "true";
            }
            edcr.getProperties().put(Route.REST_PROPERTY, rest);

            // after the route is created then set the route on the policy processor so we get hold of it
            CamelInternalProcessor.RoutePolicyAdvice task = internal.getAdvice(CamelInternalProcessor.RoutePolicyAdvice.class);
            if (task != null) {
                task.setRoute(edcr);
            }
            CamelInternalProcessor.RouteLifecycleAdvice task2 = internal.getAdvice(CamelInternalProcessor.RouteLifecycleAdvice.class);
            if (task2 != null) {
                task2.setRoute(edcr);
            }

            // invoke init on route policy
            if (routePolicyList != null && !routePolicyList.isEmpty()) {
                for (RoutePolicy policy : routePolicyList) {
                    policy.onInit(edcr);
                }
            }

            routes.add(edcr);
        }
    }

    public void addEventDrivenProcessor(Processor processor) {
        eventDrivenProcessors.add(processor);
    }

    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    public void setInterceptStrategies(List<InterceptStrategy> interceptStrategies) {
        this.interceptStrategies = interceptStrategies;
    }

    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        getInterceptStrategies().add(interceptStrategy);
    }

    public void setManagedInterceptStrategy(InterceptStrategy interceptStrategy) {
        this.managedInterceptStrategy = interceptStrategy;
    }

    public InterceptStrategy getManagedInterceptStrategy() {
        return managedInterceptStrategy;
    }

    public boolean isRouteAdded() {
        return routeAdded;
    }

    public void setIsRouteAdded(boolean routeAdded) {
        this.routeAdded = routeAdded;
    }

    public void setTracing(Boolean tracing) {
        this.trace = tracing;
    }

    public Boolean isTracing() {
        if (trace != null) {
            return trace;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isTracing();
        }
    }

    public void setMessageHistory(Boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    public Boolean isMessageHistory() {
        if (messageHistory != null) {
            return messageHistory;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isMessageHistory();
        }
    }

    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    public Boolean isLogMask() {
        if (logMask != null) {
            return logMask;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isLogMask();
        }
    }

    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public Boolean isLogExhaustedMessageBody() {
        if (logExhaustedMessageBody != null) {
            return logExhaustedMessageBody;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isLogExhaustedMessageBody();
        }
    }

    public void setStreamCaching(Boolean cache) {
        this.streamCache = cache;
    }

    public Boolean isStreamCaching() {
        if (streamCache != null) {
            return streamCache;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isStreamCaching();
        }
    }

    public void setHandleFault(Boolean handleFault) {
        this.handleFault = handleFault;
    }

    public Boolean isHandleFault() {
        if (handleFault != null) {
            return handleFault;
        } else {
            // fallback to the option from camel context
            return getCamelContext().isHandleFault();
        }
    }

    public void setDelayer(Long delay) {
        this.delay = delay;
    }

    public Long getDelayer() {
        if (delay != null) {
            return delay;
        } else {
            // fallback to the option from camel context
            return getCamelContext().getDelayer();
        }
    }

    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public Boolean isAutoStartup() {
        if (autoStartup != null) {
            return autoStartup;
        }
        // default to true
        return true;
    }

    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        // can only be configured on CamelContext
        getCamelContext().setAllowUseOriginalMessage(allowUseOriginalMessage);
    }

    public Boolean isAllowUseOriginalMessage() {
        // can only be configured on CamelContext
        return getCamelContext().isAllowUseOriginalMessage();
    }

    public ShutdownRoute getShutdownRoute() {
        if (shutdownRoute != null) {
            return shutdownRoute;
        } else {
            // fallback to the option from camel context
            return getCamelContext().getShutdownRoute();
        }
    }

    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    public ShutdownRunningTask getShutdownRunningTask() {
        if (shutdownRunningTask != null) {
            return shutdownRunningTask;
        } else {
            // fallback to the option from camel context
            return getCamelContext().getShutdownRunningTask();
        }
    }
    
    public int getAndIncrement(ProcessorDefinition<?> node) {
        AtomicInteger count = nodeIndex.get(node);
        if (count == null) {
            count = new AtomicInteger();
            nodeIndex.put(node, count);
        }
        return count.getAndIncrement();
    }

    public void setRoutePolicyList(List<RoutePolicy> routePolicyList) {
        this.routePolicyList = routePolicyList;
    }

    public List<RoutePolicy> getRoutePolicyList() {
        return routePolicyList;
    }

    @Override
    public RouteError getLastError() {
        return routeError;
    }

    @Override
    public void setLastError(RouteError routeError) {
        this.routeError = routeError;
    }

    @Override
    public RouteController getRouteController() {
        return routeController;
    }

    @Override
    public void setRouteController(RouteController routeController) {
        this.routeController = routeController;
    }
}
