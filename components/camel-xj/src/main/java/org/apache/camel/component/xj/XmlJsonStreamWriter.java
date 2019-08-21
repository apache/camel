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

package org.apache.camel.component.xj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;

/**
 * XML Json bridge. Explicitly using StreamWriter and not XMLEventWriter because saxon wants that.
 */
public class XmlJsonStreamWriter implements XMLStreamWriter {

    private static final String JSON_MIXED_CONTENT_TEXT_KEY = "#text";

    private final JsonGenerator jsonGenerator;

    private TreeElement treeRoot;
    private TreeElement currentTreeElement;

    public XmlJsonStreamWriter(JsonGenerator jsonGenerator) {
        this.jsonGenerator = jsonGenerator;
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        writeStartElement(null, localName, null);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        writeStartElement(null, localName, namespaceURI);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        final TreeElement treeElement = new TreeElement(currentTreeElement, XMLEvent.START_ELEMENT, localName);

        currentTreeElement.addChild(treeElement);
        currentTreeElement = treeElement;
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        writeStartElement(null, namespaceURI, localName);
        writeEndElement();
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        writeStartElement(prefix, localName, namespaceURI);
        writeEndElement();
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        writeStartElement(null, localName, null);
        writeEndElement();
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        try {
            currentTreeElement.writeEnd();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
        currentTreeElement = currentTreeElement.parent;
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        try {
            treeRoot.write(jsonGenerator);
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void close() throws XMLStreamException {
        try {
            jsonGenerator.close();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void flush() throws XMLStreamException {
        try {
            jsonGenerator.flush();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        writeAttribute(null, null, localName, value);
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        if (XJConstants.NS_XJ.equals(namespaceURI)) {
            switch (localName) {
                case XJConstants.TYPE_HINT_NAME:
                    currentTreeElement.setName(value);
                    return;
                case XJConstants.TYPE_HINT_TYPE:
                    currentTreeElement.setJsonToken(XJConstants.TYPE_JSONTYPE_MAP.get(value));
                    return;
            }

            return;
        }

        final TreeElement treeElement = new TreeElement(currentTreeElement, XMLEvent.ATTRIBUTE, JsonToken.VALUE_STRING, localName);
        treeElement.setValue(value);

        currentTreeElement.addChild(treeElement);
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        writeAttribute(null, namespaceURI, localName, value);
    }

    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
    }

    @Override
    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
    }

    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException {
    }

    @Override
    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
    }

    @Override
    public void writeCData(String data) throws XMLStreamException {
        writeCharacters(data);
    }

    @Override
    public void writeDTD(String dtd) throws XMLStreamException {
    }

    @Override
    public void writeEntityRef(String name) throws XMLStreamException {
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        writeStartDocument(null);
    }

    @Override
    public void writeStartDocument(String version) throws XMLStreamException {
        writeStartDocument(null, version);
    }

    @Override
    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        final TreeElement treeElement = new TreeElement(null, XMLEvent.START_DOCUMENT, JsonToken.NOT_AVAILABLE);
        this.treeRoot = treeElement;
        this.currentTreeElement = treeElement;
    }

    @Override
    public void writeCharacters(String text) throws XMLStreamException {
        // check for non coalescing read
        final List<TreeElement> childs = currentTreeElement.childs;
        if (childs.size() > 0) {
            final TreeElement child = childs.get(childs.size() - 1);
            if (child.getXmlEvent() == XMLEvent.CHARACTERS) {
                child.appendValue(text);

                return;
            }
        }

        final TreeElement treeElement = new TreeElement(currentTreeElement, XMLEvent.CHARACTERS, JsonToken.VALUE_STRING);
        treeElement.setValue(text);

        currentTreeElement.addChild(treeElement);
    }

    @Override
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        writeCharacters(new String(text, start, len));
    }

    @Override
    public String getPrefix(String uri) throws XMLStreamException {
        return null;
    }

    @Override
    public void setPrefix(String prefix, String uri) throws XMLStreamException {
    }

    @Override
    public void setDefaultNamespace(String uri) throws XMLStreamException {
    }

    @Override
    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException(XJConstants.UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE);
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        throw new IllegalArgumentException(name + " unsupported");
    }


    private static class TreeElement {

        private TreeElement parent;
        private List<TreeElement> childs = Collections.emptyList();

