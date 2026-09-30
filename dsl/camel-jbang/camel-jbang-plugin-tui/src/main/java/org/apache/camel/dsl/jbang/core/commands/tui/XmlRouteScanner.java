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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The routes of a Camel XML source for the jump links of the Source tab (CAMEL-25196): each route's from endpoint and
 * the endpoints it sends to, with their lines. The XML is read as a stream, without DTDs or external entities, and
 * nothing of the project is loaded.
 */
final class XmlRouteScanner {

    /** The steps whose uri attribute is an endpoint a route sends to. */
    private static final Set<String> SENDING = Set.of("to", "toD", "wireTap", "enrich", "pollEnrich");

    private XmlRouteScanner() {
    }

    /** The routes of an XML source; empty when it has none or is not well-formed. */
    static List<ScannedRoute> scan(String content) {
        List<ScannedRoute> answer = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader xml = null;
        try {
            xml = factory.createXMLStreamReader(new StringReader(content));
            Deque<String> open = new ArrayDeque<>();
            String id = null;
            String from = null;
            int fromLine = 0;
            List<ScannedRoute.To> tos = null;
            // an enrich or pollEnrich whose endpoint is a <constant> below it, and its line
            int enrichLine = -1;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = xml.getLocalName();
                    int line = Math.max(0, xml.getLocation().getLineNumber() - 1);
                    String parent = open.peek();
                    open.push(name);
                    if ("route".equals(name)) {
                        id = xml.getAttributeValue(null, "id");
                        from = null;
                        tos = new ArrayList<>();
                        enrichLine = -1;
                    } else if (tos == null) {
                        continue;
                    } else if ("from".equals(name) && from == null) {
                        from = xml.getAttributeValue(null, "uri");
                        fromLine = line;
                    } else if (SENDING.contains(name)) {
                        String uri = xml.getAttributeValue(null, "uri");
                        if (uri != null) {
                            tos.add(new ScannedRoute.To(uri, line));
                        } else if ("enrich".equals(name) || "pollEnrich".equals(name)) {
                            enrichLine = line;
                        }
                    } else if (("case".equals(name) || "otherwise".equals(name)) && "switch".equals(parent)) {
                        // the destinations of a switch
                        String uri = xml.getAttributeValue(null, "uri");
                        if (uri != null) {
                            tos.add(new ScannedRoute.To(uri, line));
                        }
                    } else if ("constant".equals(name) && enrichLine >= 0
                            && ("enrich".equals(parent) || "pollEnrich".equals(parent))) {
                        String uri = xml.getElementText().strip();
                        open.pop();
                        if (!uri.isEmpty()) {
                            tos.add(new ScannedRoute.To(uri, enrichLine));
                        }
                        enrichLine = -1;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = open.isEmpty() ? null : open.pop();
                    if ("route".equals(name) && tos != null) {
                        if (from != null && !from.isBlank()) {
                            answer.add(new ScannedRoute(id, from.strip(), fromLine, tos));
                        }
                        tos = null;
                    } else if ("enrich".equals(name) || "pollEnrich".equals(name)) {
                        enrichLine = -1;
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            // a file being edited may not be well-formed: what was read so far is kept
        } finally {
            if (xml != null) {
                try {
                    xml.close();
                } catch (XMLStreamException e) {
                    // ignore
                }
            }
        }
        return answer;
    }
}
