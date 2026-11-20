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
package org.apache.camel.yaml;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.yaml.out.YamlModelWriter;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Lightweight {@link org.apache.camel.spi.ModelToYAMLDumper} based on the generated {@link YamlModelWriter}.
 */
@JdkService(ModelToYAMLDumper.FACTORY)
public class LwModelToYAMLDumper implements ModelToYAMLDumper {

    @Override
    public String dumpModelAsYaml(CamelContext context, NamedNode definition) throws Exception {
        return dumpModelAsYaml(context, definition, false, false, true, false);
    }

    @Override
    public String dumpModelAsYaml(
            CamelContext context, NamedNode definition, boolean resolvePlaceholders,
            boolean uriAsParameters, boolean generatedIds, boolean sourceLocation)
            throws Exception {
        Properties properties = new Properties();
        Map<String, String> namespaces = new LinkedHashMap<>();
        Map<String, KeyValueHolder<Integer, String>> locations = new HashMap<>();
        List<Runnable> restorers = new ArrayList<>();
        Consumer<RouteDefinition> extractor = route -> {
            extractNamespaces(route, namespaces);
            if (sourceLocation || context.isDebugging()) {
                extractSourceLocations(route, locations);
            }
            restorers.add(resolveEndpointDslUris(route));
            if (Boolean.TRUE.equals(route.isTemplate())) {
                Map<String, Object> parameters = route.getTemplateParameters();
                if (parameters != null) {
                    properties.putAll(parameters);
                }
            }
        };

        // gather all namespaces from the routes or route which is stored on the expression nodes
        if (definition instanceof RouteTemplatesDefinition templates) {
            templates.getRouteTemplates().forEach(template -> extractor.accept(template.getRoute()));
        } else if (definition instanceof RouteTemplateDefinition template) {
            extractor.accept(template.getRoute());
        } else if (definition instanceof RoutesDefinition routes) {
            routes.getRoutes().forEach(extractor);
        } else if (definition instanceof RouteDefinition route) {
            extractor.accept(route);
        }

        // context scoped onExceptions (a top-level onException in the YAML DSL, an onException in the configure method
        // of a RouteBuilder, or one of a route configuration) are added to the outputs of each route when the routes
        // are prepared, but the YAML DSL does not allow them in the steps of a route
        List<OnExceptionDefinition> contextScopedOnExceptions = new ArrayList<>();
        // context scoped intercepts (intercept, interceptFrom, interceptSendToEndpoint) and onCompletions are also
        // added to the outputs of each route when the routes are prepared
        List<InterceptDefinition> contextScopedIntercepts = new ArrayList<>();
        List<InterceptFromDefinition> contextScopedInterceptFroms = new ArrayList<>();
        List<InterceptSendToEndpointDefinition> contextScopedInterceptSendTos = new ArrayList<>();
        List<OnCompletionDefinition> contextScopedOnCompletions = new ArrayList<>();
        if (definition instanceof RoutesDefinition routes) {
            routes.getRoutes().forEach(route -> {
                collectContextScopedOnExceptions(route, contextScopedOnExceptions);
                collectContextScopedIntercepts(route, contextScopedIntercepts);
                collectContextScopedInterceptFroms(route, contextScopedInterceptFroms);
                collectContextScopedInterceptSendTos(route, contextScopedInterceptSendTos);
                collectContextScopedOnCompletions(route, contextScopedOnCompletions);
            });
        } else if (definition instanceof RouteDefinition route) {
            collectContextScopedOnExceptions(route, contextScopedOnExceptions);
            collectContextScopedIntercepts(route, contextScopedIntercepts);
            collectContextScopedInterceptFroms(route, contextScopedInterceptFroms);
            collectContextScopedInterceptSendTos(route, contextScopedInterceptSendTos);
            collectContextScopedOnCompletions(route, contextScopedOnCompletions);
        }
        // the onExceptions of a route configuration are dumped with the route configuration, the others are dumped
        // once, as top-level onExceptions before the routes
        List<OnExceptionDefinition> topLevelOnExceptions = new ArrayList<>(contextScopedOnExceptions);
        topLevelOnExceptions.removeIf(oe -> isInRouteConfiguration(context, oe));

        // similarly, filter out constructs that belong to a route configuration (they are dumped with the config)
        List<InterceptDefinition> topLevelIntercepts = new ArrayList<>(contextScopedIntercepts);
        topLevelIntercepts.removeIf(i -> isInterceptInRouteConfiguration(context, i));
        List<InterceptFromDefinition> topLevelInterceptFroms = new ArrayList<>(contextScopedInterceptFroms);
        topLevelInterceptFroms.removeIf(i -> isInterceptFromInRouteConfiguration(context, i));
        List<InterceptSendToEndpointDefinition> topLevelInterceptSendTos = new ArrayList<>(contextScopedInterceptSendTos);
        topLevelInterceptSendTos.removeIf(i -> isInterceptSendToInRouteConfiguration(context, i));
        List<OnCompletionDefinition> topLevelOnCompletions = new ArrayList<>(contextScopedOnCompletions);
        topLevelOnCompletions.removeIf(oc -> isOnCompletionInRouteConfiguration(context, oc));

        // all context-scoped outputs to suppress from route steps
        List<Object> allContextScoped = new ArrayList<>();
        allContextScoped.addAll(contextScopedOnExceptions);
        allContextScoped.addAll(contextScopedIntercepts);
        allContextScoped.addAll(contextScopedInterceptFroms);
        allContextScoped.addAll(contextScopedInterceptSendTos);
        allContextScoped.addAll(contextScopedOnCompletions);
        // named route configuration intercepts and onCompletions are added to route outputs by initInterceptors/
        // initOnCompletions but must not be inlined in the route's steps — they belong to the routeConfiguration
        // block. They are not in the persistent contextScopedIntercepts list (guarded by isFromNamedRouteConfiguration),
        // so we collect them directly from route outputs using the route-configuration membership check.
        Consumer<RouteDefinition> collectRouteConfigScopedIntercepts = route -> {
            for (var output : route.getOutputs()) {
                if (output instanceof InterceptDefinition i && !containsInstance(allContextScoped, i)
                        && isInterceptInRouteConfiguration(context, i)) {
                    allContextScoped.add(i);
                } else if (output instanceof InterceptFromDefinition i && !containsInstance(allContextScoped, i)
                        && isInterceptFromInRouteConfiguration(context, i)) {
                    allContextScoped.add(i);
                } else if (output instanceof InterceptSendToEndpointDefinition i && !containsInstance(allContextScoped, i)
                        && isInterceptSendToInRouteConfiguration(context, i)) {
                    allContextScoped.add(i);
                } else if (output instanceof OnCompletionDefinition oc && !containsInstance(allContextScoped, oc)
                        && isOnCompletionInRouteConfiguration(context, oc)) {
                    allContextScoped.add(oc);
                }
            }
        };
        if (definition instanceof RoutesDefinition routes) {
            routes.getRoutes().forEach(collectRouteConfigScopedIntercepts);
        } else if (definition instanceof RouteDefinition route) {
            collectRouteConfigScopedIntercepts.accept(route);
        }

        // a global (context-level) errorHandler is set on the CamelContext, not on individual routes; it must be
        // emitted as a top-level entry and not duplicated inside the routes (route.getErrorHandler() is null for it);
        // only emit it when dumping routes — rests, templates and route configurations do not carry a context errorHandler
        ErrorHandlerDefinition contextErrorHandler
                = (definition instanceof RoutesDefinition || definition instanceof RouteDefinition)
                        ? collectContextErrorHandler(context) : null;

        YamlModelWriter writer = new YamlModelWriter() {
            @Override
            protected <T> void doWriteOutputs(JsonObject jo, List<T> list, Function<T, JsonObject> writer) {
                if (list != null && !allContextScoped.isEmpty()) {
                    list = list.stream().filter(o -> !containsInstance(allContextScoped, o)).toList();
                }
                super.doWriteOutputs(jo, list, writer);
            }

            @Override
            protected void doWriteOptionalIdentifiedDefinitionAttributes(
                    JsonObject jo, OptionalIdentifiedDefinition<?> def) {
                if (generatedIds || Boolean.TRUE.equals(def.getCustomId())) {
                    doWriteAttribute(jo, "id", def.getId(), null);
                }
                doWriteAttribute(jo, "description", def.getDescription(), null);
                doWriteAttribute(jo, "note", def.getNote(), null);
                if (sourceLocation || context.isDebugging()) {
                    String loc = (def instanceof RouteDefinition rd1 ? rd1.getInput() : def).getLocation();
                    int line = (def instanceof RouteDefinition rd2 ? rd2.getInput() : def).getLineNumber();
                    if (line != -1) {
                        doWriteAttribute(jo, "sourceLineNumber", Integer.toString(line), null);
                        doWriteAttribute(jo, "sourceLocation", loc, null);
                    }
                }
            }

            @Override
            protected void doWriteAttribute(JsonObject jo, String key, String value, String defaultValue) {
                if (resolvePlaceholders && value != null) {
                    value = resolve(value, properties);
                }
                if ("uri".equals(key) && uriAsParameters) {
                    expandUri(jo, value);
                    return;
                }
                super.doWriteAttribute(jo, key, value, defaultValue);
            }

            @Override
            protected JsonObject doWriteInterceptFromDefinition(InterceptFromDefinition def) {
                return keepUriPattern(super.doWriteInterceptFromDefinition(def), def.getUri());
            }

            @Override
            protected JsonObject doWriteInterceptSendToEndpointDefinition(InterceptSendToEndpointDefinition def) {
                return keepUriPattern(super.doWriteInterceptSendToEndpointDefinition(def), def.getUri());
            }

            // the uri of an interceptor is a pattern (such as mock:*), not an endpoint uri, and the YAML DSL has no
            // parameters for it, so it is written as it is even when the uris are written as parameters
            private JsonObject keepUriPattern(JsonObject jo, String uri) {
                if (uriAsParameters && uri != null) {
                    jo.remove("parameters");
                    jo.put("uri", resolvePlaceholders ? resolve(uri, properties) : uri);
                }
                return jo;
            }

            @Override
            protected JsonObject doWriteThrowExceptionDefinition(ThrowExceptionDefinition def) {
                JsonObject jo = super.doWriteThrowExceptionDefinition(def);
                Exception e = def.getException();
                String type = def.getExceptionClass() != null
                        ? def.getExceptionClass().getName() : e != null ? e.getClass().getName() : null;
                if (type != null && def.getExceptionType() == null && def.getRef() == null) {
                    // an exception given as a class or an instance, as in throwException(new Exception("...")), is
                    // written as its type and message, as the class or instance itself cannot be serialized
                    if (def.getMessage() == null && e != null) {
                        doWriteAttribute(jo, "message", e.getMessage(), null);
                    }
                    doWriteAttribute(jo, "exceptionType", type, null);
                }
                return jo;
            }

            @Override
            protected void doWriteValue(JsonObject jo, String value) {
                if (resolvePlaceholders && value != null) {
                    value = resolve(value, properties);
                }
                super.doWriteValue(jo, value);
            }

            String resolve(String value, Properties properties) {
                context.getPropertiesComponent().setLocalProperties(properties);
                try {
                    return context.resolvePropertyPlaceholders(value);
                } catch (Exception e) {
                    return value;
                } finally {
                    context.getPropertiesComponent().setLocalProperties(null);
                }
            }
        };
        writer.setUriAsParameters(uriAsParameters);
        writer.setCamelContext(context);

        List<JsonObject> roots = new ArrayList<>();
        try {
            // top-level errorHandler (global, context-level) before anything else
            if (contextErrorHandler != null) {
                roots.add(writer.writeErrorHandlerDefinition(contextErrorHandler));
            }
            for (InterceptDefinition intercept : topLevelIntercepts) {
                roots.add(writer.writeInterceptDefinition(intercept));
            }
            for (InterceptFromDefinition interceptFrom : topLevelInterceptFroms) {
                roots.add(writer.writeInterceptFromDefinition(interceptFrom));
            }
            for (InterceptSendToEndpointDefinition interceptSendTo : topLevelInterceptSendTos) {
                roots.add(writer.writeInterceptSendToEndpointDefinition(interceptSendTo));
            }
            for (OnCompletionDefinition onCompletion : topLevelOnCompletions) {
                roots.add(writer.writeOnCompletionDefinition(onCompletion));
            }
            for (OnExceptionDefinition oe : topLevelOnExceptions) {
                roots.add(writer.writeOnExceptionDefinition(oe));
            }
            if (definition instanceof RoutesDefinition rd) {
                for (RouteDefinition route : rd.getRoutes()) {
                    roots.add(writer.writeRouteDefinition(route));
                }
            } else if (definition instanceof RouteDefinition route) {
                roots.add(writer.writeRouteDefinition(route));
            } else if (definition instanceof RouteTemplatesDefinition rtd) {
                for (RouteTemplateDefinition template : rtd.getRouteTemplates()) {
                    roots.add(writer.writeRouteTemplateDefinition(template));
                }
            } else if (definition instanceof RouteTemplateDefinition template) {
                roots.add(writer.writeRouteTemplateDefinition(template));
            } else if (definition instanceof RestsDefinition rd) {
                for (RestDefinition rest : rd.getRests()) {
                    roots.add(writer.writeRestDefinition(rest));
                }
            } else if (definition instanceof RestDefinition rest) {
                roots.add(writer.writeRestDefinition(rest));
            } else if (definition instanceof RouteConfigurationsDefinition rcd) {
                for (RouteConfigurationDefinition config : rcd.getRouteConfigurations()) {
                    roots.add(writer.writeRouteConfigurationDefinition(config));
                }
            } else if (definition instanceof RouteConfigurationDefinition config) {
                roots.add(writer.writeRouteConfigurationDefinition(config));
            } else {
                JsonObject jo = writer.writeOptionalIdentifiedDefinitionRef((OptionalIdentifiedDefinition) definition);
                if (jo != null) {
                    roots.add(jo);
                }
            }
        } finally {
            restorers.forEach(Runnable::run);
        }

        return writer.printAsYaml(roots);
    }

