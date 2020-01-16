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
package org.apache.camel.impl;

import java.util.Optional;

import org.apache.camel.NamedNode;
import org.apache.camel.impl.engine.DefaultNodeIdFactory;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.NodeIdFactory;

/**
 * Factory for generating route ids based on uris.
 * <p>
 * For direct/seda routes it returns route name (direct:start -> start). For
 * rest routes it returns its method and context path formatted as one string.
 * <p>
 * When id cannot be generated, falls back to other {@link NodeIdFactory}
 * implementation. If none is passed in the constructor, then
 * {@link DefaultNodeIdFactory} is used.
 */
public class RouteIdFactory implements NodeIdFactory {

    private static final char SEPARATOR = '-';
    private NodeIdFactory defaultNodeIdFactory;

    public RouteIdFactory() {
        defaultNodeIdFactory = new DefaultNodeIdFactory();
    }

    public RouteIdFactory(NodeIdFactory defaultNodeIdFactory) {
        this.defaultNodeIdFactory = defaultNodeIdFactory;
    }

    @Override
    public String createId(NamedNode definition) {
        if (definition instanceof RouteDefinition) {
            Optional<String> id = extractId((RouteDefinition)definition);

            if (id.isPresent()) {
                return id.get();
            }

            id = extractIdFromRestDefinition((RouteDefinition)definition);

            if (id.isPresent()) {
                return id.get();
            }
        }

        if (definition instanceof VerbDefinition) {
            Optional<String> id = extractIdFromVerb((VerbDefinition)definition);

            if (id.isPresent()) {
                return id.get();
            }
        }

        return defaultNodeIdFactory.createId(definition);
    }

    /**
     * Extract id from routes
     */
    private Optional<String> extractId(RouteDefinition routeDefinition) {
        if (routeDefinition.getRestDefinition() != null) {
            return Optional.empty();
        }

        if (routeDefinition.getInput() == null) {
            return Optional.empty();
        }

        FromDefinition from = routeDefinition.getInput();
        String uri = from.getEndpointUri();

        // we want to use the context-path of the route
        int colon = uri.indexOf(':');

        if (colon > 0) {
            String name = uri.substring(colon + 1);

            int questionMark = name.indexOf("?");

            if (questionMark > 0) {
                return Optional.of(name.substring(0, questionMark));
            } else {
                return Optional.of(name);
            }
        }

        return Optional.empty();
    }

    /**
     * Extract id from a rest route.
     */
    private Optional<String> extractIdFromRestDefinition(RouteDefinition route) {
        if (route.getRestDefinition() != null) {
            return extractIdFromInput(route);
        }

        return Optional.empty();
    }

    /**
     * Extract id from a rest verb definition.
     */
    private Optional<String> extractIdFromVerb(VerbDefinition verb) {
        RestDefinition restDefinition = verb.getRest();

        if (restDefinition != null) {
            StringBuilder routeId = new StringBuilder();
            routeId.append(verb.asVerb());
            appendWithSeparator(routeId, prepareUri(restDefinition.getPath()));

            if (verb.getUri() != null && verb.getUri().length() > 0) {
                appendWithSeparator(routeId, prepareUri(verb.getUri()));
            }

            verb.setUsedForGeneratingNodeId(true);

            return Optional.of(routeId.toString());
        }

        return Optional.empty();

    }

    /**
     * Extract id from rest input uri.
     */
    private Optional<String> extractIdFromInput(RouteDefinition route) {
        if (route.getInput() == null) {
            return Optional.empty();
        }

        FromDefinition from = route.getInput();
        String uri = from.getEndpointUri();

        String[] uriSplitted = uri.split(":");

        // needs to have at least 3 fields
        if (uriSplitted.length < 3) {
            return Optional.empty();
        }

        String verb = uriSplitted[1];
        String contextPath = uriSplitted[2];
        String additionalUri = "";

        if (uriSplitted.length > 3 && uriSplitted[3].startsWith("/")) {
            additionalUri = uriSplitted[3];
        }

        StringBuilder routeId = new StringBuilder(verb.length() + contextPath.length() + additionalUri.length());

        routeId.append(verb);
        appendWithSeparator(routeId, prepareUri(contextPath));

        if (additionalUri.length() > 0) {
            appendWithSeparator(routeId, prepareUri(additionalUri));
        }

        return Optional.of(routeId.toString());
    }

    /**
     * Prepares uri to be part of the id.
     */
    private String prepareUri(String uri) {
        if (uri == null) {
            return "";
        }

        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf('?'));
        }

        return uri.replace('/', SEPARATOR);
    }

    /**
     * Appends new element to the builder.
     */
    private void appendWithSeparator(StringBuilder builder, String str) {
        if (builder.charAt(builder.length() - 1) == SEPARATOR) {
            if (str.startsWith(String.valueOf(SEPARATOR))) {
                builder.append(str.replaceFirst(String.valueOf(SEPARATOR), ""));
            } else {
                builder.append(str);
            }
        } else {
            if (!str.startsWith(String.valueOf(SEPARATOR))) {
                builder.append(SEPARATOR);
            }

            builder.append(str);
        }
    }
}
