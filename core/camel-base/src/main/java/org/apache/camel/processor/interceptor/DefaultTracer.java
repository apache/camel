/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.interceptor;

import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.Route;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Tracer;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Tracer} implementation that will log traced messages
 * to the logger named <tt>org.apache.camel.Tracing</tt>.
 */
public class DefaultTracer extends ServiceSupport implements Tracer {

    // TODO: Custom exchange formatter
    // TODO: Allow to configure exchange formatter options more easily
    // TODO: Expose these options in Tracer API / Main Configuration
    // TODO: Add options for spring-boot configuration too
    // TODO: Trace intercept, onCompletion (not routes)

    private static final String TRACING_OUTPUT = "%-4.4s [%-12.12s] [%-33.33s]";

    // use a fixed logger name so its easy to spot
    private static final Logger LOG = LoggerFactory.getLogger("org.apache.camel.Tracing");
    private final CamelContext camelContext;
    private boolean enabled = true;
    private long traceCounter;

    private ExchangeFormatter exchangeFormatter;
    private String tracePattern;
    private transient String[] patterns;
    private boolean traceBeforeAfterRoute = true;

    public DefaultTracer(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Creates a new tracer.
     *
     * @param context Camel context
     * @return a new tracer
     */
    public static DefaultTracer createTracer(CamelContext context) {
        return new DefaultTracer(context);
    }

    /**
     * A helper method to return the BacklogTracer instance if one is enabled
     *
     * @return the backlog tracer or null if none can be found
     */
    public static DefaultTracer getDefaultTracer(CamelContext context) {
        return context.getExtension(DefaultTracer.class);
    }

    @SuppressWarnings("unchecked")
    public void trace(NamedNode node, Exchange exchange) {
        if (shouldTrace(node)) {
            traceCounter++;
            String routeId = ExpressionBuilder.routeIdExpression().evaluate(exchange, String.class);

            // we need to avoid leak the sensible information here
            // the sanitizeUri takes a very long time for very long string and the format cuts this to
            // 33 characters, anyway. Cut this to 50 characters. This will give enough space for removing
            // characters in the sanitizeUri method and will be reasonably fast
            String label = URISupport.sanitizeUri(StringHelper.limitLength(node.getLabel(), 50));

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(TRACING_OUTPUT, "   ", routeId, label));
            sb.append(" ");
            String data = exchangeFormatter.format(exchange);
            sb.append(data);
            String out = sb.toString();
            dumpTrace(out);
        }
    }

    public void traceBeforeRoute(NamedRoute route, Exchange exchange) {
        if (!traceBeforeAfterRoute) {
            return;
        }

        // we need to avoid leak the sensible information here
        // the sanitizeUri takes a very long time for very long string and the format cuts this to
        // 33 characters, anyway. Cut this to 50 characters. This will give enough space for removing
        // characters in the sanitizeUri method and will be reasonably fast
        String uri = route.getEndpointUrl();
        String label = "from[" + URISupport.sanitizeUri(StringHelper.limitLength(uri, 50) + "]");

        // the arrow has a * if its a new exchange that is starting
        boolean original = route.getRouteId().equals(exchange.getFromRouteId());
        String arrow = original ? "*-->" : "--->";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(TRACING_OUTPUT, arrow, route.getRouteId(), label));
        sb.append(" ");
        String data = exchangeFormatter.format(exchange);
        sb.append(data);
        String out = sb.toString();
        LOG.info(out);
    }

    public void traceAfterRoute(Route route, Exchange exchange) {
        if (!traceBeforeAfterRoute) {
            return;
        }

        // we need to avoid leak the sensible information here
        // the sanitizeUri takes a very long time for very long string and the format cuts this to
        // 33 characters, anyway. Cut this to 50 characters. This will give enough space for removing
        // characters in the sanitizeUri method and will be reasonably fast
        String uri = route.getConsumer().getEndpoint().getEndpointUri();
        String label = "from[" + URISupport.sanitizeUri(StringHelper.limitLength(uri, 50) + "]");

        // the arrow has a * if its an exchange that is done
        boolean original = route.getId().equals(exchange.getFromRouteId());
        String arrow = original ? "*<--" : "<---";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(TRACING_OUTPUT, arrow, route.getId(), label));
        sb.append(" ");
        String data = exchangeFormatter.format(exchange);
        sb.append(data);
        String out = sb.toString();
        dumpTrace(out);
    }

    public void dumpTrace(String out) {
        LOG.info(out);
    }

    @Override
    public boolean shouldTrace(NamedNode definition) {
        if (!enabled) {
            return false;
        }

        boolean pattern = true;

        if (patterns != null) {
            pattern = shouldTracePattern(definition);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Should trace evaluated {} -> pattern: {}", definition.getId(), pattern);
        }
        return pattern;
    }

    private boolean shouldTracePattern(NamedNode definition) {
        for (String pattern : patterns) {
            // match either route id, or node id
            String id = definition.getId();
            // use matchPattern method from endpoint helper that has a good matcher we use in Camel
            if (PatternHelper.matchPattern(id, pattern)) {
                return true;
            }
            String routeId = CamelContextHelper.getRouteId(definition);
            if (routeId != null && !Objects.equals(routeId, id)) {
                if (PatternHelper.matchPattern(routeId, pattern)) {
                    return true;
                }
            }
        }
        // not matched the pattern
        return false;
    }

    @Override
    public long getTraceCounter() {
        return traceCounter;
    }

    @Override
    public void resetTraceCounter() {
        traceCounter = 0;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getTracePattern() {
        return tracePattern;
    }

    @Override
    public void setTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
        if (tracePattern != null) {
            // the pattern can have multiple nodes separated by comma
            this.patterns = tracePattern.split(",");
        } else {
            this.patterns = null;
        }
    }

    @Override
    public boolean isTraceBeforeAfterRoute() {
        return traceBeforeAfterRoute;
    }

    @Override
    public void setTraceBeforeAfterRoute(boolean traceBeforeAfterRoute) {
        this.traceBeforeAfterRoute = traceBeforeAfterRoute;
    }

    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

    @Override
    protected void doStart() throws Exception {
        if (exchangeFormatter == null) {
            DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
            formatter.setShowExchangeId(true);
            formatter.setShowExchangePattern(false);
            formatter.setMultiline(false);
            formatter.setShowHeaders(false);
            formatter.setStyle(DefaultExchangeFormatter.OutputStyle.Default);
            exchangeFormatter = formatter;
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
