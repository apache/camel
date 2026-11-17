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
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.app.SSLContextParametersDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
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

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.isSequenceNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {

    public static final String EXTENSION = "yaml";
    public static final String[] SUPPORTED_EXTENSION = { EXTENSION, "camel.yaml" };

    private static final Logger LOG = LoggerFactory.getLogger(YamlRoutesBuilderLoader.class);

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
        // All resources have prepared their beans before declarations are validated and routes constructed.
        setDeserializationContext(root, ctx);
        try {
            ctx.preParse(root);
        } catch (YamlDeserializationException e) {
            // Keep the precise node location already supplied by the deserializer.
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Error pre-parsing resource: " + ctx.getResource().getLocation(), e);
        }

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
                        boolean accepted = doConfigure(target);
                        if (!accepted) {
                            String loc = ctx.getResource() != null ? ctx.getResource().getLocation() : "";
                            LOG.warn(
                                    "Unsupported top-level YAML node in resource: {}. Ensure the YAML file uses a sequence (list) of route definitions.",
                                    loc);
                        }
                    }
                }

                // knowing this is the last time an YAML may have been parsed, we can clear the
                // cache
                // (route may get reloaded later)
                Resource resource = ctx.getResource();
                if (resource != null) {
                    preparseDone.remove(resource.getLocation());
                }
                if (preparseDone.isEmpty()) {
                    beansDeserializer.clearCache();
                }
            }

            private boolean doConfigure(Object item) throws Exception {
                if (item instanceof OutputAwareFromDefinition) {
                    ctx.warnCompactNotationOnce(LOG);
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
                } else if (item instanceof RestDefinition definition) {
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
                } else if (item instanceof SSLContextParametersDefinition def) {
                    SSLContextParameters scp = def.createSSLContextParameters(getCamelContext());
                    if (def.getId() != null) {
                        getCamelContext().getRegistry().bind(def.getId(), scp);
                    }
                    // set the first one as the global default
                    if (getCamelContext().getSSLContextParameters() == null) {
                        getCamelContext().setSSLContextParameters(scp);
                    }
                    return true;
                } else if (item instanceof TransformersDefinition definition) {
                    if (definition.getTransformers() != null) {
                        getCamelContext().getCamelContextExtension()
                                .getContextPlugin(Model.class)
                                .getTransformers().addAll(definition.getTransformers());
                    }
                    return true;
                } else if (item instanceof ValidatorsDefinition definition) {
                    if (definition.getValidators() != null) {
                        getCamelContext().getCamelContextExtension()
                                .getContextPlugin(Model.class)
                                .getValidators().addAll(definition.getValidators());
                    }
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
                        boolean accepted = doConfiguration(target);
                        if (!accepted) {
                            String loc = ctx.getResource() != null ? ctx.getResource().getLocation() : "";
                            LOG.warn(
                                    "Unsupported top-level YAML node in resource: {}. Ensure the YAML file uses a sequence (list) of route definitions.",
                                    loc);
                        }
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
                String hint = "";
                if (e.getMessage() != null && e.getMessage().contains("single document")) {
                    // a --- separator followed by more text, often an explanation appended to the route file
                    hint = ": the file has more than one YAML document (a --- separator); a Camel YAML file is one"
                           + " document, remove the --- and everything after it";
                }
                throw new RuntimeCamelException("Error pre-parsing resource: " + ctx.getResource().getLocation() + hint, e);
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
            if (target instanceof CamelContextCustomizer customizer) {
                customizer.configure(getCamelContext());
            }
        }

        return null;
    }

}
