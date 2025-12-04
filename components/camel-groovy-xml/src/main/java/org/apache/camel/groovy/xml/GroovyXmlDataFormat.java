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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import groovy.util.Node;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import groovy.xml.XmlUtil;
import groovy.xml.slurpersupport.GPathResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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

    private boolean attributeMapping = true;

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
            serialize(n, stream);
        } else if (graph instanceof Map map) {
            serialize(exchange, map, stream);
        } else {
            // optional jackson support
            if (graph.getClass().getName().startsWith("com.fasterxml.jackson.databind")) {
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
        XmlParser parser = new XmlParser();
        return parser.parse(stream);
    }

    @Override
    public String getDataFormatName() {
        return "groovyXml";
    }

    private void serialize(Node node, OutputStream os) {
        PrintWriter pw = new PrintWriter(os);
        XmlNodePrinter nodePrinter = new XmlNodePrinter(pw);
        nodePrinter.setPreserveWhitespace(true);
        nodePrinter.print(node);
    }

    private void printLines(List<Line> lines, OutputStream os) throws Exception {
        // add missing root end tag
        lines.add(new Line(lines.get(0).key, null, END_TAG, null));
        int level = 0;
        for (Line line : lines) {
            int kind = line.kind;
            if (kind == START_TAG) {
                String pad = StringHelper.padString(level);
                os.write(pad.getBytes());
                os.write("<".getBytes());
                os.write(line.key.getBytes());
                if (line.attrs != null) {
                    StringJoiner sj = new StringJoiner(" ");
                    for (var a : line.attrs.entrySet()) {
                        sj.add(a.getKey() + "=\"" + a.getValue() + "\"");
                    }
                    if (sj.length() > 0) {
                        os.write(" ".getBytes());
                        os.write(sj.toString().getBytes());
                    }
                }
                os.write(">\n".getBytes());
                level++;
            } else if (kind == END_TAG) {
                level--;
                String pad = StringHelper.padString(level);
                os.write(pad.getBytes());
                os.write("</".getBytes());
                os.write(line.key.getBytes());
                os.write(">\n".getBytes());
            } else {
                String pad = StringHelper.padString(level);
                os.write(pad.getBytes());
                os.write("<".getBytes());
                os.write(line.key.getBytes());
                os.write(">".getBytes());
                os.write(line.value.getBytes());
                os.write("</".getBytes());
                os.write(line.key.getBytes());
                os.write(">\n".getBytes());
            }
        }
    }

    private void serialize(Exchange exchange, Map<String, Object> map, OutputStream os) throws Exception {
        List<Line> lines = new ArrayList<>();
        doSerialize(exchange.getContext(), map, lines);
        printLines(lines, os);
    }

    private void doSerialize(CamelContext context, Map<String, Object> map, List<Line> lines) {

        // attributes
        Map<String, String> attrs = new LinkedHashMap<>();
        if (attributeMapping) {
            for (String key : map.keySet()) {
                if (key.startsWith("_") || key.startsWith("@")) {
                    String val = context.getTypeConverter().convertTo(String.class, map.get(key));
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
                    String val = context.getTypeConverter().convertTo(String.class, e.getValue());
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

    record Line(String key, String value, int kind, Map<String, String> attrs) {}
}
