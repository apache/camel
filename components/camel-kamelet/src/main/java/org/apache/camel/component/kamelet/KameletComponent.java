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
package org.apache.camel.component.kamelet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.RouteTemplateHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kamelet.Kamelet.ADVANCED_OPTIONS;
import static org.apache.camel.component.kamelet.Kamelet.BRIDGE_ERROR_HANDLER;
import static org.apache.camel.component.kamelet.Kamelet.NO_ERROR_HANDLER;
import static org.apache.camel.component.kamelet.Kamelet.PARAM_LOCATION;
import static org.apache.camel.component.kamelet.Kamelet.PARAM_ROUTE_ID;
import static org.apache.camel.component.kamelet.Kamelet.PARAM_TEMPLATE_ID;
import static org.apache.camel.component.kamelet.Kamelet.PARAM_UUID;

/**
 * Materialize route templates
 */
@Component(Kamelet.SCHEME)
public class KameletComponent extends DefaultComponent {

    /** The Kamelets whose route this thread is creating, innermost first: to find a Kamelet that uses itself. */
    private static final ThreadLocal<Deque<String>> CREATING = ThreadLocal.withInitial(ArrayDeque::new);

    private static final Logger LOG = LoggerFactory.getLogger(KameletComponent.class);

    // how long the removal of a route waits before it checks again whether the thread pools of the route are idle
    private static final long ROUTE_REMOVAL_RETRY_MILLIS = 100;

    private final ReentrantLock lock = new ReentrantLock();

    private final LifecycleHandler lifecycleHandler = new LifecycleHandler();

    // stops and removes the routes of Kamelet endpoints that are shut down, off the threads that route the messages
    private volatile ScheduledExecutorService routeRemover;
    // the thread pools that the processors of each route created (such as threads()), by route id
    private final Map<String, List<ThreadPoolExecutor>> routeThreadPools = new ConcurrentHashMap<>();

    // active consumers
    private final Map<String, KameletConsumer> consumers = new HashMap<>();
    private final Lock consumersLock = new ReentrantLock();
    private final Condition consumersCondition = consumersLock.newCondition();
    // active kamelet EIPs
    private final Map<String, Processor> kameletEips = new ConcurrentHashMap<>();

    @Metadata(label = "advanced", autowired = true)
    private RouteTemplateLoaderListener routeTemplateLoaderListener;

    // counter that is used for producers to keep track if any consumer was added/removed since they last checked
    // this is used for optimization to avoid each producer to get consumer for each message processed
    // (locking via synchronized, and then lookup in the map as the cost)
    // consumers and producers are only added/removed during startup/shutdown or if routes is manually controlled
    private volatile int stateCounter;

