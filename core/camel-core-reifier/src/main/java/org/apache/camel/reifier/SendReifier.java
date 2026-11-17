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
package org.apache.camel.reifier;

import java.io.IOException;
import java.util.Map;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LineNumberAware;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendReifier extends ProcessorReifier<ToDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(SendReifier.class);

    /** The component whose path is a script in another language, not an address: language:simple:Hello ${body}. */
    private static final String SCRIPT_PATH = "language";

    public SendReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (ToDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // route templates with optional URI should skip the processor if the URI is not provided
        RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
        if (rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            String rawUri = definition.getEndpointUri();
            if (isOptionalUriAndNotResolved(camelContext, rawUri)) {
                return null;
            }
        }

        Endpoint endpoint = resolveEndpoint();
        warnIfExpressionInUri();
        SendProcessor answer = new SendProcessor(endpoint, parse(ExchangePattern.class, definition.getPattern()));
        answer.setDisabled(isDisabled(camelContext, definition));
        answer.setVariableSend(parseString(definition.getVariableSend()));
        answer.setVariableReceive(parseString(definition.getVariableReceive()));
        return answer;
    }

    public Endpoint resolveEndpoint() {
        Endpoint answer;
        if (definition.getEndpoint() == null) {
            if (definition.getEndpointProducerBuilder() == null) {
                answer = CamelContextHelper.resolveEndpoint(camelContext, definition.getEndpointUri(), null);
            } else {
                answer = definition.getEndpointProducerBuilder().resolve(camelContext);
            }
        } else {
            answer = definition.getEndpoint();
        }
        LineNumberAware.trySetLineNumberAware(answer, definition);
        return answer;
    }

    /**
     * A to resolves its endpoint once, when the route is created, so a Simple expression in the path of its uri is not
     * evaluated for each message: every message goes to the one endpoint named by the text of the expression. Logs a
     * WARN that says to use toD, unless the component evaluates its path itself. It is the rule camel validate checks
     * the routes with: only the path is looked at, not the options after the ?.
     */
    private void warnIfExpressionInUri() {
        if (definition.getEndpoint() != null) {
            // an endpoint given as an object, not a uri written in the route
            return;
        }
        try {
            String uri = definition.getEndpointUri();
            if (uri == null) {
                return;
            }
            String resolved = uri;
            if (uri.contains(PropertiesComponent.PREFIX_TOKEN)) {
                resolved = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
            }
            String expression = expressionInPath(resolved);
            if (expression != null && !pathTakesExpression(resolved.substring(0, resolved.indexOf(':')))) {
                LOG.warn("The to in route {} sends to {}, whose uri has the expression {}: a to resolves its endpoint"
                         + " once, when the route is created, so the expression is not evaluated for each message, and"
                         + " every message goes to the endpoint named by its text. Use toD to compute the endpoint uri"
                         + " for each message.",
                        route.getRouteId(), URISupport.sanitizeUri(uri), expression);
            }
        } catch (Exception e) {
            LOG.debug("Cannot check the uri of {} for an expression. This exception is ignored.", definition, e);
        }
    }

    /**
     * The first Simple expression in the path of the uri (what comes before its options), or null when there is none.
     * Null for the language component too, whose path is a script. A :#${...} or :#in:${...} is skipped: it is a
     * parameter the sql components bind for each message, not a part of the address.
     */
    private static String expressionInPath(String uri) {
        int question = uri.indexOf('?');
        String path = question >= 0 ? uri.substring(0, question) : uri;
        int colon = path.indexOf(':');
        if (colon <= 0 || SCRIPT_PATH.equals(path.substring(0, colon))) {
            return null;
        }
        for (int i = path.indexOf('$', colon); i >= 0; i = path.indexOf('$', i + 1)) {
            if ((path.startsWith("${", i) || path.startsWith("$simple{", i))
                    && !path.startsWith(":#", i - 2) && !path.startsWith(":#in:", i - 5)) {
                int end = path.indexOf('}', i);
                return end > 0 ? path.substring(i, end + 1) : path.substring(i);
            }
        }
        return null;
    }

    /**
     * Whether the component may evaluate its path for each message: a path option of its metadata supports a Simple
     * expression (micrometer, sql-stored), or the component has no metadata, and then nothing is said rather than the
     * wrong thing.
     */
    private boolean pathTakesExpression(String scheme) throws IOException {
        if (!(camelContext instanceof CatalogCamelContext catalog)) {
            return true;
        }
        String json = catalog.getComponentParameterJsonSchema(scheme);
        JsonObject root = json != null ? Jsoner.deserialize(json, (JsonObject) null) : null;
        Map<String, Object> properties = root != null ? root.getMap("properties") : null;
        if (properties == null) {
            return true;
        }
        for (Object value : properties.values()) {
            if (value instanceof JsonObject option && "path".equals(option.getString("kind"))
                    && Boolean.TRUE.equals(option.getBoolean("supportSimpleExpression"))) {
                return true;
            }
        }
        return false;
    }

}
