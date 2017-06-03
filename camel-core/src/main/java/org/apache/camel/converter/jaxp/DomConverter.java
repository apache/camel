/**
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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Converts from some DOM types to Java types
 *
 * @version 
 */
@Converter
public final class DomConverter {
    private final XmlConverter xml;

    public DomConverter() {
        xml = new XmlConverter();
    }

    @Converter
    public String toString(NodeList nodeList, Exchange exchange) throws TransformerException {
        // converting NodeList to String is more tricky
        // sometimes the NodeList is a Node which we can then leverage
        // the XML converter to turn into XML incl. tags

        StringBuilder buffer = new StringBuilder();

        // use XML converter at first since it preserves tag names
        boolean found = false;
        if (nodeList instanceof Node) {
            Node node = (Node) nodeList;
            String s = toString(node, exchange);
            if (ObjectHelper.isNotEmpty(s)) {
                found = true;
                buffer.append(s);
            }
        } else {
            // use XML converter at first since it preserves tag names
            int size = nodeList.getLength();
            for (int i = 0; i < size; i++) {
                Node node = nodeList.item(i);
                String s = toString(node, exchange);
                if (ObjectHelper.isNotEmpty(s)) {
                    found = true;
                    buffer.append(s);
                }
            }
        }

        // and eventually we must fallback to append without tags, such as when you have
        // used an xpath to select an attribute or text() or something
        if (!found) {
            append(buffer, nodeList);
        }

        return buffer.toString();
    }
    
    private String toString(Node node, Exchange exchange) throws TransformerException {
        String s;
        if (node instanceof Text) {
            Text textnode = (Text) node;
            
            StringBuilder b = new StringBuilder();
            b.append(textnode.getNodeValue());
            textnode = (Text) textnode.getNextSibling();
            while (textnode != null) {
                b.append(textnode.getNodeValue());
                textnode = (Text) textnode.getNextSibling();
            }
            s = b.toString();
        } else {
            s = xml.toString(node, exchange);
            
        }
        return s;
    }

    @Converter
    public static Integer toInteger(NodeList nodeList) {
        StringBuilder buffer = new StringBuilder();
        append(buffer, nodeList);
        String s = buffer.toString();
        return Integer.valueOf(s);
    }

    @Converter
    public static Long toLong(NodeList nodeList) {
        StringBuilder buffer = new StringBuilder();
        append(buffer, nodeList);
        String s = buffer.toString();
        return Long.valueOf(s);
    }

    @Converter
    public static List<?> toList(NodeList nodeList) {
        List<Object> answer = new ArrayList<Object>();
        Iterator<Object> it = ObjectHelper.createIterator(nodeList);
        while (it.hasNext()) {
            answer.add(it.next());
        }
        return answer;
    }

    @Converter
    public InputStream toInputStream(NodeList nodeList, Exchange exchange) throws TransformerException, UnsupportedEncodingException {
        return new ByteArrayInputStream(toByteArray(nodeList, exchange));
    }

    @Converter
    public byte[] toByteArray(NodeList nodeList, Exchange exchange) throws TransformerException, UnsupportedEncodingException {
        String data = toString(nodeList, exchange);
        return data.getBytes(IOHelper.getCharsetName(exchange));
    }

    private static void append(StringBuilder buffer, NodeList nodeList) {
        int size = nodeList.getLength();
        for (int i = 0; i < size; i++) {
            append(buffer, nodeList.item(i));
        }
    }

    private static void append(StringBuilder buffer, Node node) {
        if (node instanceof Text) {
            Text text = (Text) node;
            buffer.append(text.getTextContent());
        } else if (node instanceof Attr) {
            Attr attribute = (Attr) node;
            buffer.append(attribute.getTextContent());
        } else if (node instanceof Element) {
            Element element = (Element) node;
            append(buffer, element.getChildNodes());
        }
    }
}
