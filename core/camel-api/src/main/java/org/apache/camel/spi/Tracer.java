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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.Route;
import org.apache.camel.StaticService;

/**
 * SPI for tracing messages.
 */
public interface Tracer extends StaticService {

    /**
     * Whether or not to trace the given processor definition.
     *
     * @param definition the processor definition
     * @return <tt>true</tt> to trace, <tt>false</tt> to skip tracing
     */
    boolean shouldTrace(NamedNode definition);

    /**
     * Trace before the route (eg input to route)
     *
     * @param route     the route
     * @param exchange  the exchange
     */
    void traceBeforeRoute(NamedRoute route, Exchange exchange);

    /**
     * Trace before the given node
     *
     * @param node      the node EIP
     * @param exchange  the exchange
     */
    void traceBeforeNode(NamedNode node, Exchange exchange);

    /**
     * Trace after the given node
     *
     * @param node      the node EIP
     * @param exchange  the exchange
     */
    void traceAfterNode(NamedNode node, Exchange exchange);

    /**
     * Trace after the route (eg output from route)
     *
     * @param route     the route
     * @param exchange  the exchange
     */
    void traceAfterRoute(Route route, Exchange exchange);

    /**
     * Number of traced messages
     */
    long getTraceCounter();

    /**
     * Reset trace counter
     */
    void resetTraceCounter();

    /**
     * Whether the tracer is enabled
     */
    boolean isEnabled();

    /**
     * Whether the tracer is enabled
     */
    void setEnabled(boolean enabled);

    /**
     * Tracing pattern to match which node EIPs to trace.
     * For example to match all To EIP nodes, use to*.
     * The pattern matches by node and route id's
     * Multiple patterns can be separated by comma.
     */
    String getTracePattern();

    /**
     * Tracing pattern to match which node EIPs to trace.
     * For example to match all To EIP nodes, use to*.
     * The pattern matches by node and route id's
     * Multiple patterns can be separated by comma.
     */
    void setTracePattern(String tracePattern);

    /**
     * Whether to include tracing of before/after routes to trace the input and responses of routes.
     */
    boolean isTraceBeforeAndAfterRoute();

    /**
     * Whether to include tracing of before/after routes to trace the input and responses of routes.
     */
    void setTraceBeforeAndAfterRoute(boolean traceBeforeAndAfterRoute);

    /**
     * To use a custom exchange formatter for formatting the output of the {@link Exchange} in the trace logs.
     */
    ExchangeFormatter getExchangeFormatter();

    /**
     * To use a custom exchange formatter for formatting the output of the {@link Exchange} in the trace logs.
     */
    void setExchangeFormatter(ExchangeFormatter exchangeFormatter);
}
