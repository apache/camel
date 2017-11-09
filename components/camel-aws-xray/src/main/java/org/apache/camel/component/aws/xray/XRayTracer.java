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
package org.apache.camel.component.aws.xray;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.*;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceID;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.StaticService;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To use AWS XRay with Camel setup this {@link XRayTracer} in your Camel application.
 * <p/>
 * This class uses a {@link org.apache.camel.spi.RoutePolicy} as well as a {@link
 * org.apache.camel.spi.EventNotifier} internally to manage the creation and termination of AWS XRay
 * {@link Segment Segments} and {@link Subsegment Subsegments} once an exchange was created,
 * forwarded or closed in order to allow monitoring the lifetime metrics of the exchange.
 * <p/>
 * A {@link InterceptStrategy} is used in order to track invocations and durations of EIP patterns
 * used in processed routes. If no strategy is passed while configuration via {@link
 * #setTracingStrategy(InterceptStrategy)}, a {@link NoopTracingStrategy} will be used by default
 * which will not monitor any invocations at all.
 * <p/>
 * By default every invoked route will be tracked by AWS XRay. If certain routes shell not be
 * tracked {@link #addExcludePattern(String)} and {@link #setExcludePatterns(Set)} can be used to
 * provide the <em>routeId</em> of the routes to exclude from monitoring.
 */
public class XRayTracer extends ServiceSupport implements RoutePolicyFactory, StaticService, CamelContextAware {

    /** Header value kept in the message of the exchange **/
    public static final String XRAY_TRACE_ID = "Camel-AWS-XRay-Trace-ID";

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static Map<String, SegmentDecorator> decorators = new HashMap<>();

    /** Exchange property for passing a segment between threads **/
    private static final String CURRENT_SEGMENT = "CAMEL_PROPERTY_AWS_XRAY_CURRENT_SEGMENT";

    private final XRayEventNotifier eventNotifier = new XRayEventNotifier();
    private CamelContext camelContext;

    private Set<String> excludePatterns = new HashSet<>();
    private InterceptStrategy tracingStrategy;

    static {
        ServiceLoader.load(SegmentDecorator.class).forEach(d -> {
            SegmentDecorator existing = decorators.get(d.getComponent());
            // Add segment decorator only if no existing decorator for the component exists yet or if we have have a
            // derived one. This allows custom decorators to be added if they extend the standard decorators
            if (existing == null || existing.getClass().isInstance(d)) {
                LOG.trace("Adding segment decorator {}", d.getComponent());
                decorators.put(d.getComponent(), d);
            }
        });
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition route) {
        init(camelContext);
        return new XRayRoutePolicy(routeId);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        if (!camelContext.getRoutePolicyFactories().contains(this)) {
            camelContext.addRoutePolicyFactory(this);
        }

        if (null == tracingStrategy) {
            LOG.info("No tracing strategy available. Defaulting to no-op strategy");
            tracingStrategy = new NoopTracingStrategy();
        }

        camelContext.addInterceptStrategy(tracingStrategy);

        LOG.debug("Starting XRay tracer");
    }

    @Override
    protected void doStop() throws Exception {
        // stop event notifier
        camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
        ServiceHelper.stopAndShutdownService(eventNotifier);

        camelContext.getRoutePolicyFactories().remove(this);
        LOG.debug("XRay tracer stopped");
    }

    /**
     * Initializes this AWS XRay tracer implementation as service within the Camel environment.
     *
     * @param camelContext The context to register this tracer as service with
     */
    public void init(CamelContext camelContext) {
        if (!camelContext.hasService(this)) {
            try {
                LOG.debug("Initializing XRay tracer");
                // start this service eager so we init before Camel is starting up
                camelContext.addService(this, true, true);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    /**
     * Returns the currently used tracing strategy which is responsible for tracking invoked EIP or
     * beans.
     *
     * @return The currently used tracing strategy
     */
    public InterceptStrategy getTracingStrategy() {
        return tracingStrategy;
    }

    /**
     * Specifies the instance responsible for tracking invoked EIP and beans with AWS XRay.
     *
     * @param tracingStrategy The instance which tracks invoked EIP and beans
     */
    public void setTracingStrategy(InterceptStrategy tracingStrategy) {
        this.tracingStrategy = tracingStrategy;
    }

    /**
     * Returns the set of currently excluded routes. Any route ID specified in the returned set will
     * not be monitored by this AWS XRay tracer implementation.
     *
     * @return The IDs of the currently excluded routes for which no tracking will be performed
     */
    public Set<String> getExcludePatterns() {
        return this.excludePatterns;
    }

    /**
     * Excludes all of the routes matching any of the contained routeIds within the given argument
     * from tracking by this tracer implementation. Excluded routes will not appear within the AWS
     * XRay monitoring.
     *
     * @param excludePatterns A set of routeIds which should not be tracked by this tracer
     */
    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    /**
     * Adds an exclude pattern that will disable tracing for Camel messages that matches the pattern.
     *
     * @param pattern The pattern such as route id, endpoint url
     */
    public void addExcludePattern(String pattern) {
        excludePatterns.add(pattern);
    }

    private boolean isExcluded(String routeId) {
        // check for a defined routeId
        if (!excludePatterns.isEmpty()) {
            for (String pattern : excludePatterns) {
                if (pattern.equals(routeId)) {
                    LOG.debug("Ignoring route with ID {}", routeId);
                    return true;
                }
            }
        }
        return false;
    }

    protected SegmentDecorator getSegmentDecorator(Endpoint endpoint) {
        SegmentDecorator sd = decorators.get(URI.create(endpoint.getEndpointUri()).getScheme());
        if (null == sd) {
            return SegmentDecorator.DEFAULT;
        }
        return sd;
    }

    /**
     * Custom camel event handler that will create a new {@link Subsegment XRay subsegment} in case
     * the current exchange is forwarded via <code>.to(someEndpoint)</code> to some endpoint and
     * accordingly closes the subsegment if the execution returns.
     * <p/>
     * Note that AWS XRay is designed to manage {@link Segment segments} and {@link Subsegment
     * subsegments} within a {@link ThreadLocal} context. Forwarding the exchange to a <em>SEDA</em>
     * endpoint will thus copy over the exchange to a new thread, though any available segment
     * information collected by AWS XRay will not be available within that new thread!
     * <p/>
     * As  {@link ExchangeSendingEvent} and {@link ExchangeSentEvent} both are executed within the
     * invoking thread (in contrast to {@link org.apache.camel.management.event.ExchangeCreatedEvent
     * ExchangeCreatedEvent} and {@link org.apache.camel.management.event.ExchangeCompletedEvent
     * ExchangeCompletedEvent} which both run in the context of the spawned thread), adding further
     * subsegments by this {@link org.apache.camel.spi.EventNotifier EventNotifier} implementation
     * should be safe.
     */
    private final class XRayEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(EventObject event) throws Exception {

            if (event instanceof ExchangeSendingEvent) {
                ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
                LOG.trace("-> {} - target: {} (routeId: {})",
                        event.getClass().getSimpleName(), ese.getEndpoint(),
                        ese.getExchange().getFromRouteId());

                if (Thread.currentThread().getName().contains("Multicast")) {
                    // copy the segment from the exchange to the thread (local) context
                    Segment segment = (Segment) ese.getExchange().getProperty(CURRENT_SEGMENT);
                    LOG.trace("Copying over segment {}/{} from exchange received from {} to exchange processing {}",
                            segment.getId(), segment.getName(), ese.getExchange().getFromEndpoint(),
                            ese.getEndpoint());
                    AWSXRay.setTraceEntity(segment);
                }

                SegmentDecorator sd = getSegmentDecorator(ese.getEndpoint());
                if (!sd.newSegment()) {
                    return;
                }

                if (AWSXRay.getCurrentSegmentOptional().isPresent()) {
                    // AWS XRay does only allow a certain set of characters to appear within a name
                    // Allowed characters: a-z, A-Z, 0-9, _, ., :, /, %, &, #, =, +, \, -, @
                    String name = sd.getOperationName(ese.getExchange(), ese.getEndpoint());
                    if (sd.getComponent() != null) {
                        name = sd.getComponent() + ":" + name;
                    }
                    Subsegment subsegment = AWSXRay.beginSubsegment(sanitizeName(name));
                    sd.pre(subsegment, ese.getExchange(), ese.getEndpoint());
                    LOG.trace("Creating new subsegment with ID {} and name {}",
                            subsegment.getId(), subsegment.getName());
                } else {
                    LOG.trace("Ignoring creation of XRay subsegment as no segment exists in the current thread");
                }

            } else if (event instanceof ExchangeSentEvent) {
                ExchangeSentEvent ese = (ExchangeSentEvent) event;
                LOG.trace("-> {} - target: {} (routeId: {})",
                        event.getClass().getSimpleName(), ese.getEndpoint(), ese.getExchange().getFromRouteId());

                SegmentDecorator sd = getSegmentDecorator(ese.getEndpoint());

                if (AWSXRay.getCurrentSubsegmentOptional().isPresent()) {
                    Subsegment subsegment = AWSXRay.getCurrentSubsegment();
                    sd.post(subsegment, ese.getExchange(), ese.getEndpoint());
                    subsegment.close();
                    LOG.trace("Closing down subsegment with ID {} and name {}",
                            subsegment.getId(), subsegment.getName());
                }
            } else {
                LOG.trace("Received event {} from source {}", event, event.getSource());
            }
        }

        @Override
        public boolean isEnabled(EventObject event) {
            // listen for either when an exchange invoked an other endpoint
            return event instanceof ExchangeSendingEvent
                    || event instanceof ExchangeSentEvent;
        }

        @Override
        public String toString() {
            return "XRayEventNotifier";
        }
    }

    /**
     * A custom {@link org.apache.camel.spi.RoutePolicy RoutePolicy} implementation that will create
     * a new AWS XRay {@link Segment} once a new exchange is being created and the current thread
     * does not know of an active segment yet. In case the exchange was forwarded within the same
     * thread (i.e. by forwarding to a direct endpoint via <code>.to("direct:...)</code>) and a
     * previous exchange already created a {@link Segment} this policy will add a new {@link
     * Subsegment} for the created exchange to the trace.
     * <p/>
     * This policy will also manage the termination of created {@link Segment Segments} and {@link
     * Subsegment Subsegments}.
     * <p/>
     * As AWS XRay is designed to manage {@link Segment Segments} in a {@link ThreadLocal} context
     * this policy will create a new segment for each forward to a new thread i.e. by sending the
     * exchange to a <em>SEDA</em> endpoint.
     */
    private final class XRayRoutePolicy extends RoutePolicySupport {

        private String routeId;

        XRayRoutePolicy(String routeId) {
            this.routeId = routeId;
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            // kicks in after a seda-thread was created. The new thread has the control
            if (isExcluded(route.getId())) {
                return;
            }

            LOG.trace("=> RoutePolicy-Begin: Route: {} - RouteId: {}", routeId, route.getId());

            TraceID traceID;
            if (exchange.getIn().getHeaders().containsKey(XRAY_TRACE_ID)) {
                traceID = TraceID.fromString(exchange.getIn().getHeader(XRAY_TRACE_ID, String.class));
            } else {
                traceID = new TraceID();
                exchange.getIn().setHeader(XRAY_TRACE_ID, traceID.toString());
            }

            SegmentDecorator sd = getSegmentDecorator(route.getEndpoint());
            if (!AWSXRay.getCurrentSegmentOptional().isPresent()) {
                Segment segment = AWSXRay.beginSegment(sanitizeName(route.getId()));
                segment.setTraceId(traceID);
                sd.pre(segment, exchange, route.getEndpoint());
                LOG.trace("Created new XRay segment {} with name {}",
                        segment.getId(), segment.getName());
                exchange.setProperty(CURRENT_SEGMENT, segment);
            } else {
                Subsegment subsegment = AWSXRay.beginSubsegment(route.getId());
                sd.pre(subsegment, exchange, route.getEndpoint());
                LOG.trace("Created new XRay subsegment {} with name {}",
                        subsegment.getId(), subsegment.getName());
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            // kicks in before the seda-thread is terminated. Control is still in the seda-thread
            if (isExcluded(route.getId())) {
                return;
            }

            LOG.trace("=> RoutePolicy-Done: Route: {} - RouteId: {}", routeId, route.getId());

            SegmentDecorator sd = getSegmentDecorator(route.getEndpoint());
            if (AWSXRay.getCurrentSubsegmentOptional().isPresent()) {
                Subsegment subsegment = AWSXRay.getCurrentSubsegment();
                sd.post(subsegment, exchange, route.getEndpoint());
                subsegment.close();
                LOG.trace("Closing down Subsegment {} with name {}",
                        subsegment.getId(), subsegment.getName());
            } else if (AWSXRay.getCurrentSegmentOptional().isPresent()) {
                Segment segment = AWSXRay.getCurrentSegment();
                sd.post(segment, exchange, route.getEndpoint());
                segment.close();
                LOG.trace("Closing down Segment {} with name {}",
                        segment.getId(), segment.getName());
            }
        }

        @Override
        public String toString() {
            return "XRayRoutePolicy";
        }
    }

    /**
     * Removes invalid characters from AWS XRay (sub-)segment names and replaces the invalid characters with an
     * underscore character.
     *
     * @param name The name to assign to an AWS XRay (sub-)segment
     * @return The sanitized name of the (sub-)segment
     */
    public static String sanitizeName(String name) {
        // Allowed characters: a-z, A-Z, 0-9, _, ., :, /, %, &, #, =, +, \, -, @
        // \w = a-zA-Z0-9_
        return name.replaceAll("[^\\w.:/%&#=+\\-@]", "_");
    }
}