    @Override
    public String dumpBeansAsYaml(CamelContext context, List<Object> beans) throws Exception {
        StringWriter buffer = new StringWriter();
        BeanModelWriter writer = new BeanModelWriter(buffer);

        List<BeanFactoryDefinition<?>> list = new ArrayList<>();
        for (Object bean : beans) {
            if (bean instanceof BeanFactoryDefinition<?> rb) {
                list.add(rb);
            }
        }
        writer.setCamelContext(context);
        writer.start();
        try {
            writer.writeBeans(list);
        } finally {
            writer.stop();
        }

        return buffer.toString();
    }

    /**
     * Dumps the global data formats as YAML
     *
     * @param  context     the CamelContext
     * @param  dataFormats list of data formats (DataFormatDefinition)
     * @return             the output in YAML (is formatted)
     * @throws Exception   is throw if error marshalling to YAML
     */
    @Override
    public String dumpDataFormatsAsYaml(CamelContext context, Map<String, Object> dataFormats) throws Exception {
        StringWriter buffer = new StringWriter();
        DataFormatModelWriter writer = new DataFormatModelWriter(buffer);

        Map<String, DataFormatDefinition> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : dataFormats.entrySet()) {
            if (entry.getValue() instanceof DataFormatDefinition def) {
                map.put(entry.getKey(), def);
            }
        }
        writer.setCamelContext(context);
        writer.start();
        try {
            writer.writeDataFormats(map);
        } finally {
            writer.stop();
        }

