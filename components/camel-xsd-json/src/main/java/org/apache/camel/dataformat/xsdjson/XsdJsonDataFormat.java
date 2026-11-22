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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.dataformat.xsdjson.XsdModel.Element;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

/**
 * Unmarshal XML to JSON and marshal JSON to XML, driven by an XML Schema (an XSD, or the types of a WSDL), which gives
 * the types, arrays, order and namespaces.
 */
@Dataformat("xsdJson")
@Metadata(firstVersion = "4.24.0", title = "XSD JSON")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP },
                                 format = "json",
                                 description = "The JSON to write as XML: JSON text (a String, bytes, a stream or a file), or a"
                                               + " Map of the root element"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "xml",
                                  description = "The XML in the stream cache, or a byte array when stream caching is off"),
           unmarshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "xml"),
           unmarshalOut = @BodyType(value = Carrier.TEXT, format = "json",
                                    description = "The JSON text, typed and ordered by the schema"))
public class XsdJsonDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    /**
     * The global element of the schema to use for this message, as {namespace}name or as a unique local name. It
     * overrides the element option.
     */
    public static final String ELEMENT = "CamelXsdJsonElement";

    private CamelContext camelContext;
    @Metadata(required = true,
              description = "The XML Schema to use: an .xsd file, or a .wsdl file whose types (every xs:schema in wsdl:types)"
                            + " are read. A resource such as classpath:orders.xsd, file:schemas/orders.wsdl or an https URL."
                            + " Imports and includes are resolved relative to it, with the same scheme only.")
    private String schema;
    @Metadata(description = "The root element: a global element of the schema, as {namespace}name or as a local name that is"
                            + " unique in the schema. Needed to marshal (JSON to XML) unless includeRoot is enabled. On unmarshal"
                            + " the XML document names its root element, and this option only restricts it. The header"
                            + " CamelXsdJsonElement overrides it per message.")
    private String element;
    @Metadata(defaultValue = "false",
              description = "Whether the JSON holds the root element as its single field, such as {getOrder: {...}}, instead of"
                            + " the content of the root element.")
    private boolean includeRoot;
    @Metadata(description = "A prefix for the JSON fields of XML attributes, such as _. By default attributes and child elements"
                            + " share the JSON object without a prefix.")
    private String attributePrefix;
    @Metadata(defaultValue = "value",
              description = "The JSON field of the text of an element with simple content and attributes, such as"
                            + " {currency: EUR, value: 9.95}.")
    private String textName = "value";
    @Metadata(defaultValue = "false",
              description = "Whether to skip XML elements and attributes, or JSON fields, that the schema does not declare,"
                            + " instead of failing.")
    private boolean ignoreUnknown;
    @Metadata(defaultValue = "false",
              description = "Whether to indent the output, the JSON on unmarshal and the XML on marshal.")
    private boolean prettyPrint;

    private XsdModel model;
    private XsdJsonSamples.DocumentReader documentReader;
    private Element defaultElement;
    private XMLInputFactory inputFactory;
    private XMLOutputFactory outputFactory;

    @Override
    public String getDataFormatName() {
        return "xsdJson";
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext");
        if (ObjectHelper.isEmpty(schema)) {
            throw new IllegalArgumentException("The schema option of the xsdJson data format must be set");
        }
        if (ObjectHelper.isEmpty(textName)) {
            textName = "value";
        }
        model = new XsdModelBuilder(
                new XsdSchemaLoader(camelContext, schema, documentReader).load(), attributePrefix, textName).build();
        if (ObjectHelper.isNotEmpty(element)) {
            defaultElement = model.findElement(element);
            model.checkConflicts(defaultElement);
        }
        inputFactory = XmlToJson.newInputFactory();
        outputFactory = XMLOutputFactory.newFactory();
    }

    @Override
    protected void doStop() throws Exception {
        // the model is kept, a restart reads the schema again
    }

    /**
     * Reads the schema and the documents it imports, instead of the CamelContext (used by {@link XsdJsonSamples}).
     */
    void setDocumentReader(XsdJsonSamples.DocumentReader documentReader) {
        this.documentReader = documentReader;
    }

    /**
     * The model of the schema, once the data format is started.
     */
    XsdModel getModel() {
        return model;
    }

    /**
     * The element for this message: the header, else the element option, else null.
     */
    private Element element(Exchange exchange) {
        String name = exchange.getMessage().getHeader(ELEMENT, String.class);
        if (ObjectHelper.isNotEmpty(name)) {
            return model.findElement(name);
        }
        return defaultElement;
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        XMLStreamReader reader;
        if (body instanceof String s) {
            reader = inputFactory.createXMLStreamReader(new StringReader(s));
        } else if (body instanceof Reader r) {
            reader = inputFactory.createXMLStreamReader(r);
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
            reader = charset != null
                    ? inputFactory.createXMLStreamReader(is, charset) : inputFactory.createXMLStreamReader(is);
        }
        try {
            Map.Entry<Element, Object> root
                    = new XmlToJson(model, ignoreUnknown, textName).read(reader, element(exchange));
            Object value = root.getValue() == XmlToJson.NULL ? null : root.getValue();
            Object json = includeRoot ? singletonMap(root.getKey().jsonName(), value) : value;
            return JsonText.write(json, prettyPrint);
        } finally {
            reader.close();
        }
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        Object json = toJson(exchange, graph);
        Element root = element(exchange);
        if (includeRoot) {
            if (!(json instanceof Map<?, ?> map) || map.size() != 1) {
                throw new IllegalArgumentException(
                        "With includeRoot the JSON must be an object with one field, the root element, such as {\"getOrder\": {...}}");
            }
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            String name = String.valueOf(entry.getKey());
            if (root == null) {
                root = model.findElement(name);
            } else if (!root.jsonName().equals(name)) {
                throw new IllegalArgumentException(
                        "The JSON has the root field " + name + " but the element " + root.name + " is expected");
            }
            json = entry.getValue();
        } else if (root == null) {
            throw new IllegalArgumentException(
                    "The element option or the " + ELEMENT + " header must name the root element to marshal JSON to XML"
                                               + " (or enable includeRoot). The global elements are: " + model.elementNames());
        }
        model.checkConflicts(root);
        String charset = ExchangeHelper.getCharsetName(exchange);
        XMLStreamWriter writer = outputFactory.createXMLStreamWriter(stream, charset);
        try {
            writer.writeStartDocument(charset, "1.0");
            new JsonToXml(model, ignoreUnknown, textName, prettyPrint, writer).write(root, json);
            writer.writeEndDocument();
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private static Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Object toJson(Exchange exchange, Object graph) throws Exception {
        if (graph == null || graph instanceof Map || graph instanceof Collection) {
            return graph;
        }
        String text = graph instanceof String s
                ? s : exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, graph);
        try {
            return Jsoner.deserialize(text);
        } catch (DeserializationException e) {
            throw new IllegalArgumentException("The message body is not JSON: " + e.getMessage(), e);
        }
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public boolean isIncludeRoot() {
        return includeRoot;
    }

    public void setIncludeRoot(boolean includeRoot) {
        this.includeRoot = includeRoot;
    }

    public String getAttributePrefix() {
        return attributePrefix;
    }

    public void setAttributePrefix(String attributePrefix) {
        this.attributePrefix = attributePrefix;
    }

    public String getTextName() {
        return textName;
    }

    public void setTextName(String textName) {
        this.textName = textName;
    }

    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    public void setIgnoreUnknown(boolean ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
