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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.apache.camel.Converter;


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
        StringBuffer buffer = new StringBuffer();
        append(buffer, nodeList);
        return buffer.toString();
    }

/*
    @Converter
    public static String toString(Node node) {
        StringBuffer buffer = new StringBuffer();
        append(buffer, node);
        return buffer.toString();
    }
*/

    protected static void append(StringBuffer buffer, NodeList nodeList) {
        int size = nodeList.getLength();
        for (int i = 0; i < size; i++) {
            append(buffer, nodeList.item(i));
        }
    }

    protected static void append(StringBuffer buffer, Node node) {
        if (node instanceof Text) {
            Text text = (Text) node;
            buffer.append(text.getTextContent());
        } else if (node instanceof Attr) {
            Attr attribute = (Attr) node;
            buffer.append(attribute.getTextContent());

        } else if (node instanceof Element) {
            Element element = (Element) node;
            append(buffer, element.getChildNodes());
        } else if (node instanceof Document) {
            Document doc = (Document) node;
            append(buffer, doc.getChildNodes());
        }
    }
}
