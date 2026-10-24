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
package org.apache.camel.xml;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.model.BasicExpressionNode;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.xml.out.BaseWriter;
import org.apache.camel.xml.out.ModelWriter;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Lightweight {@link ModelToXMLDumper} based on the generated {@link ModelWriter}.
 */
@JdkService(ModelToXMLDumper.FACTORY)
public class LwModelToXMLDumper implements ModelToXMLDumper {

    // the separator before a query parameter, and its value; the & after it is not part of the match
    private static final Pattern HASH_PARAMETER = Pattern.compile("([?&])hash=([0-9a-f]+)(?=&|$)");
    private static final Pattern ROUTE_ID_PARAMETER = Pattern.compile("([?&])routeId=([^&]*)(?=&|$)");

    @Override
    public String dumpModelAsXml(CamelContext context, NamedNode definition) throws Exception {
        return dumpModelAsXml(context, definition, false, true, false);
    }

    @Override
    public String dumpModelAsXml(
            CamelContext context, NamedNode definition, boolean resolvePlaceholders, boolean generatedIds,
            boolean sourceLocation)
            throws Exception {
        StringWriter buffer = new StringWriter();
        // while debugging the source locations are written too, for the debugger to show where a step is
        write(context, definition, buffer, resolvePlaceholders, generatedIds, sourceLocation || context.isDebugging(),
                false);
        return buffer.toString();
    }

