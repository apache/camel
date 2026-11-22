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
package org.apache.camel.util.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.crypto.Data;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.TransformException;
import javax.xml.crypto.dsig.TransformService;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Helpers that work on XML held as a String: check it is well-formed, strip the declaration, comments or namespaces,
 * compact it, or canonicalize it.
 * <p>
 * The XML is often a message body from an untrusted sender, so every method that parses uses the parser of the JDK with
 * DOCTYPE declarations disallowed: no DTD, no entity of any kind (internal or external), and no access to files or the
 * network. XML with a DOCTYPE is rejected.
 */
public final class XmlStringHelper {

    private XmlStringHelper() {
    }

    /**
     * Whether the value is well-formed XML. A document with a DOCTYPE declaration is not accepted.
     *
     * @param  xml the XML
     * @return     <code>true</code> if the value is well-formed XML without a DOCTYPE, <code>false</code> otherwise
     *             (also for <code>null</code> or a blank value)
     */
    public static boolean isWellFormed(String xml) {
        if (xml == null || xml.isBlank()) {
            return false;
        }
        try {
            parse(xml, new DefaultHandler());
            return true;
        } catch (SAXException | IOException e) {
            return false;
        }
    }

    /**
     * Removes the XML declaration ({@code <?xml version="1.0"?>}) and the whitespace after it. This is a text
     * operation: the XML is not parsed, and the rest of the value is returned unchanged.
     *
     * @param  xml the XML
     * @return     the XML without the declaration, or the value as-is if it has none
     */
    public static String stripDeclaration(String xml) {
        if (xml == null) {
            return null;
        }
        int pos = 0;
        if (xml.startsWith("﻿")) {
            pos++;
        }
        while (pos < xml.length() && isXmlSpace(xml.charAt(pos))) {
            pos++;
        }
        // <?xml-stylesheet ...?> is a processing instruction, not the declaration
        int after = pos + 5;
        if (!xml.startsWith("<?xml", pos) || after >= xml.length() || !isXmlSpace(xml.charAt(after))) {
            return xml;
        }
        int end = xml.indexOf("?>", after);
        if (end == -1) {
            return xml;
        }
        pos = end + 2;
        while (pos < xml.length() && isXmlSpace(xml.charAt(pos))) {
            pos++;
        }
        return xml.substring(pos);
    }

    /**
     * Removes all the comments.
     *
     * @param  xml          the XML
     * @return              the XML without comments
     * @throws SAXException if the value is not well-formed XML, or has a DOCTYPE declaration
     * @throws IOException  if the value cannot be read
     */
    public static String stripComments(String xml) throws SAXException, IOException {
        return rewrite(xml, false, true, false);
    }

    /**
     * Removes the namespace declarations ({@code xmlns} and {@code xmlns:prefix}) and the namespace prefixes of
     * elements and attributes, so {@code <ns1:Order xmlns:ns1="urn:a" ns1:id="1">} becomes {@code <Order id="1">}.
     * Attributes in the {@code xml:} namespace, such as {@code xml:lang}, are kept as they are. When two attributes of
     * an element have the same local name, the first one is kept. Prefixes inside attribute values or text, such as in
     * {@code xsi:type="ns1:Item"}, are not changed.
     *
     * @param  xml          the XML
     * @return              the XML without namespaces
     * @throws SAXException if the value is not well-formed XML, or has a DOCTYPE declaration
     * @throws IOException  if the value cannot be read
     */
    public static String stripNamespaces(String xml) throws SAXException, IOException {
        return rewrite(xml, true, false, false);
    }

    /**
     * Removes the whitespace-only text between elements, and the line breaks between the declaration, comments and the
     * root element, so the XML is on a single line (the inverse of pretty printing). Text with other characters than
     * whitespace, and CDATA sections, are kept as they are.
     *
     * @param  xml          the XML
     * @return              the compact XML
     * @throws SAXException if the value is not well-formed XML, or has a DOCTYPE declaration
     * @throws IOException  if the value cannot be read
     */
    public static String compact(String xml) throws SAXException, IOException {
        return rewrite(xml, false, false, true);
    }

    /**
     * Canonicalizes the XML (W3C Canonical XML, without comments) using the canonicalizer of the JDK
     * ({@code javax.xml.crypto.dsig}), so two documents that differ only in formatting (attribute order, quotes, empty
     * element tags, namespace declarations) give the same result. The XML declaration and comments are removed.
     *
     * @param  xml                      the XML
     * @param  exclusive                <code>true</code> for Exclusive XML Canonicalization (unused namespace
     *                                  declarations are removed), <code>false</code> for (inclusive) Canonical XML 1.0
     * @return                          the canonical XML
     * @throws SAXException             if the value is not well-formed XML, or has a DOCTYPE declaration
     * @throws IOException              if the value cannot be read
     * @throws GeneralSecurityException if the JDK has no canonicalizer for the algorithm
     * @throws TransformException       if the XML cannot be canonicalized
     */
    public static String canonicalize(String xml, boolean exclusive)
            throws SAXException, IOException, GeneralSecurityException, TransformException {
        if (xml == null) {
            return null;
        }
        // the JDK canonicalizer parses on its own and accepts a DOCTYPE with internal entities, so only XML that
        // passes the hardened parser reaches it
        parse(xml, new DefaultHandler());

        String algorithm = exclusive ? CanonicalizationMethod.EXCLUSIVE : CanonicalizationMethod.INCLUSIVE;
        TransformService transform = TransformService.getInstance(algorithm, "DOM");
        transform.init(null);
        // without a declaration the canonicalizer reads the bytes as UTF-8, whichever encoding the declaration names
        byte[] bytes = stripDeclaration(xml).getBytes(StandardCharsets.UTF_8);
        Data data = transform.transform(new OctetStreamData(new ByteArrayInputStream(bytes)), null);
        try (InputStream is = ((OctetStreamData) data).getOctetStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String rewrite(String xml, boolean stripNamespaces, boolean stripComments, boolean compact)
            throws SAXException, IOException {
        if (xml == null) {
            return null;
        }
        XmlWriter writer = new XmlWriter(stripNamespaces, stripComments, compact);
        parse(xml, writer);
        return writer.toString();
    }

    private static void parse(String xml, DefaultHandler handler) throws SAXException, IOException {
        SAXParser parser = newParser();
        if (handler instanceof DefaultHandler2) {
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        }
        // read from characters so the encoding in the declaration does not matter
        parser.parse(new InputSource(new StringReader(xml)), handler);
    }

    private static SAXParser newParser() throws SAXException {
        // the parser of the JDK, whichever parser is on the classpath, so all the features below are known to it
        SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            SAXParser parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return parser;
        } catch (ParserConfigurationException e) {
            throw new SAXException(e);
        }
    }

    private static boolean isXmlSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
    }