        private String name;
        private String value;
        private int xmlEvent;
        private JsonToken jsonToken;

        TreeElement(TreeElement parent, int xmlEvent, String name) {
            this.parent = parent;
            this.xmlEvent = xmlEvent;
            this.name = name;
        }

        TreeElement(TreeElement parent, int xmlEvent, JsonToken jsonToken) {
            this.parent = parent;
            this.xmlEvent = xmlEvent;
            this.jsonToken = jsonToken;
        }

        TreeElement(TreeElement parent, int xmlEvent, JsonToken jsonToken, String name) {
            this.parent = parent;
            this.xmlEvent = xmlEvent;
            this.jsonToken = jsonToken;
            this.name = name;
        }

        int getXmlEvent() {
            return this.xmlEvent;
        }

        void addChild(TreeElement treeElement) {
            if (this.childs == Collections.EMPTY_LIST) {
                this.childs = new ArrayList<>(1);
            }

            this.childs.add(treeElement);
        }

        void setJsonToken(JsonToken jsonToken) {
            if (this.jsonToken == null) {
                this.jsonToken = jsonToken;
            }
        }

        void setName(String name) {
            this.name = name;
        }

        void setValue(String value) {
            this.value = value;
        }

        void appendValue(String value) {
            this.value += value;
        }

        void writeEnd() throws IOException {
            if (jsonToken == null) {
                writeEndNoTypeHints();
            } else { // type hints
                // move type hints
                writeEndHaveTypeHints();
            }
        }

