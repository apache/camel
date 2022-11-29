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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.exception.InvalidEndpointException;
import org.apache.camel.dsl.yaml.common.exception.InvalidNodeTypeException;
import org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMap;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asStringList;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.isSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {

    public static final String EXTENSION = "yaml";

    private static final Logger LOG = LoggerFactory.getLogger(YamlRoutesBuilderLoader.class);

    // API versions for Camel-K Integration and Kamelet Binding
    // we are lenient so lets just assume we can work with any of the v1 even if they evolve
    private static final String INTEGRATION_VERSION = "camel.apache.org/v1";
    private static final String BINDING_VERSION = "camel.apache.org/v1";
    private static final String STRIMZI_VERSION = "kafka.strimzi.io/v1";
    private static final String KNATIVE_VERSION = "messaging.knative.dev/v1";

    public YamlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    YamlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    protected RouteBuilder builder(final YamlDeserializationContext ctx, final Node root) {

        // we need to keep track of already configured items as the yaml-dsl returns a
        // RouteConfigurationBuilder that is capable of both route and route configurations
        // which can lead to the same items being processed twice
        final Set<Integer> indexes = new HashSet<>();

        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                setDeserializationContext(root, ctx);

                Object target = preConfigureNode(root, ctx, false);
                if (target == null) {
                    return;
                }

                Iterator<?> it = ObjectHelper.createIterator(target);
                while (it.hasNext()) {
                    target = it.next();
                    if (target instanceof Node && isSequenceNode((Node) target)) {
                        SequenceNode seq = asSequenceNode((Node) target);
                        for (Node node : seq.getValue()) {
                            int idx = -1;
                            if (node.getStartMark().isPresent()) {
                                idx = node.getStartMark().get().getIndex();
                            }
                            if (idx == -1 || !indexes.contains(idx)) {
                                Object item = ctx.mandatoryResolve(node).construct(node);
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
                } else if (item instanceof InterceptFromDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "interceptFrom must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getInterceptFroms().add((InterceptFromDefinition) item);
                    return true;
                } else if (item instanceof InterceptDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "intercept must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getIntercepts().add((InterceptDefinition) item);
                    return true;
                } else if (item instanceof InterceptSendToEndpointDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "interceptSendToEndpoint must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getInterceptSendTos().add((InterceptSendToEndpointDefinition) item);
                    return true;
                } else if (item instanceof OnCompletionDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "onCompletion must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getOnCompletions().add((OnCompletionDefinition) item);
                    return true;
                } else if (item instanceof OnExceptionDefinition) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "onException must be defined before any routes in the RouteBuilder");
                    }
                    CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                    getRouteCollection().getOnExceptions().add((OnExceptionDefinition) item);
                    return true;
                } else if (item instanceof ErrorHandlerFactory) {
                    if (!getRouteCollection().getRoutes().isEmpty()) {
                        throw new IllegalArgumentException(
                                "errorHandler must be defined before any routes in the RouteBuilder");
                    }
                    errorHandler((ErrorHandlerFactory) item);
                    return true;
                } else if (item instanceof RouteTemplateDefinition) {
                    CamelContextAware.trySetCamelContext(getRouteTemplateCollection(), getCamelContext());
                    getRouteTemplateCollection().routeTemplate((RouteTemplateDefinition) item);
                    return true;
                } else if (item instanceof TemplatedRouteDefinition) {
                    CamelContextAware.trySetCamelContext(getTemplatedRouteCollection(), getCamelContext());
                    getTemplatedRouteCollection().templatedRoute((TemplatedRouteDefinition) item);
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
                setDeserializationContext(root, ctx);

                Object target = preConfigureNode(root, ctx, false);
                if (target == null) {
                    return;
                }

                Iterator<?> it = ObjectHelper.createIterator(target);
                while (it.hasNext()) {
                    target = it.next();
                    if (target instanceof Node && isSequenceNode((Node) target)) {
                        SequenceNode seq = asSequenceNode((Node) target);
                        for (Node node : seq.getValue()) {
                            int idx = -1;
                            if (node.getStartMark().isPresent()) {
                                idx = node.getStartMark().get().getIndex();
                            }
                            if (idx == -1 || !indexes.contains(idx)) {
                                Object item = ctx.mandatoryResolve(node).construct(node);
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

    private Object preConfigureNode(Node root, YamlDeserializationContext ctx, boolean preParse) {
        // backwards compatible fixes
        Object target = routeBackwardsCompatible(root, ctx, preParse);

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
                target = preConfigureIntegration(root, ctx, target, preParse);
            } else if (binding && !preParse) {
                // kamelet binding does not take part in pre-parse phase
                target = preConfigureKameletBinding(root, ctx, target);
            }
        }

        return target;
    }

    private Object routeBackwardsCompatible(Node root, YamlDeserializationContext ctx, boolean preParse) {
        Object target = root;

        // look for every route (the root must be a sequence)
        if (!isSequenceNode(root)) {
            return target;
        }

        SequenceNode seq = asSequenceNode(root);
        for (Node node : seq.getValue()) {
            Node route = nodeAt(node, "/route");
            if (route != null) {
                // backwards compatible steps are under route, but should be under from
                Node steps = nodeAt(route, "/steps");
                if (steps != null) {
                    int line = -1;
                    if (steps.getStartMark().isPresent()) {
                        line = steps.getStartMark().get().getLine();
                    }
                    String file = ctx.getResource().getLocation();
                    LOG.warn("Deprecated route/steps detected in {}:{}", file, line
                                                                               + ". To migrate move route/steps to route/from/steps");
                    // move steps from route to from
                    Node from = nodeAt(route, "/from");
                    MappingNode mn = asMappingNode(from);
                    if (mn != null && mn.getValue() != null) {
                        ScalarNode sn = new ScalarNode(Tag.STR, "steps", ScalarStyle.PLAIN);
                        NodeTuple nt = new NodeTuple(sn, steps);
                        mn.getValue().add(nt);
                        mn = asMappingNode(route);
                        mn.getValue().removeIf(t -> {
                            String k = asText(t.getKeyNode());
                            return "steps".equals(k);
                        });
                    }
                }
            }
        }

        return target;
    }

    /**
     * Camel K Integration file
     */
    private Object preConfigureIntegration(Node root, YamlDeserializationContext ctx, Object target, boolean preParse) {
        // when in pre-parse phase then we only want to gather spec/dependencies,spec/configuration,spec/traits

        List<Object> answer = new ArrayList<>();

        // if there are dependencies then include them first
        Node deps = nodeAt(root, "/spec/dependencies");
        if (deps != null) {
            var dep = preConfigureDependencies(deps);
            answer.add(dep);
        }

        // if there are configurations then include them early
        Node configuration = nodeAt(root, "/spec/configuration");
        if (configuration != null) {
            var list = preConfigureConfiguration(ctx.getResource(), configuration);
            answer.addAll(list);
        }
        // if there are trait configuration then include them early
        configuration = nodeAt(root, "/spec/traits/camel");
        if (configuration != null) {
            var list = preConfigureTraitConfiguration(ctx.getResource(), configuration);
            answer.addAll(list);
        }
        // if there are trait environment then include them early
        configuration = nodeAt(root, "/spec/traits/environment");
        if (configuration != null) {
            var list = preConfigureTraitEnvironment(ctx.getResource(), configuration);
            answer.addAll(list);
        }

        if (!preParse) {
            // if there are sources then include them before routes
            Node sources = nodeAt(root, "/spec/sources");
            if (sources != null) {
                var list = preConfigureSources(sources);
                answer.addAll(list);
            }
            // add routes last
            Node routes = nodeAt(root, "/spec/flows");
            if (routes == null) {
                routes = nodeAt(root, "/spec/flow");
            }
            if (routes != null) {
                // routes should be an array
                if (routes.getNodeType() != NodeType.SEQUENCE) {
                    throw new InvalidNodeTypeException(routes, NodeType.SEQUENCE);
                }
                routes = (Node) routeBackwardsCompatible(routes, ctx, preParse);
                answer.add(routes);
            }
        }

        return answer;
    }

    private CamelContextCustomizer preConfigureDependencies(Node node) {
        final List<String> dep = YamlDeserializerSupport.asStringList(node);
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                // notify the listeners about each dependency detected
                for (DependencyStrategy ds : camelContext.getRegistry().findByType(DependencyStrategy.class)) {
                    for (String d : dep) {
                        try {
                            ds.onDependency(d);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
        };
    }

    private List<CamelContextCustomizer> preConfigureConfiguration(Resource resource, Node node) {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        final List<String> lines = new ArrayList<>();
        SequenceNode seq = asSequenceNode(node);
        for (Node n : seq.getValue()) {
            MappingNode content = asMappingNode(n);
            Map<String, Object> params = asMap(content);
            Object type = params.get("type");
            Object value = params.get("value");
            if ("property".equals(type) && value != null) {
                String line = value.toString();
                lines.add(line);
            }
        }
        answer.add(new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                try {
                    org.apache.camel.component.properties.PropertiesComponent pc
                            = (org.apache.camel.component.properties.PropertiesComponent) camelContext.getPropertiesComponent();
                    IntegrationConfigurationPropertiesSource ps
                            = (IntegrationConfigurationPropertiesSource) pc.getPropertiesSource("integration-configuration");
                    if (ps == null) {
                        ps = new IntegrationConfigurationPropertiesSource(
                                pc, new PropertiesLocation(resource.getLocation()), "integration-configuration");
                        pc.addPropertiesSource(ps);
                    }
                    lines.forEach(ps::parseConfigurationValue);
                } catch (Exception e) {
                    throw new RuntimeCamelException("Error adding properties from spec/configuration", e);
                }
            }
        });

        return answer;
    }

    private List<CamelContextCustomizer> preConfigureTraitConfiguration(Resource resource, Node node) {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        Node target = nodeAt(node, "configuration/properties/");
        final List<String> lines = asStringList(target);
        if (lines == null || lines.isEmpty()) {
            return answer;
        }

        answer.add(new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                try {
                    org.apache.camel.component.properties.PropertiesComponent pc
                            = (org.apache.camel.component.properties.PropertiesComponent) camelContext.getPropertiesComponent();
                    IntegrationConfigurationPropertiesSource ps
                            = (IntegrationConfigurationPropertiesSource) pc
                                    .getPropertiesSource("integration-trait-configuration");
                    if (ps == null) {
                        ps = new IntegrationConfigurationPropertiesSource(
                                pc, new PropertiesLocation(resource.getLocation()), "integration-trait-configuration");
                        pc.addPropertiesSource(ps);
                    }
                    lines.forEach(ps::parseConfigurationValue);
                } catch (Exception e) {
                    throw new RuntimeCamelException("Error adding properties from spec/traits/camel/configuration", e);
                }
            }
        });

        return answer;
    }

    private List<CamelContextCustomizer> preConfigureTraitEnvironment(Resource resource, Node node) {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        Node target = nodeAt(node, "configuration/vars/");
        final List<String> lines = asStringList(target);
        if (lines == null || lines.isEmpty()) {
            return answer;
        }

        answer.add(new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                try {
                    org.apache.camel.component.properties.PropertiesComponent pc
                            = (org.apache.camel.component.properties.PropertiesComponent) camelContext.getPropertiesComponent();
                    IntegrationConfigurationPropertiesSource ps
                            = (IntegrationConfigurationPropertiesSource) pc
                                    .getPropertiesSource("environment-trait-configuration");
                    if (ps == null) {
                        ps = new IntegrationConfigurationPropertiesSource(
                                pc, new PropertiesLocation(resource.getLocation()), "environment-trait-configuration");
                        pc.addPropertiesSource(ps);
                    }
                    lines.forEach(ps::parseConfigurationValue);
                } catch (Exception e) {
                    throw new RuntimeCamelException("Error adding properties from spec/traits/environment/configuration", e);
                }
            }
        });

        return answer;
    }

    private List<CamelContextCustomizer> preConfigureSources(Node node) {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        SequenceNode seq = asSequenceNode(node);
        for (Node n : seq.getValue()) {
            MappingNode content = asMappingNode(n);
            Map<String, Object> params = asMap(content);
            Object name = params.get("name");
            Object code = params.get("content");
            if (name != null && code != null) {
                String ext = FileUtil.onlyExt(name.toString(), false);
                final Resource res = new IntegrationSourceResource(ext, name.toString(), code.toString());
                answer.add(new CamelContextCustomizer() {
                    @Override
                    public void configure(CamelContext camelContext) {
                        try {
                            camelContext.adapt(ExtendedCamelContext.class)
                                    .getRoutesLoader().loadRoutes(res);
                        } catch (Exception e) {
                            throw new RuntimeCamelException(
                                    "Error loading sources from resource: " + res + " due to " + e.getMessage(), e);
                        }
                    }
                });
            }
        }

        return answer;
    }

    /**
     * Camel K Kamelet Binding file
     */
    private Object preConfigureKameletBinding(Node root, YamlDeserializationContext ctx, Object target) {
        // start with a route
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
            int line = -1;
            if (source.getStartMark().isPresent()) {
                line = source.getStartMark().get().getLine();
            }

            // source at the beginning (mandatory)
            String uri = extractCamelEndpointUri(source);
            route.from(uri);

            // enrich model with line number
            if (line != -1) {
                route.getInput().setLineNumber(line);
                if (ctx != null) {
                    route.getInput().setLocation(ctx.getResource().getLocation());
                }
            }

            // steps in the middle (optional)
            Node steps = nodeAt(root, "/spec/steps");
            if (steps != null) {
                SequenceNode sn = asSequenceNode(steps);
                for (Node node : sn.getValue()) {
                    MappingNode step = asMappingNode(node);
                    uri = extractCamelEndpointUri(step);
                    if (uri != null) {
                        line = -1;
                        if (node.getStartMark().isPresent()) {
                            line = node.getStartMark().get().getLine();
                        }

                        ProcessorDefinition<?> out;
                        // if kamelet then use kamelet eip instead of to
                        boolean kamelet = uri.startsWith("kamelet:");
                        if (kamelet) {
                            uri = uri.substring(8);
                            out = new KameletDefinition(uri);
                        } else {
                            out = new ToDefinition(uri);
                        }
                        route.addOutput(out);
                        // enrich model with line number
                        if (line != -1) {
                            out.setLineNumber(line);
                            if (ctx != null) {
                                out.setLocation(ctx.getResource().getLocation());
                            }
                        }
                    }
                }
            }

            // sink is at the end (mandatory)
            line = -1;
            if (sink.getStartMark().isPresent()) {
                line = sink.getStartMark().get().getLine();
            }
            uri = extractCamelEndpointUri(sink);
            ToDefinition to = new ToDefinition(uri);
            route.addOutput(to);

            // enrich model with line number
            if (line != -1) {
                to.setLineNumber(line);
                if (ctx != null) {
                    to.setLocation(ctx.getResource().getLocation());
                }
            }

            // is there any error handler?
            MappingNode errorHandler = asMappingNode(nodeAt(root, "/spec/errorHandler"));
            if (errorHandler != null) {
                // there are 5 different error handlers, which one is it
                NodeTuple nt = errorHandler.getValue().get(0);
                String ehName = asText(nt.getKeyNode());

                ErrorHandlerFactory ehf = null;
                if ("sink".equals(ehName)) {
                    // a sink is a dead letter queue
                    DeadLetterChannelDefinition dlcd = new DeadLetterChannelDefinition();
                    MappingNode endpoint = asMappingNode(nodeAt(nt.getValueNode(), "/endpoint"));
                    String dlq = extractCamelEndpointUri(endpoint);
                    dlcd.setDeadLetterUri(dlq);
                    ehf = dlcd;
                } else if ("log".equals(ehName)) {
                    // log is the default error handler
                    ehf = new DefaultErrorHandlerDefinition();
                } else if ("none".equals(ehName)) {
                    route.errorHandler(new NoErrorHandlerDefinition());
                }

                // some error handlers support additional parameters
                if (ehf != null) {
                    // properties that are general for all kind of error handlers
                    MappingNode prop = asMappingNode(nodeAt(nt.getValueNode(), "/parameters"));
                    Map<String, Object> params = asMap(prop);
                    if (params != null) {
                        PropertyBindingSupport.build()
                                .withIgnoreCase(true)
                                .withFluentBuilder(true)
                                .withRemoveParameters(true)
                                .withCamelContext(getCamelContext())
                                .withTarget(ehf)
                                .withProperties(params)
                                .bind();
                    }
                    route.errorHandler(ehf);
                }
            }

            target = route;
        }

        return target;
    }

    private String extractCamelEndpointUri(MappingNode node) {
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
        boolean knative
                = !kamelet && !strimzi && mn != null
                        && anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(KNATIVE_VERSION));
        String uri;
        if (kamelet || strimzi || knative) {
            uri = extractTupleValue(mn.getValue(), "name");
        } else {
            uri = extractTupleValue(node.getValue(), "uri");
        }

        // properties
        MappingNode prop = asMappingNode(nodeAt(node, "/properties"));
        Map<String, Object> params = asMap(prop);
        if (params != null && !params.isEmpty()) {
            try {
                String query = URISupport.createQueryString(params);
                uri = uri + "?" + query;
            } catch (URISyntaxException e) {
                throw new InvalidEndpointException(node, "Error creating URI query parameters", e);
            }
        }

        if (kamelet) {
            return "kamelet:" + uri;
        } else if (strimzi) {
            return "kafka:" + uri;
        } else if (knative) {
            return "knative:channel/" + uri;
        } else {
            return uri;
        }
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        LOG.trace("Pre-parsing: {}", resource.getLocation());

        if (!resource.exists()) {
            throw new FileNotFoundException("Resource not found: " + resource.getLocation());
        }

        try (InputStream is = resourceInputStream(resource)) {
            LoadSettings local = LoadSettings.builder().setLabel(resource.getLocation()).build();
            final YamlDeserializationContext ctx = newYamlDeserializationContext(local, resource);
            final StreamReader reader = new StreamReader(local, new YamlUnicodeReader(is));
            final Parser parser = new ParserImpl(local, reader);
            final Composer composer = new Composer(local, parser);

            try {
                composer.getSingleNode()
                        .map(node -> preParseNode(ctx, node));
            } catch (Exception e) {
                throw new RuntimeCamelException("Error pre-parsing resource: " + ctx.getResource().getLocation(), e);
            }
        }
    }

    private Object preParseNode(final YamlDeserializationContext ctx, final Node root) {
        LOG.trace("Pre-parsing node: {}", root);

        setDeserializationContext(root, ctx);

        Object target = preConfigureNode(root, ctx, true);
        Iterator<?> it = ObjectHelper.createIterator(target);
        while (it.hasNext()) {
            target = it.next();
            if (target instanceof CamelContextCustomizer) {
                CamelContextCustomizer customizer = (CamelContextCustomizer) target;
                customizer.configure(getCamelContext());
            }
        }

        return null;
    }

}
