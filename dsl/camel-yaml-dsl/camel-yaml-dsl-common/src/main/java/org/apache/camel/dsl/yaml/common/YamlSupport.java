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
package org.apache.camel.dsl.yaml.common;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.exception.InvalidEndpointException;
import org.apache.camel.dsl.yaml.common.exception.InvalidNodeTypeException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedNodeTypeException;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.getDeserializationContext;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.parseParameters;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setSteps;

public final class YamlSupport {

    private YamlSupport() {
    }

    public static CamelContextCustomizer customizer(Collection<CamelContextCustomizer> customizers) {
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                for (CamelContextCustomizer customizer : customizers) {
                    customizer.configure(camelContext);
                }
            }
        };
    }

    public static String createEndpointUri(CamelContext context, Node node, String uri, Map<String, Object> parameters) {
        if (uri == null) {
            throw new InvalidEndpointException(node, "Uri is mandatory");
        }

        String answer = uri;

        if (parameters == null || parameters.isEmpty()) {
            //
            // nothing to do here, there are no parameters so we can return the
            // uri as it is.
            //
            return answer;
        }
        if (uri.indexOf('?') != -1) {
            //
            // we support URIs defined as scheme only or scheme and path params,
            // query params are not supported so a definition like:
            //
            // - from:
            //     uri: "foo:bar?option1=value1"
            //     parameters:
            //         option2: value2
            //
            // is not supported and leads to an InvalidEndpointException being thrown.
            //
            throw new InvalidEndpointException(node, "Uri should not contains query parameters (uri: " + uri + ")");
        }

        final String scheme = uri.contains(":") ? StringHelper.before(uri, ":") : uri;
        final EndpointUriFactory factory = context.getCamelContextExtension().getEndpointUriFactory(scheme);

        try {
            if (factory != null && factory.isEnabled(scheme)) {
                if (scheme.equals(uri)) {
                    //
                    // if the uri is expressed as simple scheme, then we can use the
                    // discovered EndpointUriFactory to build the uri
                    //
                    answer = buildUri(factory, scheme, parameters);
                } else {
                    //
                    // otherwise we have to compose it but we can still leverage the
                    // discovered EndpointUriFactory to properly handle secrets
                    //
                    // in the order the parameters are written
                    Map<String, Object> options = new LinkedHashMap<>(parameters);

                    for (String secretParameter : factory.secretPropertyNames()) {
                        Object val = options.get(secretParameter);
                        if (val instanceof String) {
                            String newVal = (String) val;
                            if (!newVal.startsWith("#") && !newVal.startsWith("RAW(")) {
                                options.put(secretParameter, "RAW(" + val + ")");
                            }
                        }
                    }
                    // keep a value with & whole (a secret option is in RAW already)
                    options.replaceAll((key, value) -> rawValue(value));

                    // flatten multiValue map parameters, where the map parameter is written
                    Map<String, String> prefixes = factory.multiValuePrefixes();
                    Map<String, Object> flat = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> option : options.entrySet()) {
                        String prefix = prefixes.get(option.getKey());
                        if (prefix != null && option.getValue() instanceof Map<?, ?> m) {
                            for (var entry : m.entrySet()) {
                                if (entry.getValue() != null) {
                                    flat.put(prefix + entry.getKey(), entry.getValue());
                                }
                            }
                        } else {
                            flat.put(option.getKey(), option.getValue());
                        }
                    }

                    answer += "?" + URISupport.createQueryString(flat, false);
                }
            } else {
                answer += "?" + createQueryString(parameters);
            }
        } catch (URISyntaxException e) {
            throw new InvalidEndpointException(node, "Error creating query", e);
        }

        return answer;
    }

    /**
     * Creates the query of an endpoint uri from the parameters, not encoded and in the order they are written. A value
     * with & is put in RAW to keep it whole.
     */
    public static String createQueryString(Map<String, Object> parameters) {
        Map<String, Object> options = new LinkedHashMap<>(parameters);
        options.replaceAll((key, value) -> rawValue(value));
        return URISupport.createQueryString(options, false);
    }

    /**
     * Builds the uri with the factory, which puts the path parameters in the path and the other parameters in the
     * query. A value with & is put in RAW to keep it whole in the query. In the path an & does no harm, and RAW would
     * become part of the value, so a parameter that the factory puts in the path is built again as it is written.
     */
    private static String buildUri(EndpointUriFactory factory, String scheme, Map<String, Object> parameters)
            throws URISyntaxException {
        // in the same order: as written, or sorted when the parameters have no order (as the factory does)
        Map<String, Object> options
                = parameters instanceof LinkedHashMap ? new LinkedHashMap<>(parameters) : new TreeMap<>(parameters);
        // the factory puts a secret option in RAW itself
        Set<String> secrets = factory.secretPropertyNames();
        options.replaceAll((key, value) -> secrets.contains(key) ? value : rawValue(value));
        String answer = factory.buildUri(scheme, options, false);

        boolean path = false;
        for (Map.Entry<String, Object> option : options.entrySet()) {
            Object value = parameters.get(option.getKey());
            if (!Objects.equals(option.getValue(), value) && !isInQuery(answer, option.getKey(), option.getValue())) {
                option.setValue(value);
                path = true;
            }
        }
        return path ? factory.buildUri(scheme, options, false) : answer;
    }

    /**
     * Whether the option is in the query of the uri, written as the factory writes it.
     */
    private static boolean isInQuery(String uri, String key, Object value) {
        String option = URISupport.createQueryString(Map.of(key, value), false);
        return uri.contains("?" + option) || uri.contains("&" + option);
    }

    /**
     * The value in RAW when it is a text with &, as the & would end the value in the query of the uri. This is
     * RAW(value), or RAW{value} when the value has )& which would end RAW(value) too soon.
     * <p/>
     * Other values are returned as they are, and so is a bean reference (#name), a value in RAW already, and a value
     * that no RAW form can keep whole: it has both )& and }&, or it has )& and starts with { (RAW{{ is the start of a
     * property placeholder).
     */
    private static Object rawValue(Object value) {
        if (value instanceof String s && s.indexOf('&') != -1 && !s.startsWith("#") && !s.startsWith("RAW(")
                && !s.startsWith("RAW{")) {
            // a RAW value ends at the first closing bracket that is followed by & or the end of the uri
            if (!s.contains(")&")) {
                return "RAW(" + s + ")";
            } else if (!s.contains("}&") && !s.startsWith("{")) {
                return "RAW{" + s + "}";
            }
        }
        return value;
    }

    public static Node setProperty(Node node, String key, Object value) {
        node.setProperty(key, value);

        if (node instanceof MappingNode) {
            for (NodeTuple item : ((MappingNode) node).getValue()) {
                item.getValueNode().setProperty(key, value);
            }
        } else if (node instanceof SequenceNode) {
            for (Node item : ((SequenceNode) node).getValue()) {
                item.setProperty(key, value);
            }
        }

        return node;
    }

    public static String creteEndpointUri(Node node, RouteDefinition route) {
        String answer = null;

        if (node.getNodeType() == NodeType.SCALAR) {
            answer = asText(node);
        } else if (node.getNodeType() == NodeType.MAPPING) {
            final MappingNode mn = (MappingNode) node;
            final YamlDeserializationContext dc = getDeserializationContext(node);

            String uri = null;
            Map<String, Object> parameters = null;

            for (NodeTuple tuple : mn.getValue()) {
                final String key = asText(tuple.getKeyNode());
                final Node val = tuple.getValueNode();

                setDeserializationContext(val, dc);

                switch (key) {
                    case "id":
                        // ignore
                        break;
                    case "description":
                        // ignore
                        break;
                    case "note":
                        // ignore
                        break;
                    case "uri":
                        uri = asText(val);
                        break;
                    case "parameters":
                        parameters = parseParameters(tuple);
                        break;
                    case "steps":
                        // steps must be set on the route
                        setSteps(route, val);
                        break;
                    case "variableReceive":
                        // is handled in FromDefinitionSerializer
                        break;
                    default:
                        throw new UnsupportedFieldException(val, key);
                }
            }
            answer = YamlSupport.createEndpointUri(dc.getCamelContext(), node, uri, parameters);
        }

        return answer;
    }

    public static String creteEndpointUri(String scheme, Node node) {
        switch (node.getNodeType()) {
            case SCALAR:
                return scheme + ':' + YamlDeserializerSupport.asText(node);
            case MAPPING:
                final YamlDeserializationContext dc = YamlDeserializerSupport.getDeserializationContext(node);
                final MappingNode bn = YamlDeserializerSupport.asMappingNode(node);
                final Map<String, Object> parameters = new HashMap<>();

                for (NodeTuple tuple : bn.getValue()) {
                    final String key = YamlDeserializerSupport.asText(tuple.getKeyNode());
                    final Node val = tuple.getValueNode();

                    if (val.getNodeType() == NodeType.SCALAR) {
                        parameters.put(StringHelper.dashToCamelCase(key), YamlDeserializerSupport.asText(val));
                    } else {
                        throw new InvalidNodeTypeException(node, NodeType.SCALAR);
                    }
                }

                return YamlSupport.createEndpointUri(dc.getCamelContext(), node, scheme, parameters);
            default:
                throw new UnsupportedNodeTypeException(node);
        }
    }

    public static <T> T creteEndpoint(String scheme, Node node, Function<String, T> constructor) {
        return constructor.apply(
                creteEndpointUri(scheme, node));
    }

    public static ConstructNode creteEndpointConstructor(String scheme, Function<String, Object> constructor) {
        return node -> creteEndpoint(scheme, node, constructor);
    }
}
