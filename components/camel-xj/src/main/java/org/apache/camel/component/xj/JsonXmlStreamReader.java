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
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.camel.util.ObjectHelper;

/**
 * XML Json bridge. Explicitly using XMLStreamReader and not XMLEventReader because saxon wants that.
 */
public class JsonXmlStreamReader implements XMLStreamReader {

    private static final String ERROR_MSG_NOT_IN_START_ELEMENT = "Current event is not start element";
    private static final String ERROR_MSG_NOT_IN_START_END_ELEMENT = "Current event is not start element";
    private static final String ERROR_MSG_NOT_IN_CHARACTERS = "Current event is not character";

    private static final Location LOCATION = new Location() {
        @Override
        public int getLineNumber() {
            return -1;
        }

        @Override
        public int getColumnNumber() {
            return -1;
        }

        @Override
        public int getCharacterOffset() {
            return -1;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public String getSystemId() {
            return null;
        }
    };

    private final JsonParser jsonParser;
    private final Deque<StackElement> tokenStack = new ArrayDeque<>();
    private boolean eof;

    /**
     * Creates a new JsonXmlStreamReader instance
     *
     * @param jsonParser the {@link JsonParser} to use to read the json document.
     */
    public JsonXmlStreamReader(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        return null;
    }

    @Override
    public int next() throws XMLStreamException {
        try {
            final StackElement previousElement = tokenStack.peek();
            if (previousElement != null) {
                switch (previousElement.jsonToken) {
                    case VALUE_STRING:
                    case VALUE_NUMBER_INT:
                    case VALUE_NUMBER_FLOAT:
                    case VALUE_NULL:
                    case VALUE_TRUE:
                    case VALUE_FALSE: {
                        switch (previousElement.xmlEvent) {
                            case XMLStreamConstants.START_ELEMENT:
                                previousElement.xmlEvent = XMLStreamConstants.CHARACTERS;
                                return XMLStreamConstants.CHARACTERS;
                            case XMLStreamConstants.CHARACTERS:
                                removeStackElement(previousElement.jsonToken);
                                removeStackElement(JsonToken.FIELD_NAME);

                                ObjectHelper.notNull(tokenStack.peek(), "tokenStack.peek()");
                                tokenStack.peek().xmlEvent = XMLStreamConstants.END_ELEMENT;
                                return XMLStreamConstants.END_ELEMENT;
                            default:
                                throw new IllegalStateException("illegal state");
                        }
                    }
                    default:
                        break;
                }
            }

            if (eof) {
                return END_DOCUMENT;
            }

            JsonToken currentToken = jsonParser.nextToken();
            if (currentToken == null) {
                throw new IllegalStateException("End of document");
            }

            StackElement stackElement = new StackElement(currentToken, toXmlString(jsonParser.getCurrentName()));
            tokenStack.push(stackElement);

            if (currentToken == JsonToken.FIELD_NAME) {
                currentToken = jsonParser.nextToken();

                stackElement = new StackElement(currentToken, toXmlString(jsonParser.getCurrentName()));
                tokenStack.push(stackElement);
            }

            switch (currentToken) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_NULL:
                case VALUE_TRUE:
                case VALUE_FALSE:
                    stackElement.xmlEvent = XMLStreamConstants.START_ELEMENT;

                    return XMLStreamConstants.START_ELEMENT;
                case END_OBJECT:
                    removeStackElement(JsonToken.END_OBJECT);
                    removeStackElement(JsonToken.START_OBJECT);
                    removeStackElement(JsonToken.FIELD_NAME);
                    eof = tokenStack.isEmpty();

                    return XMLStreamConstants.END_ELEMENT;
                case END_ARRAY:
                    removeStackElement(JsonToken.END_ARRAY);
                    removeStackElement(JsonToken.START_ARRAY);
                    removeStackElement(JsonToken.FIELD_NAME);
                    eof = tokenStack.isEmpty();

                    return XMLStreamConstants.END_ELEMENT;
                default:
                    throw new IllegalStateException("JsonToken: " + currentToken);
            }

        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    private void removeStackElement(JsonToken jsonToken) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.jsonToken != jsonToken) {
            if (stackElement != null && jsonToken == JsonToken.FIELD_NAME
                    && stackElement.jsonToken == JsonToken.START_ARRAY) {
                // anonymous array
                return;
            }

            if (stackElement == null && jsonToken == JsonToken.FIELD_NAME) {
                // root object / array
                return;
            }

            final String stackElements = tokenStack.stream().map(StackElement::toString).collect(Collectors.joining("\n"));
            throw new IllegalStateException(
                    "Stack element did not match expected (" + jsonToken + ") one. Stack:\n" + stackElements);
        }

        tokenStack.pop();
    }

