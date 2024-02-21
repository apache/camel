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

import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
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

import static org.apache.camel.support.LoggerHelper.getLineNumberLoggerName;

/**
 * Default {@link Tracer} implementation that will log traced messages to the logger named
 * <tt>org.apache.camel.Tracing</tt>.
 */
public class DefaultTracer extends ServiceSupport implements CamelContextAware, Tracer {

    private static final String TRACING_OUTPUT = "%-4.4s [%-12.12s] [%-33.33s]";

    // use a fixed logger name so easy to spot
    private static final Logger LOG = LoggerFactory.getLogger("org.apache.camel.Tracing");

    private String tracingFormat = TRACING_OUTPUT;
    private CamelContext camelContext;
    private boolean enabled = true;
    private boolean standby;
    private boolean traceRests;
    private boolean traceTemplates;
    private long traceCounter;

    private ExchangeFormatter exchangeFormatter;
    private String tracePattern;
    private transient String[] patterns;
    private boolean traceBeforeAndAfterRoute = true;

    public DefaultTracer() {
        DefaultExchangeFormatter formatter = new DefaultExchangeFormatter();
        formatter.setShowExchangeId(true);
        formatter.setShowExchangePattern(false);
        formatter.setMultiline(false);
        formatter.setShowHeaders(true);
        formatter.setStyle(DefaultExchangeFormatter.OutputStyle.Default);
        setExchangeFormatter(formatter);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void traceBeforeNode(NamedNode node, Exchange exchange) {
        if (shouldTrace(node)) {
            traceCounter++;
            String routeId = ExpressionBuilder.routeIdExpression().evaluate(exchange, String.class);

            // we need to avoid leak the sensible information here
            // the sanitizeUri takes a very long time for very long string and the format cuts this to
            // 33 characters, anyway. Cut this to 50 characters. This will give enough space for removing
            // characters in the sanitizeUri method and will be reasonably fast
            String label = URISupport.sanitizeUri(StringHelper.limitLength(node.getLabel(), 50));

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(tracingFormat, "   ", routeId, label));
            sb.append(" ");
            String data = exchangeFormatter.format(exchange);
            sb.append(data);
            String out = sb.toString();
            dumpTrace(out, node);
        }
    }

    @Override
    public void traceAfterNode(NamedNode node, Exchange exchange) {
        // noop
    }

    @Override
    public void traceBeforeRoute(NamedRoute route, Exchange exchange) {
        if (!traceBeforeAndAfterRoute) {
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
        sb.append(String.format(tracingFormat, arrow, route.getRouteId(), label));
        sb.append(" ");
        String data = exchangeFormatter.format(exchange);
        sb.append(data);
        String out = sb.toString();
        dumpTrace(out, route);
    }

    @Override
    public void traceAfterRoute(NamedRoute route, Exchange exchange) {
        if (!traceBeforeAndAfterRoute) {
            return;
        }

        // we need to avoid leak the sensible information here
        // the sanitizeUri takes a very long time for very long string and the format cuts this to
        // 33 characters, anyway. Cut this to 50 characters. This will give enough space for removing
        // characters in the sanitizeUri method and will be reasonably fast
        String uri = route.getEndpointUrl();
        String label = "from[" + URISupport.sanitizeUri(StringHelper.limitLength(uri, 50) + "]");

        // the arrow has a * if its an exchange that is done
        boolean original = route.getRouteId().equals(exchange.getFromRouteId());
        String arrow = original ? "*<--" : "<---";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(tracingFormat, arrow, route.getRouteId(), label));
        sb.append(" ");
        String data = exchangeFormatter.format(exchange);
        sb.append(data);
        String out = sb.toString();
        dumpTrace(out, route);
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
    public boolean isStandby() {
        return standby;
    }

    @Override
    public void setStandby(boolean standby) {
        this.standby = standby;
    }

    @Override
    public boolean isTraceRests() {
        return traceRests;
    }

    @Override
    public void setTraceRests(boolean traceRests) {
        this.traceRests = traceRests;
    }

    @Override
    public boolean isTraceTemplates() {
        return traceTemplates;
    }

    @Override
    public void setTraceTemplates(boolean traceTemplates) {
        this.traceTemplates = traceTemplates;
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
    public boolean isTraceBeforeAndAfterRoute() {
        return traceBeforeAndAfterRoute;
    }

    @Override
    public void setTraceBeforeAndAfterRoute(boolean traceBeforeAndAfterRoute) {
        this.traceBeforeAndAfterRoute = traceBeforeAndAfterRoute;
    }

    @Override
    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    @Override
    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }

    protected void dumpTrace(String out, Object node) {
        String name = getLineNumberLoggerName(node);
        if (name != null) {
            Logger log = LoggerFactory.getLogger(name);
            log.info(out);
        } else {
            LOG.info(out);
        }
    }

    protected boolean shouldTracePattern(NamedNode definition) {
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
    protected void doStart() throws Exception {
        if (getCamelContext().getTracingLoggingFormat() != null) {
            tracingFormat = getCamelContext().getTracingLoggingFormat();
        }
    }

}
