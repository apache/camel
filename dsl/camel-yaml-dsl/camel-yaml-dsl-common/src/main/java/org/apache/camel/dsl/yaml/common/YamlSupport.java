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
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.dsl.yaml.common.exception.InvalidEndpointException;
import org.apache.camel.dsl.yaml.common.exception.InvalidNodeTypeException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedNodeTypeException;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
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

    public static void setPropertiesOnTarget(CamelContext context, Object target, Map<String, Object> properties) {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");

        if (target instanceof CamelContext) {
            throw new UnsupportedOperationException("Configuring the Camel Context is not supported");
        }

        PropertyConfigurer configurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }

        if (configurer == null) {
            // see if there is a configurer for it
            configurer = context.adapt(ExtendedCamelContext.class)
                    .getConfigurerResolver()
                    .resolvePropertyConfigurer(target.getClass().getSimpleName(), context);
        }

        try {
            PropertyBindingSupport.build()
                    .withMandatory(true)
                    .withRemoveParameters(false)
                    .withConfigurer(configurer)
                    .withIgnoreCase(true)
                    .withFlattenProperties(true)
                    .bind(context, target, properties);
        } catch (PropertyBindingException e) {
            String key = e.getOptionKey();
            if (key == null) {
                String prefix = e.getOptionPrefix();
                if (prefix != null && !prefix.endsWith(".")) {
                    prefix = "." + prefix;
                }

                key = prefix != null
                        ? prefix + "." + e.getPropertyName()
                        : e.getPropertyName();
            }

            // enrich the error with more precise details with option prefix and key
            throw new PropertyBindingException(
                    e.getTarget(),
                    e.getPropertyName(),
                    e.getValue(),
                    null,
                    key,
                    e.getCause());
        }
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
        final EndpointUriFactory factory = context.adapt(ExtendedCamelContext.class).getEndpointUriFactory(scheme);

        try {
            if (factory != null && factory.isEnabled(scheme)) {
                if (scheme.equals(uri)) {
                    //
                    // if the uri is expressed as simple scheme, then we can use the
                    // discovered EndpointUriFactory to build the uri
                    //
                    answer = factory.buildUri(scheme, parameters, false);
                } else {
                    //
                    // otherwise we have to compose it but we can still leverage the
                    // discovered EndpointUriFactory to properly handle secrets
                    //
                    Map<String, Object> options = new TreeMap<>(parameters);

                    for (String secretParameter : factory.secretPropertyNames()) {
                        Object val = options.get(secretParameter);
                        if (val instanceof String) {
                            String newVal = (String) val;
                            if (!newVal.startsWith("#") && !newVal.startsWith("RAW(")) {
                                options.put(secretParameter, "RAW(" + val + ")");
                            }
                        }
                    }

                    answer += "?" + URISupport.createQueryString(options, false);
                }
            } else {
                answer += "?" + URISupport.createQueryString(parameters, false);
            }
        } catch (URISyntaxException e) {
            throw new InvalidEndpointException(node, "Error creating query", e);
        }

        return answer;
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
