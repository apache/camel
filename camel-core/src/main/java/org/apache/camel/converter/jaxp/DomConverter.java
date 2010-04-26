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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.camel.Converter;
import org.apache.camel.util.ObjectHelper;


/**
 * Converts from some DOM types to Java types
 *
 * @version $Revision$
 */
@Converter
public final class DomConverter {

    private DomConverter() {
        // Utility Class
    }

    @Converter
    public static String toString(NodeList nodeList) {
        StringBuilder buffer = new StringBuilder();
        append(buffer, nodeList);
        return buffer.toString();
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

    @SuppressWarnings("unchecked")
    @Converter
    public static List toList(NodeList nodeList) {
        List answer = new ArrayList();
        Iterator it = ObjectHelper.createIterator(nodeList);
        while (it.hasNext()) {
            answer.add(it.next());
        }
        return answer;
    }

    @Converter
    public static InputStream toInputStream(NodeList nodeList) {
        return new ByteArrayInputStream(toByteArray(nodeList));
    }

    @Converter
    public static byte[] toByteArray(NodeList nodeList) {
        String data = toString(nodeList);
        return data.getBytes();
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
