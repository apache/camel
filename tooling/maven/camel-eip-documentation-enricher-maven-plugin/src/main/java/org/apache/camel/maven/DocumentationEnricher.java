/**
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
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.util.JsonSchemaHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
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

    public void enrichTopLevelElementsDocumentation(NodeList elements, Map<String, File> jsonFiles) throws IOException {
        for (int i = 0; i < elements.getLength(); i++) {
            Element item = (Element) elements.item(i);
            String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
            if (jsonFiles.containsKey(name)) {
                addElementDocumentation(item, jsonFiles.get(name));
            }
        }
    }

    public void enrichTypeAttributesDocumentation(Log log, NodeList attributeElements, File jsonFile) throws IOException {
        for (int j = 0; j < attributeElements.getLength(); j++) {
            Element item = (Element) attributeElements.item(j);
            addAttributeDocumentation(log, item, jsonFile);
        }
    }

    private void addElementDocumentation(Element item, File jsonFile) throws IOException {
        List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema(Constants.MODEL_ATTRIBUTE_NAME, PackageHelper.fileToString(jsonFile), false);
        for (Map<String, String> row : rows) {
            if (row.containsKey(Constants.DESCRIPTION_ATTRIBUTE_NAME)) {
                String descriptionText = row.get(Constants.DESCRIPTION_ATTRIBUTE_NAME);
                addDocumentation(item, descriptionText);
                break;
            }
        }
    }

    private void addAttributeDocumentation(Log log, Element item, File jsonFile) throws IOException {

        String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
        if (isNullOrEmpty(name)) {
            return;
        }

        String descriptionText = null;
        String defaultValueText = null;
        String deprecatedText = null;

        List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema(Constants.PROPERTIES_ATTRIBUTE_NAME, PackageHelper.fileToString(jsonFile), true);
        for (Map<String, String> row : rows) {
            if (name.equals(row.get(Constants.NAME_ATTRIBUTE_NAME))) {
                descriptionText = row.get(Constants.DESCRIPTION_ATTRIBUTE_NAME);
                defaultValueText = row.get(Constants.DEFAULT_VALUE_ATTRIBUTE_NAME);
                deprecatedText = row.get(Constants.DEPRECATED_ATTRIBUTE_NAME);
            }
        }

        // special as this option is only in camel-blueprint
        if ("useBlueprintPropertyResolver".equals(name)) {
            descriptionText = "Whether to automatic detect OSGi Blueprint property placeholder service in use, and bridge with Camel property placeholder."
                    + " When enabled this allows you to only setup OSGi Blueprint property placeholder and Camel can use the properties in the <camelContext>.";
            defaultValueText = "true";
        }

        if ("true".equals(deprecatedText)) {
            descriptionText = "Deprecated: " + descriptionText;
        }

        if (!isNullOrEmpty(descriptionText)) {
            String text = descriptionText;
            if (!isNullOrEmpty(defaultValueText)) {
                text += (!text.endsWith(".") ? "." : "") + (" Default value: " + defaultValueText);
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
                .append(System.lineSeparator())
                // Fix closing tag intention.
                .append(StringUtils.repeat(Constants.DEFAULT_XML_INTENTION, getNodeDepth(item)));
        return stringBuilder.toString();
    }

    private int getNodeDepth(Node item) {
        int depth = 1;
        while (item.getParentNode() != null) {
            depth++;
            item = item.getParentNode();
        }
        return depth;
    }
}
