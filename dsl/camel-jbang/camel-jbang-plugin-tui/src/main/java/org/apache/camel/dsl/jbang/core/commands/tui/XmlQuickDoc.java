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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;

/**
 * The quick doc of the XML element or attribute the cursor is on (CAMEL-25244): on an attribute name or in its value
 * the attribute (its documentation, default and values), on an element name the element and its required attributes.
 * From the XML schema of the catalog, completed by the catalog models, as the XML completion; read from the text above
 * the cursor, so it works while the file does not parse. The uri attribute is left to the endpoint doc of the line.
 */
final class XmlQuickDoc {

    static final String TITLE = "XML";

    private XmlQuickDoc() {
    }

    /** The doc of what the cursor is on: col of row; nothing in the view (col -1), which has the doc of the line. */
    static List<SourceViewer.DocEntry> at(CamelCatalog catalog, List<String> lines, int row, int col) {
        if (catalog == null || lines == null || row < 0 || row >= lines.size() || col < 0) {
            return List.of();
        }
        String line = lines.get(row);
        if (col > line.length()) {
            return List.of();
        }
        // the whole name the cursor is in: the context is read up to its end
        int end = col;
        while (end < line.length() && isNameChar(line.charAt(end))) {
            end++;
        }
        XmlCompletionContext c = XmlCompletionContext.at(lines, row, end);
        if (c == null) {
            return List.of();
        }
        XmlSchemaModel model = XmlCompletions.model(catalog);
        if (model == null) {
            return List.of();
        }
        return switch (c.kind()) {
            case ELEMENT -> c.open() && !c.prefix().isEmpty() ? element(catalog, model, c) : List.of();
            case ATTRIBUTE -> c.prefix().isEmpty() ? List.of() : attribute(catalog, model, c, c.prefix());
            case VALUE -> "uri".equals(c.attribute()) ? List.of() : attribute(catalog, model, c, c.attribute());
        };
    }

    private static List<SourceViewer.DocEntry> element(
            CamelCatalog catalog, XmlSchemaModel model, XmlCompletionContext c) {
        String name = c.prefix();
        List<String> path = new ArrayList<>(c.path());
        path.add(name);
        String type = model.typeOf(path);
        if (type == null) {
            return List.of();
        }
        String doc = null;
        String parentType = c.path().isEmpty() ? null : model.typeOf(c.path());
        if (parentType != null) {
            for (XmlSchemaModel.Child child : model.children(parentType)) {
                if (child.name().equals(name)) {
                    doc = child.doc();
                }
            }
        }
        if (doc == null) {
            doc = model.elementDoc(name);
        }
        List<SourceViewer.DocEntry> entries = new ArrayList<>();
        entries.add(new SourceViewer.DocEntry(name + (doc != null ? " — " + doc : ""), false, TITLE));
        List<String> required = new ArrayList<>();
        for (XmlSchemaModel.Attribute a : XmlCompletions.attributes(catalog, model, name, type)) {
            if (a.required()) {
                required.add(a.name());
            }
        }
        if (!required.isEmpty()) {
            entries.add(new SourceViewer.DocEntry("Required: " + String.join(", ", required), false, TITLE));
        }
        return entries;
    }

    private static List<SourceViewer.DocEntry> attribute(
            CamelCatalog catalog, XmlSchemaModel model, XmlCompletionContext c, String name) {
        if (c.element() == null) {
            return List.of();
        }
        List<String> path = new ArrayList<>(c.path());
        path.add(c.element());
        String type = model.typeOf(path);
        if (type == null) {
            return List.of();
        }
        XmlSchemaModel.Attribute attribute = null;
        for (XmlSchemaModel.Attribute a : XmlCompletions.attributes(catalog, model, c.element(), type)) {
            if (a.name().equals(name)) {
                attribute = a;
            }
        }
        if (attribute == null) {
            return List.of();
        }
        List<SourceViewer.DocEntry> entries = new ArrayList<>();
        entries.add(new SourceViewer.DocEntry(
                c.element() + " " + name + (attribute.required() ? " (required)" : "")
                                              + (attribute.doc() != null ? " — " + attribute.doc() : ""),
                false, TITLE));
        BaseOptionModel option = XmlCompletions.option(catalog, c.element(), name);
        if (option != null) {
            // the values and default, unless the documentation says them already (the panel has three lines)
            String doc = attribute.doc() != null ? attribute.doc() : "";
            List<String> facts = new ArrayList<>();
            List<String> values = option.getEnums() != null && !option.getEnums().isEmpty()
                    ? option.getEnums() : "boolean".equals(option.getType()) ? List.of("true", "false") : List.of();
            if (!values.isEmpty() && !values.stream().allMatch(doc::contains)) {
                facts.add("Values: " + String.join(", ", values));
            }
            if (option.getDefaultValue() != null && !String.valueOf(option.getDefaultValue()).isEmpty()
                    && !doc.contains("Default value")) {
                facts.add("Default: " + option.getDefaultValue());
            }
            if (!facts.isEmpty()) {
                entries.add(new SourceViewer.DocEntry(String.join("   ", facts), false, TITLE));
            }
        }
        return entries;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':';
    }
}