    @Override
    public void require(int type, String namespaceURI, String localName) {
        throw new UnsupportedOperationException(XJConstants.UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE);
    }

    @Override
    public String getElementText() {
        throw new UnsupportedOperationException(XJConstants.UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE);
    }

    @Override
    public int nextTag() throws XMLStreamException {
        int evt;
        do {
            evt = next();
        } while (evt != XMLStreamConstants.START_ELEMENT && evt != XMLStreamConstants.END_ELEMENT);

        return evt;
    }

    @Override
    public boolean hasNext() {
        return !eof;
    }

    @Override
    public void close() throws XMLStreamException {
        try {
            jsonParser.close();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return null;
    }

    @Override
    public boolean isStartElement() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null) {
            return false;
        }

        return stackElement.xmlEvent == XMLStreamConstants.START_ELEMENT;
    }

    @Override
    public boolean isEndElement() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null) {
            return false;
        }

        return stackElement.xmlEvent == XMLStreamConstants.END_ELEMENT;
    }

    @Override
    public boolean isCharacters() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null) {
            return false;
        }

        return stackElement.xmlEvent == XMLStreamConstants.CHARACTERS;
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public String getAttributeValue(String namespaceURI, String localName) {
        throw new UnsupportedOperationException(XJConstants.UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE);
    }

    @Override
    public int getAttributeCount() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return stackElement.getAttributeCount();
    }

    @Override
    public QName getAttributeName(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return stackElement.getAttribute(index);
    }

    @Override
    public String getAttributeNamespace(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return stackElement.getAttribute(index).getNamespaceURI();
    }

    @Override
    public String getAttributeLocalName(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return stackElement.getAttribute(index).getLocalPart();
    }

    @Override
    public String getAttributePrefix(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return stackElement.getAttribute(index).getPrefix();
    }

    @Override
    public String getAttributeType(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return "CDATA";
    }

    @Override
    public String getAttributeValue(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_ELEMENT);
        }

        return tokenStack.peek().getAttributeValue(index);
    }

    @Override
    public boolean isAttributeSpecified(int index) {
        return false;
    }

    @Override
    public int getNamespaceCount() {
        // declare ns on root element
        if (tokenStack.size() == 1) {
            return 1;
        }

        return 0;
    }

    @Override
    public String getNamespacePrefix(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT
                && stackElement.xmlEvent != XMLStreamConstants.END_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_END_ELEMENT);
        }

        return XJConstants.NS_PREFIX_XJ;
    }

    @Override
    public String getNamespaceURI(int index) {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.START_ELEMENT
                && stackElement.xmlEvent != XMLStreamConstants.END_ELEMENT) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_START_END_ELEMENT);
        }

        return XJConstants.NS_XJ;
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException(XJConstants.UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE);
    }

    @Override
    public int getEventType() {
        if (eof) {
            return XMLStreamConstants.END_DOCUMENT;
        }

        if (tokenStack.isEmpty()) {
            return XMLStreamConstants.START_DOCUMENT;
        }

        return tokenStack.peek().xmlEvent;
    }

    @Override
    public String getText() {
        return new String(getTextCharacters());
    }

    @Override
    public char[] getTextCharacters() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null || stackElement.xmlEvent != XMLStreamConstants.CHARACTERS) {
            throw new IllegalStateException(ERROR_MSG_NOT_IN_CHARACTERS);
        }

        try {
            setXmlText(stackElement, jsonParser);
            return stackElement.value;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) {
        final char[] text = getTextCharacters();
        System.arraycopy(text, sourceStart, target, targetStart, length);

        return sourceStart + length;
    }

    @Override
    public int getTextStart() {
        // always starts at 0 because we normalized the text in setXmlText();
        return 0;
    }

    @Override
    public int getTextLength() {
        final StackElement stackElement = tokenStack.peek();

        try {
            ObjectHelper.notNull(stackElement, "stackElement");
            setXmlText(stackElement, jsonParser);
            return stackElement.value.length;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setXmlText(StackElement stackElement, JsonParser jsonParser) throws IOException {
        if (stackElement.value == null) {
            stackElement.value
                    = toXmlString(jsonParser.getTextCharacters(), jsonParser.getTextOffset(), jsonParser.getTextLength());
        }
    }

    @Override
    public String getEncoding() {
        return null;
    }

    @Override
    public boolean hasText() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null) {
            return false;
        }

        return stackElement.xmlEvent == XMLStreamConstants.CHARACTERS;
    }

    @Override
    public Location getLocation() {
        return LOCATION;
    }

    @Override
    public QName getName() {
        return new QName("object");
    }

    @Override
    public String getLocalName() {
        return "object";
    }

    @Override
    public boolean hasName() {
        final StackElement stackElement = tokenStack.peek();
        if (stackElement == null) {
            return false;
        }

        return stackElement.xmlEvent == XMLStreamConstants.START_ELEMENT
                || stackElement.xmlEvent == XMLStreamConstants.END_ELEMENT;
    }

    @Override
    public String getNamespaceURI() {
        return null;
    }

    @Override
    public String getPrefix() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean isStandalone() {
        return false;
    }

    @Override
    public boolean standaloneSet() {
        return false;
    }

    @Override
    public String getCharacterEncodingScheme() {
        return null;
    }

    @Override
    public String getPITarget() {
        return null;
    }

    @Override
    public String getPIData() {
        return null;
    }

    private String toXmlString(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        final char[] chars = input.toCharArray();
        return new String(toXmlString(chars, 0, chars.length));
    }

    private char[] toXmlString(char[] input, int offset, int length) {
        if (length == 0) {
            return new char[0];
        }

        char[] res = new char[length];
        int copied = 0;

        for (int i = offset; i < (offset + length); i++) {
            final char cur = input[i];
            if (cur < 9 || cur > 10 && cur < 13 || cur > 13 && cur < 32) { // non valid xml characters
                continue;
            }

            res[copied++] = cur;
        }

        return Arrays.copyOfRange(res, 0, copied);
    }

    /**
     * Class that represents an element on the stack.
     */
    private static class StackElement {

        private final JsonToken jsonToken;
        private final String name;
        private final List<QName> attributes;
        private int xmlEvent;
        private char[] value;

        StackElement(JsonToken jsonToken, String name) {
            this.jsonToken = jsonToken;
            this.name = name;

            this.attributes = new ArrayList<>(2);

            if (name != null) {
                final QName nameAttribute = new QName(XJConstants.NS_XJ, XJConstants.TYPE_HINT_NAME, XJConstants.NS_PREFIX_XJ);
                attributes.add(nameAttribute);
            }

            final QName typeAttribute = new QName(XJConstants.NS_XJ, XJConstants.TYPE_HINT_TYPE, XJConstants.NS_PREFIX_XJ);
            attributes.add(typeAttribute);
        }

        int getAttributeCount() {
            return attributes.size();
        }

        QName getAttribute(int idx) {
            return attributes.get(idx);
        }

        String getAttributeValue(int idx) {
            final QName attribute = getAttribute(idx);
            switch (attribute.getLocalPart()) {
                case XJConstants.TYPE_HINT_NAME:
                    return this.name;
                case XJConstants.TYPE_HINT_TYPE:
                    return XJConstants.JSONTYPE_TYPE_MAP.get(this.jsonToken);
                default:
                    throw new IllegalArgumentException("Unknown attribute " + attribute.getLocalPart());
            }
        }

        @Override
        public String toString() {
            return "StackElement{"
                   + "jsonToken=" + jsonToken
                   + ", name='" + name + '\''
                   + ", xmlEvent=" + xmlEvent
                   + ", value=" + Arrays.toString(value)
                   + ", attributes=" + attributes
                   + '}';
        }
    }
}
