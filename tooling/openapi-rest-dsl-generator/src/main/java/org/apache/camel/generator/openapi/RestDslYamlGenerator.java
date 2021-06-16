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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.util.ObjectHelper;

public class RestDslYamlGenerator extends RestDslGenerator<RestDslYamlGenerator> {

    private static final String[] VERBS = new String[] { "delete", "get", "head", "patch", "post", "put" };

    RestDslYamlGenerator(final OasDocument document) {
        super(document);
    }

    public String generate(final CamelContext context) throws Exception {
        final RestDefinitionEmitter emitter = new RestDefinitionEmitter(context);
        final String basePath = RestDslGenerator.determineBasePathFrom(this.basePath, document);
        final PathVisitor<RestsDefinition> restDslStatement = new PathVisitor<>(
                basePath, emitter, filter,
                destinationGenerator());

        document.paths.getPathItems().forEach(restDslStatement::visit);

        final RestsDefinition rests = emitter.result();
        final ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        final String xml = ecc.getModelToXMLDumper().dumpModelAsXml(context, rests);

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

        if (restComponent != null) {
            final Element configuration = document.createElement("restConfiguration");
            configuration.setAttribute("component", restComponent);

            if (restContextPath != null) {
                configuration.setAttribute("contextPath", restContextPath);
            }

            if (ObjectHelper.isNotEmpty(apiContextPath)) {
                configuration.setAttribute("apiContextPath", apiContextPath);
            }

            root.insertBefore(configuration, root.getFirstChild());
        }

        // convert from xml to yaml via jackson
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        final Transformer transformer = transformerFactory.newTransformer();

        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String newXml = writer.toString();

        // convert XML to yaml
        XmlMapper xmlMapper = new XmlMapper();
        JsonNode node = xmlMapper.readTree(newXml.getBytes());

        // param nodes should be an array list, but if there is only 1 param then there is only 1 <param> in XML
        // and jackson parses that into a single node, so we need to change that into an array node
        for (String v : VERBS) {
            paramAsArray(xmlMapper, node, v);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        String yaml = mapper.writeValueAsString(node);
        return yaml;
    }

    private static void paramAsArray(XmlMapper xmlMapper, JsonNode node, String verb) {
        JsonNode puts = node.path("rest").path(verb);
        for (JsonNode n : puts) {
            JsonNode p = n.get("param");
            if (p != null && !p.isArray()) {
                // it should be an array
                ArrayNode arr = xmlMapper.createArrayNode();
                arr.add(p);
                ObjectNode on = (ObjectNode) n;
                on.set("param", arr);
            }
        }
    }

}
