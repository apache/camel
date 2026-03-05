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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link org.apache.camel.spi.TransformerResolver} which tries to find components by
 * using the URI scheme prefix and searching for a file of the URI scheme name in the
 * <b>META-INF/services/org/apache/camel/transformer/</b> directory on the classpath.
 */
public class DefaultTransformerResolver implements TransformerResolver<TransformerKey> {

    public static final String DATA_TYPE_TRANSFORMER_RESOURCE_PATH = "META-INF/services/org/apache/camel/transformer/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTransformerResolver.class);

    @Override
    public Transformer resolve(TransformerKey key, CamelContext context) {
        String normalizedKey = normalize(key);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving data type transformer for key {} via: {}{}", key, DATA_TYPE_TRANSFORMER_RESOURCE_PATH,
                    normalizedKey);
        }

        Optional<Transformer> transformer = findTransformer(normalizedKey, context);
        if (LOG.isDebugEnabled() && transformer.isPresent()) {
            LOG.debug("Found data type transformer for key {} via type: {} via: {}{}", key,
                    ObjectHelper.name(transformer.getClass()), DATA_TYPE_TRANSFORMER_RESOURCE_PATH, normalizedKey);
        }

        transformer.ifPresent(t -> CamelContextAware.trySetCamelContext(t, context));

        return transformer.orElse(null);
    }

    private Optional<Transformer> findTransformer(String key, CamelContext context) {
        FactoryFinder ff = context.getCamelContextExtension().getBootstrapFactoryFinder(DATA_TYPE_TRANSFORMER_RESOURCE_PATH);
        Optional<Transformer> transformer = ResolverHelper.resolveService(context, ff, key, Transformer.class);

        // Special handling for Jackson transformers - detect which implementation is available
        if (transformer.isEmpty() && isJacksonTransformer(key)) {
            Class<?> transformerClass = detectJacksonTransformer(key, context);
            if (transformerClass != null) {
                try {
                    Transformer instance = (Transformer) context.getInjector().newInstance(transformerClass, false);
                    transformer = Optional.of(instance);
                } catch (Exception e) {
                    LOG.debug("Failed to instantiate Jackson transformer class: {}", transformerClass.getName(), e);
                }
            }
        }

        return transformer;
    }

    /**
     * Checks if the transformer key is a Jackson-based transformer that needs version detection.
     *
     * @param  key the transformer key
     * @return     true if this is a Jackson transformer
     */
    private boolean isJacksonTransformer(String key) {
        return "application-json".equals(key)
                || "application-x-java-object".equals(key)
                || "application-x-struct".equals(key)
                || "avro-binary".equals(key)
                || "avro-x-java-object".equals(key)
                || "avro-x-struct".equals(key)
                || "protobuf-binary".equals(key)
                || "protobuf-x-java-object".equals(key)
                || "protobuf-x-struct".equals(key);
    }

    /**
     * Detects which Jackson implementation is available on the classpath and returns the appropriate Transformer class.
     * Tries Jackson 3.x first (tools.jackson), then falls back to Jackson 2.x (com.fasterxml.jackson).
     *
     * @param  key     the transformer key
     * @param  context the CamelContext
     * @return         the Jackson Transformer class, or null if neither is available
     */
    private Class<?> detectJacksonTransformer(String key, CamelContext context) {
        // Try Jackson 3.x first (tools.jackson.databind.ObjectMapper)
        Class<?> jackson3Marker = context.getClassResolver().resolveClass("tools.jackson.databind.ObjectMapper");
        if (jackson3Marker != null) {
            // Jackson 3.x is available, use camel-jackson3 transformers
            return resolveJackson3TransformerClass(key, context);
        }

        // Try Jackson 2.x (com.fasterxml.jackson.databind.ObjectMapper)
        Class<?> jackson2Marker = context.getClassResolver().resolveClass("com.fasterxml.jackson.databind.ObjectMapper");
        if (jackson2Marker != null) {
            // Jackson 2.x is available, use camel-jackson transformers
            return resolveJackson2TransformerClass(key, context);
        }

        // Neither Jackson version is available
        return null;
    }

    private Class<?> resolveJackson3TransformerClass(String key, CamelContext context) {
        String className = switch (key) {
            case "application-json" -> "org.apache.camel.component.jackson3.transform.JsonDataTypeTransformer";
            case "application-x-java-object" -> "org.apache.camel.component.jackson3.transform.JsonPojoDataTypeTransformer";
            case "application-x-struct" -> "org.apache.camel.component.jackson3.transform.JsonStructDataTypeTransformer";
            case "avro-binary" -> "org.apache.camel.component.jackson3.avro.transform.AvroBinaryDataTypeTransformer";
            case "avro-x-java-object" -> "org.apache.camel.component.jackson3.avro.transform.AvroPojoDataTypeTransformer";
            case "avro-x-struct" -> "org.apache.camel.component.jackson3.avro.transform.AvroStructDataTypeTransformer";
            case "protobuf-binary" ->
                "org.apache.camel.component.jackson3.protobuf.transform.ProtobufBinaryDataTypeTransformer";
            case "protobuf-x-java-object" ->
                "org.apache.camel.component.jackson3.protobuf.transform.ProtobufPojoDataTypeTransformer";
            case "protobuf-x-struct" ->
                "org.apache.camel.component.jackson3.protobuf.transform.ProtobufStructDataTypeTransformer";
            default -> null;
        };
        return className != null ? context.getClassResolver().resolveClass(className) : null;
    }

    private Class<?> resolveJackson2TransformerClass(String key, CamelContext context) {
        String className = switch (key) {
            case "application-json" -> "org.apache.camel.component.jackson.transform.JsonDataTypeTransformer";
            case "application-x-java-object" -> "org.apache.camel.component.jackson.transform.JsonPojoDataTypeTransformer";
            case "application-x-struct" -> "org.apache.camel.component.jackson.transform.JsonStructDataTypeTransformer";
            case "avro-binary" -> "org.apache.camel.component.jackson.avro.transform.AvroBinaryDataTypeTransformer";
            case "avro-x-java-object" -> "org.apache.camel.component.jackson.avro.transform.AvroPojoDataTypeTransformer";
            case "avro-x-struct" -> "org.apache.camel.component.jackson.avro.transform.AvroStructDataTypeTransformer";
            case "protobuf-binary" -> "org.apache.camel.component.jackson.protobuf.transform.ProtobufBinaryDataTypeTransformer";
            case "protobuf-x-java-object" ->
                "org.apache.camel.component.jackson.protobuf.transform.ProtobufPojoDataTypeTransformer";
            case "protobuf-x-struct" ->
                "org.apache.camel.component.jackson.protobuf.transform.ProtobufStructDataTypeTransformer";
            default -> null;
        };
        return className != null ? context.getClassResolver().resolveClass(className) : null;
    }
}
