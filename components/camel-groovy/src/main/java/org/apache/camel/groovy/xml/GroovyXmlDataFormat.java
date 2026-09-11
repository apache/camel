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
package org.apache.camel.groovy.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import groovy.util.Node;
import groovy.xml.FactorySupport;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import groovy.xml.XmlUtil;
import groovy.xml.slurpersupport.GPathResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;

@Dataformat("groovyXml")
public class GroovyXmlDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final int START_TAG = 1;
    private static final int VALUE = 2;
    private static final int END_TAG = 3;

    // replaces the XmlParser of a thread as content handler of its SAX parser after a parse, so the parsed document
    // is not retained by the parser
    private static final DefaultHandler DETACHED = new DefaultHandler();

    private boolean attributeMapping = true;

    /**
     * Configured like the factory of {@code new XmlParser()} (secure processing, DOCTYPE disallowed, namespace aware,
     * not validating), created once as its lookup and feature setup dominate the cost of parsing small documents.
     */
    private volatile SAXParserFactory saxParserFactory;

    /**
     * A SAX parser is not thread safe but can parse sequentially: a parser is borrowed for one unmarshal and returned,
     * so at most one parser per concurrent unmarshal exists, and all of them are released when the data format stops.
     */
    private final ConcurrentLinkedQueue<SAXParser> parsers = new ConcurrentLinkedQueue<>();

    public boolean isAttributeMapping() {
        return attributeMapping;
    }

    public void setAttributeMapping(boolean attributeMapping) {
        this.attributeMapping = attributeMapping;
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (graph instanceof GPathResult gp) {
            XmlUtil.serialize(gp, stream);
        } else if (graph instanceof Node n) {
            serialize(exchange, n, stream);
        } else if (graph instanceof Map map) {
            serialize(exchange, map, stream);
        } else {
            // optional jackson 2.x or 3.x support
            String type = graph.getClass().getName();
            if (type.startsWith("com.fasterxml.jackson.databind") || type.startsWith("tools.jackson.databind")) {
                var map = exchange.getContext().getTypeConverter().convertTo(Map.class, exchange, graph);
                serialize(exchange, map, stream);
            } else {
                byte[] arr = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, graph);
                stream.write(arr);
            }
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        SAXParser parser = parsers.poll();
        if (parser == null) {
            parser = createSaxParser();
        }
        try {
            XmlParser xmlParser = new XmlParser(parser);
            // XmlParser(SAXParser) does not set this, the no-arg constructor does: keep namespace prefixes on the QNames
            xmlParser.setNamespaceAware(true);
            return xmlParser.parse(stream);
        } finally {
            parser.getXMLReader().setContentHandler(DETACHED);
            parsers.offer(parser);
        }
    }

    @Override
    public String getDataFormatName() {
        return "groovyXml";
    }

    @Override
    protected void doStart() throws Exception {
        getSaxParserFactory();
    }

    @Override
    protected void doStop() throws Exception {
        parsers.clear();
    }

    private SAXParserFactory getSaxParserFactory() throws ParserConfigurationException {
        SAXParserFactory factory = saxParserFactory;
        if (factory == null) {
            synchronized (this) {
                factory = saxParserFactory;
                if (factory == null) {
                    // the same setup as the no-arg XmlParser constructor: secure processing and DOCTYPE disallowed
                    factory = FactorySupport.createSaxParserFactory();
                    factory.setNamespaceAware(true);
                    factory.setValidating(false);
                    XmlUtil.setFeatureQuietly(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    XmlUtil.setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
                    saxParserFactory = factory;
                }
            }
        }
        return factory;
    }

    private SAXParser createSaxParser() {
        try {
            SAXParserFactory factory = getSaxParserFactory();
            // a factory is not guaranteed to be thread safe, and a thread only creates one parser
            synchronized (factory) {
                return factory.newSAXParser();
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeCamelException(e);
        }
    }

    private void serialize(Exchange exchange, Node node, OutputStream os) {
        // XmlNodePrinter writes no XML declaration, so the document must be UTF-8 to be self-describing
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        XmlNodePrinter nodePrinter = new XmlNodePrinter(pw);
        nodePrinter.setPreserveWhitespace(true);
        nodePrinter.print(node);
    }

    private void printLines(List<Line> lines, Writer w) throws IOException {
        // add missing root end tag
        lines.add(new Line(lines.get(0).key, null, END_TAG, null));
        int level = 0;
        for (Line line : lines) {
            int kind = line.kind;
            if (kind == START_TAG) {
                w.write(StringHelper.padString(level));
                w.write('<');
                w.write(line.key);
                if (line.attrs != null) {
                    StringJoiner sj = new StringJoiner(" ");
                    for (var a : line.attrs.entrySet()) {
                        sj.add(a.getKey() + "=\"" + a.getValue() + "\"");
                    }
                    if (sj.length() > 0) {
                        w.write(' ');
                        w.write(sj.toString());
                    }
                }
                w.write(">\n");
                level++;
            } else if (kind == END_TAG) {
                level--;
                w.write(StringHelper.padString(level));
                w.write("</");
                w.write(line.key);
                w.write(">\n");
            } else {
                w.write(StringHelper.padString(level));
                w.write('<');
                w.write(line.key);
                w.write('>');
                w.write(line.value);
                w.write("</");
                w.write(line.key);
                w.write(">\n");
            }
        }
    }

    private void serialize(Exchange exchange, Map<String, Object> map, OutputStream os) throws Exception {
        List<Line> lines = new ArrayList<>();
        doSerialize(exchange.getContext(), map, lines);
        // render in memory and write once: a Writer over the stream costs 16 KB of buffers per call, which is more
        // than a typical document, and the Marshal EIP already writes into a memory stream
        StringWriter w = new StringWriter(lines.size() * 32);
        printLines(lines, w);
        os.write(w.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String asString(CamelContext context, Object value) {
        if (value instanceof String s) {
            return s;
        }
        // final JDK types no user converter can target: the type converter would return toString() for them
        if (value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Boolean
                || value instanceof Short || value instanceof Byte || value instanceof Float
                || value instanceof BigDecimal || value instanceof BigInteger) {
            return value.toString();
        }
        return context.getTypeConverter().convertTo(String.class, value);
    }

    private void doSerialize(CamelContext context, Map<String, Object> map, List<Line> lines) {

        // attributes
        Map<String, String> attrs = new LinkedHashMap<>();
        if (attributeMapping) {
            for (String key : map.keySet()) {
                if (key.startsWith("_") || key.startsWith("@")) {
                    String val = asString(context, map.get(key));
                    if (val != null) {
                        val = val.trim();
                        if (!val.isBlank()) {
                            attrs.put(key.substring(1), val);
                        }
                    }
                }
            }
        }

        boolean root = false;
        for (var e : map.entrySet()) {
            String key = e.getKey();

            // attribute mappings are disabled
            if (key.startsWith("_") || key.startsWith("@")) {
                continue;
            }

            if (!attrs.isEmpty() && !lines.isEmpty()) {
                int pos = lines.size() - 1;
                Line prev = lines.get(pos);
                lines.remove(pos);
                Line updated = new Line(prev.key, prev.value, prev.kind, new LinkedHashMap<>(attrs));
                lines.add(updated);
                attrs.clear();
            }

            // root tag
            if (lines.isEmpty()) {
                root = true;
                lines.add(new Line(key, null, START_TAG, null));
            }

            if (e.getValue() != null) {
                // nested list or map
                if (e.getValue() instanceof Map cm) {
                    doSerialize(context, cm, lines);
                } else if (e.getValue() instanceof List cl) {
                    doSerialize(context, cl, key, attrs, lines, root);
                } else {
                    String val = asString(context, e.getValue());
                    if (val != null) {
                        val = val.trim();
                        if (!val.isBlank()) {
                            lines.add(new Line(key, val, VALUE, null));
                        }
                    }
                }
            }
        }
    }

    private void doSerialize(
            CamelContext context, List list, String key, Map<String, String> attrs, List<Line> lines, boolean root) {
        for (var e : list) {
            if (!root) {
                lines.add(new Line(key, null, START_TAG, attrs));
            }
            if (e instanceof Map map) {
                doSerialize(context, map, lines);
            }
            if (!root) {
                lines.add(new Line(key, null, END_TAG, null));
            }
        }
    }

    record Line(String key, String value, int kind, Map<String, String> attrs) {
    }

}