    /**
     * Writes the XML back as it is parsed, leaving out what is stripped.
     */
    private static final class XmlWriter extends DefaultHandler2 {

        private final boolean stripNamespaces;
        private final boolean stripComments;
        private final boolean compact;
        private final StringBuilder out = new StringBuilder(256);
        private final StringBuilder text = new StringBuilder(64);
        private final List<String[]> prefixes = new ArrayList<>();
        private int depth;
        private boolean startTagOpen;
        private boolean inCdata;

        XmlWriter(boolean stripNamespaces, boolean stripComments, boolean compact) {
            this.stripNamespaces = stripNamespaces;
            this.stripComments = stripComments;
            this.compact = compact;
        }

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
                throws SAXException {
            // never reached as a DOCTYPE is disallowed, but external entities must never be read
            throw new SAXException("External entities are not allowed: " + systemId);
        }

        @Override
        public void declaration(String version, String encoding, String standalone) {
            out.append("<?xml");
            if (version != null) {
                out.append(" version=\"").append(version).append('"');
            }
            if (encoding != null) {
                out.append(" encoding=\"").append(encoding).append('"');
            }
            if (standalone != null) {
                out.append(" standalone=\"").append(standalone).append('"');
            }
            out.append("?>");
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            if (!stripNamespaces) {
                prefixes.add(new String[] { prefix, uri });
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            beforeNode();
            out.append('<').append(stripNamespaces ? localName : qName);
            for (String[] mapping : prefixes) {
                out.append(mapping[0].isEmpty() ? " xmlns" : " xmlns:" + mapping[0]).append("=\"");
                escapeAttribute(mapping[1]);
                out.append('"');
            }
            prefixes.clear();
            Set<String> names = stripNamespaces ? new HashSet<>() : null;
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (stripNamespaces && !XMLConstants.XML_NS_URI.equals(attributes.getURI(i))) {
                    name = attributes.getLocalName(i);
                }
                if (names != null && !names.add(name)) {
                    continue;
                }
                out.append(' ').append(name).append("=\"");
                escapeAttribute(attributes.getValue(i));
                out.append('"');
            }
            startTagOpen = true;
            depth++;
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            flushText();
            depth--;
            if (startTagOpen) {
                out.append("/>");
                startTagOpen = false;
            } else {
                out.append("</").append(stripNamespaces ? localName : qName).append('>');
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inCdata) {
                closeStartTag();
                out.append(ch, start, length);
            } else {
                text.append(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
            characters(ch, start, length);
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            if (stripComments) {
                // the text before and after the comment joins up as one
                return;
            }
            beforeNode();
            out.append("<!--").append(ch, start, length).append("-->");
        }

        @Override
        public void processingInstruction(String target, String data) {
            beforeNode();
            out.append("<?").append(target);
            if (data != null && !data.isEmpty()) {
                out.append(' ').append(data);
            }
            out.append("?>");
        }

        @Override
        public void startCDATA() {
            beforeNode();
            out.append("<![CDATA[");
            inCdata = true;
        }

        @Override
        public void endCDATA() {
            out.append("]]>");
            inCdata = false;
        }

        @Override
        public String toString() {
            return out.toString();
        }

        private void beforeNode() {
            flushText();
            closeStartTag();
            // nodes outside the root element: SAX does not report the line breaks between them
            if (depth == 0 && !compact && !out.isEmpty()) {
                out.append('\n');
            }
        }

        private void closeStartTag() {
            if (startTagOpen) {
                out.append('>');
                startTagOpen = false;
            }
        }

        private void flushText() {
            if (text.isEmpty()) {
                return;
            }
            if (!compact || !text.toString().isBlank()) {
                closeStartTag();
                escapeText(text);
            }
            text.setLength(0);
        }

        private void escapeText(CharSequence value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '&' -> out.append("&amp;");
                    case '<' -> out.append("&lt;");
                    case '>' -> out.append("&gt;");
                    case '\r' -> out.append("&#13;");
                    default -> out.append(ch);
                }
            }
        }

        private void escapeAttribute(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '&' -> out.append("&amp;");
                    case '<' -> out.append("&lt;");
                    case '"' -> out.append("&quot;");
                    case '\t' -> out.append("&#9;");
                    case '\n' -> out.append("&#10;");
                    case '\r' -> out.append("&#13;");
                    default -> out.append(ch);
                }
            }
        }
    }
}
