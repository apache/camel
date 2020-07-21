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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.xml.io.MXParser;
import org.apache.camel.xml.io.XmlPullParser;
import org.apache.camel.xml.io.XmlPullParserException;

public class BaseParser {

    protected final MXParser parser;
    protected String namespace;

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

    protected <T> T doParse(T definition, AttributeHandler<T> attributeHandler, ElementHandler<T> elementHandler, ValueHandler<T> valueHandler)
        throws IOException, XmlPullParserException {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            String ns = parser.getAttributeNamespace(i);
            String val = parser.getAttributeValue(i);
            if (Objects.equals(ns, "") || Objects.equals(ns, namespace)) {
                if (attributeHandler == null || !attributeHandler.accept(definition, name, val)) {
                    handleUnexpectedAttribute(namespace, name);
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
                if (Objects.equals(ns, namespace)) {
                    if (elementHandler == null || !elementHandler.accept(definition, name)) {
                        handleUnexpectedElement(namespace, name);
                    }
                } else {
                    handleUnexpectedElement(ns, name);
                }
            } else if (event == XmlPullParser.END_TAG) {
                return definition;
            } else {
                throw new XmlPullParserException("expected START_TAG or END_TAG not " + XmlPullParser.TYPES[event], parser, null);
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

    @SuppressWarnings("unchecked")
    protected <T> void doAdd(T element, T[] existing, Consumer<T[]> setter) {
        int len = existing != null ? existing.length : 0;
        T[] newArray = (T[])Array.newInstance(element.getClass(), len + 1);
        if (len > 0) {
            System.arraycopy(existing, 0, newArray, 0, len);
        }
        newArray[len] = element;
        setter.accept(newArray);
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

    protected boolean handleUnexpectedAttribute(String namespace, String name) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected attribute '{" + namespace + "}" + name + "'");
    }

    protected boolean handleUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected element '{" + namespace + "}" + name + "'");
    }

    protected void handleUnexpectedText(String text) throws XmlPullParserException {
        throw new XmlPullParserException("Unexpected text '" + text + "'");
    }

    protected void expectTag(String name) throws XmlPullParserException, IOException {
        if (parser.nextTag() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("Expected starting tag '{" + namespace + "}" + name + "', read ending tag '{" + parser.getNamespace() + "}" + parser.getName()
                                             + "' instead");
        }
        if (!Objects.equals(name, parser.getName()) || !Objects.equals(namespace, parser.getNamespace())) {
            throw new XmlPullParserException("Expected starting tag '{" + namespace + "}" + name + "', read starting tag '{" + parser.getNamespace() + "}" + parser.getName()
                                             + "' instead");
        }
    }

    @SuppressWarnings("unchecked")
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

    protected <T extends ExpressionDefinition> ValueHandler<T> expressionDefinitionValueHandler() {
        return ExpressionDefinition::setExpression;
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
}