    @Metadata(label = "producer", defaultValue = "true")
    private boolean block = true;
    @Metadata(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @Metadata(label = "advanced")
    private boolean noErrorHandler;

    @Metadata
    private Map<String, Properties> templateProperties;
    @Metadata
    private Map<String, Properties> routeProperties;
    @Metadata(defaultValue = Kamelet.DEFAULT_LOCATION)
    private String location = Kamelet.DEFAULT_LOCATION;

    @Metadata(label = "advanced", defaultValue = "false",
              description = "Whether to forward extra URI parameters to the endpoint wrapped by the Kamelet. When enabled, parameters that are not Kamelet template properties or reserved Kamelet options are appended to the wrapped source or sink endpoint. Disabled by default.")
    private boolean advancedOptions;

    public KameletComponent() {
    }

    @Override
    public boolean useRawUri() {
        // must use raw because sensitive information need to be preserved when building URIs
        // as otherwise a kamelet would encode parameters (also sensitive which leads to wrong passwords)
        return true;
    }

    public void addKameletEip(String key, Processor callback) {
        kameletEips.put(key, callback);
    }

    public Processor removeKameletEip(String key) {
        return kameletEips.remove(key);
    }

    public Processor getKameletEip(String key) {
        return kameletEips.get(key);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String templateId = Kamelet.extractTemplateId(getCamelContext(), remaining, parameters);
        final String uuid = Kamelet.extractUuid();
        final String routeId = Kamelet.extractRouteId(getCamelContext(), remaining, parameters, uuid);
        final String loc = Kamelet.extractLocation(getCamelContext(), parameters);
        // a route id that is not given in the uri is unique to this endpoint
        final boolean ownRouteId = routeId.equals(templateId + "-" + uuid);

        parameters.remove(PARAM_TEMPLATE_ID);
        parameters.remove(PARAM_ROUTE_ID);
        parameters.remove(PARAM_LOCATION);
        parameters.remove(PARAM_UUID);

        // manually need to resolve raw parameters as input to the kamelet because
        // resolveRawParameterValues is false
        // this ensures that parameters such as passwords are used as-is and not encoded
        // but this requires to use RAW() syntax in the kamelet template.
        // keep the values as written first (useRawUri), as advanced options are forwarded as URI text
        final Map<String, Object> rawParameters = copyParameters(parameters);
        URISupport.resolveRawParameterValues(parameters);

        final KameletEndpoint endpoint;

        if (Kamelet.SOURCE_ID.equals(remaining) || Kamelet.SINK_ID.equals(remaining)) {
            //
            // if remaining is either `source` or `sink' then it is a virtual
            // endpoint that is used inside the kamelet definition to mark it
            // as in/out endpoint.
            //
            // The following snippet defines a template which will act as a
            // consumer for this Kamelet:
            //
            //     from("kamelet:source")
            //         .to("log:info")
            //
            // The following snippet defines a template which will act as a
            // producer for this Kamelet:
            //
            //     from("telegram:bots")
            //         .to("kamelet:sink")
            //
            // Note that at the moment, there's no enforcement around `source`
            // and `sink' to be defined on the right side (producer or consumer)
            //
            endpoint = new KameletEndpoint(uri, this, templateId, routeId);

            // forward component properties
            endpoint.setNoErrorHandler(noErrorHandler);
            endpoint.setBlock(block);
            endpoint.setTimeout(timeout);
            // endpoint specific location
            endpoint.setLocation(loc);
            endpoint.setAdvancedOptions(advancedOptions);

            // set endpoint specific properties
            setProperties(endpoint, parameters);
        } else {
            endpoint = new KameletEndpoint(uri, this, templateId, routeId) {
                // the route this endpoint created from the template while the context runs (such as from toD), with
                // a route id of its own, which is removed when this endpoint is shut down
                private volatile Route createdRoute;
                // the exchange whose call of this endpoint is completing on the thread that runs the callback
                private volatile Exchange completing;

                @Override
                protected void doInit() throws Exception {
                    super.doInit();
                    //
                    // since this is the real kamelet, then we need to hand it
                    // over to the tracker.
                    //
                    String routeId = getCamelContext().getCamelContextExtension().getCreateRoute();
                    String processorId = getCamelContext().getCamelContextExtension().getCreateProcessor();
                    String id = lifecycleHandler.track(this, routeId, processorId);
                    if (id != null && ownRouteId) {
                        // a route id given in the uri is shared by all the endpoints that use it, and each of them
                        // replaces the route of the previous one: such a route is left alone
                        createdRoute = getCamelContext().getRoute(id);
                    }
                }

                @Override
                AsyncCallback completingCallback(Exchange exchange, AsyncCallback callback) {
                    if (createdRoute == null) {
                        return callback;
                    }
                    return doneSync -> {
                        completing = exchange;
                        try {
                            callback.done(doneSync);
                        } finally {
                            completing = null;
                        }
                    };
                }

                @Override
                protected void doShutdown() throws Exception {
                    // An endpoint used once and discarded (toD or recipientList with cacheSize=-1) is shut down after
                    // use: remove the route it created, otherwise every call keeps one more route for ever.
                    // The endpoint of a route that consumes from a Kamelet (from("kamelet:my-source")) is shut down
                    // as well when that route is removed: when that route was added while the context runs, the
                    // Kamelet's route is removed with it.
                    Route route = createdRoute;
                    createdRoute = null;
                    if (route != null) {
                        removeCreatedRoute(route, completing);
                    }
                    super.doShutdown();
                }
            };

            // forward component properties
            endpoint.setNoErrorHandler(noErrorHandler);
            endpoint.setBlock(block);
            endpoint.setTimeout(timeout);
            // endpoint specific location
            endpoint.setLocation(loc);
            endpoint.setAdvancedOptions(advancedOptions);

            // set and remove endpoint specific properties
            setProperties(endpoint, parameters);

            Map<String, Object> kameletProperties = new HashMap<>();

            //
            // Load properties from the component configuration. Template and route specific properties
            // can be set through properties, as an example:
            //
            //     camel.component.kamelet.template-properties[templateId].key = val
            //     camel.component.kamelet.route-properties[routeId].key = val
            //
            if (templateProperties != null) {
                Properties props = templateProperties.get(templateId);
                if (props != null) {
                    props.stringPropertyNames().forEach(name -> kameletProperties.put(name, props.get(name)));
                }
            }
            if (routeProperties != null) {
                Properties props = routeProperties.get(routeId);
                if (props != null) {
                    props.stringPropertyNames().forEach(name -> kameletProperties.put(name, props.get(name)));
                }
            }

            //
            // We can't mix configuration styles so if properties are not configured through the component,
            // then fallback to the old - deprecated - style.
            //
            if (kameletProperties.isEmpty()) {
                //
                // The properties for the kamelets are determined by global properties
                // and local endpoint parameters,
                //
                // Global parameters are loaded in the following order:
                //
                //   camel.kamelet." + templateId
                //   camel.kamelet." + templateId + "." routeId
                //
                Kamelet.extractKameletProperties(getCamelContext(), kameletProperties, templateId, routeId);
            }

            //
            // Look for OS environment variables that match the Kamelet properties
            // Environment variables are loaded in the following order:
            //
            //   CAMEL_KAMELET_" + templateId
            //   CAMEL_KAMELET_" + templateId + "_" routeId
            //
            Kamelet.extractKameletEnvironmentVariables(kameletProperties, templateId, routeId);

            // Snapshot remaining URI parameters as advanced-option candidates when enabled. Template
            // parameters are filtered later in templateToRoute, after the template is loaded. Only URI
            // leftovers are candidates, not camel.kamelet.* or environment properties. The values are
            // taken as written in the URI (percent-encoding and RAW(...) kept), so they can be appended
            // to the wrapped endpoint URI as is.
            Map<String, Object> advancedParamOptions = new LinkedHashMap<>();
            if (endpoint.isAdvancedOptions()) {
                parameters.keySet().forEach(key -> advancedParamOptions.put(key, rawParameters.get(key)));
            }

            //
            // Uri params have the highest precedence
            //
            kameletProperties.putAll(parameters);

            //
            // And finally we have some specific properties that cannot be changed by the user.
            //
            kameletProperties.put(PARAM_TEMPLATE_ID, templateId);
            kameletProperties.put(PARAM_ROUTE_ID, routeId);
            kameletProperties.put(PARAM_UUID, uuid);
            kameletProperties.put(NO_ERROR_HANDLER, endpoint.isNoErrorHandler());
            kameletProperties.put(BRIDGE_ERROR_HANDLER, endpoint.isBridgeErrorHandler());

            // Remove any route-scoped properties that might have used the internal prefix
            // to prevent users from bypassing the intended separation
            kameletProperties.keySet().removeIf(key -> key.startsWith("internal.advanced."));

            // Pass the original option map as a single value so inner keys keep the user-supplied spelling
            if (!advancedParamOptions.isEmpty()) {
                kameletProperties.put(ADVANCED_OPTIONS, advancedParamOptions);
            }

            // set kamelet specific properties
            endpoint.setKameletProperties(kameletProperties);

            //
            // Add a custom converter to convert a RouteTemplateDefinition to a RouteDefinition
            // and make sure consumer URIs are unique.
            //
            ((ModelCamelContext) getCamelContext()).addRouteTemplateDefinitionConverter(
                    templateId,
                    Kamelet::templateToRoute);
        }

        return endpoint;
    }

    @Override
    protected boolean resolveRawParameterValues() {
        return false;
    }

    private static Map<String, Object> copyParameters(Map<String, Object> parameters) {
        // resolveRawParameterValues updates repeated parameters (lists) in place, so copy those too
        Map<String, Object> answer = new LinkedHashMap<>();
        parameters.forEach((key, value) -> answer.put(key, value instanceof List<?> list ? new ArrayList<>(list) : value));
        return answer;
    }

    public boolean isNoErrorHandler() {
        return noErrorHandler;
    }

    /**
     * Whether kamelets should use error handling or not. By default, the Kamelet uses the same error handler as from
     * the calling route. This means that if the calling route has error handling that performs retries, or routing to a
     * dead letter channel, then the kamelet route will use this also.
     * <p>
     * This can be turned off by setting this option to true. If off then the kamelet route is not using error handling,
     * and any exception thrown will for source kamelets be logged by the consumer, and the sink/action kamelets will
     * fail processing.
     */
    public void setNoErrorHandler(boolean noErrorHandler) {
        this.noErrorHandler = noErrorHandler;
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a kamelet endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Map<String, Properties> getTemplateProperties() {
        return templateProperties;
    }

    /**
     * Set template local parameters.
     */
    public void setTemplateProperties(Map<String, Properties> templateProperties) {
        this.templateProperties = templateProperties;
    }

    public Map<String, Properties> getRouteProperties() {
        return routeProperties;
    }

    /**
     * Set route local parameters.
     */
    public void setRouteProperties(Map<String, Properties> routeProperties) {
        this.routeProperties = routeProperties;
    }

    public String getLocation() {
        return location;
    }

    /**
     * The location(s) of the Kamelets on the file system. Multiple locations can be set separated by comma.
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public RouteTemplateLoaderListener getRouteTemplateLoaderListener() {
        return routeTemplateLoaderListener;
    }

    /**
     * To plugin a custom listener for when the Kamelet component is loading Kamelets from external resources.
     */
    public void setRouteTemplateLoaderListener(RouteTemplateLoaderListener routeTemplateLoaderListener) {
        this.routeTemplateLoaderListener = routeTemplateLoaderListener;
    }

    int getStateCounter() {
        return stateCounter;
    }

    public boolean isAdvancedOptions() {
        return advancedOptions;
    }

    /**
     * Whether to forward extra URI parameters to the endpoint wrapped by the Kamelet. When enabled, parameters that are
     * not Kamelet template properties or reserved Kamelet options are appended to the wrapped source or sink endpoint.
     * Disabled by default.
     */
    public void setAdvancedOptions(boolean advancedOptions) {
        this.advancedOptions = advancedOptions;
    }

    public void addConsumer(String key, KameletConsumer consumer) {
        consumersLock.lock();
        try {
            if (consumers.putIfAbsent(key, consumer) != null) {
                throw new IllegalArgumentException(
                        "Cannot add a 2nd consumer to the same endpoint: " + key
                                                   + ". KameletEndpoint only allows one consumer.");
            }
            // state changed so inc counter
            stateCounter++;
            consumersCondition.signalAll();
        } finally {
            consumersLock.unlock();
        }
    }

    public void removeConsumer(String key, KameletConsumer consumer) {
        consumersLock.lock();
        try {
            consumers.remove(key, consumer);
            // state changed so inc counter
            stateCounter++;
            consumersCondition.signalAll();
        } finally {
            consumersLock.unlock();
        }
    }

    protected KameletConsumer getConsumer(String key, boolean block, long timeout) throws InterruptedException {
        consumersLock.lock();
        try {
            KameletConsumer answer = consumers.get(key);
            if (answer == null && block) {
                StopWatch watch = new StopWatch();
                for (;;) {
                    answer = consumers.get(key);
                    if (answer != null) {
                        break;
                    }
                    long rem = timeout - watch.taken();
                    if (rem <= 0) {
                        break;
                    }
                    consumersCondition.await(rem, TimeUnit.MILLISECONDS);
                }
            }
            return answer;
        } finally {
            consumersLock.unlock();
        }
    }

    @Override
    protected void doInit() throws Exception {
        getCamelContext().addLifecycleStrategy(lifecycleHandler);

        if (getCamelContext().isRunAllowed()) {
            lifecycleHandler.setInitialized(true);
        }

        super.doInit();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ScheduledExecutorService executor = routeRemover;
        if (executor == null || executor.isShutdown()) {
            // the thread is created on first use
            routeRemover = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                    "KameletRouteRemover");
        }
    }

    /**
     * Removes the route a Kamelet endpoint created from its template while the context runs, when that endpoint is shut
     * down (it is not shut down while the context runs unless it was used once and discarded, or its route consumed
     * from the Kamelet and was removed).
     * <p/>
     * The route is stopped and removed on a thread of this component: stopping a route holds the lock of the
     * CamelContext while the route shuts down gracefully. The calling route can continue on a thread that the Kamelet's
     * route owns (threads(), an asynchronous producer), and stopping the route shuts that thread pool down, which
     * interrupts that thread. So the removal waits:
     * <ul>
     * <li>until the exchange whose call is completing on this thread is done, when the endpoint is shut down inside the
     * producer callback (toD);</li>
     * <li>and until no thread of the thread pools that the route's processors created (threads(), and the other EIPs
     * with an executor service) runs a task. This covers an endpoint that is shut down where the exchange of the
     * calling route is not known: recipientList with cacheSize=-1 (the endpoint is shut down when the producer is
     * released, after the call), routingSlip with cacheSize=-1 (its copy of the exchange has no unit of work) and
     * pollEnrich. The route is used only by the endpoint that created it, so its thread pools stay idle once the
     * calling route has left them.</li>
     * </ul>
     * Not covered: a thread pool that a component of the route creates for itself (such as the worker pool of an
     * asynchronous Kafka producer, which waits for its running tasks when it stops, up to its shutdown timeout), and an
     * executor service that is not a {@link ThreadPoolExecutor} (virtual threads), which the removal cannot tell idle.
     *
     * @param route    the route the endpoint created
     * @param exchange the exchange whose call of the endpoint is completing on this thread, or {@code null}
     */
    private void removeCreatedRoute(Route route, Exchange exchange) {
        if (exchange != null && exchange.getUnitOfWork() != null) {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange done) {
                    submitRemoveRoute(route, 0);
                }
            });
        } else {
            submitRemoveRoute(route, 0);
        }
    }

    private void submitRemoveRoute(Route route, long delayMillis) {
        ScheduledExecutorService executor = routeRemover;
        if (executor == null) {
            // the context removes all the routes when it stops
            return;
        }
        try {
            executor.schedule(() -> doRemoveRoute(route), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // the component is shut down, and the context removes all the routes when it stops
            LOG.debug("Cannot remove route with id={} as the component is shut down", route.getRouteId());
        }
    }

    private void doRemoveRoute(Route route) {
        CamelContext context = getCamelContext();
        String routeId = route.getRouteId();
        if (!context.isStarted() || context.isStopping() || context.getRoute(routeId) != route) {
            // the context removes all the routes when it stops, and a route that is gone or was replaced is not ours
            return;
        }
        if (isThreadPoolActive(routeId)) {
            // the calling route may still run on a thread of the route's thread pools: stopping the route now would
            // shut the pool down under it
            LOG.trace("Route with id={} still has an active thread: removing it later", routeId);
            submitRemoveRoute(route, ROUTE_REMOVAL_RETRY_MILLIS);
            return;
        }
        try {
            LOG.debug("Removing route with id={} created by a Kamelet endpoint that is shut down", routeId);
            context.getRouteController().stopRoute(routeId);
            context.removeRoute(routeId);
        } catch (Exception e) {
            LOG.warn("Error removing route {} created by a Kamelet endpoint that is shut down. This exception is ignored.",
                    routeId, e);
        }
    }

    private boolean isThreadPoolActive(String routeId) {
        List<ThreadPoolExecutor> pools = routeThreadPools.get(routeId);
        if (pools != null) {
            for (ThreadPoolExecutor pool : pools) {
                if (pool.getActiveCount() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doShutdown() throws Exception {
        getCamelContext().getLifecycleStrategies().remove(lifecycleHandler);

        ServiceHelper.stopAndShutdownService(consumers);
        consumers.clear();
        kameletEips.clear();
        ScheduledExecutorService executor = routeRemover;
        routeRemover = null;
        if (executor != null) {
            // the context removes all the routes when it stops, so a removal that has not started is not needed
            getCamelContext().getExecutorServiceManager().shutdown(executor);
        }
        routeThreadPools.clear();
        super.doShutdown();
    }

    /*
     * This LifecycleHandler is used to keep track of created kamelet endpoints during startup as
     * we need to defer create routes from templates until camel context has finished loading
     * all routes and whatnot.
     *
     * Once the camel context is initialized all the endpoint tracked by this LifecycleHandler will
     * be used to create routes from templates.
     */
    private class LifecycleHandler extends LifecycleStrategySupport {

        record Tuple(KameletEndpoint endpoint, String parentRouteId, String parentProcessorId) {
        }

        private final List<Tuple> endpoints;
        private final AtomicBoolean initialized;

        public LifecycleHandler() {
            this.endpoints = new ArrayList<>();
            this.initialized = new AtomicBoolean();
        }

        public String createRouteForEndpoint(KameletEndpoint endpoint, String parentRouteId, String parentProcessorId)
                throws Exception {
            // creating dynamic routes from kamelets should not happen concurrently so we use locking
            lock.lock();
            try {
                return doCreateRouteForEndpoint(endpoint, parentRouteId, parentProcessorId);
            } finally {
                lock.unlock();
            }
        }

        protected String doCreateRouteForEndpoint(KameletEndpoint endpoint, String parentRouteId, String parentProcessorId)
                throws Exception {
            final String templateId = endpoint.getTemplateId();
            final String loc = endpoint.getLocation() != null ? endpoint.getLocation() : getLocation();
            // creating a Kamelet's route creates the Kamelets its template uses, on this thread: one that is already
            // being created is a cycle, which created routes until the stack overflowed (CAMEL-25387)
            Deque<String> creating = CREATING.get();
            if (creating.contains(templateId)) {
                List<String> cycle = new ArrayList<>(creating);
                Collections.reverse(cycle);
                cycle = cycle.subList(cycle.indexOf(templateId), cycle.size());
                cycle.add(templateId);
                String msg = cycle.size() == 2
                        ? "Kamelet " + templateId + " uses itself: its template uses kamelet:" + templateId
                          + ", which creates the Kamelet again while creating it. The template of an action or a sink"
                          + " starts from kamelet:source, the message the route sends to the Kamelet"
                        : "Kamelets use each other in a cycle: " + String.join(" -> ", cycle)
                          + ". Creating one creates the next, endlessly";
                throw new FailedToCreateKameletException(templateId, loc, new IllegalArgumentException(msg));
            }
            creating.push(templateId);
            try {
                return doCreateRouteForEndpoint(endpoint, parentRouteId, parentProcessorId, templateId, loc);
            } finally {
                creating.pop();
                if (creating.isEmpty()) {
                    CREATING.remove();
                }
            }
        }

        private String doCreateRouteForEndpoint(
                KameletEndpoint endpoint, String parentRouteId, String parentProcessorId, String templateId, String loc)
                throws Exception {

            final ModelCamelContext context = (ModelCamelContext) getCamelContext();
            final String routeId = endpoint.getRouteId();
            final String uuid = (String) endpoint.getKameletProperties().get(PARAM_UUID);

            if (context.getRouteTemplateDefinition(templateId) == null && loc != null) {
                LOG.debug("Loading route template={} from {}", templateId, loc);
                RouteTemplateHelper.loadRouteTemplateFromLocation(getCamelContext(), routeTemplateLoaderListener, templateId,
                        loc);
            }

            LOG.debug("Creating route from template={} and id={}", templateId, routeId);
            try {
                String id = context.addRouteFromKamelet(routeId, templateId, uuid, parentRouteId, parentProcessorId,
                        endpoint.getKameletProperties());
                RouteDefinition def = context.getRouteDefinition(id);

                // start the route if it was not already materialized (avoid duplicate Route instances on reload)
                ServiceStatus status = context.getRouteController().getRouteStatus(id);
                if (status == null) {
                    context.startRouteDefinitions(Collections.singletonList(def));
                }

                LOG.debug("Route with id={} created from template={}", id, templateId);
                return id;
            } catch (Exception e) {
                throw new FailedToCreateKameletException(templateId, loc, e);
            }
        }

        @Override
        public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
            if (this.initialized.compareAndSet(false, true)) {
                for (Tuple tuple : endpoints) {
                    try {
                        createRouteForEndpoint(tuple.endpoint, tuple.parentRouteId, tuple.parentProcessorId);
                    } catch (Exception e) {
                        throw new VetoCamelContextStartException(
                                "Failure creating route from template: " + tuple.endpoint.getTemplateId(), e, context);
                    }
                }

                endpoints.clear();
            }
        }

        @Override
        public void onThreadPoolAdd(
                CamelContext camelContext, ThreadPoolExecutor threadPool, String id, String sourceId, String routeId,
                String threadPoolProfileId) {
            if (routeId != null) {
                routeThreadPools.computeIfAbsent(routeId, k -> new CopyOnWriteArrayList<>()).add(threadPool);
            }
        }

        @Override
        public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
            routeThreadPools.values().removeIf(pools -> pools.remove(threadPool) && pools.isEmpty());
        }

        public void setInitialized(boolean initialized) {
            this.initialized.set(initialized);
        }

        /**
         * Creates the route of the endpoint now if the context is initialized, or when it is.
         *
         * @return the id of the route created now, or {@code null} if it is created later
         */
        public String track(KameletEndpoint endpoint, String parentRouteId, String parentProcessorId) {
            if (this.initialized.get()) {
                try {
                    return createRouteForEndpoint(endpoint, parentRouteId, parentProcessorId);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                LOG.debug("Tracking route template={} and id={}", endpoint.getTemplateId(), endpoint.getRouteId());
                this.endpoints.add(new Tuple(endpoint, parentRouteId, parentProcessorId));
                return null;
            }
        }
    }

}
