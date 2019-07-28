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
     * Trace at the given node
     *
     * @param node      the node EIP
     * @param exchange  the exchange
     */
    void trace(NamedNode node, Exchange exchange);

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

    // TODO: Add javadoc

    boolean isEnabled();

    void setEnabled(boolean enabled);

    String getTracePattern();

    void setTracePattern(String tracePattern);

    boolean isTraceBeforeAfterRoute();

    void setTraceBeforeAfterRoute(boolean traceBeforeAfterRoute);

    ExchangeFormatter getExchangeFormatter();

    void setExchangeFormatter(ExchangeFormatter exchangeFormatter);
}
