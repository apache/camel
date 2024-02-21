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
package org.apache.camel.component.jslt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Function;
import com.schibsted.spt.data.jslt.JsltException;
import com.schibsted.spt.data.jslt.Parser;
import com.schibsted.spt.data.jslt.filters.DefaultJsonFilter;
import com.schibsted.spt.data.jslt.filters.JsonFilter;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Query or transform JSON payloads using JSLT.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "jslt", title = "JSLT", syntax = "jslt:resourceUri", producerOnly = true,
             remote = false, category = { Category.TRANSFORMATION }, headersClass = JsltConstants.class)
public class JsltEndpoint extends ResourceEndpoint {

    private static final ObjectMapper OBJECT_MAPPER;
    private static final JsonFilter DEFAULT_JSON_FILTER = new DefaultJsonFilter();

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.setSerializerFactory(OBJECT_MAPPER.getSerializerFactory().withSerializerModifier(
                new SafeTypesOnlySerializerModifier()));
    }

    private Expression transform;

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam(defaultValue = "false", label = "common")
    private boolean prettyPrint;
    @UriParam(defaultValue = "false")
    private boolean mapBigDecimalAsFloats;
    @UriParam
    private ObjectMapper objectMapper;

    public JsltEndpoint() {
    }

    public JsltEndpoint(String uri, JsltComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "jslt:" + getResourceUri();
    }

    private synchronized Expression getTransform(Message msg) throws Exception {
        final String jsltStringFromHeader
                = allowTemplateFromHeader ? msg.getHeader(JsltConstants.HEADER_JSLT_STRING, String.class) : null;

        final boolean useTemplateFromUri = jsltStringFromHeader == null;

        if (useTemplateFromUri && transform != null) {
            return transform;
        }

        final Collection<Function> functions = Objects.requireNonNullElse(
                ((JsltComponent) getComponent()).getFunctions(),
                Collections.emptyList());

        final JsonFilter objectFilter = Objects.requireNonNullElse(
                ((JsltComponent) getComponent()).getObjectFilter(),
                DEFAULT_JSON_FILTER);

        final String transformSource;
        final InputStream stream;

        if (useTemplateFromUri) {
            transformSource = getResourceUri();

            if (log.isDebugEnabled()) {
                log.debug("Jslt content read from resource {} with resourceUri: {} for endpoint {}",
                        transformSource,
                        transformSource,
                        getEndpointUri());
            }

            stream = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), transformSource);
            if (stream == null) {
                throw new JsltException("Cannot load resource '" + transformSource + "': not found");
            }
        } else { // use template from header
            stream = new ByteArrayInputStream(jsltStringFromHeader.getBytes(StandardCharsets.UTF_8));
            transformSource = "<inline>";
        }

        final Expression transform;
        try {
            transform = new Parser(new InputStreamReader(stream))
                    .withFunctions(functions)
                    .withObjectFilter(objectFilter)
                    .withSource(transformSource)
                    .compile();
        } finally {
            // the stream is consumed only on .compile(), cannot be closed before
            IOHelper.close(stream);
        }

        if (useTemplateFromUri) {
            this.transform = transform;
        }
        return transform;
    }

    public JsltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, JsltEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = null;
        if (allowTemplateFromHeader) {
            newResourceUri = exchange.getIn().getHeader(JsltConstants.HEADER_JSLT_RESOURCE_URI, String.class);
        }
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(JsltConstants.HEADER_JSLT_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", JsltConstants.HEADER_JSLT_RESOURCE_URI,
                    newResourceUri);
            JsltEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        JsonNode input;

        ObjectMapper objectMapper;
        if (ObjectHelper.isEmpty(getObjectMapper())) {
            objectMapper = new ObjectMapper();
        } else {
            objectMapper = getObjectMapper();
        }
        if (isMapBigDecimalAsFloats()) {
            objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        }

        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile) {
            body = ((WrappedFile<?>) body).getFile();
        }
        if (body instanceof String) {
            input = objectMapper.readTree((String) body);
        } else if (body instanceof Reader) {
            input = objectMapper.readTree((Reader) body);
        } else if (body instanceof File) {
            input = objectMapper.readTree((File) body);
        } else if (body instanceof byte[]) {
            input = objectMapper.readTree((byte[]) body);
        } else if (body instanceof InputStream) {
            input = objectMapper.readTree((InputStream) body);
        } else {
            throw new ValidationException(exchange, "Allowed body types are String, Reader, File, byte[] or InputStream.");
        }

        Map<String, JsonNode> variables = extractVariables(exchange);
        JsonNode output = getTransform(exchange.getMessage()).apply(variables, input);

        String result = isPrettyPrint() ? output.toPrettyString() : output.toString();
        ExchangeHelper.setInOutBodyPatternAware(exchange, result);
    }

    /**
     * Extract the variables from the headers in the message.
     */
    private Map<String, JsonNode> extractVariables(Exchange exchange) {
        Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
        Map<String, JsonNode> serializedVariableMap = new HashMap<>();
        if (variableMap.containsKey("headers")) {
            serializedVariableMap.put("headers", serializeMapToJsonNode((Map<String, Object>) variableMap.get("headers")));
        }
        if (variableMap.containsKey("variables")) {
            serializedVariableMap.put("variables", serializeMapToJsonNode((Map<String, Object>) variableMap.get("variables")));
        }
        if (variableMap.containsKey("exchange")) {
            Exchange ex = (Exchange) variableMap.get("exchange");
            ObjectNode exchangeNode = OBJECT_MAPPER.createObjectNode();
            if (ex.getProperties() != null) {
                exchangeNode.set("properties", serializeMapToJsonNode(ex.getProperties()));
            }
            serializedVariableMap.put("exchange", exchangeNode);
        }
        return serializedVariableMap;
    }

    private ObjectNode serializeMapToJsonNode(Map<String, Object> map) {
        ObjectNode mapNode = OBJECT_MAPPER.createObjectNode();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                try {
                    // Use Jackson to convert value to JsonNode
                    mapNode.set(entry.getKey(), OBJECT_MAPPER.valueToTree(entry.getValue()));
                } catch (IllegalArgumentException e) {
                    //If Jackson cannot convert the value to json (e.g. infinite recursion in the value to serialize)
                    log.debug("Value could not be converted to JsonNode", e);
                }
            }
        }
        return mapNode;
    }

    /**
     * If true, JSON in output message is pretty printed.
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public boolean isMapBigDecimalAsFloats() {
        return mapBigDecimalAsFloats;
    }

    /**
     * If true, the mapper will use the USE_BIG_DECIMAL_FOR_FLOATS in serialization features
     */
    public void setMapBigDecimalAsFloats(boolean mapBigDecimalAsFloats) {
        this.mapBigDecimalAsFloats = mapBigDecimalAsFloats;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Setting a custom JSON Object Mapper to be used
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static class SafeTypesOnlySerializerModifier extends BeanSerializerModifier {
        // Serialize only safe types: primitives, records, serializable objects and
        // collections/maps/arrays of them. To avoid serializing something like Response object.
        // Types that are not safe are serialized as their toString() value.
        @Override
        public JsonSerializer<?> modifySerializer(
                SerializationConfig config, BeanDescription beanDesc,
                JsonSerializer<?> serializer) {
            final Class<?> beanClass = beanDesc.getBeanClass();

            if (Collection.class.isAssignableFrom(beanClass)
                    || Map.class.isAssignableFrom(beanClass)
                    || beanClass.isArray()
                    || beanClass.isPrimitive()
                    || isRecord(beanClass)
                    || Serializable.class.isAssignableFrom(beanClass)) {
                return serializer;
            }

            return ToStringSerializer.instance;
        }

        private static boolean isRecord(Class<?> clazz) {
            final Class<?> parent = clazz.getSuperclass();
            return parent != null && parent.getName().equals("java.lang.Record");
        }
    }
}