        private void writeEndNoTypeHints() {
            switch (xmlEvent) {
                case XMLEvent.START_ELEMENT:
                    if (childs.isEmpty()) {
                        // empty root element
                        if (this.parent.jsonToken == JsonToken.NOT_AVAILABLE) {
                            jsonToken = JsonToken.START_OBJECT;
                        } else {
                            jsonToken = JsonToken.FIELD_NAME;

                            final TreeElement treeElement = new TreeElement(this, -1, JsonToken.VALUE_STRING);
                            treeElement.setValue("");
                            this.addChild(treeElement);
                        }
                    } else if (childs.size() == 1 && childs.get(0).xmlEvent == XMLEvent.CHARACTERS) {
                        // just character childs.

                        // empty root element
                        if (this.parent.jsonToken == JsonToken.NOT_AVAILABLE) {
                            jsonToken = JsonToken.START_OBJECT;

                            final TreeElement child = childs.get(0);
                            if (isWhitespace(child.value)) {
                                childs.remove(0);
                            } else {
                                // create new intermediary element
                                final TreeElement treeElement = new TreeElement(this, -1, JsonToken.FIELD_NAME, JSON_MIXED_CONTENT_TEXT_KEY);
                                treeElement.addChild(child);
                                childs.set(childs.indexOf(child), treeElement);
                                child.parent = treeElement;
                            }
                        } else {
                            jsonToken = JsonToken.FIELD_NAME;
                        }
                    } else {
                        // mixed content fixup.
                        final Iterator<TreeElement> iterator = childs.iterator();
                        while (iterator.hasNext()) {
                            TreeElement element = iterator.next();
                            if (element.jsonToken == JsonToken.VALUE_STRING) {
                                if (isWhitespace(element.value)) {
                                    // remove element if is (ignorable-) whitespace
                                    iterator.remove();
                                } else {
                                    // create new intermediary element
                                    final TreeElement treeElement = new TreeElement(this, -1, JsonToken.FIELD_NAME,
                                            element.name != null ? element.name : JSON_MIXED_CONTENT_TEXT_KEY);
                                    treeElement.addChild(element);
                                    childs.set(childs.indexOf(element), treeElement);
                                    element.parent = treeElement;
                                    element.jsonToken = JsonToken.VALUE_STRING;
                                }
                            }
                        }

                        jsonToken = JsonToken.START_OBJECT;

                        final Map<String, Set<TreeElement>> childElementsMap = childs.stream()
                                .collect(Collectors.groupingBy(o -> o.name, HashMap::new, Collectors.toCollection(LinkedHashSet::new)));

                        // create arrays if element with the same name occurs more than once.
                        for (Map.Entry<String, Set<TreeElement>> mapEntry : childElementsMap.entrySet()) {
                            if (mapEntry.getValue().size() > 1) {
                                if (childElementsMap.size() == 1) {
                                    jsonToken = JsonToken.START_ARRAY;
                                } else {
                                    final TreeElement treeElement = new TreeElement(this, -1, JsonToken.START_ARRAY, mapEntry.getKey());
                                    treeElement.childs = new ArrayList<>(mapEntry.getValue());
                                    for (TreeElement child : treeElement.childs) {
                                        child.parent = treeElement;
                                    }

                                    final List<TreeElement> newChildList = new ArrayList<>(this.childs.size() - mapEntry.getValue().size() + 1);
                                    for (TreeElement e : this.childs) {
                                        if (!mapEntry.getValue().contains(e)) {
                                            newChildList.add(e);
                                        }
                                    }
                                    childs = newChildList;
                                    childs.add(treeElement);
                                }
                            }
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("XMLEvent: " + xmlEvent + "; Json Token: " + jsonToken);
            }
        }

        private void writeEndHaveTypeHints() {
            switch (jsonToken) {
                case VALUE_NULL:
                case VALUE_STRING:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_TRUE:
                case VALUE_FALSE:
                    if (childs.isEmpty()) {
                        final TreeElement treeElement = new TreeElement(this, -1, jsonToken);
                        treeElement.setValue("");
                        this.addChild(treeElement);

                        jsonToken = JsonToken.FIELD_NAME;
                    } else if (childs.size() == 1) {
                        childs.get(0).jsonToken = jsonToken;
                        jsonToken = JsonToken.FIELD_NAME;
                    } else {
                        // create FIELD childs if element contains text and attributes.
                        final Iterator<TreeElement> iterator = childs.iterator();
                        while (iterator.hasNext()) {
                            TreeElement element = iterator.next();
                            if (isValueToken(element.jsonToken)) {
                                if (isWhitespace(element.value)) {
                                    // remove element if is (ignorable-) whitespace
                                    iterator.remove();
                                } else {
                                    // create new intermediary element
                                    final TreeElement treeElement = new TreeElement(this, -1, JsonToken.FIELD_NAME,
                                            element.name != null ? element.name : JSON_MIXED_CONTENT_TEXT_KEY);
                                    treeElement.addChild(element);
                                    childs.set(childs.indexOf(element), treeElement);
                                    element.parent = treeElement;
                                    if (element.xmlEvent == XMLEvent.CHARACTERS) {
                                        element.jsonToken = jsonToken;
                                    }
                                }
                            }
                        }

                        jsonToken = JsonToken.START_OBJECT;

                        final Map<String, Set<TreeElement>> childElementsMap = childs.stream()
                                .collect(Collectors.groupingBy(o -> o.name, HashMap::new, Collectors.toCollection(LinkedHashSet::new)));

                        // create arrays if element with the same name occurs more than once.
                        for (Map.Entry<String, Set<TreeElement>> mapEntry : childElementsMap.entrySet()) {
                            if (mapEntry.getValue().size() > 1) {

                                if (childElementsMap.size() == 1) {
                                    jsonToken = JsonToken.START_ARRAY;
                                } else {
                                    final TreeElement treeElement = new TreeElement(this, -1, JsonToken.START_ARRAY, mapEntry.getKey());
                                    treeElement.childs = new ArrayList<>(mapEntry.getValue());
                                    for (TreeElement child : treeElement.childs) {
                                        child.parent = treeElement;
                                    }

                                    final List<TreeElement> newChildList = new ArrayList<>(this.childs.size() - mapEntry.getValue().size() + 1);
                                    for (TreeElement e : this.childs) {
                                        if (!mapEntry.getValue().contains(e)) {
                                            newChildList.add(e);
                                        }
                                    }
                                    childs = newChildList;
                                    childs.add(treeElement);
                                }
                            }
                        }
                    }
                    break;
                case START_OBJECT:
                case START_ARRAY:
                    // mixed content fixup.
                    final Iterator<TreeElement> iterator = childs.iterator();
                    while (iterator.hasNext()) {
                        TreeElement element = iterator.next();
                        if (isValueToken(element.jsonToken)) {
                            if (isWhitespace(element.value)) {
                                // remove element if is (ignorable-) whitespace
                                iterator.remove();
                            } else {
                                // create new intermediary element
                                final TreeElement treeElement = new TreeElement(this, -1, JsonToken.FIELD_NAME, JSON_MIXED_CONTENT_TEXT_KEY);
                                treeElement.addChild(element);
                                childs.set(childs.indexOf(element), treeElement);
                                element.parent = treeElement;
                            }
                        }
                    }

                    final Map<String, Set<TreeElement>> childElementsMap = childs.stream()
                            .collect(Collectors.groupingBy(o -> o.name, HashMap::new, Collectors.toCollection(LinkedHashSet::new)));

                    if (this.jsonToken != JsonToken.START_ARRAY) {

                        // create arrays if element with the same name occurs more than once.
                        for (Map.Entry<String, Set<TreeElement>> mapEntry : childElementsMap.entrySet()) {
                            if (mapEntry.getValue().size() > 1) {

                                final TreeElement treeElement = new TreeElement(this, -1, JsonToken.START_ARRAY, mapEntry.getKey());
                                treeElement.childs = new ArrayList<>(mapEntry.getValue());
                                for (TreeElement child : treeElement.childs) {
                                    child.parent = treeElement;
                                }

                                final List<TreeElement> newChildList = new ArrayList<>(this.childs.size() - mapEntry.getValue().size() + 1);
                                for (TreeElement e : this.childs) {
                                    if (!mapEntry.getValue().contains(e)) {
                                        newChildList.add(e);
                                    }
                                }
                                childs = newChildList;
                                childs.add(treeElement);
                            }
                        }
                    }

                    break;
                default:
                    throw new IllegalStateException("XMLEvent: " + xmlEvent + "; Json Token: " + jsonToken);
            }
        }

        private boolean isWhitespace(String text) {
            int len = text.length();
            int st = 0;

            while ((st < len) && (text.charAt(st) <= ' ')) {
                st++;
            }

            return st == len;
        }

        private boolean isValueToken(JsonToken jsonToken) {
            return jsonToken == JsonToken.VALUE_STRING ||
                    jsonToken == JsonToken.VALUE_NUMBER_FLOAT ||
                    jsonToken == JsonToken.VALUE_NUMBER_INT ||
                    jsonToken == JsonToken.VALUE_TRUE ||
                    jsonToken == JsonToken.VALUE_FALSE ||
                    jsonToken == JsonToken.VALUE_NULL;
        }

        void write(JsonGenerator jsonGenerator) throws IOException {
            switch (jsonToken) {
                case NOT_AVAILABLE:
                    break;
                case START_OBJECT:
                    if (parent.jsonToken == JsonToken.START_OBJECT) {
                        jsonGenerator.writeObjectFieldStart(name);
                    } else {
                        jsonGenerator.writeStartObject();
                    }

                    break;
                case START_ARRAY:
                    if (parent.jsonToken == JsonToken.START_OBJECT) {
                        jsonGenerator.writeArrayFieldStart(name);
                    } else {
                        jsonGenerator.writeStartArray();
                    }

                    break;
                case FIELD_NAME:
                    if (parent.jsonToken != JsonToken.START_ARRAY) {
                        jsonGenerator.writeFieldName(name);
                    }

                    break;
                case VALUE_STRING:
                    jsonGenerator.writeString(value);

                    break;
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_TRUE:
                case VALUE_FALSE:
                    if (value == null || value.isEmpty()) {
                        jsonGenerator.writeNull();
                    } else {
                        jsonGenerator.writeRawValue(value);
                    }

                    break;
                case VALUE_NULL:
                    jsonGenerator.writeNull();

                    break;
                default:
                    throw new IllegalStateException("XMLEvent: " + xmlEvent + "; Json Token: " + jsonToken);
            }

            for (TreeElement treeElement : childs) {
                treeElement.write(jsonGenerator);
            }

            switch (jsonToken) {
                case START_OBJECT:
                    jsonGenerator.writeEndObject();
                    break;
                case START_ARRAY:
                    jsonGenerator.writeEndArray();
                    break;
                case VALUE_NULL:
                case NOT_AVAILABLE:
                case FIELD_NAME:
                case VALUE_STRING:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_TRUE:
                case VALUE_FALSE:
                    // nop;
                    break;
                default:
                    throw new IllegalStateException("XMLEvent: " + xmlEvent + "; Json Token: " + jsonToken);
            }
        }

        @Override
        public String toString() {
            return "TreeElement{" +
                    "name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    ", xmlEvent=" + xmlEvent +
                    ", jsonToken=" + jsonToken +
                    '}';
        }
    }
}
