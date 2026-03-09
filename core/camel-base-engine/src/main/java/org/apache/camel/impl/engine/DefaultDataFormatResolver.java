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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.ResolverHelper;

/**
 * Default data format resolver
 */
public class DefaultDataFormatResolver implements DataFormatResolver {

    public static final String DATAFORMAT_RESOURCE_PATH = "META-INF/services/org/apache/camel/dataformat/";

    private FactoryFinder dataformatFactory;

    @Override
    public DataFormat createDataFormat(String name, CamelContext context) {
        DataFormat dataFormat = null;

        // lookup in registry first
        DataFormatFactory dataFormatFactory = ResolverHelper.lookupDataFormatFactoryInRegistryWithFallback(context, name);
        if (dataFormatFactory != null) {
            dataFormat = dataFormatFactory.newInstance();
        }

        if (dataFormat == null) {
            dataFormat = createDataFormatFromResource(name, context);
        }

        return dataFormat;
    }

    private DataFormat createDataFormatFromResource(String name, CamelContext context) {
        DataFormat dataFormat = null;

        Class<?> type;
        try {
            if (dataformatFactory == null) {
                dataformatFactory = context.getCamelContextExtension().getFactoryFinder(DATAFORMAT_RESOURCE_PATH);
            }
            type = dataformatFactory.findClass(name).orElse(null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no DataFormat registered for scheme: " + name, e);
        }

        if (type == null) {
            type = context.getClassResolver().resolveClass(name);
        }

        // Special handling for Jackson-based data formats - detect which implementation is available
        if (type == null && isJacksonDataFormat(name)) {
            type = detectJacksonDataFormat(name, context);
        }

        if (type != null) {
            if (DataFormat.class.isAssignableFrom(type)) {
                dataFormat = (DataFormat) context.getInjector().newInstance(type, false);
            } else {
                throw new IllegalArgumentException(
                        "Resolving dataformat: " + name + " detected type conflict: Not a DataFormat implementation. Found: "
                                                   + type.getName());
            }
        }

        return dataFormat;
    }

    /**
     * Checks if the data format name is a Jackson-based data format that needs version detection.
     *
     * @param  name the data format name
     * @return      true if this is a Jackson-based data format
     */
    private boolean isJacksonDataFormat(String name) {
        return "jackson".equals(name)
                || "avroJackson".equals(name)
                || "jacksonXml".equals(name)
                || "protobufJackson".equals(name);
    }

    /**
     * Detects which Jackson implementation is available on the classpath and returns the appropriate DataFormat class.
     * Tries Jackson 3.x first (tools.jackson), then falls back to Jackson 2.x (com.fasterxml.jackson).
     *
     * @param  name    the data format name
     * @param  context the CamelContext
     * @return         the Jackson DataFormat class, or null if neither is available
     */
    private Class<?> detectJacksonDataFormat(String name, CamelContext context) {
        // Try Jackson 3.x first (tools.jackson.databind.ObjectMapper)
        Class<?> jackson3Marker = context.getClassResolver().resolveClass("tools.jackson.databind.ObjectMapper");
        if (jackson3Marker != null) {
            // Jackson 3.x is available, use camel-jackson3 variants
            return resolveJackson3DataFormatClass(name, context);
        }

        // Try Jackson 2.x (com.fasterxml.jackson.databind.ObjectMapper)
        Class<?> jackson2Marker = context.getClassResolver().resolveClass("com.fasterxml.jackson.databind.ObjectMapper");
        if (jackson2Marker != null) {
            // Jackson 2.x is available, use camel-jackson variants
            return resolveJackson2DataFormatClass(name, context);
        }

        // Neither Jackson version is available
        return null;
    }

    private Class<?> resolveJackson3DataFormatClass(String name, CamelContext context) {
        String className = switch (name) {
            case "jackson" -> "org.apache.camel.component.jackson3.JacksonDataFormat";
            case "avroJackson" -> "org.apache.camel.component.jackson3.avro.JacksonAvroDataFormat";
            case "jacksonXml" -> "org.apache.camel.component.jackson3xml.JacksonXMLDataFormat";
            case "protobufJackson" -> "org.apache.camel.component.jackson3.protobuf.JacksonProtobufDataFormat";
            default -> null;
        };
        return className != null ? context.getClassResolver().resolveClass(className) : null;
    }

    private Class<?> resolveJackson2DataFormatClass(String name, CamelContext context) {
        String className = switch (name) {
            case "jackson" -> "org.apache.camel.component.jackson.JacksonDataFormat";
            case "avroJackson" -> "org.apache.camel.component.jackson.avro.JacksonAvroDataFormat";
            case "jacksonXml" -> "org.apache.camel.component.jacksonxml.JacksonXMLDataFormat";
            case "protobufJackson" -> "org.apache.camel.component.jackson.protobuf.JacksonProtobufDataFormat";
            default -> null;
        };
        return className != null ? context.getClassResolver().resolveClass(className) : null;
    }

}
