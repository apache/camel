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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.KeyValueHolder;
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
        List<OnExceptionDefinition> contextScoped = new ArrayList<>();
        if (definition instanceof RoutesDefinition routes) {
            routes.getRoutes().forEach(route -> collectContextScopedOnExceptions(route, contextScoped));
        } else if (definition instanceof RouteDefinition route) {
            collectContextScopedOnExceptions(route, contextScoped);
        }
        // the onExceptions of a route configuration are dumped with the route configuration, the others are dumped
        // once, as top-level onExceptions before the routes
        List<OnExceptionDefinition> topLevel = new ArrayList<>(contextScoped);
        topLevel.removeIf(oe -> isInRouteConfiguration(context, oe));

        YamlModelWriter writer = new YamlModelWriter() {
            @Override
            protected <T> void doWriteOutputs(JsonObject jo, List<T> list, Function<T, JsonObject> writer) {
                if (list != null && !contextScoped.isEmpty()) {
                    list = list.stream().filter(o -> !containsInstance(contextScoped, o)).toList();
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
            for (OnExceptionDefinition oe : topLevel) {
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

    private static boolean containsInstance(List<?> list, Object instance) {
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
            buffer.write("- beans:\n");
            for (BeanFactoryDefinition<?> b : beans) {
                doWriteBeanFactoryDefinition(b);
            }
        }

        private void doWriteBeanFactoryDefinition(BeanFactoryDefinition<?> b) {
            String type = b.getType();
            if (type.startsWith("#class:")) {
                type = type.substring(7);
            }
            buffer.write(String.format("    - name: %s%n", b.getName()));
            buffer.write(String.format("      type: \"%s\"%n", type));
            if (b.getFactoryBean() != null) {
                buffer.write(String.format("      factoryBean: \"%s\"%n", b.getFactoryBean()));
            }
            if (b.getFactoryMethod() != null) {
                buffer.write(String.format("      factoryMethod: \"%s\"%n", b.getFactoryMethod()));
            }
            if (b.getBuilderClass() != null) {
                buffer.write(String.format("      builderClass: \"%s\"%n", b.getBuilderClass()));
            }
            if (b.getBuilderMethod() != null) {
                buffer.write(String.format("      builderMethod: \"%s\"%n", b.getBuilderMethod()));
            }
            if (b.getInitMethod() != null) {
                buffer.write(String.format("      initMethod: \"%s\"%n", b.getInitMethod()));
            }
            if (b.getDestroyMethod() != null) {
                buffer.write(String.format("      destroyMethod: \"%s\"%n", b.getDestroyMethod()));
            }
            if (b.getScriptLanguage() != null) {
                buffer.write(String.format("      scriptLanguage: \"%s\"%n", b.getScriptLanguage()));
            }
            if (b.getScript() != null) {
                buffer.write(String.format("      script: \"%s\"%n", b.getScript()));
            }
            if (b.getConstructors() != null && !b.getConstructors().isEmpty()) {
                buffer.write(String.format("      constructors:%n"));
                final AtomicInteger counter = new AtomicInteger();
                b.getConstructors().forEach((key, value) -> {
                    if (key == null) {
                        key = counter.getAndIncrement();
                    }
                    buffer.write(String.format("        %d: \"%s\"%n", key, value));
                });
            }
            if (b.getProperties() != null && !b.getProperties().isEmpty()) {
                buffer.write(String.format("      properties:%n"));
                b.getProperties().forEach((key, value) -> {
                    if (value instanceof String) {
                        buffer.write(String.format("        %s: \"%s\"%n", key, value));
                    } else {
                        buffer.write(String.format("        %s: %s%n", key, value));
                    }
                });
            }
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

            buffer.write("- dataFormats:\n");

            DataFormatsDefinition def = new DataFormatsDefinition();
            def.setDataFormats(new ArrayList<>(dataFormats.values()));

            YamlModelWriter writer = new YamlModelWriter();
            writer.setCamelContext(camelContext);
            JsonObject jo = writer.writeDataFormatsDefinition(def);
            List<JsonObject> roots = new ArrayList<>();
            roots.add(jo);
            String yaml = writer.printAsYaml(roots);
            for (String line : yaml.split("\n")) {
                buffer.write("    ");
                buffer.write(line);
                buffer.write("\n");
            }
        }
    }

}
