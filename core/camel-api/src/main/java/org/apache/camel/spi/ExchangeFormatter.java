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

/**
 * Strategy for rendering an {@link Exchange} as a human-readable {@link String}, used primarily for logging and tracing
 * output.
 * <p/>
 * Camel's built-in logging processor, the {@link BacklogTracer}, and the log component all delegate to an
 * {@code ExchangeFormatter} to produce the text that appears in log lines. The default implementation
 * ({@code DefaultExchangeFormatter} in {@code camel-support}) provides options to include/exclude headers, body
 * content, and exchange properties, as well as to truncate large payloads. Callers can register a custom
 * {@code ExchangeFormatter} bean in the registry to override the default rendering globally.
 * <p/>
 * Implementations must be thread-safe: a single instance is typically shared across all threads that process exchanges
 * on a route.
 */
public interface ExchangeFormatter {

    /**
     * Generates a string representation of the exchange
     *
     * @param  exchange the exchange
     * @return          a string representation of the exchange
     */
    String format(Exchange exchange);
}
