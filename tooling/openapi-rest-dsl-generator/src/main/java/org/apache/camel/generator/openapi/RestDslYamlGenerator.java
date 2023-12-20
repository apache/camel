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
package org.apache.camel.generator.openapi;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import io.apicurio.datamodels.models.openapi.OpenApiPathItem;
import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;

public class RestDslYamlGenerator extends RestDslGenerator<RestDslYamlGenerator> {

    private static final String[] VERBS = new String[] { "delete", "get", "head", "patch", "post", "put" };
    private static final String[] FIELD_ORDER
            = new String[] { "id", "path", "description", "consumes", "produces", "type", "outType", "param" };

    RestDslYamlGenerator(final OpenApiDocument document) {
        super(document);
    }

    public String generate(final CamelContext context) throws Exception {
        return generate(context, false);
    }

    public String generate(final CamelContext context, boolean generateRoutes) throws Exception {
        final RestDefinitionEmitter emitter = new RestDefinitionEmitter();
        final String basePath = RestDslGenerator.determineBasePathFrom(this.basePath, document);
        final PathVisitor<RestsDefinition> restDslStatement = new PathVisitor<>(
                basePath, emitter, filter,
                destinationGenerator());

        for (String name : document.getPaths().getItemNames()) {
            OpenApiPathItem item = document.getPaths().getItem(name);
            restDslStatement.visit(name, item);
        }

        final RestsDefinition rests = emitter.result();
        final String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, rests);

        final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        builderFactory.setNamespaceAware(true);

        final DocumentBuilder builder = builderFactory.newDocumentBuilder();

        final Document document = builder.parse(new InputSource(new StringReader(xml)));

        final Element root = document.getDocumentElement();