    /**
     * Writes the definition as XML: the dump, and the text the checksum of a route is computed from
     * ({@link LwModelChecksumGenerator}), which never has the source locations.
     *
     * @param checksum whether the text is for the checksum: then what Camel makes from a counter or an identity hash is
     *                 left out (the node prefix and the route id that a route template or Kamelet makes, the hash of
     *                 the endpoint DSL options that are objects), and the secrets in endpoint URIs are masked
     */
    static void write(
            CamelContext context, NamedNode definition, Writer out, boolean resolvePlaceholders, boolean generatedIds,
            boolean sourceLocation, boolean checksum)
            throws Exception {

        Properties properties = new Properties();
        Map<String, String> namespaces = new LinkedHashMap<>();
        Map<String, KeyValueHolder<Integer, String>> locations = new HashMap<>();
        List<Runnable> restorers = new ArrayList<>();
        // the definitions whose id Camel makes from a counter, though it is marked as custom, and those ids
        Set<OptionalIdentifiedDefinition<?>> counterIds = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> counterRouteIds = new HashSet<>();
        Consumer<RouteDefinition> extractor = route -> {
            extractNamespaces(route, namespaces);
            if (sourceLocation) {
                extractSourceLocations(route, locations);
            }
            restorers.add(resolveEndpointDslUris(route, checksum));
            if (checksum) {
                restorers.add(leaveOutCounters(route, counterIds, counterRouteIds));
            }
            if (Boolean.TRUE.equals(route.isTemplate())) {
                Map<String, Object> parameters = route.getTemplateParameters();
                if (parameters != null) {
                    properties.putAll(parameters);
                }
            }
        };

        ModelWriter writer = new ModelWriter(out, BaseWriter.DEFAULT_NAMESPACE) {
            @Override
            protected void doWriteOptionalIdentifiedDefinitionAttributes(OptionalIdentifiedDefinition<?> def)
                    throws IOException {

                if ((generatedIds || Boolean.TRUE.equals(def.getCustomId())) && !counterIds.contains(def)) {
                    // write id
                    doWriteAttribute("id", def.getId());
                }
                // write description
                if (def.getDescriptionText() != null) {
                    doWriteAttribute("description", def.getDescriptionText());
                }
                // write note
                if (def.getNote() != null) {
                    doWriteAttribute("note", def.getNote());
                }
                // write location information
                if (sourceLocation) {
                    int line = (def instanceof RouteDefinition ? ((RouteDefinition) def).getInput() : def).getLineNumber();
                    if (line != -1) {
                        writer.addAttribute("sourceLineNumber", Integer.toString(line));
                    }
                    String loc = (def instanceof RouteDefinition ? ((RouteDefinition) def).getInput() : def).getLocation();
                    if (loc != null) {
                        writer.addAttribute("sourceLocation", loc);
                    }
                }
            }

            @Override
            protected void startElement(String name) throws IOException {
                boolean namespaceWritten = this.namespaceWritten;
                super.startElement(name);
                if (!namespaceWritten) {
                    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                        // TODO: check duplicate xmlns="xxx" attribute ?
                        String nsPrefix = entry.getKey();
                        String prefix = nsPrefix.equals("xmlns") ? nsPrefix : "xmlns:" + nsPrefix;
                        writer.addAttribute(prefix, entry.getValue());
                    }
                }
            }

            @Override
            protected void doWriteThrowExceptionDefinition(String name, ThrowExceptionDefinition def) throws IOException {
                Exception e = def.getException();
                String type = def.getExceptionClass() != null
                        ? def.getExceptionClass().getName() : e != null ? e.getClass().getName() : null;
                if (type == null || def.getExceptionType() != null || def.getRef() != null) {
                    super.doWriteThrowExceptionDefinition(name, def);
                    return;
                }
                // an exception given as a class or an instance, as in throwException(new Exception("...")), is
                // written as its type and message, as the class or instance itself cannot be serialized
                startElement(name);
                doWriteProcessorDefinitionAttributes(def);
                String message = def.getMessage() != null ? def.getMessage() : e != null ? e.getMessage() : null;
                doWriteAttribute("message", message, null);
                doWriteAttribute("exceptionType", type, null);
                endElement(name);
            }

            @Override
            protected void doWriteValue(String value) throws IOException {
                if (value != null && !value.isEmpty()) {
                    if (resolvePlaceholders) {
                        value = resolve(value, properties);
                    }
                    super.doWriteValue(value);
                }
            }

            @Override
            protected void text(String name, String text) throws IOException {
                if (resolvePlaceholders) {
                    text = resolve(text, properties);
                }
                super.text(name, text);
            }

            @Override
            protected void attribute(String name, Object value) throws IOException {
                if (resolvePlaceholders && value != null) {
                    value = resolve(value.toString(), properties);
                }
                if (checksum && "uri".equals(name) && value != null) {
                    String uri = value.toString();
                    for (String id : counterRouteIds) {
                        // kamelet:source and kamelet:sink have the id of their route
                        uri = withoutRouteId(uri, id);
                    }
                    // a literal password is no part of the checksum, which is printed beside the masked URIs
                    value = URISupport.sanitizeUri(uri);
                }
                super.attribute(name, value);
            }

            String resolve(String value, Properties properties) {
                context.getPropertiesComponent().setLocalProperties(properties);
                try {
                    return context.resolvePropertyPlaceholders(value);
                } catch (Exception e) {
                    return value;
                } finally {
                    // clear local after the route is dumped
                    context.getPropertiesComponent().setLocalProperties(null);
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
        try {
            writer.writeOptionalIdentifiedDefinitionRef((OptionalIdentifiedDefinition) definition);
        } finally {
            restorers.forEach(Runnable::run);
        }
    }

    @Override
    public String dumpBeansAsXml(CamelContext context, List<Object> beans) throws Exception {
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

    @Override
    public String dumpDataFormatsAsXml(CamelContext context, Map<String, Object> dataFormats) throws Exception {
        StringWriter buffer = new StringWriter();
        buffer.write("\n");

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
        for (ExpressionNode en : filterTypeInOutputs(route.getOutputs(), ExpressionNode.class)) {
            NamespaceAware na = getNamespaceAwareFromExpression(en);
            if (na != null) {
                Map<String, String> map = na.getNamespaces();
                if (map != null && !map.isEmpty()) {
                    namespaces.putAll(map);
                }
            }
        }
        for (BasicExpressionNode<?> en : filterTypeInOutputs(route.getOutputs(), BasicExpressionNode.class)) {
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
     * The URI of an endpoint DSL builder, for the checksum without the hash of its options that are objects: an
     * identity hash, another one in every run. The objects are beans, which the checksum leaves out.
     */
    static String rawUri(String uri, boolean checksum) {
        return checksum && uri != null ? withoutParameter(uri, HASH_PARAMETER, null) : uri;
    }

    /** The URI without its routeId parameter of that id, which kamelet:source and kamelet:sink have. */
    static String withoutRouteId(String uri, String id) {
        return withoutParameter(uri, ROUTE_ID_PARAMETER, id);
    }

    /**
     * The URI without the first query parameter the pattern matches, whose value is the given value, or any value when
     * it is null.
     */
    static String withoutParameter(String uri, Pattern parameter, String value) {
        Matcher matcher = parameter.matcher(uri);
        while (matcher.find()) {
            if (value == null || value.equals(matcher.group(2))) {
                if (matcher.end() < uri.length()) {
                    // more options follow: keep the separator before it, and drop the & after it
                    return uri.substring(0, matcher.start() + 1) + uri.substring(matcher.end() + 1);
                }
                return uri.substring(0, matcher.start());
            }
        }
        return uri;
    }

    /**
     * Leaves out of the checksum what a route template or a Kamelet makes from a counter when it creates the route: the
     * node prefix (RouteTemplateDefinition.asRouteDefinition and Kamelet.templateToRoute), and the id of a Kamelet
     * route that has none of its own, the template id and that prefix, which its kamelet:source and kamelet:sink have
     * too. The same route then has the same checksum each time it is created, such as when a reload creates it again.
     */
    private static Runnable leaveOutCounters(
            RouteDefinition route, Set<OptionalIdentifiedDefinition<?>> counterIds, Set<String> counterRouteIds) {
        if (!Boolean.TRUE.equals(route.isTemplate())) {
            return () -> {
            };
        }
        String prefix = route.getNodePrefixId();
        if (prefix != null && Boolean.TRUE.equals(route.isKamelet()) && route.getId() != null
                && route.getId().endsWith("-" + prefix)) {
            counterIds.add(route);
            counterRouteIds.add(route.getId());
        }
        route.setNodePrefixId(null);
        return () -> route.setNodePrefixId(prefix);
    }

    /**
     * If the route has been built with endpoint-dsl, then the model will not have uri set which then cannot be included
     * in the JAXB model dump. Returns a {@link Runnable} that restores the original endpoint DSL builders after the XML
     * has been written, so that the live {@link RouteDefinition} is not permanently mutated by the dump.
     */
    @SuppressWarnings("rawtypes")
    private static Runnable resolveEndpointDslUris(RouteDefinition route, boolean checksum) {
        List<Runnable> restorers = new ArrayList<>();
        FromDefinition from = route.getInput();
        if (from != null && from.getEndpointConsumerBuilder() != null) {
            EndpointConsumerBuilder builder = from.getEndpointConsumerBuilder();
            from.setUri(rawUri(builder.getRawUri(), checksum));
            restorers.add(() -> from.setEndpointConsumerBuilder(builder));
        }
        Collection<SendDefinition> col = filterTypeInOutputs(route.getOutputs(), SendDefinition.class);
        for (SendDefinition<?> to : col) {
            if (to.getEndpointProducerBuilder() != null) {
                EndpointProducerBuilder builder = to.getEndpointProducerBuilder();
                to.setUri(rawUri(builder.getRawUri(), checksum));
                restorers.add(() -> to.setEndpointProducerBuilder(builder));
            }
        }
        Collection<ToDynamicDefinition> col2 = filterTypeInOutputs(route.getOutputs(), ToDynamicDefinition.class);
        for (ToDynamicDefinition to : col2) {
            if (to.getEndpointProducerBuilder() != null) {
                EndpointProducerBuilder builder = to.getEndpointProducerBuilder();
                to.setUri(rawUri(builder.getRawUri(), checksum));
                // ToDynamicDefinition.setUri does not call clear(), so the builder is preserved;
                // only the uri field needs to be reset.
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

    private static NamespaceAware getNamespaceAwareFromExpression(BasicExpressionNode expressionNode) {
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
            for (BeanFactoryDefinition<?> b : beans) {
                doWriteBeanFactoryDefinition(b);
            }
        }

        private void doWriteBeanFactoryDefinition(BeanFactoryDefinition<?> b) {
            // the values are escaped as they can have characters such as & and quotes (such as urls)
            buffer.write("    <bean");
            writeAttribute("name", b.getName());
            String type = b.getType();
            if (type != null && type.startsWith("#class:")) {
                type = type.substring(7);
            }
            writeAttribute("type", type);
            writeAttribute("factoryBean", b.getFactoryBean());
            writeAttribute("factoryMethod", b.getFactoryMethod());
            writeAttribute("builderClass", b.getBuilderClass());
            writeAttribute("builderMethod", b.getBuilderMethod());
            writeAttribute("initMethod", b.getInitMethod());
            writeAttribute("destroyMethod", b.getDestroyMethod());
            writeAttribute("scriptLanguage", b.getScriptLanguage());
            buffer.write(">\n");
            if (b.getScript() != null) {
                // the script is an element (and not an attribute)
                buffer.write(String.format("        <script>%n"));
                buffer.write(StringHelper.xmlEncode(b.getScript()));
                buffer.write("\n");
                buffer.write(String.format("        </script>%n"));
            }
            if (b.getConstructors() != null && !b.getConstructors().isEmpty()) {
                buffer.write(String.format("        <constructors>%n"));
                b.getConstructors().forEach((idx, value) -> {
                    if (idx != null) {
                        buffer.write(String.format("            <constructor index=\"%d\" value=\"%s\"/>%n", idx,
                                StringHelper.xmlEncode(String.valueOf(value))));
                    } else {
                        buffer.write(String.format("            <constructor value=\"%s\"/>%n",
                                StringHelper.xmlEncode(String.valueOf(value))));
                    }
                });
                buffer.write(String.format("        </constructors>%n"));
            }
            if (b.getProperties() != null && !b.getProperties().isEmpty()) {
                buffer.write(String.format("        <properties>%n"));
                b.getProperties().forEach((key, value) -> {
                    buffer.write(String.format("            <property key=\"%s\" value=\"%s\"/>%n",
                            StringHelper.xmlEncode(key), StringHelper.xmlEncode(String.valueOf(value))));
                });
                buffer.write(String.format("        </properties>%n"));
            }
            buffer.write(String.format("    </bean>%n"));
        }

        private void writeAttribute(String name, String value) {
            if (value != null) {
                buffer.write(String.format(" %s=\"%s\"", name, StringHelper.xmlEncode(value)));
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

        public void writeDataFormats(Map<String, DataFormatDefinition> dataFormats) throws Exception {
            if (dataFormats.isEmpty()) {
                return;
            }

            DataFormatsDefinition def = new DataFormatsDefinition();
            def.setDataFormats(new ArrayList<>(dataFormats.values()));

            StringWriter tmp = new StringWriter();
            ModelWriter writer = new ModelWriter(tmp, BaseWriter.DEFAULT_NAMESPACE);
            writer.writeDataFormatsDefinition(def);

            // output with 4 space indent
            for (String line : tmp.toString().split("\n")) {
                buffer.write("    ");
                buffer.write(line);
                buffer.write("\n");
            }
        }
    }

}
