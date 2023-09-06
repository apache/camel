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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.util.XmlHelper;
import org.apache.camel.model.Model;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for parsing and discovering legacy OSGi <blueprint> XML to make it runnable on camel-jbang, and for tooling to
 * migrate this to modern Camel DSL in plain Camel XML or YAML DSL.
 */
public class BlueprintXmlBeansHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BlueprintXmlBeansHandler.class);
    private static final Pattern BLUEPRINT_PATTERN = Pattern.compile("\\$\\{(.*?)}"); // non-greedy mode

    // when preparing blueprint-based beans, we may have problems loading classes which are provided with Java DSL
    // that's why some beans should be processed later
    private final Map<String, Node> delayedBeans = new LinkedHashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final List<RegistryBeanDefinition> delayedRegistrations = new ArrayList<>();

    /**
     * Parses the XML documents and discovers blueprint beans, which will be created manually via Camel.
     */
    public void processBlueprintBeans(
            CamelContext camelContext, MainConfigurationProperties config, final Map<String, Document> xmls) {

        LOG.debug("Loading beans from classic OSGi <blueprint> XML");

        xmls.forEach((id, doc) -> {
            if (id.startsWith("camel-xml-io-dsl-blueprint-xml:")) {
                // this is a camel bean via camel-xml-io-dsl
                String fileName = StringHelper.afterLast(id, ":");
                discoverBeans(camelContext, fileName, doc);
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
            RegistryBeanDefinition def = createBeanModel(camelContext, id, n);
            LOG.debug("Creating bean: {}", def.getName());
            registerBeanDefinition(camelContext, def, true);
        }

        if (!delayedRegistrations.isEmpty()) {
            // some of the beans were not available yet, so we have to try register them now
            for (RegistryBeanDefinition def : delayedRegistrations) {
                LOG.debug("Creating bean (2nd-try): {}", def.getName());
                registerBeanDefinition(camelContext, def, false);
            }
            delayedRegistrations.clear();
        }

    }

    private RegistryBeanDefinition createBeanModel(CamelContext camelContext, String name, Node node) {
        RegistryBeanDefinition rrd = new RegistryBeanDefinition();
        rrd.setResource(resources.get(name));
        rrd.setType(XmlHelper.getAttribute(node, "class"));
        rrd.setName(name);

        // constructor arguments
        StringJoiner sj = new StringJoiner(", ");
        NodeList props = node.getChildNodes();
        for (int i = 0; i < props.getLength(); i++) {
            Node child = props.item(i);
            // assume the args are in order (1, 2)
            if ("argument".equals(child.getNodeName())) {
                String val = XmlHelper.getAttribute(child, "value");
                String ref = XmlHelper.getAttribute(child, "ref");
                if (val != null) {
                    sj.add("'" + extractValue(camelContext, val, false) + "'");
                } else if (ref != null) {
                    sj.add("'#bean:" + extractValue(camelContext, ref, false) + "'");
                }
            }
        }
        if (sj.length() > 0) {
            rrd.setType("#class:" + rrd.getType() + "(" + sj + ")");
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

                // TODO: List/Map properties
                if (key != null && val != null) {
                    properties.put(key, extractValue(camelContext, val, false));
                } else if (key != null && ref != null) {
                    properties.put(key, extractValue(camelContext, "#bean:" + ref, false));
                }
            }
        }
        if (!properties.isEmpty()) {
            rrd.setProperties(properties);
        }

        return rrd;
    }

    private void discoverBeans(CamelContext camelContext, String fileName, Document dom) {
        Resource resource = camelContext.getCamelContextExtension().getContextPlugin(ResourceLoader.class)
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
    private void registerBeanDefinition(CamelContext camelContext, RegistryBeanDefinition def, boolean delayIfFailed) {
        String type = def.getType();
        String name = def.getName();
        if (name == null || name.trim().isEmpty()) {
            name = type;
        }
        if (type != null) {
            if (!type.startsWith("#")) {
                type = "#class:" + type;
            }
            try {
                final Object target = PropertyBindingSupport.resolveBean(camelContext, type);

                if (def.getProperties() != null && !def.getProperties().isEmpty()) {
                    PropertyBindingSupport.setPropertiesOnTarget(camelContext, target, def.getProperties());
                }
                camelContext.getRegistry().unbind(name);
                camelContext.getRegistry().bind(name, target);

                // register bean in model
                Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
                model.addRegistryBean(def);

            } catch (Exception e) {
                if (delayIfFailed) {
                    delayedRegistrations.add(def);
                } else {
                    LOG.warn("Error creating bean: {} due to: {}. This exception is ignored.", type, e.getMessage(), e);
                }
            }
        }
    }

}
