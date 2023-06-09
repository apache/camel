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
package org.apache.camel.xml.out;

import java.io.IOException;
import java.io.Writer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.camel.xml.io.XMLWriter;

public class BaseWriter {

    protected final XMLWriter writer;
    protected final Deque<String> namespacesStack = new LinkedList<>();
    protected boolean namespaceWritten;

    public BaseWriter(Writer writer, String namespace) throws IOException {
        this.writer = new XMLWriter(writer);
        this.namespacesStack.push(namespace);
    }

    protected void startElement(String name) throws IOException {
        writer.startElement(name);
        if (!namespaceWritten && namespacesStack.peek() != null) {
            writer.addAttribute("xmlns", namespacesStack.peek());
            namespaceWritten = true;
        }
    }

    protected void startElement(String name, String namespace) throws IOException {
        writer.startElement(name);
        if (!namespacesStack.isEmpty() && !namespace.equals(namespacesStack.peek())) {
            namespacesStack.push(namespace);
            writer.addAttribute("xmlns", namespace);
        }
    }

    protected void startOutputElement(String name) throws IOException {
        startElement(name);
    }

    protected void startExpressionElement(String name) throws IOException {
        startElement(name);
    }

    protected void endExpressionElement(String name) throws IOException {
        writer.endElement(name);
    }

    protected void endElement() throws IOException {
        writer.endElement(null);
    }

    protected void endElement(String namespace) throws IOException {
        endElement();
        if (!namespacesStack.isEmpty() && namespacesStack.peek().equals(namespace)) {
            namespacesStack.pop();
        }
    }

    protected void text(String text) throws IOException {
        writer.writeText(text);
    }

    protected void text(String name, String text) throws IOException {
        writer.writeText(text);
    }

    protected void value(String value) throws IOException {
        writer.writeText(value);
    }

    protected void attribute(String name, Object value) throws IOException {
        if (value != null) {
            writer.addAttribute(name, value.toString());
        }
    }

    protected void domElements(List<Element> elements) throws IOException {
        for (Element e : elements) {
            domElement(e);
        }
    }

    protected void domElement(Element v) throws IOException {
        if (v != null) {
            startElement(v.getTagName(), v.getNamespaceURI());
            NamedNodeMap nnm = v.getAttributes();
            if (nnm != null) {
                for (int i = 0; i < nnm.getLength(); i++) {
                    Attr attr = (Attr) nnm.item(i);
                    attribute(attr.getName(), attr.getValue());
                }
            }
            NodeList children = v.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node item = children.item(i);
                if (item instanceof Element) {
                    domElement((Element) item);
                } else if (item instanceof Text) {
                    text(((Text) item).getWholeText());
                }
            }
            endElement(v.getNamespaceURI());
        }
    }

}
