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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerProperties;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.PropertyBindingSupport;
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
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

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

    protected RouteBuilder builder(final Node root, final Resource resource) {

        // we need to keep track of already configured items as the yaml-dsl returns a
        // RouteConfigurationBuilder that is capable of both route and route configurations
        // which can lead to the same items being processed twice
        final Set<Integer> indexes = new HashSet<>();

        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                getDeserializationContext().setResource(resource);
                setDeserializationContext(root, getDeserializationContext());

                Object target = preConfigureNode(root);
                if (target == null) {
                    return;
                }

                if (target instanceof Node) {
                    SequenceNode seq = asSequenceNode((Node) target);
                    for (Node node : seq.getValue()) {
                        int idx = -1;
                        if (node.getStartMark().isPresent()) {
                            idx = node.getStartMark().get().getIndex();
                        }
                        if (idx == -1 || !indexes.contains(idx)) {
                            Object item = getDeserializationContext().mandatoryResolve(node).construct(node);
                            boolean accepted = doConfigure(item);
                            if (accepted && idx != -1) {
                                indexes.add(idx);
                            }
                        }
                    }
                } else {
                    doConfigure(target);
                }
            }

            private boolean doConfigure(Object item) throws Exception {
                if (item instanceof OutputAwareFromDefinition) {
                    RouteDefinition route = new RouteDefinition();
                    route.setInput(((OutputAwareFromDefinition) item).getDelegate());
                    route.setOutputs(((OutputAwareFromDefinition) item).getOutputs());

                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().route(route);
                    return true;
                } else if (item instanceof RouteDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().route((RouteDefinition) item);
                    return true;
                } else if (item instanceof CamelContextCustomizer) {
                    ((CamelContextCustomizer) item).configure(getCamelContext());
                    return true;
                } else if (item instanceof OnExceptionDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "onException must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getOnExceptions().add((OnExceptionDefinition) item);
                    return true;
                } else if (item instanceof ErrorHandlerBuilder) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "errorHandler must be defined before any routes in the RouteBuilder");
                    }
                    errorHandler((ErrorHandlerBuilder) item);
                    return true;
                } else if (item instanceof RouteTemplateDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteTemplateCollection(), getCamelContext());
                    getRouteTemplateCollection().routeTemplate((RouteTemplateDefinition) item);
                    return true;
                } else if (item instanceof RestDefinition) {
                    RestDefinition definition = (RestDefinition) item;
                    for (VerbDefinition verb : definition.getVerbs()) {
                        verb.setRest(definition);
                    }
                    CamelContextAware.trySetCamelContext(getRestCollection(), getCamelContext());
                    getRestCollection().rest(definition);
                    return true;
                } else if (item instanceof RestConfigurationDefinition) {
                    ((RestConfigurationDefinition) item).asRestConfiguration(
                            getCamelContext(),
                            getCamelContext().getRestConfiguration());
                    return true;
                }

                return false;
            }

            @Override
            public void configuration() throws Exception {
                getDeserializationContext().setResource(resource);
                setDeserializationContext(root, getDeserializationContext());

                Object target = preConfigureNode(root);
                if (target == null) {
                    return;
                }

                if (target instanceof Node) {
                    SequenceNode seq = asSequenceNode((Node) target);
                    for (Node node : seq.getValue()) {
                        int idx = -1;
                        if (node.getStartMark().isPresent()) {
                            idx = node.getStartMark().get().getIndex();
                        }
                        if (idx == -1 || !indexes.contains(idx)) {
                            Object item = getDeserializationContext().mandatoryResolve(node).construct(node);
                            boolean accepted = doConfiguration(item);
                            if (accepted && idx != -1) {
                                indexes.add(idx);
                            }
                        }
                    }
                } else {
                    doConfiguration(target);
                }
            }

            private boolean doConfiguration(Object item) {
                if (item instanceof RouteConfigurationDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteConfigurationCollection(), getCamelContext());
                    getRouteConfigurationCollection().routeConfiguration((RouteConfigurationDefinition) item);
                    return true;
                }
                return false;
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
                        // if kamelet then use kamelet eip instead of to
                        boolean kamelet = uri.startsWith("kamelet:");
                        if (kamelet) {
                            uri = uri.substring(8);
                            route.kamelet(uri);
                        } else {
                            route.to(uri);
                        }
                    }
                }
            }

            // sink is at the end (mandatory)
            String to = extractCamelEndpointUri(sink);
            route.to(to);

            // is there any error handler?
            MappingNode errorHandler = asMappingNode(nodeAt(root, "/spec/errorHandler"));
            if (errorHandler != null) {
                // there are 5 different error handlers, which one is it
                NodeTuple nt = errorHandler.getValue().get(0);
                String ehName = asText(nt.getKeyNode());

                DefaultErrorHandlerProperties ehb = null;
                if ("dead-letter-channel".equals(ehName)) {
                    DeadLetterChannelBuilder dlch = new DeadLetterChannelBuilder();
                    // endpoint
                    MappingNode endpoint = asMappingNode(nodeAt(nt.getValueNode(), "/endpoint"));
                    String dlq = extractCamelEndpointUri(endpoint);
                    dlch.setDeadLetterUri(dlq);
                    ehb = dlch;
                } else if ("log".equals(ehName)) {
                    // log is the default error handler
                    ehb = new DefaultErrorHandlerBuilder();
                } else if ("none".equals(ehName)) {
                    route.errorHandler(new NoErrorHandlerBuilder());
                } else if ("bean".equals(ehName)) {
                    throw new IllegalArgumentException("Bean error handler is not supported");
                } else if ("ref".equals(ehName)) {
                    throw new IllegalArgumentException("Ref error handler is not supported");
                }

                // some error handlers support additional parameters
                if (ehb != null) {
                    // properties that are general for all kind of error handlers
                    MappingNode prop = asMappingNode(nodeAt(nt.getValueNode(), "/parameters"));
                    Map<String, Object> params = asMap(prop);
                    if (params != null) {
                        PropertyBindingSupport.build()
                                .withIgnoreCase(true)
                                .withFluentBuilder(true)
                                .withRemoveParameters(true)
                                .withCamelContext(getCamelContext())
                                .withTarget(ehb)
                                .withProperties(params)
                                .bind();
                    }
                    route.errorHandler(ehb);
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
