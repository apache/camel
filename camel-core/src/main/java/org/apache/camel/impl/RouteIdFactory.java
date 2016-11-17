/**
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

import java.util.List;
import java.util.Optional;

import org.apache.camel.NamedNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestBindingDefinition;
import org.apache.camel.spi.NodeIdFactory;

/**
 * Factory for generating route ids based on uris.
 * <p>
 * For direct/seda routes it returns route name (direct:start -> start).
 * For rest routes it returns its context path.
 * <p>
 * When id cannot be generated, falls back to other {@link NodeIdFactory} implementation.
 * If none is passed in the constructor, then {@link DefaultNodeIdFactory} is used.
 */
public class RouteIdFactory implements NodeIdFactory {

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
            Optional<String> id = extractId((RouteDefinition) definition);

            if (id.isPresent()) {
                return id.get();
            }

            id = extractIdFromRestDefinition((RouteDefinition) definition);

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
        List<FromDefinition> inputs = routeDefinition.getInputs();

        if (inputs == null || inputs.isEmpty()) {
            return Optional.empty();
        }

        FromDefinition from = inputs.get(0);
        String uri = from.getUri();

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
     * Extract id from rest route.
     */
    private Optional<String> extractIdFromRestDefinition(RouteDefinition route) {
        if (route.getOutputs().get(0) instanceof RestBindingDefinition) {
            if (route.getRestDefinition() == null) {
                return Optional.empty();
            }

            String path = route.getRestDefinition().getPath();

            if (path == null) {
                return Optional.empty();
            }

            if (path.indexOf('/') > 0) {
                return Optional.of(path.substring(0, path.indexOf('/')));
            }

            return Optional.of(path);
        }

        return Optional.empty();
    }
}
