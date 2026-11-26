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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.ws.commons.schema.XmlSchemaCollection;

/**
 * Loads an XML Schema, or the inline schemas of a WSDL, into an {@link XmlSchemaCollection}.
 * <p/>
 * The schema is configuration of the route and so is trusted, yet every document is parsed with DOCTYPE declarations
 * and external entities turned off, and xs:import and xs:include are only followed to resources with the same scheme as
 * the schema option, so a schema loaded over https cannot pull in a local file.
 */
final class XsdSchemaLoader {

    static final String XSD_NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    static final String WSDL11_NS = "http://schemas.xmlsoap.org/wsdl/";
    static final String WSDL20_NS = "http://www.w3.org/ns/wsdl";

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]+:.*");

    private final CamelContext camelContext;
    private final String location;
    private final String scheme;
    private final XsdJsonSamples.DocumentReader reader;

    XsdSchemaLoader(CamelContext camelContext, String schema) {
        this(camelContext, schema, null);
    }

    /**
     * @param reader reads the schema and the documents it imports or includes, instead of the CamelContext (or null)
     */
    XsdSchemaLoader(CamelContext camelContext, String schema, XsdJsonSamples.DocumentReader reader) {
        this.camelContext = camelContext;
        this.reader = reader;
        String s = schema.trim();
        this.location = hasScheme(s) ? s : "classpath:" + s;
        this.scheme = schemeOf(this.location);
    }

    XmlSchemaCollection load() throws Exception {
        Document doc = parse(read(location), location);
        Element root = doc.getDocumentElement();
        XmlSchemaCollection collection = new XmlSchemaCollection();
        collection.setSchemaResolver(this::resolve);
        String ns = root.getNamespaceURI();
        String name = root.getLocalName();
        if (XSD_NS.equals(ns) && "schema".equals(name)) {
            collection.read(doc, location);
        } else if ((WSDL11_NS.equals(ns) && "definitions".equals(name))
                || (WSDL20_NS.equals(ns) && "description".equals(name))) {
            List<Element> schemas = inlineSchemas(root);
            if (schemas.isEmpty()) {
                throw new IllegalArgumentException(
                        "The WSDL " + location + " has no xs:schema in wsdl:types, so there are no elements to map");
            }
            int i = 0;
            for (Element schema : schemas) {
                inheritNamespaces(schema);
                // each inline schema needs its own system id, as two with the same target namespace would be one
                collection.read(schema, location + "#types" + ++i);
            }
        } else {
            throw new IllegalArgumentException(
                    "The schema " + location + " is neither an XML Schema (xs:schema) nor a WSDL (wsdl:definitions), its root"
                                               + " element is {" + ns + "}" + name);
        }
        return collection;
    }

    /**
     * The xs:schema elements in the wsdl:types of a WSDL 1.1 or 2.0 document.
     */
    private static List<Element> inlineSchemas(Element definitions) {
        List<Element> answer = new ArrayList<>();
        for (Node n = definitions.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element types && "types".equals(types.getLocalName())
                    && definitions.getNamespaceURI().equals(types.getNamespaceURI())) {
                for (Node c = types.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c instanceof Element schema && XSD_NS.equals(schema.getNamespaceURI())
                            && "schema".equals(schema.getLocalName())) {
                        answer.add(schema);
                    }
                }
            }
        }
        return answer;
    }

    /**
     * An inline schema inherits the namespace declarations of wsdl:definitions, such as xmlns:tns. They are copied to
     * the xs:schema element, so the prefixes in type="tns:Item" resolve when the schema is read on its own.
     */
    private static void inheritNamespaces(Element schema) {
        Deque<Element> ancestors = new ArrayDeque<>();
        for (Node p = schema.getParentNode(); p instanceof Element e; p = e.getParentNode()) {
            ancestors.push(e);
        }
        for (Element ancestor : ancestors) {
            NamedNodeMap attributes = ancestor.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())
                        && !schema.hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attr.getLocalName())) {
                    schema.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attr.getName(), attr.getValue());
                }
            }
        }
    }

    /**
     * Resolves an xs:import or xs:include: relative to the document that has it, and only with the scheme of the schema
     * option.
     */
    InputSource resolve(String targetNamespace, String schemaLocation, String baseUri) {
        if (schemaLocation == null || schemaLocation.isBlank()) {
            // an import of a namespace without a location, such as of another inline schema of the same WSDL
            return null;
        }
        String resolved = resolveLocation(baseUri != null ? baseUri : location, schemaLocation.trim());
        if (!scheme.equals(schemeOf(resolved))) {
            throw new IllegalArgumentException(
                    "The schema " + location + " imports or includes " + schemaLocation + " (" + resolved
                                               + "), which is not loaded: only " + scheme
                                               + ": resources, the scheme of the schema option, are followed");
        }
        try {
            byte[] data = read(resolved);
            // parse to reject a DOCTYPE before XmlSchema reads the document
            parse(data, resolved);
            InputSource source = new InputSource(new ByteArrayInputStream(data));
            source.setSystemId(resolved);
            return source;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load " + resolved + ", imported or included by " + baseUri, e);
        }
    }

    static String resolveLocation(String baseUri, String schemaLocation) {
        if (hasScheme(schemaLocation)) {
            return schemaLocation;
        }
        String base = baseUri;
        int hash = base.indexOf('#');
        if (hash > 0) {
            base = base.substring(0, hash);
        }
        String baseScheme = schemeOf(base);
        if ("http".equals(baseScheme) || "https".equals(baseScheme)) {
            return URI.create(base).resolve(schemaLocation).toString();
        }
        String path = base.substring(baseScheme.length() + 1);
        String joined;
        if (schemaLocation.startsWith("/")) {
            joined = schemaLocation;
        } else {
            int slash = path.lastIndexOf('/');
            joined = (slash >= 0 ? path.substring(0, slash + 1) : "") + schemaLocation;
        }
        return baseScheme + ":" + normalize(joined);
    }

    /**
     * Removes the . and .. segments of a path, keeping its leading slashes.
     */
    private static String normalize(String path) {
        int start = 0;
        while (start < path.length() && path.charAt(start) == '/') {
            start++;
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.substring(start).split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment) && !segments.isEmpty() && !"..".equals(segments.peekLast())) {
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return path.substring(0, start) + String.join("/", segments);
    }

    static boolean hasScheme(String uri) {
        return SCHEME.matcher(uri).matches();
    }

    static String schemeOf(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon).toLowerCase(Locale.ROOT) : "";
    }

    private byte[] read(String uri) throws IOException {
        if (reader != null) {
            return reader.read(uri);
        }
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
            return is.readAllBytes();
        }
    }

    /**
     * Parses a schema or WSDL document with a DOCTYPE disallowed and no external entities or XInclude.
     */
    static Document parse(byte[] data, String systemId) {
        try {
            DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    // ignore
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            InputSource source = new InputSource(new ByteArrayInputStream(data));
            source.setSystemId(systemId);
            return builder.parse(source);
        } catch (SAXException e) {
            String reason = e.getMessage() != null && e.getMessage().contains("DOCTYPE")
                    ? "a DOCTYPE is not allowed in a schema" : e.getMessage();
            throw new IllegalArgumentException("Cannot parse the schema " + systemId + ": " + reason, e);
        } catch (ParserConfigurationException | IOException e) {
            throw new IllegalArgumentException("Cannot parse the schema " + systemId + ": " + e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // a DOCTYPE is the way to entities, so it must be refused, and the factory must support refusing it
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException e) {
            // not supported by this parser; the DOCTYPE is refused anyway
        }
        return factory;
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            // not supported by this parser; the DOCTYPE is refused anyway
        }
    }
}
