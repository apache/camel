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

package org.apache.camel.main.xml.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.util.XmlHelper;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for parsing and discovering legacy OSGi <blueprint> XML to make it runnable on camel-jbang, and for tooling to
 * migrate this to modern Camel DSL in plain Camel XML or YAML DSL.
 */
@Deprecated(since = "4.13.0")
public class BlueprintXmlBeansHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintXmlBeansHandler.class);
    private static final Pattern BLUEPRINT_PATTERN = Pattern.compile("\\$\\{(.*?)}"); // non-greedy mode

    // when preparing blueprint-based beans, we may have problems loading classes which are provided with Java DSL
    // that's why some beans should be processed later
    private final Map<String, Node> delayedBeans = new LinkedHashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final List<BeanFactoryDefinition<?>> delayedRegistrations = new ArrayList<>();
    private final Map<String, KeyValueHolder<Object, String>> beansToDestroy = new LinkedHashMap<>();
    private boolean transform;

    public boolean isTransform() {
        return transform;
    }

    public void setTransform(boolean transform) {
        this.transform = transform;
    }

    /**
     * Parses the XML documents and discovers blueprint beans, which will be created manually via Camel.
     */
    public void processBlueprintBeans(
            CamelContext camelContext, MainConfigurationProperties config, final Map<String, Document> xmls) {

        LOG.warn("Loading beans from classic OSGi <blueprint> XML is deprecated");

        xmls.forEach((id, doc) -> {
            if (id.startsWith("camel-xml-io-dsl-blueprint-xml:")) {
                // this is a camel bean via camel-xml-io-dsl
                String fileName = StringHelper.afterLast(id, ":");
                discoverBeans(camelContext, fileName, doc);
                // configure <camelContext>
                configureCamelContext(camelContext, fileName);
            }
        });
    }

    /**
     * Invoked at later stage to create and register Blueprint beans into Camel {@link org.apache.camel.spi.Registry}.
     */
    public void createAndRegisterBeans(CamelContext camelContext) {
        if (delayedBeans.isEmpty()) {
            return;
        }

        LOG.info("Discovered {} OSGi <blueprint> XML beans", delayedBeans.size());
        for (Map.Entry<String, Node> entry : delayedBeans.entrySet()) {
            String id = entry.getKey();
            Node n = entry.getValue();
            BeanFactoryDefinition<?> def = createBeanModel(camelContext, id, n);
            if (transform) {
                // transform mode should only discover and remember bean in model
                LOG.debug("Discovered bean: {}", def.getName());
                addBeanToCamelModel(camelContext, def.getName(), def);
            } else {
                LOG.debug("Creating bean: {}", def.getName());
                registerAndCreateBean(camelContext, def, true);
            }
        }

        if (!delayedRegistrations.isEmpty()) {
            // some of the beans were not available yet, so we have to try register them now
            for (BeanFactoryDefinition<?> def : delayedRegistrations) {
                LOG.debug("Creating bean (2nd-try): {}", def.getName());
                registerAndCreateBean(camelContext, def, false);
            }
            delayedRegistrations.clear();
        }
    }

    private BeanFactoryDefinition<?> createBeanModel(CamelContext camelContext, String name, Node node) {
        BeanFactoryDefinition<?> def = new BeanFactoryDefinition<>();
        def.setResource(resources.get(name));
        def.setType(XmlHelper.getAttribute(node, "class"));
        def.setName(name);

        // factory bean/method
        String fb = XmlHelper.getAttribute(node, "factory-ref");
        if (fb != null) {
            def.setFactoryBean(fb);
        }
        String fm = XmlHelper.getAttribute(node, "factory-method");
        if (fm != null) {
            def.setFactoryMethod(fm);
        }
        String im = XmlHelper.getAttribute(node, "init-method");
        if (im != null) {
            def.setInitMethod(im);
        }
        String dm = XmlHelper.getAttribute(node, "destroy-method");
        if (dm != null) {
            def.setDestroyMethod(dm);
        }
        // constructor arguments
        Map<Integer, Object> constructors = new LinkedHashMap<>();
        def.setConstructors(constructors);
        NodeList props = node.getChildNodes();
        int index = 0;
        for (int i = 0; i < props.getLength(); i++) {
            Node child = props.item(i);
            // assume the args are in order (1, 2)
            if ("argument".equals(child.getNodeName())) {
                String val = XmlHelper.getAttribute(child, "value");
                String ref = XmlHelper.getAttribute(child, "ref");
                if (val != null) {
                    constructors.put(index++, extractValue(camelContext, val, false));
                } else if (ref != null) {
                    constructors.put(index++, "#bean:" + extractValue(camelContext, ref, false));
                }
            }
        }
        if (!constructors.isEmpty()) {
            def.setConstructors(constructors);
        }

        // property values
        Map<String, Object> properties = new LinkedHashMap<>();
        props = node.getChildNodes();
        for (int i = 0; i < props.getLength(); i++) {
            Node child = props.item(i);
            // assume the args are in order (1, 2)
            if ("property".equals(child.getNodeName())) {
                String key = XmlHelper.getAttribute(child, "name");
                String val = XmlHelper.getAttribute(child, "value");
                String ref = XmlHelper.getAttribute(child, "ref");
                if (key != null && val != null) {
                    properties.put(key, extractValue(camelContext, val, false));
                } else if (key != null && ref != null) {
                    properties.put(key, extractValue(camelContext, "#bean:" + ref, false));
                }
                for (Node n : getChildNodes(child, "list")) {
                    int j = 0;
                    for (Node v : getChildNodes(n, "value")) {
                        val = v.getTextContent();
                        if (key != null && val != null) {
                            String k = key + "[" + j + "]";
                            properties.put(k, extractValue(camelContext, val, false));
                        }
                        j++;
                    }
                }
                for (Node n : getChildNodes(child, "map")) {
                    for (Node v : getChildNodes(n, "entry")) {
                        String k = XmlHelper.getAttribute(v, "key");
                        val = XmlHelper.getAttribute(v, "value");
                        if (key != null && k != null && val != null) {
                            k = key + "[" + k + "]";
                            properties.put(k, extractValue(camelContext, val, false));
                        }
                    }
                }
            }
        }
        if (!properties.isEmpty()) {
            def.setProperties(properties);
        }

        return def;
    }

    private static List<Node> getChildNodes(Node node, String name) {
        List<Node> answer = new ArrayList<>();
        NodeList list = node.getChildNodes();
        for (int j = 0; j < list.getLength(); j++) {
            Node entry = list.item(j);
            if (name.equals(entry.getNodeName())) {
                answer.add(entry);
            }
        }
        return answer;
    }

    private void configureCamelContext(CamelContext camelContext, String fileName) {
        Resource resource = camelContext
                .getCamelContextExtension()
                .getContextPlugin(ResourceLoader.class)
                .resolveResource("file:" + fileName);
        if (!resource.exists()) {
            return;
        }

        try {
            // need to load and parse again as we need to load <camelContext> to configure CamelContext from XML
            Document dom = new XmlConverter().toDOMDocument(resource.getInputStream(), null);
            NodeList camels = dom.getElementsByTagNameNS("http://camel.apache.org/schema/blueprint", "camelContext");
            if (camels.getLength() == 1) {
                Node n = camels.item(0);
                if (n.hasAttributes()) {
                    String value = XmlHelper.getAttribute(n, "id");
                    if (value != null) {
                        camelContext
                                .getCamelContextExtension()
                                .setName(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "startupSummaryLevel");
                    if (value != null) {
                        camelContext.setStartupSummaryLevel(
                                CamelContextHelper.parse(camelContext, StartupSummaryLevel.class, value));
                    }
                    value = XmlHelper.getAttribute(n, "trace");
                    if (value != null) {
                        camelContext.setTracing(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "backlogTrace");
                    if (value != null) {
                        camelContext.setBacklogTracing(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "tracePattern");
                    if (value != null) {
                        camelContext.setTracingPattern(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "traceLoggingFormat");
                    if (value != null) {
                        camelContext.setTracingLoggingFormat(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "debug");
                    if (value != null) {
                        camelContext.setDebugging(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "messageHistory");
                    if (value != null) {
                        camelContext.setMessageHistory(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "sourceLocationEnabled");
                    if (value != null) {
                        camelContext.setSourceLocationEnabled(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "logMask");
                    if (value != null) {
                        camelContext.setLogMask(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "logExhaustedMessageBody");
                    if (value != null) {
                        camelContext.setLogExhaustedMessageBody(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "streamCache");
                    if (value != null) {
                        camelContext.setStreamCaching(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "delayer");
                    if (value != null) {
                        camelContext.setDelayer(CamelContextHelper.parseLong(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "autoStartup");
                    if (value != null) {
                        camelContext.setAutoStartup(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "dumpRoutes");
                    if (value != null) {
                        camelContext.setDumpRoutes(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "useMDCLogging");
                    if (value != null) {
                        camelContext.setUseMDCLogging(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "mdcLoggingKeysPattern");
                    if (value != null) {
                        camelContext.setMDCLoggingKeysPattern(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "useDataType");
                    if (value != null) {
                        camelContext.setUseDataType(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "useBreadcrumb");
                    if (value != null) {
                        camelContext.setUseBreadcrumb(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "allowUseOriginalMessage");
                    if (value != null) {
                        camelContext.setAllowUseOriginalMessage(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "caseInsensitiveHeaders");
                    if (value != null) {
                        camelContext.setCaseInsensitiveHeaders(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "autowiredEnabled");
                    if (value != null) {
                        camelContext.setAutowiredEnabled(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "threadNamePattern");
                    if (value != null) {
                        camelContext
                                .getExecutorServiceManager()
                                .setThreadNamePattern(CamelContextHelper.parseText(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "shutdownRoute");
                    if (value != null) {
                        camelContext.setShutdownRoute(
                                CamelContextHelper.parse(camelContext, ShutdownRoute.class, value));
                    }
                    value = XmlHelper.getAttribute(n, "shutdownRunningTask");
                    if (value != null) {
                        camelContext.setShutdownRunningTask(
                                CamelContextHelper.parse(camelContext, ShutdownRunningTask.class, value));
                    }
                    value = XmlHelper.getAttribute(n, "shutdownRunningTask");
                    if (value != null) {
                        camelContext.setLoadTypeConverters(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "typeConverterStatisticsEnabled");
                    if (value != null) {
                        camelContext.setTypeConverterStatisticsEnabled(
                                CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "loadHealthChecks");
                    if (value != null) {
                        camelContext.setLoadHealthChecks(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "inflightRepositoryBrowseEnabled");
                    if (value != null) {
                        camelContext
                                .getInflightRepository()
                                .setInflightBrowseEnabled(CamelContextHelper.parseBoolean(camelContext, value));
                    }
                    value = XmlHelper.getAttribute(n, "typeConverterExists");
                    if (value != null) {
                        camelContext
                                .getTypeConverterRegistry()
                                .setTypeConverterExists(
                                        CamelContextHelper.parse(camelContext, TypeConverterExists.class, value));
                    }
                    value = XmlHelper.getAttribute(n, "typeConverterExistsLoggingLevel");
                    if (value != null) {
                        camelContext
                                .getTypeConverterRegistry()
                                .setTypeConverterExistsLoggingLevel(
                                        CamelContextHelper.parse(camelContext, LoggingLevel.class, value));
                    }
                    value = XmlHelper.getAttribute(n, "errorHandlerRef");
                    if (value != null) {
                        camelContext
                                .getCamelContextExtension()
                                .setErrorHandlerFactory(new RefErrorHandlerDefinition(
                                        CamelContextHelper.parseText(camelContext, value)));
                    }
                }
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    private void discoverBeans(CamelContext camelContext, String fileName, Document dom) {
        Resource resource = camelContext
                .getCamelContextExtension()
                .getContextPlugin(ResourceLoader.class)
                .resolveResource("file:" + fileName);

        NodeList beans = dom.getElementsByTagName("bean");
        for (int i = 0; i < beans.getLength(); i++) {
            Node n = beans.item(i);
            if (n.hasAttributes()) {
                String id = XmlHelper.getAttribute(n, "id");
                if (id != null) {
                    delayedBeans.put(id, n);
                    resources.put(id, resource);
                }
            }
        }
    }

    protected String extractValue(CamelContext camelContext, String val, boolean resolve) {
        // blueprint placeholder prefix
        if (val != null && val.contains("${")) {
            Matcher matcher = BLUEPRINT_PATTERN.matcher(val);
            while (matcher.find()) {
                String replace = "{{" + matcher.group(1) + "}}";
                val = matcher.replaceFirst(replace);
                // we changed so reset matcher so it can find more
                matcher.reset(val);
            }
        }

        if (resolve && camelContext != null) {
            // if running camel then resolve property placeholders from beans
            val = camelContext.resolvePropertyPlaceholders(val);
        }
        return val;
    }

    /**
     * Try to instantiate bean from the definition.
     */
    private void registerAndCreateBean(CamelContext camelContext, BeanFactoryDefinition<?> def, boolean delayIfFailed) {
        String type = def.getType();
        String name = def.getName();
        if (name == null || name.isBlank()) {
            name = type;
        }
        if (type != null) {
            if (!type.startsWith("#")) {
                type = "#class:" + type;
            }
            try {
                // factory bean/method
                if (def.getFactoryBean() != null && def.getFactoryMethod() != null) {
                    type = type + "#" + def.getFactoryBean() + ":" + def.getFactoryMethod();
                } else if (def.getFactoryMethod() != null) {
                    type = type + "#" + def.getFactoryMethod();
                }
                // property binding support has constructor arguments as part of the type
                StringJoiner ctr = new StringJoiner(", ");
                if (def.getConstructors() != null && !def.getConstructors().isEmpty()) {
                    // need to sort constructor args based on index position
                    Map<Integer, Object> sorted = new TreeMap<>(def.getConstructors());
                    for (Object val : sorted.values()) {
                        String text = val.toString();
                        if (!StringHelper.isQuoted(text)) {
                            text = "\"" + text + "\"";
                        }
                        ctr.add(text);
                    }
                    type = type + "(" + ctr + ")";
                }

                final Object target = PropertyBindingSupport.resolveBean(camelContext, type);

                if (def.getProperties() != null && !def.getProperties().isEmpty()) {
                    PropertyBindingSupport.setPropertiesOnTarget(camelContext, target, def.getProperties());
                }

                bindBean(camelContext, def, name, target);

            } catch (Exception e) {
                if (delayIfFailed) {
                    delayedRegistrations.add(def);
                } else {
                    boolean ignore = PluginHelper.getRoutesLoader(camelContext).isIgnoreLoadingError();
                    if (ignore) {
                        // still add bean if we are ignore loading as we want to know about all beans if possible
                        addBeanToCamelModel(camelContext, name, def);
                    }
                    LOG.warn("Error creating bean: {} due to: {}. This exception is ignored.", type, e.getMessage(), e);
                }
            }
        }
    }

    protected void bindBean(CamelContext camelContext, BeanFactoryDefinition<?> def, String name, Object target)
            throws Exception {
        // destroy and unbind any existing bean
        destroyBean(name, true);
        camelContext.getRegistry().unbind(name);

        // invoke init method and register bean
        String initMethod = def.getInitMethod();
        if (initMethod != null) {
            ObjectHelper.invokeMethodSafe(initMethod, target);
        }
        camelContext.getRegistry().bind(name, target);

        // remember to destroy bean on shutdown
        if (def.getDestroyMethod() != null) {
            beansToDestroy.put(name, new KeyValueHolder<>(target, def.getDestroyMethod()));
        }

        addBeanToCamelModel(camelContext, name, def);
    }

    protected void addBeanToCamelModel(CamelContext camelContext, String name, BeanFactoryDefinition<?> def) {
        // register bean in model
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            LOG.debug("Adding OSGi <blueprint> XML bean: {} to DSL model", name);
            model.addCustomBean(def);
        }
    }

    protected void destroyBean(String name, boolean remove) {
        var holder = remove ? beansToDestroy.remove(name) : beansToDestroy.get(name);
        if (holder != null) {
            String destroyMethod = holder.getValue();
            Object target = holder.getKey();
            try {
                ObjectHelper.invokeMethodSafe(destroyMethod, target);
            } catch (Exception e) {
                LOG.warn(
                        "Error invoking destroy method: {} on bean: {} due to: {}. This exception is ignored.",
                        destroyMethod,
                        target,
                        e.getMessage(),
                        e);
            }
        }
    }

    public void stop() {
        // beans should trigger destroy methods on shutdown
        for (String name : beansToDestroy.keySet()) {
            destroyBean(name, false);
        }
        beansToDestroy.clear();
    }
}