        return buffer.toString();
    }

    /**
     * Collects the context scoped onExceptions that were added to the outputs of the route when it was prepared
     */
    private static void collectContextScopedOnExceptions(RouteDefinition route, List<OnExceptionDefinition> answer) {
        for (var output : route.getOutputs()) {
            if (output instanceof OnExceptionDefinition oe && !oe.isRouteScoped() && !containsInstance(answer, oe)) {
                answer.add(oe);
            }
        }
    }

    /**
     * Collects the context scoped intercepts that were added to the route when it was prepared. The persistent list on
     * the route is used as the primary source because {@code InterceptReifier} removes plain {@code intercept}
     * definitions from route outputs during {@code context.start()}.
     */
    private static void collectContextScopedIntercepts(RouteDefinition route, List<InterceptDefinition> answer) {
        List<InterceptDefinition> persistent = route.getContextScopedIntercepts();
        if (persistent != null) {
            for (InterceptDefinition intercept : persistent) {
                if (!containsInstance(answer, intercept)) {
                    answer.add(intercept);
                }
            }
        }
    }

    /**
     * Collects the context scoped interceptFroms that were added to the outputs of the route when it was prepared
     */
    private static void collectContextScopedInterceptFroms(RouteDefinition route, List<InterceptFromDefinition> answer) {
        for (var output : route.getOutputs()) {
            if (output instanceof InterceptFromDefinition interceptFrom
                    && !interceptFrom.isRouteScoped()
                    && !containsInstance(answer, interceptFrom)) {
                answer.add(interceptFrom);
            }
        }
    }

    /**
     * Collects the context scoped interceptSendToEndpoints that were added to the outputs of the route when it was
     * prepared
     */
    private static void collectContextScopedInterceptSendTos(
            RouteDefinition route, List<InterceptSendToEndpointDefinition> answer) {
        for (var output : route.getOutputs()) {
            if (output instanceof InterceptSendToEndpointDefinition interceptSendTo
                    && !interceptSendTo.isRouteScoped()
                    && !containsInstance(answer, interceptSendTo)) {
                answer.add(interceptSendTo);
            }
        }
    }

    /**
     * Collects the context scoped onCompletions that were recorded on the route when it was prepared. Uses the
     * persistent list rather than scanning outputs and checking {@code !oc.isRouteScoped()}, because
     * {@code routeScoped} already has runtime semantics in {@code OnCompletionProcessor.shouldSkip()} and must not be
     * altered.
     */
    private static void collectContextScopedOnCompletions(RouteDefinition route, List<OnCompletionDefinition> answer) {
        List<OnCompletionDefinition> persistent = route.getContextScopedOnCompletions();
        if (persistent != null) {
            for (OnCompletionDefinition oc : persistent) {
                if (!containsInstance(answer, oc)) {
                    answer.add(oc);
                }
            }
        }
    }

    /**
     * Returns the context-level error handler as an {@link ErrorHandlerDefinition} if one was explicitly set on the
     * CamelContext using one of the built-in types, or {@code null} otherwise. Custom {@code ErrorHandlerFactory}
     * implementations cannot be serialized to YAML and are skipped.
     */
    private static ErrorHandlerDefinition collectContextErrorHandler(CamelContext context) {
        if (context == null) {
            return null;
        }
        ErrorHandlerFactory factory = context.getCamelContextExtension().getErrorHandlerFactory();
        if (factory == null) {
            // no explicit global error handler was configured; nothing to emit
            return null;
        }
        // only the built-in types (DefaultErrorHandlerDefinition subclasses, NoErrorHandlerDefinition,
        // RefErrorHandlerDefinition) are known to the YAML writer; a custom ErrorHandlerFactory cannot be
        // serialized to YAML and would produce an empty errorHandler: {} — skip it
        if (!(factory instanceof DefaultErrorHandlerDefinition)
                && !(factory instanceof NoErrorHandlerDefinition)
                && !(factory instanceof RefErrorHandlerDefinition)) {
            return null;
        }
        ErrorHandlerDefinition def = new ErrorHandlerDefinition();
        def.setErrorHandlerType(factory);
        return def;
    }

    private static boolean isInRouteConfiguration(CamelContext context, OnExceptionDefinition oe) {
        Model model = context != null ? context.getCamelContextExtension().getContextPlugin(Model.class) : null;
        if (model != null) {
            for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                if (containsInstance(config.getOnExceptions(), oe)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInterceptInRouteConfiguration(CamelContext context, InterceptDefinition intercept) {
        Model model = context != null ? context.getCamelContextExtension().getContextPlugin(Model.class) : null;
        if (model != null) {
            for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                if (containsInstance(config.getIntercepts(), intercept)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInterceptFromInRouteConfiguration(
            CamelContext context, InterceptFromDefinition interceptFrom) {
        Model model = context != null ? context.getCamelContextExtension().getContextPlugin(Model.class) : null;
        if (model != null) {
            for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                if (containsInstance(config.getInterceptFroms(), interceptFrom)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInterceptSendToInRouteConfiguration(
            CamelContext context, InterceptSendToEndpointDefinition interceptSendTo) {
        Model model = context != null ? context.getCamelContextExtension().getContextPlugin(Model.class) : null;
        if (model != null) {
            for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                if (containsInstance(config.getInterceptSendTos(), interceptSendTo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOnCompletionInRouteConfiguration(
            CamelContext context, OnCompletionDefinition onCompletion) {
        Model model = context != null ? context.getCamelContextExtension().getContextPlugin(Model.class) : null;
        if (model != null) {
            for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                if (containsInstance(config.getOnCompletions(), onCompletion)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsInstance(List<?> list, Object instance) {
        if (list == null) {
            return false;
        }
        for (Object o : list) {
            if (o == instance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route      the route
     * @param namespaces the map of namespaces to add discovered XML namespaces into
     */
    private static void extractNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        for (SwitchDefinition sw : filterTypeInOutputs(route.getOutputs(), SwitchDefinition.class)) {
            sw.preCreateProcessor();
            NamespaceAware aware = getNamespaceAwareFromExpression(sw);
            if (aware != null && aware.getNamespaces() != null) {
                namespaces.putAll(aware.getNamespaces());
            }
        }
        Collection<ExpressionNode> col = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        for (ExpressionNode en : col) {
            NamespaceAware na = getNamespaceAwareFromExpression(en);
            if (na != null) {
                Map<String, String> map = na.getNamespaces();
                if (map != null && !map.isEmpty()) {
                    namespaces.putAll(map);
                }
            }
        }
    }

    /**
     * Extract all source locations from the route
     *
     * @param route     the route
     * @param locations the map of source locations for EIPs in the route
     */
    private static void extractSourceLocations(RouteDefinition route, Map<String, KeyValueHolder<Integer, String>> locations) {
        // input
        String id = route.getRouteId();
        String loc = route.getInput().getLocation();
        int line = route.getInput().getLineNumber();
        if (id != null && line != -1) {
            locations.put(id, new KeyValueHolder<>(line, loc));
        }
        // and then walk all nodes in the route graphs
        for (var def : filterTypeInOutputs(route.getOutputs(), OptionalIdentifiedDefinition.class)) {
            id = def.getId();
            loc = def.getLocation();
            line = def.getLineNumber();
            if (id != null && line != -1) {
                locations.put(id, new KeyValueHolder<>(line, loc));
            }
        }
    }

    /**
     * If the route has been built with endpoint-dsl, then the model will not have uri set which then cannot be included
     * in the model dump
     */
    @SuppressWarnings("rawtypes")
    private static Runnable resolveEndpointDslUris(RouteDefinition route) {
        List<Runnable> restorers = new ArrayList<>();
        FromDefinition from = route.getInput();
        if (from != null && from.getEndpointConsumerBuilder() != null) {
            EndpointConsumerBuilder builder = from.getEndpointConsumerBuilder();
            from.setUri(builder.getRawUri());
            restorers.add(() -> from.setEndpointConsumerBuilder(builder));
        }
        Collection<SendDefinition> col = filterTypeInOutputs(route.getOutputs(), SendDefinition.class);
        for (SendDefinition<?> to : col) {
            if (to.getEndpointProducerBuilder() != null) {
                EndpointProducerBuilder builder = to.getEndpointProducerBuilder();
                to.setUri(builder.getRawUri());
                restorers.add(() -> to.setEndpointProducerBuilder(builder));
            }
        }
        Collection<ToDynamicDefinition> col2 = filterTypeInOutputs(route.getOutputs(), ToDynamicDefinition.class);
        for (ToDynamicDefinition to : col2) {
            if (to.getEndpointProducerBuilder() != null) {
                EndpointProducerBuilder builder = to.getEndpointProducerBuilder();
                to.setUri(builder.getRawUri());
                restorers.add(() -> to.setUri(null));
            }
        }
        return () -> restorers.forEach(Runnable::run);
    }

    private static NamespaceAware getNamespaceAwareFromExpression(SwitchDefinition definition) {
        if (definition.getSelector() == null || definition.getSelector().getExpressionType() == null) {
            return null;
        }
        ExpressionDefinition expression = definition.getSelector().getExpressionType();
        if (expression.getExpressionValue() instanceof NamespaceAware aware) {
            return aware;
        }
        return expression instanceof NamespaceAware aware ? aware : null;
    }

    private static NamespaceAware getNamespaceAwareFromExpression(ExpressionNode expressionNode) {
        ExpressionDefinition ed = expressionNode.getExpression();

        NamespaceAware na = null;
        Expression exp = ed.getExpressionValue();
        if (exp instanceof NamespaceAware namespaceAware) {
            na = namespaceAware;
        } else if (ed instanceof NamespaceAware namespaceAware) {
            na = namespaceAware;
        }

        return na;
    }

    private static class BeanModelWriter implements CamelContextAware {

        private final StringWriter buffer;
        private CamelContext camelContext;

        public BeanModelWriter(StringWriter buffer) {
            this.buffer = buffer;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public void start() {
            // noop
        }

        public void stop() {
            // noop
        }

        public void writeBeans(List<BeanFactoryDefinition<?>> beans) {
            if (beans.isEmpty()) {
                return;
            }
            // written by the model writer, so nested properties come out as nested maps, as the yaml dsl loads them
            var writer = new YamlModelWriter() {
                @Override
                public JsonObject doWriteBeanFactoryDefinition(BeanFactoryDefinition<?> def) {
                    return super.doWriteBeanFactoryDefinition(def);
                }
            };
            writer.setCamelContext(camelContext);
            JsonArray list = new JsonArray();
            for (BeanFactoryDefinition<?> b : beans) {
                JsonObject bean = writer.doWriteBeanFactoryDefinition(b);
                if (bean.get("type") instanceof String type && type.startsWith("#class:")) {
                    bean.put("type", type.substring(7));
                }
                list.add(bean);
            }
            JsonObject root = new JsonObject();
            root.put("beans", list);
            buffer.write(writer.printAsYaml(List.of(root)));
        }
    }

    private static class DataFormatModelWriter implements CamelContextAware {

        private final StringWriter buffer;
        private CamelContext camelContext;

        public DataFormatModelWriter(StringWriter buffer) {
            this.buffer = buffer;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public void start() {
            // noop
        }

        public void stop() {
            // noop
        }

        public void writeDataFormats(Map<String, DataFormatDefinition> dataFormats) {
            if (dataFormats.isEmpty()) {
                return;
            }

            // a list of data formats, each as its own single-key map (such as - json: ...), as the yaml dsl loads them
            // (and so two data formats of the same kind do not overwrite each other)
            var writer = new YamlModelWriter() {
                @Override
                public JsonObject doWriteDataFormatsDefinition(DataFormatsDefinition def) {
                    return super.doWriteDataFormatsDefinition(def);
                }
            };
            writer.setCamelContext(camelContext);
            JsonArray list = new JsonArray();
            for (DataFormatDefinition df : dataFormats.values()) {
                DataFormatsDefinition def = new DataFormatsDefinition();
                def.setDataFormats(List.of(df));
                list.add(writer.doWriteDataFormatsDefinition(def));
            }
            JsonObject root = new JsonObject();
            root.put("dataFormats", list);
            buffer.write(writer.printAsYaml(List.of(root)));
        }
    }

}
