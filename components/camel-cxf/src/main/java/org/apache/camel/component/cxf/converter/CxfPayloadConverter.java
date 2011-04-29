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
package org.apache.camel.component.cxf.converter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.spi.TypeConverterRegistry;

@Converter
public final class CxfPayloadConverter {

    private CxfPayloadConverter() {
        // Helper class
    }

    @Converter
    public static <T> CxfPayload<T> documentToCxfPayload(Document doc, Exchange exchange) {
        return elementToCxfPayload(doc.getDocumentElement(), exchange);
    }

    @Converter
    public static <T> CxfPayload<T> elementToCxfPayload(Element element, Exchange exchange) {
        List<T> headers = new ArrayList<T>();
        List<Element> body = new ArrayList<Element>();
        body.add(element);
        return new CxfPayload<T>(headers, body);
    }

    @Converter
    public static <T> CxfPayload<T> nodeListToCxfPayload(NodeList nodeList, Exchange exchange) {
        List<T> headers = new ArrayList<T>();
        List<Element> body = new ArrayList<Element>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            // add all nodes to the body that are elements
            if (Element.class.isAssignableFrom(node.getClass())) {
                body.add((Element) node);
            }
        }
        return new CxfPayload<T>(headers, body);
    }

    @Converter
    public static <T> NodeList cxfPayloadToNodeList(CxfPayload<T> payload, Exchange exchange) {
        return new NodeListWrapper(payload.getBody());
    }

    @SuppressWarnings("unchecked")
    @FallbackConverter
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // use fallback type converter, so we can probably convert into
        // CxfPayloads from other types
        if (type.isAssignableFrom(CxfPayload.class)) {
            TypeConverter tc = registry.lookup(NodeList.class, value.getClass());
            if (tc != null) {
                NodeList nodeList = tc.convertTo(NodeList.class, exchange, value);
                return (T) nodeListToCxfPayload(nodeList, exchange);
            }
            tc = registry.lookup(Document.class, value.getClass());
            if (tc != null) {
                Document document = tc.convertTo(Document.class, exchange, value);
                return (T) documentToCxfPayload(document, exchange);
            }
            // maybe we can convert via an InputStream
            CxfPayload<?> p;
            p = convertVia(InputStream.class, exchange, value, registry);
            if (p != null) {
                return (T) p;
            }
            // String is the converter of last resort
            p = convertVia(String.class, exchange, value, registry);
            if (p != null) {
                return (T) p;
            }
            // no we could not do it currently
            return (T) Void.TYPE;
        }
        // Convert a CxfPayload into something else
        if (CxfPayload.class.isAssignableFrom(value.getClass())) {
            TypeConverter tc = registry.lookup(type, NodeList.class);
            if (tc != null) {
                return tc.convertTo(type, cxfPayloadToNodeList((CxfPayload<?>) value, exchange));
            }
            // we cannot convert a node list, so we try the first item from the
            // node list
            tc = registry.lookup(type, Node.class);
            if (tc != null) {
                NodeList nodeList = cxfPayloadToNodeList((CxfPayload<?>) value, exchange);
                if (nodeList.getLength() > 0) {
                    return tc.convertTo(type, nodeList.item(0));
                } else {
                    // no we could not do it currently
                    return (T) Void.TYPE;
                }
            }
        }
        return null;
    }

    private static <T, V> CxfPayload<T> convertVia(Class<V> via, Exchange exchange, Object value, TypeConverterRegistry registry) {
        TypeConverter tc = registry.lookup(via, value.getClass());
        if (tc != null) {
            TypeConverter tc1 = registry.lookup(Document.class, via);
            if (tc1 != null) {
                V is = tc.convertTo(via, exchange, value);
                Document document = tc1.convertTo(Document.class, exchange, is);
                return documentToCxfPayload(document, exchange);
            }
        }
        return null;
    }
}
