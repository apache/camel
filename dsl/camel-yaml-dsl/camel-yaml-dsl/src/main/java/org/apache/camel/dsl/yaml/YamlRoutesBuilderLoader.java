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
package org.apache.camel.dsl.yaml;

import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.util.URISupport;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMap;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {

    public static final String EXTENSION = "yaml";

    // API versions for Camel-K Integration and Kamelet Binding
    // we are lenient so lets just assume we can work with any of the v1 even if they evolve
    private static final String INTEGRATION_VERSION = "camel.apache.org/v1";
    private static final String BINDING_VERSION = "camel.apache.org/v1";
    private static final String STRIMZI_VERSION = "kafka.strimzi.io/v1";

    public YamlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    protected RouteBuilder builder(Node root) {
        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                Object target = preConfigureNode(root);
                if (target == null) {
                    return;
                }

                if (target instanceof Node) {
                    SequenceNode seq = asSequenceNode((Node) target);
                    for (Node node : seq.getValue()) {
                        Object item = getDeserializationContext().mandatoryResolve(node).construct(node);
                        doConfigure(item);
                    }
                } else {
                    doConfigure(target);
                }
            }

            private void doConfigure(Object item) throws Exception {
                if (item instanceof OutputAwareFromDefinition) {
                    RouteDefinition route = new RouteDefinition();
                    route.setInput(((OutputAwareFromDefinition) item).getDelegate());
                    route.setOutputs(((OutputAwareFromDefinition) item).getOutputs());

                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().route(route);
                } else if (item instanceof RouteDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().route((RouteDefinition) item);
                } else if (item instanceof CamelContextCustomizer) {
                    ((CamelContextCustomizer) item).configure(getCamelContext());
                } else if (item instanceof OnExceptionDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "onException must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getOnExceptions().add((OnExceptionDefinition) item);
                } else if (item instanceof ErrorHandlerBuilder) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "errorHandler must be defined before any routes in the RouteBuilder");
                    }
                    errorHandler((ErrorHandlerBuilder) item);
                } else if (item instanceof RouteTemplateDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteTemplateCollection(), getCamelContext());
                    getRouteTemplateCollection().routeTemplate((RouteTemplateDefinition) item);
                } else if (item instanceof RestDefinition) {
                    RestDefinition definition = (RestDefinition) item;
                    for (VerbDefinition verb : definition.getVerbs()) {
                        verb.setRest(definition);
                    }
                    CamelContextAware.trySetCamelContext(getRestCollection(), getCamelContext());
                    getRestCollection().rest(definition);
                } else if (item instanceof RestConfigurationDefinition) {
                    ((RestConfigurationDefinition) item).asRestConfiguration(
                            getCamelContext(),
                            getCamelContext().getRestConfiguration());
                }
            }

            @Override
            public void configuration() throws Exception {
                Object target = preConfigureNode(root);
                if (target == null) {
                    return;
                }

                if (target instanceof Node) {
                    SequenceNode seq = asSequenceNode((Node) target);
                    for (Node node : seq.getValue()) {
                        Object item = getDeserializationContext().mandatoryResolve(node).construct(node);
                        doConfiguration(item);
                    }
                } else {
                    doConfiguration(target);
                }
            }

            private void doConfiguration(Object item) {
                if (item instanceof RouteConfigurationDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteConfigurationCollection(), getCamelContext());
                    getRouteConfigurationCollection().routeConfiguration((RouteConfigurationDefinition) item);
                }
            }
        };
    }

    private Object preConfigureNode(Node root) throws Exception {
        Object target = root;

        // check if the yaml is a camel-k yaml with embedded binding/routes (called flow(s))
        if (Objects.equals(root.getNodeType(), NodeType.MAPPING)) {
            final MappingNode mn = YamlDeserializerSupport.asMappingNode(root);
            // camel-k: integration
            boolean integration = anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(INTEGRATION_VERSION)) &&
                    anyTupleMatches(mn.getValue(), "kind", "Integration");
            // camel-k: kamelet binding are still at v1alpha1
            boolean binding = anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(BINDING_VERSION)) &&
                    anyTupleMatches(mn.getValue(), "kind", "KameletBinding");
            if (integration) {
                target = preConfigureIntegration(root, target);
            } else if (binding) {
                target = preConfigureKameletBinding(root, target);
            }
        }

        return target;
    }

    /**
     * Camel K Integration file
     */
    private Object preConfigureIntegration(Node root, Object target) {
        Node routes = nodeAt(root, "/spec/flows");
        if (routes == null) {
            routes = nodeAt(root, "/spec/flow");
        }
        if (routes != null) {
            target = routes;
        }
        return target;
    }

    /**
     * Camel K Kamelet Binding file
     */
    private Object preConfigureKameletBinding(Node root, Object target) throws Exception {
        final RouteDefinition route = new RouteDefinition();
        String routeId = asText(nodeAt(root, "/metadata/name"));
        if (routeId != null) {
            route.routeId(routeId);
        }

        // kamelet binding is a bit more complex, so grab the source and sink
        // and map those to Camel route definitions
        MappingNode source = asMappingNode(nodeAt(root, "/spec/source"));
        MappingNode sink = asMappingNode(nodeAt(root, "/spec/sink"));
        if (source != null && sink != null) {
            // source at the beginning (mandatory)
            String from = extractCamelEndpointUri(source);
            route.from(from);

            // steps in the middle (optional)
            Node steps = nodeAt(root, "/spec/steps");
            if (steps != null) {
                SequenceNode sn = asSequenceNode(steps);
                for (Node node : sn.getValue()) {
                    MappingNode step = asMappingNode(node);
                    String uri = extractCamelEndpointUri(step);
                    if (uri != null) {
                        route.to(uri);
                    }
                }
            }

            // sink is at the end (mandatory)
            String to = extractCamelEndpointUri(sink);
            route.to(to);

            // is there any error handler?
            // TODO: set it globally via configuration so its inherited
            MappingNode errorHandler = asMappingNode(nodeAt(root, "/spec/errorHandler"));
            if (errorHandler != null) {
                // there are 5 different error handlers, which one is it
                NodeTuple nt = errorHandler.getValue().get(0);
                String ehName = asText(nt.getKeyNode());
                if ("dead-letter-channel".equals(ehName)) {
                    DeadLetterChannelBuilder dlcb = new DeadLetterChannelBuilder();
               }
            }

            target = route;
        }

        return target;
    }

    private String extractCamelEndpointUri(MappingNode node) throws Exception {
        MappingNode mn = null;
        Node ref = nodeAt(node, "/ref");
        if (ref != null) {
            mn = asMappingNode(ref);
        }

        // extract uri is different if kamelet or not
        boolean kamelet = mn != null && anyTupleMatches(mn.getValue(), "kind", "Kamelet");
        boolean strimzi
                = !kamelet && mn != null && anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(STRIMZI_VERSION))
                        && anyTupleMatches(mn.getValue(), "kind", "KafkaTopic");
        String uri;
        if (kamelet || strimzi) {
            uri = extractTupleValue(mn.getValue(), "name");
        } else {
            uri = extractTupleValue(node.getValue(), "uri");
        }

        // properties
        MappingNode prop = asMappingNode(nodeAt(node, "/properties"));
        Map<String, Object> params = asMap(prop);
        if (params != null && !params.isEmpty()) {
            String query = URISupport.createQueryString(params);
            uri = uri + "?" + query;
        }

        if (kamelet) {
            return "kamelet:" + uri;
        } else if (strimzi) {
            return "kafka:" + uri;
        } else {
            return uri;
        }
    }

}
