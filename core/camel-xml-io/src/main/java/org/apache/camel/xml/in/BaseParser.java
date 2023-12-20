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
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.apache.camel.LineNumberAware;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.xml.io.MXParser;
import org.apache.camel.xml.io.XmlPullParser;
import org.apache.camel.xml.io.XmlPullParserException;
import org.apache.camel.xml.io.XmlPullParserLocationException;

public class BaseParser {

    protected final MXParser parser;
    protected String namespace;
    protected final Set<String> secondaryNamespaces = new HashSet<>();
    protected Resource resource;

    public BaseParser(Resource resource) throws IOException, XmlPullParserException {
        this(resource.getInputStream(), null);
        this.resource = resource;
    }

    public BaseParser(Resource resource, String namespace) throws IOException, XmlPullParserException {
        this(resource.getInputStream(), namespace);
        this.resource = resource;
    }

    public BaseParser(InputStream input) throws IOException, XmlPullParserException {
        this(input, null);
    }

    public BaseParser(Reader reader) throws IOException, XmlPullParserException {
        this(reader, null);
    }

    public BaseParser(InputStream input, String namespace) throws IOException, XmlPullParserException {
        this.parser = new MXParser();
        this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        this.parser.setInput(input, null);
        this.namespace = namespace != null ? namespace : "";
    }

    public BaseParser(Reader reader, String namespace) throws IOException, XmlPullParserException {
        this.parser = new MXParser();
        this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        this.parser.setInput(reader);
        this.namespace = namespace != null ? namespace : "";
    }

    public void addSecondaryNamespace(String namespace) {
        this.secondaryNamespaces.add(namespace);
    }

    protected <T> T doParse(
            T definition, AttributeHandler<T> attributeHandler, ElementHandler<T> elementHandler, ValueHandler<T> valueHandler)
            throws IOException, XmlPullParserException {
        return doParse(definition, attributeHandler, elementHandler, valueHandler, false);
    }

    protected <T> T doParse(
            T definition, AttributeHandler<T> attributeHandler, ElementHandler<T> elementHandler, ValueHandler<T> valueHandler,
            boolean supportsExternalNamespaces)
            throws IOException, XmlPullParserException {

        try {
            return doParseXml(definition, attributeHandler, elementHandler, valueHandler, supportsExternalNamespaces);
        } catch (Exception e) {
            if (e instanceof XmlPullParserLocationException) {
                throw e;
            }
            // wrap in XmlPullParserLocationException so we have line-precise error
            String msg = e.getMessage();
            Throwable cause = e;
            if (e instanceof XmlPullParserException) {
                if (e.getCause() != null) {
                    cause = e.getCause();
                    msg = e.getCause().getMessage();
                }
            }
            throw new XmlPullParserLocationException(msg, resource, parser.getLineNumber(), parser.getColumnNumber(), cause);
        }
    }

