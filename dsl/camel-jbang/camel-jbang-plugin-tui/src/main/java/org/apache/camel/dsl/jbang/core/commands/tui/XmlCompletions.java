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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * The Tab completions of the XML DSL in the Source editor (CAMEL-25240): after < the elements that go inside the parent
 * element, in a start tag its attributes, in an attribute its values. The structure comes from the camel-xml-io XSD of
 * the catalog, the values (enums, true/false) from the catalog models of the EIPs and languages, as YAML values do.
 */
final class XmlCompletions {

    /** Where the cursor goes in an inserted text; taken out before it is inserted. */
    static final char CARET = '';

    /** The elements an XML routes file starts with. */
    private static final List<String> ROOTS = List.of(
            "routes", "route", "routeConfiguration", "routeTemplates", "templatedRoutes", "rests", "rest", "beans");

    // the schema of the last catalog: reading it once per catalog version
    private static volatile Schema schema;

    private record Schema(CamelCatalog catalog, XmlSchemaModel model) {
    }

    private XmlCompletions() {
    }

    static List<AutocompletePopup.CompletionItem> provide(
            CamelCatalog catalog, XmlCompletionContext context,
            Supplier<List<AutocompletePopup.CompletionItem>> placeholders) {
        if (catalog == null || context == null) {
            return List.of();
        }
        XmlSchemaModel model = model(catalog);
        if (model == null) {
            return List.of();
        }
        return switch (context.kind()) {
            case ELEMENT -> elements(catalog, model, context);
            case ATTRIBUTE -> attributes(catalog, model, context);
            case VALUE -> values(catalog, context, placeholders);
        };
    }

    static XmlSchemaModel model(CamelCatalog catalog) {
        Schema s = schema;
        if (s == null || s.catalog() != catalog) {
            XmlSchemaModel model = XmlSchemaModel.parse(catalog.xmlIoSchemaAsXml());
            if (model == null) {
                return null;
            }
            s = new Schema(catalog, model);
            schema = s;
        }
        return s.model();
    }

    private static List<AutocompletePopup.CompletionItem> elements(
            CamelCatalog catalog, XmlSchemaModel model, XmlCompletionContext c) {
        List<XmlSchemaModel.Child> children = new ArrayList<>();
        if (c.path().isEmpty()) {
            for (String root : ROOTS) {
                if (model.elementType(root) != null) {
                    children.add(new XmlSchemaModel.Child(root, model.elementType(root), model.elementDoc(root)));
                }
            }
        } else {
            String type = model.typeOf(c.path());
            if (type == null) {
                return List.of();
            }
            children.addAll(model.children(type));
            // one expression per element: once the when has its simple, the other languages are no choice anymore
            Set<String> languages = new HashSet<>(catalog.findLanguageNames());
            // the generic expression elements, besides the languages by name
            languages.add("language");
            languages.add("expressionDefinition");
            if (c.siblings().stream().anyMatch(languages::contains)) {
                children.removeIf(child -> languages.contains(child.name()));
            }
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (XmlSchemaModel.Child child : children) {
            items.add(new AutocompletePopup.CompletionItem(
                    child.name(), child.doc(), null, null, false, null, null, false,
                    elementSnippet(catalog, model, child, c.open(), c.ns())));
        }
        return items;
    }

    /**
     * The text of a new element: its required attributes, and its end tag or /> when nothing goes inside it, the cursor
     * in the first required attribute, else inside the element: &lt;to uri="|"/&gt;, &lt;split&gt;|&lt;/split&gt;.
     */
    static String elementSnippet(
            CamelCatalog catalog, XmlSchemaModel model, XmlSchemaModel.Child child, boolean open, String ns) {
        StringBuilder sb = new StringBuilder(open ? "" : "<" + ns);
        sb.append(child.name());
        boolean caret = false;
        if (child.type() != null) {
            for (XmlSchemaModel.Attribute a : attributes(catalog, model, child.name(), child.type())) {
                if (a.required()) {
                    sb.append(' ').append(a.name()).append("=\"");
                    if (!caret) {
                        sb.append(CARET);
                        caret = true;
                    }
                    sb.append('"');
                }
            }
        }
        boolean empty = child.type() != null && model.children(child.type()).isEmpty()
                && !model.hasText(child.type());
        if (empty) {
            sb.append("/>");
            if (!caret) {
                sb.append(CARET);
            }
        } else {
            sb.append('>');
            if (!caret) {
                sb.append(CARET);
            }
            sb.append("</").append(ns).append(child.name()).append('>');
        }
        return sb.toString();
    }

    private static List<AutocompletePopup.CompletionItem> attributes(
            CamelCatalog catalog, XmlSchemaModel model, XmlCompletionContext c) {
        List<String> path = new ArrayList<>(c.path());
        path.add(c.element());
        String type = model.typeOf(path);
        if (type == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (XmlSchemaModel.Attribute a : attributes(catalog, model, c.element(), type)) {
            if (c.given().contains(a.name())) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    a.name(), a.doc(), null, null, false, null, null, a.required(),
                    a.name() + "=\"" + CARET + "\""));
        }
        // the required ones first, then as the schema has them (the element's own before id, description...)
        items.sort((x, y) -> Boolean.compare(y.required(), x.required()));
        return items;
    }

    /**
     * The attributes of an element, the schema's completed by the catalog model of the EIP or language: the schema
     * leaves out required and the documentation of some inherited ones (uri of to, toD, wireTap).
     */
    static List<XmlSchemaModel.Attribute> attributes(
            CamelCatalog catalog, XmlSchemaModel model, String element, String type) {
        List<XmlSchemaModel.Attribute> found = new ArrayList<>();
        List<? extends BaseOptionModel> options = options(catalog, element);
        for (XmlSchemaModel.Attribute a : model.attributes(type)) {
            BaseOptionModel option = options.stream().filter(o -> o.getName().equals(a.name())).findFirst().orElse(null);
            if (option == null) {
                found.add(a);
            } else {
                found.add(new XmlSchemaModel.Attribute(
                        a.name(), a.required() || option.isRequired(), a.doc() != null ? a.doc() : option.getDescription()));
            }
        }
        return found;
    }

    /** The options of the EIP, else of the language, of an element. */
    private static List<? extends BaseOptionModel> options(CamelCatalog catalog, String element) {
        EipModel eip = catalog.eipModel(element);
        if (eip != null) {
            return eip.getOptions();
        }
        LanguageModel language = catalog.languageModel(element);
        return language != null ? language.getOptions() : List.of();
    }

    /** The values of an attribute: the enums or true/false of the option it sets, and the property placeholders. */
    private static List<AutocompletePopup.CompletionItem> values(
            CamelCatalog catalog, XmlCompletionContext c, Supplier<List<AutocompletePopup.CompletionItem>> placeholders) {
        BaseOptionModel option = options(catalog, c.element()).stream()
                .filter(o -> o.getName().equals(c.attribute())).findFirst().orElse(null);
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        if (option != null) {
            if (option.getEnums() != null && !option.getEnums().isEmpty()) {
                for (String value : option.getEnums()) {
                    items.add(new AutocompletePopup.CompletionItem(
                            value, option.getDescription(), option.getType(), option.getDefaultValue(), false, null,
                            option.getGroup()));
                }
            } else if ("boolean".equals(option.getType())) {
                for (String value : List.of("true", "false")) {
                    items.add(new AutocompletePopup.CompletionItem(
                            value, option.getDescription(), "boolean", option.getDefaultValue(), false, null,
                            option.getGroup()));
                }
            }
        }
        if (placeholders != null) {
            items.addAll(placeholders.get());
        }
        return items;
    }
}
