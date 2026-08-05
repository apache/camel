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

package org.apache.camel.processor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PatternHelper;
import org.slf4j.MDC;

final class ProcessorHelper {

    private ProcessorHelper() {
    }

    /**
     * Parses a comma-separated list of component schemes into a set, or {@code null} when unset. Used by the
     * dynamic-uri EIPs to hold an optional {@code allowedSchemes} allow-list (see CAMEL-24298).
     */
    static Set<String> parseAllowedSchemes(String allowedSchemes) {
        if (allowedSchemes == null) {
            return null;
        }
        Set<String> answer = new HashSet<>();
        for (String scheme : allowedSchemes.split(",")) {
            answer.add(scheme.trim());
        }
        return answer;
    }

    /**
     * Enforces the optional {@code allowedSchemes} allow-list on a resolved dynamic recipient: when the set is non-null
     * and the recipient's scheme is not in it, a {@link ResolveEndpointFailedException} is thrown. A null set (the
     * default) allows any scheme.
     */
    static void checkAllowedSchemes(Set<String> allowedSchemes, Object recipient) {
        if (allowedSchemes != null && recipient != null) {
            String uri = recipient.toString();
            String scheme = ExchangeHelper.resolveScheme(uri);
            if (scheme != null && !allowedSchemes.contains(scheme)) {
                throw new ResolveEndpointFailedException(
                        uri, "Scheme " + scheme + " is not in the allowed schemes: " + allowedSchemes);
            }
        }
    }

    static Object prepareRecipient(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        if (recipient instanceof Endpoint || recipient instanceof NormalizedEndpointUri) {
            return recipient;
        } else if (recipient instanceof String string) {
            // trim strings as end users might have added spaces between separators
            recipient = string.trim();
        }
        if (recipient != null) {
            CamelContext ecc = exchange.getContext();
            String uri;
            if (recipient instanceof String string) {
                uri = string;
            } else {
                // convert to a string type we can work with
                uri = ecc.getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
            }
            // optimize and normalize endpoint
            return ecc.getCamelContextExtension().normalizeUri(uri);
        }
        return null;
    }

    static Endpoint getExistingEndpoint(Exchange exchange, Object recipient) {
        return getExistingEndpoint(exchange.getContext(), recipient);
    }

    static Endpoint getExistingEndpoint(CamelContext context, Object recipient) {
        if (recipient instanceof Endpoint endpoint) {
            return endpoint;
        }
        if (recipient != null) {
            if (recipient instanceof NormalizedEndpointUri nu) {
                ExtendedCamelContext ecc = context.getCamelContextExtension();
                return ecc.hasEndpoint(nu);
            } else {
                String uri = recipient.toString();
                return context.hasEndpoint(uri);
            }
        }
        return null;
    }

    @Deprecated(since = "4.19.0")
    static Runnable prepareMDCParallelTask(CamelContext camelContext, Runnable runnable) {
        Runnable answer = runnable;

        // if MDC is enabled we need to propagate the information
        // to the sub task which is executed on another thread from the thread pool
        if (camelContext.isUseMDCLogging()) {
            String pattern = camelContext.getMDCLoggingKeysPattern();
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            if (mdc != null && !mdc.isEmpty()) {
                answer = () -> {
                    try {
                        if (pattern == null || "*".equals(pattern)) {
                            mdc.forEach(MDC::put);
                        } else {
                            final String[] patterns = pattern.split(",");
                            mdc.forEach((k, v) -> {
                                if (PatternHelper.matchPatterns(k, patterns)) {
                                    MDC.put(k, v);
                                }
                            });
                        }
                    } finally {
                        runnable.run();
                    }
                };
            }
        }

        return answer;
    }
}
