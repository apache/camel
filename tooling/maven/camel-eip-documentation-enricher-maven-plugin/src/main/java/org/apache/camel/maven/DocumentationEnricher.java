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
package org.apache.camel.maven;

import java.io.File;
import java.util.Map;
import java.util.stream.Stream;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.plugin.logging.Log;

import static org.apache.camel.maven.XmlHelper.isNullOrEmpty;

/**
 * Enriches xml document with documentation from json files.
 */
public class DocumentationEnricher {
    private final Document document;

    public DocumentationEnricher(Document document) {
        this.document = document;
    }

    public void enrichTopLevelElementsDocumentation(NodeList elements, Map<String, File> jsonFiles) {
        for (int i = 0; i < elements.getLength(); i++) {
            Element item = (Element) elements.item(i);
            String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
            if (jsonFiles.containsKey(name)) {
                addElementDocumentation(item, jsonFiles.get(name));
            }
        }
    }

    public void enrichTypeAttributesDocumentation(Log log, NodeList attributeElements, File jsonFile) {
        for (int j = 0; j < attributeElements.getLength(); j++) {
            Element item = (Element) attributeElements.item(j);
            addAttributeDocumentation(log, item, jsonFile);
        }
    }

    private void addElementDocumentation(Element item, File jsonFile) {
        BaseModel<?> model = JsonMapper.generateModel(jsonFile.toPath());
        String descriptionText = model.getDescription();
        addDocumentation(item, descriptionText);
    }

    private void addAttributeDocumentation(Log log, Element item, File jsonFile) {

        String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
        if (isNullOrEmpty(name)) {
            return;
        }

        BaseModel<?> model = JsonMapper.generateModel(jsonFile.toPath());
        BaseOptionModel option = Stream.concat(model.getOptions().stream(),
                    model instanceof ComponentModel ? ((ComponentModel) model).getEndpointOptions().stream() : Stream.empty())
                .filter(o -> name.equals(o.getName()))
                .findAny().orElse(null);

        String descriptionText = option != null ? option.getDescription() : null;
        Object defaultValueText = option != null ? option.getDefaultValue() : null;

        // special for this option
        if ("useBlueprintPropertyResolver".equals(name)) {
            descriptionText = "Whether to automatic detect OSGi Blueprint property placeholder service in use, and bridge with Camel property placeholder."
                    + " When enabled this allows you to only setup OSGi Blueprint property placeholder and Camel can use the properties in the camelContext.";
        } else if ("binding".equals(name)) {
            descriptionText = "In binding mode we bind the passed in arguments (args) to the created exchange using the existing Camel"
                    + " @Body, @Header, @Headers, @ExchangeProperty annotations if no annotation then its bound as the message body";
        } else if ("serviceRef".equals(name) && jsonFile.getName().endsWith("proxy.json")) {
            descriptionText = "Reference to existing endpoint to lookup by endpoint id in the Camel registry to be used as proxied service";
        }

        if (option != null && option.isDeprecated()) {
            descriptionText = "Deprecated: " + descriptionText;
        }

        if (!isNullOrEmpty(descriptionText)) {
            String text = descriptionText;
            if (!text.endsWith(".")) {
                text += ".";
            }
            if (defaultValueText != null && !"".equals(defaultValueText)) {
                text += " Default value: " + defaultValueText;
            }
            addDocumentation(item, text);
        } else {
            // we should skip warning about these if no documentation as they are special
            boolean skip = "customId".equals(name) || "inheritErrorHandler".equals(name)
                    || "rest".equals(name) && jsonFile.getName().endsWith("route.json");
            if (!skip) {
                log.warn("Cannot find documentation for name: " + name + " in json schema: " + jsonFile);
            }
        }
    }

    private void addDocumentation(Element item, String textContent) {
        Element annotation = document.createElement(Constants.XS_ANNOTATION_ELEMENT_NAME);
        Element documentation = document.createElement(Constants.XS_DOCUMENTATION_ELEMENT_NAME);
        documentation.setAttribute("xml:lang", "en");
        CDATASection cdataDocumentationText = document.createCDATASection(formatTextContent(item, textContent));
        documentation.appendChild(cdataDocumentationText);
        annotation.appendChild(documentation);
        if (item.getFirstChild() != null) {
            item.insertBefore(annotation, item.getFirstChild());
        } else {
            item.appendChild(annotation);
        }
    }

    private String formatTextContent(Element item, String textContent) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(System.lineSeparator())
                .append(WordUtils.wrap(textContent, Constants.WRAP_LENGTH))
                .append(System.lineSeparator());
        // Fix closing tag intention.
        stringBuilder.append(Constants.DEFAULT_XML_INTENTION);
        for (Node parent = item.getParentNode(); parent != null; parent = parent.getParentNode()) {
            stringBuilder.append(Constants.DEFAULT_XML_INTENTION);
        }
        return stringBuilder.toString();
    }

}
