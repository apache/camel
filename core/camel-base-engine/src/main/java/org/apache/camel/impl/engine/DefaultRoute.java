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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.Service;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.resume.ConsumerListener;
import org.apache.camel.resume.ConsumerListenerAware;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.resume.AdapterHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;

/**
 * Default implementation of {@link Route}.
 * <p/>
 * Use the API from {@link org.apache.camel.CamelContext} to control the lifecycle of a route, such as starting and
 * stopping using the {@link org.apache.camel.spi.RouteController#startRoute(String)} and
 * {@link org.apache.camel.spi.RouteController#stopRoute(String)} methods.
 */
public class DefaultRoute extends ServiceSupport implements Route {

    private final CamelContext camelContext;
    private NamedNode route;
    private final String routeId;
    private final String routeDescription;
    private final Resource sourceResource;
    private final String sourceLocation;
    private final String sourceLocationShort;
    private final List<Processor> eventDrivenProcessors = new ArrayList<>();
    private final List<InterceptStrategy> interceptStrategies = new ArrayList<>(0);
    private ManagementInterceptStrategy managementInterceptStrategy;
    private Boolean trace;
    private Boolean backlogTrace;
    private Boolean debug;
    private Boolean messageHistory;
    private Boolean logMask;
    private Boolean logExhaustedMessageBody;
    private Boolean streamCache;
    private Long delay;
    private Boolean autoStartup = Boolean.TRUE;
    private final List<RoutePolicy> routePolicyList = new ArrayList<>();
    private ShutdownRoute shutdownRoute;
    private ShutdownRunningTask shutdownRunningTask;
    private final Map<String, Processor> onCompletions = new HashMap<>();
    private final Map<String, Processor> onExceptions = new HashMap<>();
    private ResumeStrategy resumeStrategy;
    private ConsumerListener<?, ?> consumerListener;

    // camel-core-model
    @Deprecated
    private ErrorHandlerFactory errorHandlerFactory;
    // camel-core-model: must be concurrent as error handlers can be mutated concurrently via multicast/recipientlist EIPs
    private final ConcurrentMap<ErrorHandlerFactory, Set<NamedNode>> errorHandlers = new ConcurrentHashMap<>();

    private final Endpoint endpoint;
    private final Map<String, Object> properties = new HashMap<>();
    private final List<Service> services = new ArrayList<>();
    private long startDate;
    private RouteError routeError;
    private Integer startupOrder;
    private RouteController routeController;
    private Processor processor;
    private Consumer consumer;

    public DefaultRoute(CamelContext camelContext, NamedNode route, String routeId,
                        String routeDescription, Endpoint endpoint, Resource resource) {
        this.camelContext = camelContext;
        this.route = route;
        this.routeId = routeId;
        this.routeDescription = routeDescription;
        this.endpoint = endpoint;
        this.sourceResource = resource;
        this.sourceLocation = LoggerHelper.getSourceLocation(route);
        this.sourceLocationShort = LoggerHelper.getLineNumberLoggerName(route);
    }

    @Override
    public String getId() {
        return routeId;
    }

    @Override
    public boolean isCustomId() {
        return "true".equals(properties.get(Route.CUSTOM_ID_PROPERTY));
    }

    @Override
    public String getGroup() {
        return (String) properties.get(Route.GROUP_PROPERTY);
    }

