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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.support.PatternHelper;
import org.slf4j.MDC;

final class ProcessorHelper {

    private ProcessorHelper() {
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
