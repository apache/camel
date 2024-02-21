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
package org.apache.camel.dsl.jbang.core.common;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;
import org.fusesource.jansi.Ansi;

public final class XmlHelper {

    private XmlHelper() {
    }

    /**
     * Prints the XML in pretty mode (no color).
     */
    public static String prettyPrint(String xml, int spaces) throws Exception {
        return XmlPrettyPrinter.pettyPrint(xml, spaces);
    }

    /**
     * Prints the XML with ANSi color (similar to jq)
     */
    public static String colorPrint(String xml, int spaces, boolean pretty) throws Exception {
        return XmlPrettyPrinter.colorPrint(xml, spaces, pretty, new XmlPrettyPrinter.ColorPrintElement() {
            @Override
            public String color(int type, String value) {
                String s = value != null ? value : "null";
                if (type == XmlPrettyPrinter.ColorPrintElement.DECLARATION) {
                    s = Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ELEMENT) {
                    s = Ansi.ansi().fgBright(Ansi.Color.BLUE).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.VALUE) {
                    s = Ansi.ansi().fgDefault().a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_KEY) {
                    s = Ansi.ansi().fg(Ansi.Color.MAGENTA).a(s).reset().toString();
                } else if (type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_VALUE
                        || type == XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_QUOTE) {
                    s = Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
                }
                return s;
            }
        });
    }

    public static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        try {
            // Set secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (ParserConfigurationException e) {
        }
        try {
            // Disable the external-general-entities by default
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
        }
        try {
            // Disable the external-parameter-entities by default
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
        }
        // setup the SecurityManager by default if it's apache xerces
        try {
            Class<?> smClass = ObjectHelper.loadClass("org.apache.xerces.util.SecurityManager");
            if (smClass != null) {
                Object sm = smClass.getDeclaredConstructor().newInstance();
                // Here we just use the default setting of the SeurityManager
                factory.setAttribute("http://apache.org/xml/properties/security-manager", sm);
            }
        } catch (Exception e) {
        }
        return factory;
    }

}
