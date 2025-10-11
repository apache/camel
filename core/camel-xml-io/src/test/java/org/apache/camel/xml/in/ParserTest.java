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
package org.apache.camel.xml.in;

import java.io.IOException;
import java.io.StringReader;

import org.apache.camel.xml.io.MXParser;
import org.apache.camel.xml.io.XmlPullParserException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Run manually to check how the MX parser works")
public class ParserTest {

    private static final Logger LOG = LoggerFactory.getLogger(ParserTest.class);

    @Test
    public void justParse() throws XmlPullParserException, IOException {
        final MXParser xpp = getMxParser();
        int eventType = xpp.getEventType();
        while (eventType != MXParser.END_DOCUMENT) {
            xpp.getStartLineNumber();
            xpp.getLineNumber();
            xpp.getColumnNumber();
            xpp.getDepth();
            xpp.getPositionDescription();
            xpp.getNamespace("prefix"); // to check - implementation is weird
            int nsc = xpp.getNamespaceCount(xpp.getDepth());
            if (nsc > 0) {
                xpp.getNamespacePrefix(0/*pos*/);
                xpp.getNamespaceUri(0/*pos*/);
            }
            xpp.getText(); // check handling for non START/END_TAG
            xpp.getTextCharacters(new int[2]); // check handling for non START/END_TAG
            switch (eventType) {
                case MXParser.START_DOCUMENT -> {
                    LOG.debug("START_DOCUMENT");
                }
                case MXParser.START_TAG -> {
                    xpp.getText(); // never uses org.apache.camel.xml.io.MXParser#pc
                    xpp.getTextCharacters(new int[2]);
                    xpp.isEmptyElementTag();
                    LOG.debug("START_TAG" + (xpp.isEmptyElementTag() ? " (empty tag)" : ""));
                    LOG.debug(" - name: " + xpp.getName());
                    LOG.debug(" - ns: " + xpp.getNamespace());
                    LOG.debug(" - prefix: " + xpp.getPrefix());
                    int ac = xpp.getAttributeCount();
                    if (ac > 0) {
                        LOG.debug(" - attributes:");
                        for (int i = 0; i < ac; i++) {
                            LOG.debug("    - " + xpp.getAttributeName(i)
                                      + (xpp.getAttributePrefix(i) == null
                                              ? "" : " (prefix: " + xpp.getAttributePrefix(i) + ")")
                                      + ": " + xpp.getAttributeValue(i)
                                      + ", ns: " + xpp.getAttributeNamespace(i));
                        }
                    }
                    if ("e1".equals(xpp.getName())) {
                        assertEquals("value-1", xpp.getAttributeValue("", "a"));
                        assertEquals("value-1", xpp.getAttributeValue(null, "a"));
                        assertEquals("value-2", xpp.getAttributeValue("uri:b", "a"));
                        // check with non-interned String
                        assertEquals("value-2", xpp.getAttributeValue("uri:b", "a"));
                    }
                }
                case MXParser.END_TAG -> {
                    xpp.getText(); // never uses org.apache.camel.xml.io.MXParser#pc
                    xpp.getTextCharacters(new int[2]);
                    LOG.debug("END_TAG");
                    LOG.debug(" - name: " + xpp.getName());
                    LOG.debug(" - ns: " + xpp.getNamespace());
                    LOG.debug(" - prefix: " + xpp.getPrefix());
                }
                case MXParser.TEXT -> {
                    LOG.debug("TEXT");
                    LOG.debug(" - name: " + xpp.getName());
                    LOG.debug(" - text: '" + xpp.getText() + (xpp.isWhitespace() ? "' (whitespace)" : "'"));
                }
                case MXParser.CDSECT -> {
                    xpp.isWhitespace();
                }
                case MXParser.ENTITY_REF -> {
                    xpp.getName();
                    xpp.getText(); // always returns text - even if null
                    LOG.debug("ENTITY_REF");
                    LOG.debug(" - name: " + xpp.getName());
                    LOG.debug(" - text: " + xpp.getText());
                }
                case MXParser.IGNORABLE_WHITESPACE -> {
                    xpp.isWhitespace(); // always true
                }
                case MXParser.PROCESSING_INSTRUCTION -> {
                }
                case MXParser.COMMENT -> {
                }
                case MXParser.DOCDECL -> {
                }
                default -> {
                }
            }
            eventType = xpp.next();
        }
    }

    private static MXParser getMxParser() throws IOException, XmlPullParserException {
        String xml = """
                <?xml version='1.0'?>
                <c:root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:c="uri:camel" xmlns="uri:camel-beans">
                    <c:e1 a="value-1" b:a="value-2" xmlns:c="uri:cxf" xmlns:b="uri:b" />
                    <watch-out-for-entities>&nbsp;</watch-out-for-entities>
                </c:root>
                """;
        BaseParser p = new BaseParser(new StringReader(xml));
        MXParser xpp = p.parser;
        xpp.defineEntityReplacementText("nbsp", "—");
        return xpp;
    }

    @Test
    public void parseTheEdge() throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("<?xml version='1.0'?>\n");
        sb.append("<!--\n");
        for (int i = sb.toString().length() + 4 - 2; i < 8 * 1024; i += 4) {
            sb.append("abc\n");
        }
        sb.append("-->\n");
        sb.append("<root><child a=\"b\" /></root>\n");
        BaseParser p = new BaseParser(new StringReader(sb.toString()));
        MXParser xpp = p.parser;
        int eventType = xpp.getEventType();
        while (eventType != MXParser.END_DOCUMENT) {
            eventType = xpp.next();
            if (eventType == MXParser.START_TAG) {
                LOG.debug(xpp.getName());
            }
        }
    }

}
