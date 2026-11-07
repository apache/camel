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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The timeline of the integration: per second, for the last minutes, what each route did, the errors by exception, and
 * a few exchanges of each second with the calls they made. It records from the start of the console, so the history is
 * there when a tool first looks.
 */
@org.apache.camel.spi.annotations.DevConsole(name = "timeline", displayName = "Camel Timeline",
                                             description = "Per second, what each route did, the errors, and the failed and slowest exchanges")
@Configurer(extended = true)
public class TimelineDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(TimelineDevConsole.class);

    /** The exchange property camel-chaos marks a faulted exchange with, with the id of the rule. */
    static final String CHAOS_RULE = "CamelChaosRule";

    public record Send(
            @Metadata(description = "The endpoint URI") String endpointUri,
            @Metadata(description = "Elapsed time of the call in milliseconds") long elapsed) {
    }

    public record RouteSeries(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The from endpoint URI (only present when known)") String from,
            @Metadata(description = "Exchanges completed per second, not counting the recovered and handled ones") List<Long> completed,
            @Metadata(description = "Exchanges per second that completed after a redelivery in this route") List<Long> recovered,
            @Metadata(description = "Exchanges per second whose failure the error handler handled") List<Long> handled,
            @Metadata(description = "Exchanges failed per second") List<Long> failed,
            @Metadata(description = "Exchanges in flight at the end of each second") List<Long> inflight,
            @Metadata(description = "Mean processing time per second in milliseconds") List<Long> meanMillis,
            @Metadata(description = "p95 processing time per second in milliseconds, of the exchanges that came into this route") List<Long> p95Millis,
            @Metadata(description = "Max processing time per second in milliseconds, of the exchanges that came into this route") List<Long> maxMillis,
            @Metadata(description = "Redelivery attempts per second in this route") List<Long> redeliveryAttempts,
            @Metadata(description = "Per second 1 when a camel-chaos rule had the route down (stall or a lifecycle fault), else 0 (only present with camel-chaos)") List<Long> down) {
    }

    public record RouteEvent(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "What happened to the route: started, stopped, reloaded, removed or restartFailed") String event,
            @Metadata(description = "The epoch second it happened") long second) {
    }

    public record ChaosSeries(
            @Metadata(description = "The id of the camel-chaos rule") String id,
            @Metadata(description = "The rule in its one-line text form") String rule,
            @Metadata(description = "The faults the rule fired per second") List<Long> faults) {
    }

    public record ErrorSeries(
            @Metadata(description = "The route ID where the exchange failed") String routeId,
            @Metadata(description = "The exception class name") String exception,
            @Metadata(description = "The HTTP status code of the exception (only present when known)") Integer statusCode,
            @Metadata(description = "The camel-chaos rule that made the error (only present for a chaos fault)") String chaosRule,
            @Metadata(description = "Errors per second") List<Long> counts) {
    }

    public record TimelineExchange(
            @Metadata(description = "The exchange ID") String exchangeId,
            @Metadata(description = "The route ID the exchange came in") String routeId,
            @Metadata(description = "The from endpoint URI (only present when known)") String fromEndpointUri,
            @Metadata(description = "Epoch time in milliseconds the exchange was created") long timestamp,
            @Metadata(description = "Elapsed time in milliseconds") long elapsed,
            @Metadata(description = "Whether the exchange failed") boolean failed,
            @Metadata(description = "Whether the error handler handled a failure of the exchange") boolean handled,
            @Metadata(description = "Whether the exchange was redelivered") boolean redelivered,
            @Metadata(description = "The exception message (only present when the exchange failed, or when it recovered: the failure it was redelivered for)") String exception,
            @Metadata(description = "The camel-chaos rule that faulted the exchange (only present when one did)") String chaosRule,
            @Metadata(description = "The remote endpoints the exchange was sent to (only present when any)") List<Send> endpointSends) {
    }

    public record Response(
            @Metadata(description = "Epoch second of the last second (the oldest is end - seconds + 1)") long end,
            @Metadata(description = "The number of seconds of each series, oldest first") int seconds,
            @Metadata(description = "Exchanges done per second, in the routes they came in") List<Long> exchangesDone,
            @Metadata(description = "The routes") List<RouteSeries> routes,
            @Metadata(description = "The errors by route and exception (only present when there are any)") List<ErrorSeries> errors,
            @Metadata(description = "The faults per second of each camel-chaos rule that fired in the seconds (only present when one did)") List<ChaosSeries> chaos,
            @Metadata(description = "What happened to the routes in the seconds: started, stopped, reloaded, removed (only present when anything did)") List<RouteEvent> routeEvents,
            @Metadata(description = "Per second the failed, handled and recovered exchanges (up to 10) and the 3 slowest, oldest first (only present when asked for)") List<TimelineExchange> exchanges) {
    }

    @Metadata(label = "query", description = "The number of seconds, up to the recorded seconds (the default)",
              javaType = "java.lang.Integer")
    public static final String SECONDS = "seconds";

    @Metadata(label = "query", description = "Filter by route id (pattern)", javaType = "java.lang.String")
    public static final String ROUTE_ID = "routeId";

    @Metadata(label = "query", description = "Whether to include the kept exchanges", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String EXCHANGES = "exchanges";

    @Metadata(defaultValue = "300", description = "The number of seconds to record (between 60 and 3600)")
    private int seconds = 300;

    private volatile TimelineRecorder recorder;
    private final TimelineEventNotifier listener = new TimelineEventNotifier();
    private ScheduledExecutorService executor;

    public TimelineDevConsole() {
        super("camel", "timeline", "Camel Timeline",
              "Per second, what each route did, the errors, and the failed and slowest exchanges");
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        if (seconds < 60 || seconds > 3600) {
            throw new IllegalArgumentException("The seconds must be between 60 and 3600, was: " + seconds);
        }
        if (recorder != null && recorder.getSeconds() != seconds) {
            // Camel Main configures the console after the registry has started it: start the recording again
            recorder = new TimelineRecorder(seconds);
        }
        this.seconds = seconds;
    }

    TimelineRecorder getRecorder() {
        return recorder;
    }

    @Override
    protected void doInit() throws Exception {
        recorder = new TimelineRecorder(seconds);
    }

    @Override
    protected void doStart() throws Exception {
        getCamelContext().getManagementStrategy().addEventNotifier(listener);
        executor = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "Timeline");
        // the counters as they are now, so the first second counts from here
        sample();
        long now = System.currentTimeMillis();
        // just after each whole second
        executor.scheduleAtFixedRate(this::sample, 1000 - now % 1000 + 5, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() throws Exception {
        getCamelContext().getManagementStrategy().removeEventNotifier(listener);
        if (executor != null) {
            getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    void sample() {
        long now = System.currentTimeMillis();
        recorder.sample(now, counters());
        sampleChaos(now);
    }

    /**
     * With camel-chaos, the faults per second of its rules and the routes they have down, from its dev console (so this
     * console does not depend on camel-chaos).
     */
    private void sampleChaos(long now) {
        DevConsoleRegistry registry = getCamelContext().getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        DevConsole chaos = registry != null ? registry.resolveById("chaos") : null;
        if (chaos == null) {
            return;
        }
        Object json;
        try {
            json = chaos.call(DevConsole.MediaType.JSON, Map.of("report", "false"));
        } catch (Exception e) {
            LOG.debug("Failed to sample the chaos dev console", e);
            return;
        }
        if (json instanceof Map<?, ?> root && root.get("rules") instanceof List<?> rules) {
            Map<String, long[]> faults = new LinkedHashMap<>();
            Map<String, String> texts = new LinkedHashMap<>();
            Set<String> down = new LinkedHashSet<>();
            for (Object o : rules) {
                if (o instanceof Map<?, ?> r && r.get("id") instanceof String id) {
                    if (r.get("rule") instanceof String text) {
                        texts.put(id, text);
                    }
                    if (r.get("faultsPerSecond") instanceof List<?> perSecond) {
                        long[] values = new long[perSecond.size()];
                        for (int i = 0; i < values.length; i++) {
                            values[i] = perSecond.get(i) instanceof Number n ? n.longValue() : 0;
                        }
                        faults.put(id, values);
                    }
                    if (r.get("lifecycle") instanceof Map<?, ?> lifecycle && "down".equals(lifecycle.get("state"))
                            && lifecycle.get("route") instanceof String route) {
                        down.add(route);
                    }
                }
            }
            recorder.chaos(now, faults, texts, down);
        }
    }

    private Map<String, TimelineRecorder.Counters> counters() {
        Map<String, TimelineRecorder.Counters> answer = new HashMap<>();
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            for (Route route : getCamelContext().getRoutes()) {
                ManagedRouteMBean mr = mcc.getManagedRoute(route.getRouteId());
                if (mr != null) {
                    answer.put(route.getRouteId(), new TimelineRecorder.Counters(
                            mr.getExchangesCompleted(), mr.getExchangesFailed(), mr.getFailuresHandled(),
                            mr.getExchangesInflight(), mr.getTotalProcessingTime()));
                }
            }
        }
        return answer;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        Response r = response(options, false);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Last %d seconds:%n", r.seconds()));
        for (RouteSeries rs : r.routes()) {
            sb.append(String.format("    %s: %d completed, %d recovered, %d handled, %d failed, %d redelivery attempts%n",
                    rs.routeId(), sum(rs.completed()), sum(rs.recovered()), sum(rs.handled()), sum(rs.failed()),
                    sum(rs.redeliveryAttempts())));
        }
        if (r.errors() != null) {
            sb.append("Errors:\n");
            for (ErrorSeries es : r.errors()) {
                sb.append(String.format("    %s in %s: %d%s%n", es.exception(), es.routeId(), sum(es.counts()),
                        es.chaosRule() != null ? " (chaos rule " + es.chaosRule() + ")" : ""));
            }
        }
        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        return JsonRecordSupport.toJsonObject(response(options, optionBoolean(options, EXCHANGES, true)));
    }

    private Response response(Map<String, Object> options, boolean exchanges) {
        TimelineRecorder rec = recorder;
        int count = Math.min(optionInt(options, SECONDS, rec.getSeconds()), rec.getSeconds());
        count = Math.max(1, count);
        String filter = optionString(options, ROUTE_ID);
        Predicate<String> accept = id -> filter == null || PatternHelper.matchPattern(id, filter);
        long end = rec.endSecond(System.currentTimeMillis());
        List<TimelineRecorder.Second> secs;
        List<RouteSeries> routes = new ArrayList<>();
        List<ErrorSeries> errors = new ArrayList<>();
        List<TimelineExchange> kept = new ArrayList<>();
        List<Long> done = new ArrayList<>(count);
        List<ChaosSeries> chaos = new ArrayList<>();
        List<RouteEvent> routeEvents = new ArrayList<>();
        synchronized (rec) {
            secs = rec.seconds(end, count);
            boolean chaosSeen = !rec.chaosRules().isEmpty();
            for (TimelineRecorder.Second s : secs) {
                done.add(s != null ? s.done : 0L);
            }
            for (String routeId : TimelineRecorder.routes(secs, accept)) {
                Route route = getCamelContext().getRoute(routeId);
                String from = route != null && route.getConsumer() != null
                        ? route.getConsumer().getEndpoint().getEndpointUri() : null;
                routes.add(new RouteSeries(
                        routeId, from,
                        series(secs, routeId, rs -> rs.completed), series(secs, routeId, rs -> rs.recovered),
                        series(secs, routeId, rs -> rs.handled), series(secs, routeId, rs -> rs.failed),
                        series(secs, routeId, rs -> rs.inflight), series(secs, routeId, TimelineRecorder.RouteSecond::mean),
                        series(secs, routeId, rs -> rs.p95), series(secs, routeId, rs -> rs.max),
                        series(secs, routeId, rs -> rs.attempts), chaosSeen ? downSeries(secs, routeId) : null));
            }
            Map<String, Long[]> faults = new LinkedHashMap<>();
            for (int i = 0; i < secs.size(); i++) {
                TimelineRecorder.Second s = secs.get(i);
                if (s != null && s.faults != null) {
                    for (Map.Entry<String, Long> e : s.faults.entrySet()) {
                        faults.computeIfAbsent(e.getKey(), k -> zeros(secs.size()))[i] = e.getValue();
                    }
                }
            }
            faults.forEach((id, v) -> chaos.add(new ChaosSeries(id, rec.chaosRules().get(id), List.of(v))));
            for (TimelineRecorder.Second s : secs) {
                if (s != null && s.routeEvents != null) {
                    s.routeEvents.forEach((routeId, event) -> {
                        if (accept.test(routeId)) {
                            routeEvents.add(new RouteEvent(routeId, event, s.second));
                        }
                    });
                }
            }
            Map<TimelineRecorder.ErrorKind, Long[]> byKind = new LinkedHashMap<>();
            for (int i = 0; i < secs.size(); i++) {
                TimelineRecorder.Second s = secs.get(i);
                if (s == null) {
                    continue;
                }
                for (Map.Entry<TimelineRecorder.ErrorKind, long[]> e : s.errors.entrySet()) {
                    if (e.getKey().routeId() != null && !accept.test(e.getKey().routeId())) {
                        continue;
                    }
                    Long[] values = byKind.computeIfAbsent(e.getKey(), k -> zeros(secs.size()));
                    values[i] = e.getValue()[0];
                }
                if (exchanges) {
                    List<TimelineRecorder.Kept> all = new ArrayList<>(s.failed);
                    all.addAll(s.slowest);
                    all.sort(Comparator.comparingLong(TimelineRecorder.Kept::timestamp));
                    for (TimelineRecorder.Kept k : all) {
                        if (accept.test(k.routeId())) {
                            kept.add(exchange(k));
                        }
                    }
                }
            }
            byKind.forEach((k, v) -> errors.add(
                    new ErrorSeries(k.routeId(), k.exception(), k.statusCode(), k.chaosRule(), List.of(v))));
        }
        return new Response(
                end, count, done, routes, errors.isEmpty() ? null : errors, chaos.isEmpty() ? null : chaos,
                routeEvents.isEmpty() ? null : routeEvents,
                exchanges ? kept : null);
    }

    private static List<Long> downSeries(List<TimelineRecorder.Second> secs, String routeId) {
        List<Long> answer = new ArrayList<>(secs.size());
        for (TimelineRecorder.Second s : secs) {
            answer.add(s != null && s.down != null && s.down.contains(routeId) ? 1L : 0L);
        }
        return answer;
    }

    private static TimelineExchange exchange(TimelineRecorder.Kept k) {
        List<Send> sends = k.sends().isEmpty()
                ? null : k.sends().stream().map(s -> new Send(s.endpointUri(), s.elapsed())).toList();
        return new TimelineExchange(
                k.exchangeId(), k.routeId(), k.fromEndpointUri(), k.timestamp(), k.elapsed(), k.failed(), k.handled(),
                k.redelivered(), k.exception(), k.chaosRule(), sends);
    }

    private static List<Long> series(
            List<TimelineRecorder.Second> secs, String routeId, Function<TimelineRecorder.RouteSecond, Long> value) {
        List<Long> answer = new ArrayList<>(secs.size());
        for (TimelineRecorder.Second s : secs) {
            TimelineRecorder.RouteSecond rs = s != null ? s.routes.get(routeId) : null;
            answer.add(rs != null ? value.apply(rs) : 0L);
        }
        return answer;
    }

    private static Long[] zeros(int n) {
        Long[] answer = new Long[n];
        Arrays.fill(answer, 0L);
        return answer;
    }

    private static long sum(List<Long> values) {
        long sum = 0;
        for (Long v : values) {
            sum += v;
        }
        return sum;
    }

    private void onEvent(CamelEvent event) {
        TimelineRecorder rec = recorder;
        if (event instanceof CamelEvent.RouteEvent re) {
            String what = event instanceof CamelEvent.RouteStartedEvent ? "started"
                    : event instanceof CamelEvent.RouteStoppedEvent ? "stopped"
                    : event instanceof CamelEvent.RouteReloadedEvent ? "reloaded"
                    : event instanceof CamelEvent.RouteRemovedEvent ? "removed"
                    : event instanceof CamelEvent.RouteRestartingFailureEvent ? "restartFailed" : null;
            if (what != null && re.getRoute() != null) {
                rec.routeEvent(re.getRoute().getRouteId(), what, timestamp(event));
            }
            return;
        }
        if (event instanceof CamelEvent.ExchangeCreatedEvent ce) {
            rec.exchangeCreated(ce.getExchange().getExchangeId(), ce.getExchange().getFromRouteId(), timestamp(ce));
        } else if (event instanceof CamelEvent.ExchangeSentEvent se) {
            rec.exchangeSent(se.getExchange().getExchangeId(), se.getExchange().getFromRouteId(),
                    se.getEndpoint().isRemote() ? se.getEndpoint().getEndpointUri() : null, se.getTimeTaken());
        } else if (event instanceof CamelEvent.ExchangeRedeliveryEvent re) {
            // the exception is cleared for the redelivery: the failure is still the caught exception
            Throwable cause = re.getExchange().getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
            rec.redelivery(re.getExchange().getExchangeId(), ExchangeHelper.getRouteId(re.getExchange()), timestamp(re),
                    cause != null ? cause.getMessage() : null);
        } else if (event instanceof CamelEvent.ExchangeFailureHandledEvent fe) {
            Exchange exchange = fe.getExchange();
            ExchangeExtension ee = exchange.getExchangeExtension();
            Throwable cause = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
            if (cause != null && ee.isErrorHandlerHandledSet() && ee.isErrorHandlerHandled()) {
                rec.error(TimelineRecorder.errorKind(failureRoute(exchange), cause, chaosRule(exchange)), timestamp(fe));
            }
        } else if (event instanceof CamelEvent.ExchangeCompletedEvent ce) {
            done(rec, ce, false);
        } else if (event instanceof CamelEvent.ExchangeFailedEvent fe) {
            Exchange exchange = fe.getExchange();
            if (exchange.getException() != null) {
                rec.error(TimelineRecorder.errorKind(failureRoute(exchange), exchange.getException(), chaosRule(exchange)),
                        timestamp(fe));
            }
            done(rec, fe, true);
        }
    }

    private void done(TimelineRecorder rec, CamelEvent.ExchangeEvent event, boolean failed) {
        Exchange exchange = event.getExchange();
        String routeId = exchange.getFromRouteId();
        Route route = routeId != null ? getCamelContext().getRoute(routeId) : null;
        String from = route != null && route.getConsumer() != null ? route.getConsumer().getEndpoint().getEndpointUri() : null;
        Exception cause = exchange.getException();
        rec.exchangeDone(
                exchange.getExchangeId(), routeId, from, timestamp(event), exchange.getClock().elapsed(), failed,
                ExchangeHelper.isFailureHandled(exchange), cause != null ? cause.getMessage() : null, chaosRule(exchange),
                exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class), isIndexed(exchange));
    }

    /** Whether the exchange is a part of a split or multicast: it has the index of the part. */
    static boolean isIndexed(Exchange exchange) {
        return exchange.getProperty(ExchangePropertyKey.SPLIT_INDEX) != null
                || exchange.getProperty(ExchangePropertyKey.MULTICAST_INDEX) != null;
    }

    private static String failureRoute(Exchange exchange) {
        String answer = exchange.getProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, String.class);
        return answer != null ? answer : ExchangeHelper.getRouteId(exchange);
    }

    private static String chaosRule(Exchange exchange) {
        return exchange.getProperty(CHAOS_RULE, String.class);
    }

    private static long timestamp(CamelEvent event) {
        return event.getTimestamp() > 0 ? event.getTimestamp() : System.currentTimeMillis();
    }

    private class TimelineEventNotifier extends EventNotifierSupport implements NonManagedService {

        private TimelineEventNotifier() {
            setIgnoreCamelContextInitEvents(true);
            setIgnoreCamelContextEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreStepEvents(true);
            setIgnoreAiEvents(true);
            setIgnoreExchangeSendingEvents(true);
            setIgnoreExchangeAsyncProcessingStartedEvents(true);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            onEvent(event);
        }
    }
}