        // remove all customId attributes as we do not want them in the output
        final NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            final Element element = (Element) elements.item(i);
            element.removeAttribute("customId");
        }

        boolean restConfig = restComponent != null || restContextPath != null || clientRequestValidation;
        if (restConfig) {
            final Element configuration = document.createElement("restConfiguration");
            if (ObjectHelper.isNotEmpty(restComponent)) {
                configuration.setAttribute("component", restComponent);
            }
            if (ObjectHelper.isNotEmpty(restContextPath)) {
                configuration.setAttribute("contextPath", restContextPath);
            }
            if (ObjectHelper.isNotEmpty(apiContextPath)) {
                configuration.setAttribute("apiContextPath", apiContextPath);
            }
            if (clientRequestValidation) {
                configuration.setAttribute("clientRequestValidation", "true");
            }
            root.insertBefore(configuration, root.getFirstChild());
        }

        // convert from xml to yaml via jackson
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        try {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException e) {
            // ignore
        }
        try {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException e) {
            // ignore
        }
        final Transformer transformer = transformerFactory.newTransformer();

        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String newXml = writer.toString();

        // convert XML to yaml
        XmlMapper xmlMapper = new XmlMapper();
        JsonNode node = xmlMapper.readTree(newXml.getBytes());

        Map<String, String> toTagData = new HashMap<>();

        for (String v : VERBS) {
            fixVerbNodes(xmlMapper, node, v);
            fixParamNodes(xmlMapper, node, v);
            sortVerb(node, v);
            toTagData.putAll(fixToTags(xmlMapper, node, v));
        }

        // the root tag should be an array
        node = fixRootNode(xmlMapper, node);

        // add Routes
        if (generateRoutes) {
            for (Map.Entry<String, String> entry : toTagData.entrySet()) {
                ObjectNode from = JsonNodeFactory.instance.objectNode();
                from.set("uri", new TextNode(entry.getKey()));
                String description = entry.getValue();
                if (description != null && !description.isBlank()) {
                    from.set("description", new TextNode(description));
                }
                ObjectNode route = JsonNodeFactory.instance.objectNode();
                route.set("from", from);
                ((ArrayNode) node).add(xmlMapper.createObjectNode().set("route", route));
            }
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        return mapper.writeValueAsString(node);
    }

    private static JsonNode fixRootNode(XmlMapper xmlMapper, JsonNode node) {
        JsonNode r = node.get("rest");
        if (r != null) {
            ArrayNode arr = xmlMapper.createArrayNode();
            // if rest configuration is present then put it in the top
            JsonNode rc = node.get("restConfiguration");
            if (rc != null) {
                arr.add(xmlMapper.createObjectNode().set("restConfiguration", rc));
            }
            arr.add(xmlMapper.createObjectNode().set("rest", r));
            node = arr;
        }
        return node;
    }

    /**
     * we want verbs to have its children sorted in a specific order so the generated rest-dsl is always the same
     * structure and that we have id, uri, ... in the top
     */
    private static void sortVerb(JsonNode node, String verb) {
        JsonNode verbs = node.path("rest").path(verb);
        if (verbs == null || verbs.isMissingNode()) {
            return;
        }
        for (JsonNode n : verbs) {
            List<String> names = new ArrayList<>();
            if (n.isObject()) {
                ObjectNode on = (ObjectNode) n;
                // sort the elements: id, path, description, consumes, produces, type, outType, param
                Iterator<String> it = on.fieldNames();
                while (it.hasNext()) {
                    names.add(it.next());
                }
                // sort the nodes
                names.sort((o1, o2) -> {
                    int i1 = fieldOrderIndex(o1);
                    int i2 = fieldOrderIndex(o2);
                    if (i1 == i2) {
                        return o1.compareTo(o2);
                    }
                    return i1 < i2 ? -1 : 1;
                });
                // reorder according to sorted set of names
                List<JsonNode> nodes = new ArrayList<>();
                for (String name : names) {
                    nodes.add(on.get(name));
                }
                on.removeAll();
                for (int i = 0; i < nodes.size(); i++) {
                    JsonNode nn = nodes.get(i);
                    String fn = names.get(i);
                    on.set(fn, nn);
                }
            }
        }
    }

    /**
     * param nodes should be an array list, but if there is only 1 param then there is only 1 <param> in XML and jackson
     * parses that into a single node, so we need to change that into an array node
     */
    private static void fixParamNodes(XmlMapper xmlMapper, JsonNode node, String verb) {
        JsonNode verbs = node.path("rest").path(verb);
        if (verbs == null || verbs.isMissingNode()) {
            return;
        }
        if (!verbs.isArray()) {
            // the rest has only 1 verb so fool the code below and wrap in an new array
            ArrayNode arr = xmlMapper.createArrayNode();
            arr.add(verbs);
            verbs = arr;
        }
        for (JsonNode n : verbs) {
            // fix param to always be an array node
            JsonNode p = n.get("param");
            if (p != null && !p.isArray()) {
                // it should be an array
                ArrayNode arr = xmlMapper.createArrayNode();
                arr.add(p);
                ObjectNode on = (ObjectNode) n;
                on.set("param", arr);
                p = arr;
            }
            // fix required to be boolean type
            if (p != null) {
                for (JsonNode pc : p) {
                    JsonNode r = pc.get("required");
                    if (r != null) {
                        String t = r.textValue();
                        boolean b = Boolean.parseBoolean(t);
                        ObjectNode on = (ObjectNode) pc;
                        BooleanNode bn = xmlMapper.createObjectNode().booleanNode(b);
                        on.set("required", bn);
                    }
                    String k = "allowableValues";
                    r = pc.get(k);
                    if (r == null) {
                        k = "allowable-values";
                        r = pc.get(k);
                    }
                    if (r != null) {
                        // remove value node
                        JsonNode v = r.get("value");
                        if (v.isArray()) {
                            ObjectNode on = (ObjectNode) pc;
                            on.set(k, v);
                            on.remove("value");
                        }
                    }
                }
            }
        }
    }

    /**
     * verb nodes should be an array list, but if there is only 1 verb then there is only 1 <verb> in XML and jackson
     * parses that into a single node, so we need to change that into an array node
     */
    private static void fixVerbNodes(XmlMapper xmlMapper, JsonNode node, String verb) {
        JsonNode verbs = node.path("rest").path(verb);
        if (verbs == null || verbs.isMissingNode()) {
            return;
        }
        if (verbs.isObject()) {
            ArrayNode arr = xmlMapper.createArrayNode();
            ObjectNode on = (ObjectNode) verbs;
            arr.add(on);
            ObjectNode n = (ObjectNode) node.path("rest");
            n.set(verb, arr);
        }
    }

    /**
     * to tag should be in implicit mode, ex: to: "direct:directX"
     */
    private static Map<String, String> fixToTags(XmlMapper xmlMapper, JsonNode node, String verb) {
        Map<String, String> toTags = new HashMap<>();
        JsonNode verbs = node.path("rest").path(verb);
        if (verbs == null || verbs.isMissingNode()) {
            return toTags;
        }
        if (!verbs.isArray()) {
            // the rest has only 1 verb so fool the code below and wrap in an new array
            ArrayNode arr = xmlMapper.createArrayNode();
            arr.add(verbs);
            verbs = arr;
        }
        for (JsonNode n : verbs) {
            if (n.has("to")) {
                ObjectNode on = (ObjectNode) n;
                JsonNode uri = n.get("to").get("uri");
                on.set("to", uri);
                String description = n.has("description") ? n.get("description").asText() : "";
                toTags.put(uri.textValue(), description);
            }
        }
        return toTags;
    }

    private static int fieldOrderIndex(String field) {
        // to should be last
        if ("to".equals(field)) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < FIELD_ORDER.length; i++) {
            if (FIELD_ORDER[i].equals(field)) {
                return i;
            }
        }
        return Integer.MAX_VALUE - 1;
    }

}
