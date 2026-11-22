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
package org.apache.camel.dataformat.xsdjson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.dataformat.xsdjson.XsdModel.Attribute;
import org.apache.camel.dataformat.xsdjson.XsdModel.Element;
import org.apache.camel.dataformat.xsdjson.XsdModel.Group;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleType;
import org.apache.camel.dataformat.xsdjson.XsdModel.Type;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.ObjectHelper;

/**
 * Sample documents for the global elements of an XML Schema (an .xsd file, or the types of a .wsdl file), such as the
 * request and response of a SOAP operation. Every element and attribute is filled in with a value of its type: the
 * first value of an enumeration, a date for xs:date, a number for xs:int, and so on, and a repeating element has one
 * item. Of an xs:choice, only the first member is filled in, and an element whose type contains itself is filled in
 * once. A value is not made to match the other facets of its type, such as a pattern or a length.
 * <p>
 * The JSON is the form that the xsdJson data format reads and writes, without the root element (the default of
 * includeRoot), and the XML is what the data format writes from it, so a sample can be used as it is in a route.
 *
 * @since 4.24
 */
public final class XsdJsonSamples {

    private static final int MAX_DEPTH = 32;

    /**
     * Reads a document: the schema, or a schema or WSDL that it imports or includes.
     */
    @FunctionalInterface
    public interface DocumentReader {

        /**
         * @param  uri         the uri of the document, such as file:orders.wsdl or https://example.com/orders.xsd
         * @return             the content of the document
         * @throws IOException if the document cannot be read, or must not be
         */
        byte[] read(String uri) throws IOException;
    }

    private final XsdJsonDataFormat dataFormat;
    private final CamelContext camelContext;

    /**
     * Loads the schema as the xsdJson data format does.
     *
     * @param camelContext the CamelContext, which loads the schema
     * @param schema       the schema, such as file:orders.wsdl or classpath:orders.xsd
     */
    public XsdJsonSamples(CamelContext camelContext, String schema) {
        this(camelContext, schema, null);
    }

    /**
     * Loads the schema as the xsdJson data format does, with the documents read by the reader, such as to only read
     * from the host of a WSDL. The imports and includes still keep to the scheme of the schema.
     *
     * @param camelContext the CamelContext
     * @param schema       the schema, such as file:orders.wsdl or https://example.com/orders?wsdl
     * @param reader       reads the schema and the documents it imports or includes (null to use the CamelContext)
     */
    public XsdJsonSamples(CamelContext camelContext, String schema, DocumentReader reader) {
        this.camelContext = camelContext;
        this.dataFormat = new XsdJsonDataFormat();
        dataFormat.setCamelContext(camelContext);
        dataFormat.setSchema(schema);
        dataFormat.setDocumentReader(reader);
        dataFormat.setPrettyPrint(true);
        dataFormat.start();
    }

    /**
     * The sample of a global element as JSON text, indented.
     *
     * @param  element                  the element, as {namespace}name or as a local name that is unique in the schema
     * @throws IllegalArgumentException if the schema has no such element, or the element cannot be mapped
     */
    public String json(String element) {
        return JsonText.write(sample(element), true);
    }

    /**
     * The sample of a global element as an XML document, indented, as the xsdJson data format writes it from the JSON
     * sample.
     *
     * @param  element                  the element, as {namespace}name or as a local name that is unique in the schema
     * @throws IllegalArgumentException if the schema has no such element, or the element cannot be mapped
     */
    public String xml(String element) {
        Object json = sample(element);
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(XsdJsonDataFormat.ELEMENT, element);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            dataFormat.marshal(exchange, json, out);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot write the sample of " + element + ": " + e.getMessage(), e);
        }
        String xml = out.toString(StandardCharsets.UTF_8);
        // the root element on a line of its own, after the XML declaration
        int end = xml.startsWith("<?xml") ? xml.indexOf("?>") : -1;
        if (end > 0 && end + 2 < xml.length() && xml.charAt(end + 2) != '\n') {
            xml = xml.substring(0, end + 2) + "\n" + xml.substring(end + 2);
        }
        return xml;
    }

    private Object sample(String element) {
        XsdModel model = dataFormat.getModel();
        Element root = model.findElement(element);
        model.checkConflicts(root);
        if (root.unsupported != null) {
            throw new IllegalArgumentException("Cannot map " + root.name + ": " + root.unsupported);
        }
        return value(root.type, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private Object value(Type type, Set<Type> path, int depth) {
        if (type == null) {
            return "string";
        }
        if (type.unsupported != null) {
            throw new IllegalArgumentException("Cannot map " + type.displayName() + ": " + type.unsupported);
        }
        if (!type.complex) {
            return simple(type.simple);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        path.add(type);
        try {
            for (Attribute attribute : type.attributes) {
                map.put(attribute.jsonName, simple(attribute.type));
            }
            if (type.hasSimpleContent()) {
                map.put(dataFormat.getTextName(), simple(type.simple));
            } else if (type.content != null) {
                group(type.content, map, path, depth);
            }
        } finally {
            path.remove(type);
        }
        return map;
    }

    private void group(Group group, Map<String, Object> map, Set<Type> path, int depth) {
        for (Object member : group.members) {
            if (member instanceof Element e) {
                element(e, map, path, depth);
            } else if (member instanceof Group g) {
                group(g, map, path, depth);
            }
            if (group.kind == XsdModel.GroupKind.CHOICE) {
                // the first member of a choice only
                return;
            }
        }
    }

    private void element(Element e, Map<String, Object> map, Set<Type> path, int depth) {
        if (e.unsupported != null || e.type != null && path.contains(e.type) || depth >= MAX_DEPTH) {
            // an element that cannot be mapped, or that contains itself, is left out
            return;
        }
        Object value = value(e.type, path, depth + 1);
        map.put(e.jsonName(), e.array ? List.of(value) : value);
    }

    private static Object simple(SimpleType type) {
        if (type == null) {
            return "string";
        }
        Object value = scalar(type);
        return type.list() ? List.of(value) : value;
    }

    private static Object scalar(SimpleType type) {
        String text = ObjectHelper.isNotEmpty(type.enumeration()) ? type.enumeration() : null;
        return switch (type.kind()) {
            case BOOLEAN -> text != null ? Boolean.valueOf(text.trim()) : Boolean.TRUE;
            case INTEGER -> text != null ? number(text) : switch (type.builtin()) {
                case "negativeInteger" -> -1L;
                case "nonPositiveInteger" -> 0L;
                default -> 1L;
            };
            case DECIMAL -> text != null ? number(text) : new BigDecimal("9.99");
            case FLOAT -> text != null ? number(text) : 1.5d;
            default -> text != null ? text : string(type.builtin());
        };
    }

    private static Object number(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return text;
        }
    }

    private static String string(String builtin) {
        if (builtin == null) {
            return "string";
        }
        return switch (builtin) {
            case "dateTime", "dateTimeStamp" -> "2026-01-31T12:00:00Z";
            case "date" -> "2026-01-31";
            case "time" -> "12:00:00";
            case "duration" -> "P1D";
            case "gYear" -> "2026";
            case "gYearMonth" -> "2026-01";
            case "gMonth" -> "--01";
            case "gMonthDay" -> "--01-31";
            case "gDay" -> "---31";
            case "base64Binary" -> "c2FtcGxl";
            case "hexBinary" -> "73616D706C65";
            case "anyURI" -> "http://example.com";
            case "language" -> "en";
            case "ID", "IDREF", "NCName", "Name", "NMTOKEN", "ENTITY" -> "id1";
            default -> "string";
        };
    }
}
