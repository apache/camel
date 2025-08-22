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
import java.util.List;
import java.util.Map;

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

    int START_TAG = 1;
    int VALUE = 2;
    int END_TAG = 3;

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
        lines.add(new Line(lines.get(0).key, null, END_TAG));
        int level = 0;
        for (Line line : lines) {
            int kind = line.kind;
            if (kind == START_TAG) {
                String pad = StringHelper.padString(level);
                os.write(pad.getBytes());
                os.write("<".getBytes());
                os.write(line.key.getBytes());
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
        for (var e : map.entrySet()) {
            String key = e.getKey();

            // attributes are not yet supported
            if (key.startsWith("_")) {
                continue;
            }

            // root tag
            if (lines.isEmpty()) {
                lines.add(new Line(key, null, START_TAG));
            }

            if (e.getValue() != null) {
                // nested list or map
                if (e.getValue() instanceof Map cm) {
                    doSerialize(context, cm, lines);
                } else if (e.getValue() instanceof List cl) {
                    doSerialize(context, cl, key, lines);
                } else {
                    String val = context.getTypeConverter().convertTo(String.class, e.getValue());
                    if (val != null) {
                        val = val.trim();
                        if (!val.isBlank()) {
                            lines.add(new Line(key, val, VALUE));
                        }
                    }
                }
            }
        }
    }

    private void doSerialize(CamelContext context, List list, String key, List<Line> lines) {
        // list of map or value entries
        for (var e : list) {
            lines.add(new Line(key, null, START_TAG));
            if (e instanceof Map map) {
                doSerialize(context, map, lines);
            }
            lines.add(new Line(key, null, END_TAG));
        }
    }

    record Line(String key, String value, int kind) {
    }

}
