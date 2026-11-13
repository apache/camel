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
package org.apache.camel.component.ai.resource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-agnostic executor for Camel route resources: resolves the route processor from the resource's consumer,
 * invokes the route and converts the resulting body according to the declared MIME type.
 * <p>
 * A resource read carries no arguments: the route is invoked with an empty exchange and its body is the content.
 * Textual resources are converted to a string, binary ones to a byte array.
 * <p>
 * The calling adapter owns the exchange lifecycle: it must create the exchange before calling this method and release
 * it afterwards (via {@code consumer.releaseExchange()}) in a try-finally block.
 * <p>
 * This is an internal support class used by Camel AI adapters and is not intended for direct use by end users.
 *
 * @since 4.23
 */
public final class AiResourceExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AiResourceExecutor.class);

    private AiResourceExecutor() {
    }

    /**
     * Reads a resource by invoking its route.
     *
     * @param  spec     the resource specification containing the consumer and the declared MIME type
     * @param  exchange the Camel exchange to execute
     * @return          an {@link AiResourceResult} classifying the outcome; never null
     */
    public static AiResourceResult execute(AiResourceSpec spec, Exchange exchange) {
        String resourceName = spec.getName();

        DefaultConsumer consumer = spec.getConsumer();
        if (consumer == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("No consumer available for resource '%s'", resourceName));
            return new AiResourceResult.ExecutionError(cause.getMessage(), cause);
        }

        Processor routeProcessor = consumer.getProcessor();
        if (routeProcessor == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("No route processor available for resource '%s'", resourceName));
            return new AiResourceResult.ExecutionError(cause.getMessage(), cause);
        }

        LOG.debug("Reading Camel route resource: '{}'", resourceName);

        try {
            routeProcessor.process(exchange);

            if (exchange.getException() != null) {
                Exception routeError = exchange.getException();
                LOG.error("Error reading resource '{}': {}", resourceName, routeError.getMessage(), routeError);
                return new AiResourceResult.ExecutionError(
                        String.format("Error reading resource '%s': %s", resourceName, routeError.getMessage()), routeError);
            }

            return convertBody(spec, exchange);
        } catch (Exception e) {
            LOG.error("Error reading resource '{}': {}", resourceName, e.getMessage(), e);
            return new AiResourceResult.ExecutionError(
                    String.format("Error reading resource '%s': %s", resourceName, e.getMessage()), e);
        }
    }

    private static AiResourceResult convertBody(AiResourceSpec spec, Exchange exchange) {
        String resourceName = spec.getName();
        if (exchange.getMessage().getBody() == null) {
            IllegalStateException cause = new IllegalStateException(
                    String.format("Resource '%s' produced no content", resourceName));
            return new AiResourceResult.ExecutionError(cause.getMessage(), cause);
        }

        if (spec.isTextual()) {
            String text;
            try {
                text = exchange.getMessage().getBody(String.class);
            } catch (TypeConversionException e) {
                return conversionError(spec, "String", e);
            }
            if (text == null) {
                return conversionError(spec, "String", null);
            }
            LOG.debug("Resource '{}' read {} characters", resourceName, text.length());
            return new AiResourceResult.Text(text);
        }

        byte[] blob;
        try {
            blob = exchange.getMessage().getBody(byte[].class);
        } catch (TypeConversionException e) {
            return conversionError(spec, "byte[]", e);
        }
        if (blob == null) {
            return conversionError(spec, "byte[]", null);
        }
        LOG.debug("Resource '{}' read {} bytes", resourceName, blob.length);
        return new AiResourceResult.Binary(blob);
    }

    private static AiResourceResult conversionError(
            AiResourceSpec spec, String targetType, TypeConversionException conversionFailure) {
        String resourceName = spec.getName();
        IllegalStateException cause = new IllegalStateException(
                String.format("Resource '%s' produced a body that cannot be converted to %s "
                              + "(declared mimeType is %s)",
                        resourceName, targetType, spec.getMimeType()),
                conversionFailure);
        return new AiResourceResult.ExecutionError(cause.getMessage(), cause);
    }
}