    protected <T> T doParseXml(
            T definition, AttributeHandler<T> attributeHandler, ElementHandler<T> elementHandler, ValueHandler<T> valueHandler,
            boolean supportsExternalNamespaces)
            throws IOException, XmlPullParserException {
        if (definition instanceof LineNumberAware) {
            // we want to get the line number where the tag starts (in case its multi-line)
            int line = parser.getStartLineNumber();
            if (line == -1) {
                line = parser.getLineNumber();
            }
            ((LineNumberAware) definition).setLineNumber(line);
            if (resource != null) {
                ((LineNumberAware) definition).setLocation(resource.getLocation());
            }
        }
        if (definition instanceof NamespaceAware) {
            final Map<String, String> namespaces = new LinkedHashMap<>();
            for (int i = 0; i < parser.getNamespaceCount(parser.getDepth()); i++) {
                final String prefix = parser.getNamespacePrefix(i);
                if (prefix != null) {
                    namespaces.put(prefix, parser.getNamespaceUri(i));
                }
            }
            ((NamespaceAware) definition).setNamespaces(namespaces);
        }
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            String ns = parser.getAttributeNamespace(i);
            String val = parser.getAttributeValue(i);
            if (name.equals("uri") || name.endsWith("Uri")) {
                val = URISupport.removeNoiseFromUri(val);
            }
            if (matchNamespace(ns, true)) {
                if (attributeHandler == null || !attributeHandler.accept(definition, name, val)) {
                    handleUnexpectedAttribute(ns, name);
                }
            } else {
                handleOtherAttribute(definition, name, ns, val);
            }
        }
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.TEXT) {
                if (!parser.isWhitespace()) {
                    valueHandler.accept(definition, parser.getText());
                }
            } else if (event == XmlPullParser.START_TAG) {
                String ns = parser.getNamespace();
                String name = parser.getName();
                if (supportsExternalNamespaces) {
                    // pass element to the handler regardless of namespace
                    if (elementHandler == null || !elementHandler.accept(definition, name)) {
                        handleUnexpectedElement(ns, name);
                    }
                } else {
                    // pass element to the handler only if matches the declared namespace for the parser
                    if (matchNamespace(ns, false)) {
                        if (elementHandler == null || !elementHandler.accept(definition, name)) {
                            handleUnexpectedElement(namespace, name);
                        }
                    } else {
                        handleUnexpectedElement(ns, name);
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                // we need to check first if the end tag is from
                // and unexpected element which we should ignore,
                // and continue parsing (special need for camel-xml-io-dsl)
                String ns = parser.getNamespace();
                String name = parser.getName();
                boolean ignore = false;
                if (supportsExternalNamespaces) {
                    ignore = ignoreUnexpectedElement(ns, name);
                }
                if (!ignore) {
                    return definition;
                }
            } else {
                throw new XmlPullParserException(
                        "expected START_TAG or END_TAG not " + XmlPullParser.TYPES[event], parser, null);
            }
        }
    }

    protected <T> List<T> doParseValue(Supplier<T> definitionSupplier, ValueHandler<T> valueHandler)
            throws IOException, XmlPullParserException {

        List<T> answer = new ArrayList<>();

        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.TEXT) {
                if (!parser.isWhitespace()) {
                    T definition = definitionSupplier.get();
                    if (definition instanceof LineNumberAware) {
                        // we want to get the line number where the tag starts (in case its multi-line)
                        int line = parser.getStartLineNumber();
                        if (line == -1) {
                            line = parser.getLineNumber();
                        }
                        ((LineNumberAware) definition).setLineNumber(line);
                        if (resource != null) {
                            ((LineNumberAware) definition).setLocation(resource.getLocation());
                        }
                    }
                    valueHandler.accept(definition, parser.getText());
                    answer.add(definition);
                }
            } else if (event == XmlPullParser.START_TAG) {
                String ns = parser.getNamespace();
                String name = parser.getName();
                if (matchNamespace(ns, false)) {
                    if (!"value".equals(name)) {
                        handleUnexpectedElement(ns, name);
                    }
                } else {
                    handleUnexpectedElement(ns, name);
                }
            } else if (event == XmlPullParser.END_TAG) {
                String ns = parser.getNamespace();
                String name = parser.getName();
                if (matchNamespace(ns, false)) {
                    if ("value".equals(name)) {
                        continue;
                    }
                }
                return answer;
            } else {
                throw new XmlPullParserException(
                        "expected START_TAG or END_TAG not " + XmlPullParser.TYPES[event], parser, null);
            }
        }
    }

    protected Class<?> asClass(String val) throws XmlPullParserException {
        try {
            return Class.forName(val);
        } catch (ClassNotFoundException e) {
            throw new XmlPullParserException("Unable to load class " + val, parser, e);
        }
    }

    protected Class<?>[] asClassArray(String val) throws XmlPullParserException {
        String[] vals = val.split(" ");
        Class<?>[] cls = new Class<?>[vals.length];
        for (int i = 0; i < vals.length; i++) {
            cls[i] = asClass(vals[i]);
        }
        return cls;
    }

    protected byte[] asByteArray(String val) {
        return Base64.getDecoder().decode(val);
    }

    protected List<String> asStringList(String val) {
        return new ArrayList<>(Arrays.asList(val.split(" ")));
    }

    protected Set<String> asStringSet(String val) {
        return new LinkedHashSet<>(Arrays.asList(val.split(" ")));
    }

    protected <T> void doAdd(T element, List<T> existing, Consumer<List<T>> setter) {
        if (existing == null) {
            existing = new ArrayList<>();
            setter.accept(existing);
        }
        existing.add(element);
    }

    protected <T> void doAddValues(List<T> elements, List<T> existing, Consumer<List<T>> setter) {
        if (existing == null) {
            existing = new ArrayList<>();
            setter.accept(existing);
        }
        existing.addAll(elements);
    }

    protected String doParseText() throws IOException, XmlPullParserException {
        String s = "";
        int e = parser.next();
        if (e == XmlPullParser.TEXT) {
            s = parser.getText();
            e = parser.next();
        }
        if (e != XmlPullParser.END_TAG) {
            throw new XmlPullParserException("Expected text element");
        }
        return s;
    }

    protected Element doParseDOMElement(String rootElementName, String namespace, List<Element> existing)
            throws XmlPullParserException, IOException {
        Document doc = null;
        if (existing != null && !existing.isEmpty()) {
            doc = existing.get(0).getOwnerDocument();
        } else {
            // create a new one
            try {
                doc = createDocumentBuilderFactory().newDocumentBuilder().newDocument();
                // with root element generated from @ExternalSchemaElement.documentElement
                Element rootElement = doc.createElementNS(namespace, rootElementName);
                doc.appendChild(rootElement);
            } catch (ParserConfigurationException e) {
                throw new XmlPullParserException(
                        "Problem handling external element '{" + namespace + "}" + parser.getName()
                                                 + ": " + e.getMessage());
            }
        }
        if (doc == null) {
            return null;
        }

        Element element = doc.createElementNS(namespace, parser.getName());
        doc.getDocumentElement().appendChild(element);
        doParse(element, domAttributeHandler(), domElementHandler(), domValueHandler(), true);
        return element;
    }

    protected void doAddElement(Element element, List<Element> existing, Consumer<List<Element>> setter) {
        if (existing == null) {
            existing = new ArrayList<>();
            setter.accept(existing);
        }
        existing.add(element);
    }

    protected boolean handleUnexpectedAttribute(String namespace, String name) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected attribute '{" + namespace + "}" + name + "'");
    }

    protected boolean handleUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected element '{" + namespace + "}" + name + "'");
    }

    protected void handleUnexpectedText(String text) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected text '" + text + "'");
    }

    protected boolean ignoreUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        return false;
    }

    protected void expectTag(String name) throws XmlPullParserException, IOException {
        if (parser.nextTag() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException(
                    "Expected starting tag '{" + namespace + "}" + name + "', read ending tag '{" + parser.getNamespace() + "}"
                                             + parser.getName()
                                             + "' instead");
        }
        if (!Objects.equals(name, parser.getName()) || !Objects.equals(namespace, parser.getNamespace())) {
            throw new XmlPullParserException(
                    "Expected starting tag '{" + namespace + "}" + name + "', read starting tag '{" + parser.getNamespace()
                                             + "}" + parser.getName()
                                             + "' instead");
        }
    }

    protected boolean hasTag(String name) throws XmlPullParserException, IOException {
        if (parser.nextTag() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("Expected starting tag");
        }

        if (!Objects.equals(name, parser.getName()) || !matchNamespace(namespace, parser.getNamespace(), null, false)) {
            return false;
        }

        return true;
    }

    protected String getNextTag(String name, String name2) throws XmlPullParserException, IOException {
        if (parser.nextTag() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("Expected starting tag");
        }

        String pn = parser.getName();
        boolean match = Objects.equals(name, pn) || Objects.equals(name2, pn);
        if (!match || !matchNamespace(namespace, parser.getNamespace(), null, false)) {
            return ""; // empty tag
        }

        return pn;
    }

    protected String getNextTag(String name, String name2, String name3) throws XmlPullParserException, IOException {
        if (parser.nextTag() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("Expected starting tag");
        }

        String pn = parser.getName();
        boolean match = Objects.equals(name, pn) || Objects.equals(name2, pn) || Objects.equals(name3, pn);
        if (!match || !matchNamespace(namespace, parser.getNamespace(), null, false)) {
            return ""; // empty tag
        }

        return pn;
    }

    protected void handleOtherAttribute(Object definition, String name, String ns, String val) throws XmlPullParserException {
        // Ignore
        if ("http://www.w3.org/2001/XMLSchema-instance".equals(ns)) {
            return;
        }
        String fqn = ns.isEmpty() ? name : "{" + ns + "}" + name;
        throw new XmlPullParserException("Unsupported attribute '" + fqn + "'");
    }

    protected <T> AttributeHandler<T> noAttributeHandler() {
        return null;
    }

    protected <T> ElementHandler<T> noElementHandler() {
        return (def, name) -> handleUnexpectedElement(namespace, name);
    }

    protected <T> ValueHandler<T> noValueHandler() {
        return (def, text) -> handleUnexpectedText(text);
    }

    protected AttributeHandler<Element> domAttributeHandler() {
        return (el, name, value) -> {
            // for now, handle only XMLs where schema declares attributeFormDefault="unqualified"
            el.setAttributeNS(null, name, value);
            return true;
        };
    }

    protected ElementHandler<Element> domElementHandler() {
        return (def, name) -> {
            Element child = def.getOwnerDocument().createElementNS(parser.getNamespace(), name);
            def.appendChild(child);
            doParse(child, domAttributeHandler(), domElementHandler(), domValueHandler(), true);
            return true;
        };
    }

    protected ValueHandler<Element> domValueHandler() {
        return (def, text) -> {
            Text txt = def.getOwnerDocument().createTextNode(text);
            def.appendChild(txt);
        };
    }

    protected <T extends ExpressionDefinition> ValueHandler<T> expressionDefinitionValueHandler() {
        return ExpressionDefinition::setExpression;
    }

    // another one...
    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
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

    interface AttributeHandler<T> {
        boolean accept(T definition, String name, String value) throws IOException, XmlPullParserException;
    }

    interface ElementHandler<T> {
        boolean accept(T definition, String name) throws IOException, XmlPullParserException;
    }

    interface ValueHandler<T> {
        void accept(T definition, String value) throws IOException, XmlPullParserException;
    }

    protected boolean matchNamespace(String ns, boolean optional) {
        return matchNamespace(ns, namespace, secondaryNamespaces, optional);
    }

    protected static boolean matchNamespace(String ns, String namespace, Set<String> secondaryNamespaces, boolean optional) {
        if (optional && ns.isEmpty()) {
            return true;
        }
        if (Objects.equals(ns, namespace)) {
            return true;
        }
        for (String second : secondaryNamespaces) {
            if (Objects.equals(ns, second)) {
                return true;
            }
        }
        return false;
    }

}
