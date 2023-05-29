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
package org.apache.camel.component.cxf.converter;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.cxf.staxutils.StaxSource;
import org.apache.cxf.staxutils.StaxUtils;

import static org.apache.camel.TypeConverter.MISS_VALUE;

@Converter(generateLoader = true)
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
        List<T> headers = new ArrayList<>();
        List<Element> body = new ArrayList<>();
        body.add(element);
        return new CxfPayload<>(headers, body);
    }

    @Converter
    public static <T> CxfPayload<T> nodeListToCxfPayload(NodeList nodeList, Exchange exchange) {
        List<T> headers = new ArrayList<>();
        List<Element> body = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            // add all nodes to the body that are elements
            if (Element.class.isAssignableFrom(node.getClass())) {
                body.add((Element) node);
            }
        }
        return new CxfPayload<>(headers, body);
    }

    @Converter
    public static <T> CxfPayload<T> sourceToCxfPayload(Source src, Exchange exchange) {
        List<T> headers = new ArrayList<>();
        List<Source> body = new ArrayList<>();
        body.add(src);
        return new CxfPayload<>(headers, body, null);
    }

    @Converter
    public static <T> NodeList cxfPayloadToNodeList(CxfPayload<T> payload, Exchange exchange) {
        return new NodeListWrapper(payload.getBody());
    }

    @Converter
    public static <T> Node cxfPayLoadToNode(CxfPayload<T> payload, Exchange exchange) {
        List<Element> payloadBodyElements = payload.getBody();

        if (!payloadBodyElements.isEmpty()) {
            return payloadBodyElements.get(0);
        }
        return null;
    }

    @Converter
    public static <T> Source cxfPayLoadToSource(CxfPayload<T> payload, Exchange exchange) {
        List<Source> payloadBody = payload.getBodySources();

        if (!payloadBody.isEmpty()) {
            return payloadBody.get(0);
        }
        return null;
    }

    @Converter
    public static <T> StreamCache cxfPayLoadToStreamCache(CxfPayload<T> payload, Exchange exchange) {
        return new CachedCxfPayload<>(payload, exchange);
    }

    @SuppressWarnings("unchecked")
    @Converter(fallback = true)
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // use fallback type converter, so we can probably convert into
        // CxfPayloads from other types
        if (type.isAssignableFrom(CxfPayload.class)) {
            try {
                if (!value.getClass().isArray()) {
                    Source src = null;
                    // many of the common format that can have a Source created
                    // directly
                    if (value instanceof InputStream) {
                        src = new StreamSource((InputStream) value);
                    } else if (value instanceof Reader) {
                        src = new StreamSource((Reader) value);
                    } else if (value instanceof String) {
                        src = new StreamSource(new StringReader((String) value));
                    } else if (value instanceof Node) {
                        src = new DOMSource((Node) value);
                    } else if (value instanceof Source) {
                        src = (Source) value;
                    }
                    if (src == null) {
                        // assuming staxsource is preferred, otherwise use the
                        // one preferred
                        TypeConverter tc = registry.lookup(javax.xml.transform.stax.StAXSource.class, value.getClass());
                        if (tc == null) {
                            tc = registry.lookup(Source.class, value.getClass());
                        }
                        if (tc != null) {
                            src = tc.convertTo(Source.class, exchange, value);
                        }
                    }
                    if (src != null) {
                        return (T) sourceToCxfPayload(src, exchange);
                    }
                }
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
            } catch (RuntimeCamelException e) {
                // the internal conversion to XML can throw an exception if the content is not XML
                // ignore this and return MISS_VALUE to indicate that we cannot convert this
                return (T) MISS_VALUE;
            }
            // Let other fallback converter try
            return null;
        }
        // Convert a CxfPayload into something else
        if (CxfPayload.class.isAssignableFrom(value.getClass())) {
            CxfPayload<?> payload = (CxfPayload<?>) value;
            int size = payload.getBodySources().size();
            if (size == 1) {
                if (type.isAssignableFrom(Document.class)) {
                    Source s = payload.getBodySources().get(0);
                    Document d;
                    try {
                        d = StaxUtils.read(s);
                    } catch (XMLStreamException e) {
                        throw new RuntimeException(e);
                    }
                    return type.cast(d);
                }
                // CAMEL-8410 Just make sure we get the Source object directly from the payload body source
                Source s = payload.getBodySources().get(0);
                if (type.isInstance(s)) {
                    return type.cast(s);
                }
                TypeConverter tc = registry.lookup(type, XMLStreamReader.class);
                if (tc != null && (s instanceof StaxSource || s instanceof StAXSource)) {
                    XMLStreamReader r = (s instanceof StAXSource)
                            ? ((StAXSource) s).getXMLStreamReader() : ((StaxSource) s).getXMLStreamReader();
                    if (payload.getNsMap() != null) {
                        r = new DelegatingXMLStreamReader(r, payload.getNsMap());
                    }
                    return tc.convertTo(type, exchange, r);
                }
                tc = registry.lookup(type, Source.class);
                if (tc != null) {
                    XMLStreamReader r = null;
                    if (payload.getNsMap() != null) {
                        if (s instanceof StaxSource) {
                            r = ((StaxSource) s).getXMLStreamReader();
                        } else if (s instanceof StAXSource) {
                            r = ((StAXSource) s).getXMLStreamReader();
                        }
                        if (r != null) {
                            s = new StAXSource(new DelegatingXMLStreamReader(r, payload.getNsMap()));
                        }
                    }
                    return tc.convertTo(type, exchange, s);
                }
            }
            TypeConverter tc = registry.lookup(type, NodeList.class);
            if (tc != null) {
                Object result = tc.convertTo(type, exchange, cxfPayloadToNodeList((CxfPayload<?>) value, exchange));
                if (result == null) {
                    // no we could not do it currently, and we just abort the convert here
                    return (T) MISS_VALUE;
                } else {
                    return (T) result;
                }

            }
            // we cannot convert a node list, so we try the first item from the
            // node list
            tc = registry.lookup(type, Node.class);
            if (tc != null) {
                NodeList nodeList = cxfPayloadToNodeList((CxfPayload<?>) value, exchange);
                if (nodeList.getLength() > 0) {
                    return tc.convertTo(type, exchange, nodeList.item(0));
                } else {
                    // no we could not do it currently
                    return (T) MISS_VALUE;
                }
            } else {
                if (size == 0) {
                    // empty size so we cannot convert
                    return (T) MISS_VALUE;
                }
            }
        }
        return null;
    }

    private static <
            T, V> CxfPayload<T> convertVia(Class<V> via, Exchange exchange, Object value, TypeConverterRegistry registry) {
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