    @Override
    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
    }

    @Override
    public long getUptimeMillis() {
        if (startDate == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startDate;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String getDescription() {
        Object value = properties.get(Route.DESCRIPTION_PROPERTY);
        return value != null ? (String) value : null;
    }

    @Override
    public String getConfigurationId() {
        Object value = properties.get(Route.CONFIGURATION_ID_PROPERTY);
        return value != null ? (String) value : null;
    }

    @Override
    public Resource getSourceResource() {
        return sourceResource;
    }

    @Override
    public String getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public String getSourceLocationShort() {
        return sourceLocationShort;
    }

    @Override
    public void initializeServices() throws Exception {
        // gather all the services for this route
        gatherServices(services);
    }

    @Override
    public List<Service> getServices() {
        return services;
    }

    @Override
    public void addService(Service service) {
        if (!services.contains(service)) {
            services.add(service);
        }
    }

    @Override
    public void warmUp() {
        // noop
    }

    /**
     * Do not invoke this method directly, use {@link org.apache.camel.spi.RouteController#startRoute(String)} to start
     * a route.
     */
    @Override
    public void start() {
        super.start();
    }

    /**
     * Do not invoke this method directly, use {@link org.apache.camel.spi.RouteController#stopRoute(String)} to stop a
     * route.
     */
    @Override
    public void stop() {
        super.stop();
    }

    @Override
    protected void doStart() throws Exception {
        startDate = System.currentTimeMillis();
    }

    @Override
    protected void doStop() throws Exception {
        // and clear start date
        startDate = 0;
    }

    @Override
    protected void doShutdown() throws Exception {
        // clear services when shutting down
        services.clear();
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
    public Integer getStartupOrder() {
        return startupOrder;
    }

    @Override
    public void setStartupOrder(Integer startupOrder) {
        this.startupOrder = startupOrder;
    }

    @Override
    public RouteController getRouteController() {
        return routeController;
    }

    @Override
    public void setRouteController(RouteController routeController) {
        this.routeController = routeController;
    }

    @Override
    public Boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    @Override
    public NamedNode getRoute() {
        return route;
    }

    @Override
    public void clearRouteModel() {
        route = null;
        errorHandlerFactory = null;
        errorHandlers.clear();
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public String getRouteDescription() {
        return routeDescription;
    }

    @Override
    public List<Processor> getEventDrivenProcessors() {
        return eventDrivenProcessors;
    }

    @Override
    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    @Override
    public void setManagementInterceptStrategy(ManagementInterceptStrategy interceptStrategy) {
        this.managementInterceptStrategy = interceptStrategy;
    }

    @Override
    public ManagementInterceptStrategy getManagementInterceptStrategy() {
        return managementInterceptStrategy;
    }

    @Override
    public void setTracing(Boolean tracing) {
        this.trace = tracing;
    }

    @Override
    public Boolean isTracing() {
        if (trace != null) {
            return trace;
        } else {
            // fallback to the option from camel context
            return camelContext.isTracing();
        }
    }

    @Override
    public String getTracingPattern() {
        // can only set this on context level
        return camelContext.getTracingPattern();
    }

    @Override
    public void setTracingPattern(String tracePattern) {
        // can only set this on context level
        camelContext.setTracingPattern(tracePattern);
    }

    @Override
    public void setBacklogTracing(Boolean backlogTrace) {
        this.backlogTrace = backlogTrace;
    }

    @Override
    public Boolean isBacklogTracing() {
        if (backlogTrace != null) {
            return backlogTrace;
        } else {
            // fallback to the option from camel context
            return camelContext.isBacklogTracing();
        }
    }

    @Override
    public void setDebugging(Boolean debugging) {
        this.debug = debugging;
    }

    @Override
    public Boolean isDebugging() {
        if (debug != null) {
            return debug;
        } else {
            // fallback to the option from camel context
            return camelContext.isDebugging();
        }
    }

    @Override
    public void setMessageHistory(Boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    @Override
    public Boolean isMessageHistory() {
        if (messageHistory != null) {
            return messageHistory;
        } else {
            // fallback to the option from camel context
            return camelContext.isMessageHistory();
        }
    }

    @Override
    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    @Override
    public Boolean isLogMask() {
        if (logMask != null) {
            return logMask;
        } else {
            // fallback to the option from camel context
            return camelContext.isLogMask();
        }
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    @Override
    public Boolean isLogExhaustedMessageBody() {
        if (logExhaustedMessageBody != null) {
            return logExhaustedMessageBody;
        } else {
            // fallback to the option from camel context
            return camelContext.isLogExhaustedMessageBody();
        }
    }

    @Override
    public void setStreamCaching(Boolean cache) {
        this.streamCache = cache;
    }

    @Override
    public Boolean isStreamCaching() {
        if (streamCache != null) {
            return streamCache;
        } else {
            // fallback to the option from camel context
            return camelContext.isStreamCaching();
        }
    }

    @Override
    public void setDelayer(Long delay) {
        this.delay = delay;
    }

    @Override
    public Long getDelayer() {
        if (delay != null) {
            return delay;
        } else {
            // fallback to the option from camel context
            return camelContext.getDelayer();
        }
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
    }

    @Override
    public ErrorHandlerFactory getErrorHandlerFactory() {
        return errorHandlerFactory;
    }

    @Override
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    @Override
    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        // can only be configured on CamelContext
        camelContext.setAllowUseOriginalMessage(allowUseOriginalMessage);
    }

    @Override
    public Boolean isAllowUseOriginalMessage() {
        // can only be configured on CamelContext
        return camelContext.isAllowUseOriginalMessage();
    }

    @Override
    public Boolean isCaseInsensitiveHeaders() {
        // can only be configured on CamelContext
        return camelContext.isCaseInsensitiveHeaders();
    }

    @Override
    public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {
        // can only be configured on CamelContext
        camelContext.setCaseInsensitiveHeaders(caseInsensitiveHeaders);
    }

    @Override
    public Boolean isAutowiredEnabled() {
        // can only be configured on CamelContext
        return camelContext.isAutowiredEnabled();
    }

    @Override
    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        // can only be configured on CamelContext
        camelContext.setAutowiredEnabled(autowiredEnabled);
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        if (shutdownRoute != null) {
            return shutdownRoute;
        } else {
            // fallback to the option from camel context
            return camelContext.getShutdownRoute();
        }
    }

    @Override
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        if (shutdownRunningTask != null) {
            return shutdownRunningTask;
        } else {
            // fallback to the option from camel context
            return camelContext.getShutdownRunningTask();
        }
    }

    @Override
    public List<RoutePolicy> getRoutePolicyList() {
        return routePolicyList;
    }

    @Override
    public Collection<Processor> getOnCompletions() {
        return onCompletions.values();
    }

    @Override
    public void setOnCompletion(String onCompletionId, Processor processor) {
        onCompletions.put(onCompletionId, processor);
    }

    @Override
    public Collection<Processor> getOnExceptions() {
        return onExceptions.values();
    }

    @Override
    public Processor getOnException(String onExceptionId) {
        return onExceptions.get(onExceptionId);
    }

    @Override
    public void setOnException(String onExceptionId, Processor processor) {
        onExceptions.put(onExceptionId, processor);
    }

    @Override
    public Set<NamedNode> getErrorHandlers(ErrorHandlerFactory factory) {
        return errorHandlers.computeIfAbsent(factory, f -> new LinkedHashSet<>());
    }

    @Override
    public void addErrorHandler(ErrorHandlerFactory factory, NamedNode onException) {
        errorHandlers.computeIfAbsent(factory, f -> new LinkedHashSet<>()).add(onException);
    }

    @Override
    public void addErrorHandlerFactoryReference(ErrorHandlerFactory source, ErrorHandlerFactory target) {
        Set<NamedNode> list = errorHandlers.computeIfAbsent(source, f -> new LinkedHashSet<>());
        Set<NamedNode> previous = errorHandlers.put(target, list);
        if (list != previous && ObjectHelper.isNotEmpty(previous) && ObjectHelper.isNotEmpty(list)) {
            throw new IllegalStateException("Multiple references with different handlers");
        }
    }

    @Override
    public String toString() {
        return "Route[" + getEndpoint() + " -> " + processor + "]";
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Factory method to lazily create the complete list of services required for this route such as adding the
     * processor or consumer
     */
    protected void gatherServices(List<Service> services) throws Exception {
        // first gather the root services
        gatherRootServices(services);
        // and then all the child services
        List<Service> children = new ArrayList<>();
        for (Service service : services) {
            Set<Service> extra = ServiceHelper.getChildServices(service);
            children.addAll(extra);
        }
        for (Service extra : children) {
            if (!services.contains(extra)) {
                services.add(extra);
            }
        }
    }

    protected void gatherRootServices(List<Service> services) throws Exception {
        Endpoint endpoint = getEndpoint();
        consumer = endpoint.createConsumer(processor);
        if (consumer != null) {
            services.add(consumer);
            if (consumer instanceof RouteAware) {
                ((RouteAware) consumer).setRoute(this);
            }
            if (consumer instanceof RouteIdAware) {
                ((RouteIdAware) consumer).setRouteId(this.getId());
            }

            if (consumer instanceof ResumeAware<?> && resumeStrategy != null) {
                ResumeAdapter resumeAdapter = AdapterHelper.eval(getCamelContext(), (ResumeAware<?>) consumer, resumeStrategy);
                resumeStrategy.setAdapter(resumeAdapter);
                ((ResumeAware) consumer).setResumeStrategy(resumeStrategy);
            }

            if (consumer instanceof ConsumerListenerAware<?>) {
                ((ConsumerListenerAware) consumer).setConsumerListener(consumerListener);
            }
        }
        if (processor instanceof Service) {
            services.add((Service) processor);
        }
        for (Processor p : onCompletions.values()) {
            if (processor instanceof Service) {
                services.add((Service) p);
            }
        }
        for (Processor p : onExceptions.values()) {
            if (processor instanceof Service) {
                services.add((Service) p);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Navigate<Processor> navigate() {
        Processor answer = getProcessor();

        // we want to navigate routes to be easy, so skip the initial channel
        // and navigate to its output where it all starts from end user point of view
        if (answer instanceof Navigate) {
            Navigate<Processor> nav = (Navigate<Processor>) answer;
            if (nav.next().size() == 1) {
                Object first = nav.next().get(0);
                if (first instanceof Navigate) {
                    return (Navigate<Processor>) first;
                }
            }
            return (Navigate<Processor>) answer;
        }
        return null;
    }

    @Override
    public List<Processor> filter(String pattern) {
        List<Processor> match = new ArrayList<>();
        doFilter(pattern, navigate(), match);
        return match;
    }

    @SuppressWarnings("unchecked")
    private void doFilter(String pattern, Navigate<Processor> nav, List<Processor> match) {
        List<Processor> list = nav.next();
        if (list != null) {
            for (Processor proc : list) {
                String id = null;
                if (proc instanceof IdAware) {
                    id = ((IdAware) proc).getId();
                }
                if (PatternHelper.matchPattern(id, pattern)) {
                    match.add(proc);
                }
                if (proc instanceof Navigate) {
                    Navigate<Processor> child = (Navigate<Processor>) proc;
                    doFilter(pattern, child, match);
                }
            }
        }
    }

    @Override
    public Consumer getConsumer() {
        return consumer;
    }

    @Override
    public boolean supportsSuspension() {
        return consumer instanceof Suspendable && consumer instanceof SuspendableService;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public void setConsumerListener(ConsumerListener<?, ?> consumerListener) {
        this.consumerListener = consumerListener;
    }
}
