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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Function;
import com.schibsted.spt.data.jslt.JsltException;
import com.schibsted.spt.data.jslt.Parser;
import com.schibsted.spt.data.jslt.filters.JsonFilter;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Query or transform JSON payloads using an JSLT.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "jslt", title = "JSLT", syntax = "jslt:resourceUri", producerOnly = true,
             category = { Category.TRANSFORMATION })
public class JsltEndpoint extends ResourceEndpoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private Expression transform;

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam(defaultValue = "false", label = "common")
    private boolean prettyPrint;

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
        if (transform == null) {
            if (log.isDebugEnabled()) {
                String path = getResourceUri();
                log.debug("Jslt content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(), path,
                        getEndpointUri());
            }

            String jsltStringFromHeader
                    = allowTemplateFromHeader ? msg.getHeader(JsltConstants.HEADER_JSLT_STRING, String.class) : null;
            Collection<Function> functions = ((JsltComponent) getComponent()).getFunctions();
            JsonFilter objectFilter = ((JsltComponent) getComponent()).getObjectFilter();

            Parser parser;
            InputStream stream = null;
            try {
                if (jsltStringFromHeader != null) {
                    parser = new Parser(new StringReader(jsltStringFromHeader)).withSource("<inline>");
                } else {
                    stream = JsltEndpoint.class.getClassLoader().getResourceAsStream(getResourceUri());
                    if (stream == null) {
                        throw new JsltException("Cannot load resource '" + getResourceUri() + "': not found");
                    }
                    Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                    parser = new Parser(reader).withSource(getResourceUri());
                }
                if (functions != null) {
                    parser = parser.withFunctions(functions);
                }
                if (objectFilter != null) {
                    parser = parser.withObjectFilter(objectFilter);
                }
                this.transform = parser.compile();
            } finally {
                IOHelper.close(stream);
            }
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

        ObjectMapper objectMapper = new ObjectMapper();
        if (exchange.getIn().getBody() instanceof String) {
            input = objectMapper.readTree(exchange.getIn().getBody(String.class));
        } else if (exchange.getIn().getBody() instanceof InputStream) {
            input = objectMapper.readTree(exchange.getIn().getBody(InputStream.class));
        } else {
            log.debug("Body content is not String neither InputStream.");
            throw new ValidationException(exchange, "Allowed body types are String or InputStream.");
        }

        Map<String, JsonNode> variables = extractVariables(exchange);

        JsonNode output = getTransform(exchange.getMessage()).apply(variables, input);

        Message out = exchange.getMessage();
        out.setBody(isPrettyPrint() ? output.toPrettyString() : output.toString());
        out.setHeaders(exchange.getIn().getHeaders());
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

}
