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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContextAware;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
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
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMap;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.isSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {

    public static final String EXTENSION = "yaml";
    public static final String[] SUPPORTED_EXTENSION = { EXTENSION, "camel.yaml", "pipe.yaml" };

    private static final Logger LOG = LoggerFactory.getLogger(YamlRoutesBuilderLoader.class);

    // API versions for Pipe
    // we are lenient so lets just assume we can work with any of the v1 even if
    // they evolve
    @Deprecated
    private static final String PIPE_VERSION = "camel.apache.org/v1";
    private static final String STRIMZI_VERSION = "kafka.strimzi.io/v1beta2";
    private static final String KNATIVE_MESSAGING_VERSION = "messaging.knative.dev/v1";
    private static final String KNATIVE_EVENTING_VERSION = "eventing.knative.dev/v1";
    private static final String KNATIVE_EVENT_TYPE = "org.apache.camel.event";

    private final Map<String, Boolean> preparseDone = new ConcurrentHashMap<>();

    public YamlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    YamlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    @Override
    public boolean isSupportedExtension(String extension) {
        // this builder can support multiple extensions
        return Arrays.asList(SUPPORTED_EXTENSION).contains(extension);
    }

    protected RouteBuilder builder(final YamlDeserializationContext ctx, final Node root) {

        // we need to keep track of already configured items as the yaml-dsl returns a
        // RouteConfigurationBuilder that is capable of both route and route
        // configurations
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

                // knowing this is the last time an YAML may have been parsed, we can clear the
                // cache
                // (route may get reloaded later)
                Resource resource = ctx.getResource();
                if (resource != null) {
                    preparseDone.remove(resource.getLocation());
                }
                beansDeserializer.clearCache();
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
                                if (node.getNodeType() == NodeType.MAPPING) {
                                    MappingNode mn = asMappingNode(node);
                                    for (NodeTuple nt : mn.getValue()) {
                                        String key = asText(nt.getKeyNode());
                                        // only accept route-configuration
                                        if ("route-configuration".equals(key) || "routeConfiguration".equals(key)) {
                                            Object item = ctx.mandatoryResolve(node).construct(node);
                                            boolean accepted = doConfiguration(item);
                                            if (accepted && idx != -1) {
                                                indexes.add(idx);
                                            }
                                        }
                                    }
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
        Object target = root;

        // check if the yaml with embedded pipes
        if (Objects.equals(root.getNodeType(), NodeType.MAPPING)) {
            final MappingNode mn = YamlDeserializerSupport.asMappingNode(root);
            // pipe
            boolean pipe = anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(PIPE_VERSION)) &&
                    anyTupleMatches(mn.getValue(), "kind", "Pipe");
            if (pipe) {
                target = preConfigurePipe(root, ctx, target, preParse);
            }
        }

        // only detect beans during pre-parsing
        if (preParse && Objects.equals(root.getNodeType(), NodeType.SEQUENCE)) {
            final List<Object> list = new ArrayList<>();

            final SequenceNode sn = asSequenceNode(root);
            for (Node node : sn.getValue()) {
                if (Objects.equals(node.getNodeType(), NodeType.MAPPING)) {
                    MappingNode mn = asMappingNode(node);
                    for (NodeTuple nt : mn.getValue()) {
                        String key = asText(nt.getKeyNode());
                        if ("beans".equals(key)) {
                            // inlined beans
                            Node beans = nt.getValueNode();
                            setDeserializationContext(beans, ctx);
                            Object output = beansDeserializer.construct(beans);
                            if (output != null) {
                                list.add(output);
                            }
                        }
                    }
                }
            }
            if (!list.isEmpty()) {
                target = list;
            }
        }
        return target;
    }

    /**
     * Pipe file
     */
    private Object preConfigurePipe(Node root, YamlDeserializationContext ctx, Object target, boolean preParse) {
        // when in pre-parse phase then we only want to gather /metadata/annotations

        List<Object> answer = new ArrayList<>();

        MappingNode ann = asMappingNode(nodeAt(root, "/metadata/annotations"));
        Map<String, Object> params = asMap(ann);

        if (!preParse) {
            // start with a route
            final RouteDefinition route = new RouteDefinition();
            String routeId = asText(nodeAt(root, "/metadata/name"));
            if (routeId != null) {
                route.routeId(routeId);
            }

            // Pipe is a bit more complex, so grab the source and sink
            // and map those to Camel route definitions
            MappingNode source = asMappingNode(nodeAt(root, "/spec/source"));
            MappingNode sink = asMappingNode(nodeAt(root, "/spec/sink"));
            if (source != null) {
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

                MappingNode dataTypes = asMappingNode(nodeAt(source, "/dataTypes"));
                if (dataTypes != null) {
                    MappingNode in = asMappingNode(nodeAt(dataTypes, "/in"));
                    if (in != null) {
                        route.inputType(extractDataType(in));
                    }

                    MappingNode out = asMappingNode(nodeAt(dataTypes, "/out"));
                    if (out != null) {
                        route.transform(new DataType(extractDataType(out)));
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

                if (sink != null) {
                    dataTypes = asMappingNode(nodeAt(sink, "/dataTypes"));
                    if (dataTypes != null) {
                        MappingNode in = asMappingNode(nodeAt(dataTypes, "/in"));
                        if (in != null) {
                            route.transform(new DataType(extractDataType(in)));
                        }

                        MappingNode out = asMappingNode(nodeAt(dataTypes, "/out"));
                        if (out != null) {
                            route.outputType(extractDataType(out));
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
                        params = asMap(prop);
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
            }

            answer.add(route);
        }

        return answer;
    }

    /**
     * Extracts the data type transformer name information form nodes dataTypes/in or dataTypes/out. When scheme is set
     * construct the transformer name with a prefix like scheme:format. Otherwise, just use the given format as a data
     * type transformer name.
     *
     * @param  node
     * @return
     */
    private String extractDataType(MappingNode node) {
        String scheme = extractTupleValue(node.getValue(), "scheme");
        String format = extractTupleValue(node.getValue(), "format");
        if (scheme != null) {
            return scheme + ":" + format;
        }

        return format;
    }

    private String extractCamelEndpointUri(MappingNode node) {
        MappingNode mn = null;
        Node ref = nodeAt(node, "/ref");
        if (ref != null) {
            mn = asMappingNode(ref);
        }

        // extract uri is different if kamelet or not
        boolean kamelet = mn != null && anyTupleMatches(mn.getValue(), "kind", "Kamelet");
        boolean strimzi = !kamelet && mn != null
                && anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(STRIMZI_VERSION))
                && anyTupleMatches(mn.getValue(), "kind", "KafkaTopic");
        boolean knativeBroker = !kamelet && mn != null
                && anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(KNATIVE_EVENTING_VERSION))
                && anyTupleMatches(mn.getValue(), "kind", "Broker");
        boolean knativeChannel = !kamelet && !strimzi && mn != null
                && anyTupleMatches(mn.getValue(), "apiVersion", v -> v.startsWith(KNATIVE_MESSAGING_VERSION));
        String uri;
        if (knativeBroker) {
            uri = KNATIVE_EVENT_TYPE;
        } else if (kamelet || strimzi || knativeChannel) {
            uri = extractTupleValue(mn.getValue(), "name");
        } else {
            uri = extractTupleValue(node.getValue(), "uri");
        }

        // properties
        MappingNode prop = asMappingNode(nodeAt(node, "/properties"));
        Map<String, Object> params = asMap(prop);

        if (knativeBroker && params != null && params.containsKey("type")) {
            // Use explicit event type from properties - remove setting from params and set
            // as uri
            uri = params.remove("type").toString();
        }

        if (params != null && !params.isEmpty()) {
            String query = URISupport.createQueryString(params);
            uri = uri + "?" + query;
        }

        if (kamelet) {
            return "kamelet:" + uri;
        } else if (strimzi) {
            return "kafka:" + uri;
        } else if (knativeBroker) {
            if (uri.contains("?")) {
                uri += "&kind=Broker&name=" + extractTupleValue(mn.getValue(), "name");
            } else {
                uri += "?kind=Broker&name=" + extractTupleValue(mn.getValue(), "name");
            }
            return "knative:event/" + uri;
        } else if (knativeChannel) {
            return "knative:channel/" + uri;
        } else {
            return uri;
        }
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        // preparsing is done at early stage, so we have a chance to load additional
        // beans and populate
        // Camel registry
        if (preparseDone.getOrDefault(resource.getLocation(), false)) {
            return;
        }

        LOG.trace("Pre-parsing: {}", resource.getLocation());

        if (!resource.exists()) {
            throw new FileNotFoundException("Resource not found: " + resource.getLocation());
        }

        try (InputStream is = resourceInputStream(resource)) {
            LoadSettings local = LoadSettings.builder().setLabel(resource.getLocation()).build();
            YamlDeserializationContext ctx = newYamlDeserializationContext(local, resource);
            StreamReader reader = new StreamReader(local, new YamlUnicodeReader(is));
            Parser parser = new ParserImpl(local, reader);
            Composer composer = new Composer(local, parser);
            try {
                composer.getSingleNode()
                        .map(node -> preParseNode(ctx, node));
            } catch (Exception e) {
                throw new RuntimeCamelException("Error pre-parsing resource: " + ctx.getResource().getLocation(), e);
            } finally {
                ctx.close();
            }
        }

        preparseDone.put(resource.getLocation(), true);
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
