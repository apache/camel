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
package org.apache.camel.util.xml.pretty;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public final class XmlPrettyPrinter {

    private XmlPrettyPrinter() {
    }

    @FunctionalInterface
    public interface ColorPrintElement {

        int DECLARATION = 1;
        int ELEMENT = 2;
        int ATTRIBUTE_KEY = 3;
        int ATTRIBUTE_VALUE = 4;
        int ATTRIBUTE_EQUAL = 5;
        int ATTRIBUTE_QUOTE = 6;
        int VALUE = 7;

        String color(int type, String value);
    }

    /**
     * Pad the string with leading spaces
     *
     * @param level  level
     * @param blanks number of blanks per level
     */
    private static String padString(int level, int blanks) {
        return " ".repeat(level * blanks);
    }

    public static String colorPrint(String xml, int blanks, boolean declaration, ColorPrintElement color)
            throws Exception {
        return doParse(xml, blanks, declaration, color);
    }

    /**
     * Pretty print the XML (does not use DOM or any kind of parser)
     *
     * @param  xml    the XML
     * @param  blanks number of blanks to use as indent
     * @return        the XML in pretty, without XML declaration
     */
    public static String pettyPrint(String xml, int blanks) throws Exception {
        return doParse(xml, blanks, false, new NoopColor());
    }

    /**
     * Pretty print the XML (does not use DOM or any kind of parser)
     *
     * @param  xml         the XML
     * @param  blanks      number of blanks to use as indent
     * @param  declaration whether to include XML declaration
     * @return             the XML in pretty
     */
    public static String pettyPrint(String xml, int blanks, boolean declaration) throws Exception {
        return doParse(xml, blanks, declaration, new NoopColor());
    }

    private static class NoopColor implements ColorPrintElement {

        @Override
        public String color(int type, String value) {
            return value;
        }
    }

    private static String doParse(String xml, int blanks, boolean declaration, ColorPrintElement color) throws Exception {
        SAXParser parser;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/namespaces", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parser = factory.newSAXParser();

        final StringBuilder sb = new StringBuilder();
        final DefaultHandler handler = new DefaultHandler() {
            int indent;

            @Override
            public void declaration(String version, String encoding, String standalone) throws SAXException {
                if (declaration) {
                    StringBuilder lb = new StringBuilder();
                    lb.append("<?xml");
                    if (version != null) {
                        lb.append(" version=\"").append(version).append("\"");
                    }
                    if (encoding != null) {
                        lb.append(" encoding=\"").append(encoding).append("\"");
                    }
                    if (standalone != null) {
                        lb.append(" standalone=\"").append(encoding).append("\"");
                    }
                    lb.append("?>");

                    String value = color.color(ColorPrintElement.DECLARATION, lb.toString());
                    sb.append(value);
                    sb.append("\n");
                }
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                sb.append(XmlPrettyPrinter.padString(indent, blanks));

                StringBuilder lb = new StringBuilder();
                lb.append("<");
                lb.append(qName);
                String value = color.color(ColorPrintElement.ELEMENT, lb.toString());
                sb.append(value);

                lb.setLength(0);
                for (int i = 0; i < attributes.getLength(); i++) {
                    String k = color.color(ColorPrintElement.ATTRIBUTE_KEY, attributes.getQName(i));
                    String v = color.color(ColorPrintElement.ATTRIBUTE_VALUE, attributes.getValue(i));
                    String eq = color.color(ColorPrintElement.ATTRIBUTE_EQUAL, "=");
                    String quote = color.color(ColorPrintElement.ATTRIBUTE_QUOTE, "\"");
                    lb.append(" ").append(k).append(eq).append(quote).append(v).append(quote);
                }
                sb.append(lb);

                value = color.color(ColorPrintElement.ELEMENT, ">");
                sb.append(value);
                sb.append("\n");

                indent++;
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                --indent;

                StringBuilder lb = new StringBuilder();
                lb.append("</");
                lb.append(qName);
                lb.append(">");

                sb.append(XmlPrettyPrinter.padString(indent, blanks));
                String value = color.color(ColorPrintElement.ELEMENT, lb.toString());
                sb.append(value);
                if (indent > 0) {
                    sb.append("\n");
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                char[] chars = new char[length];
                System.arraycopy(ch, start, chars, 0, length);
                String value = color.color(ColorPrintElement.VALUE, new String(chars));

                sb.append(XmlPrettyPrinter.padString(indent, blanks));
                sb.append(value);
                sb.append("\n");
            }
        };

        parser.parse(new ByteArrayInputStream(xml.getBytes()), handler);
        return sb.toString();
    }

}
