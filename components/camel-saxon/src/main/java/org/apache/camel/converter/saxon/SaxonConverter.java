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
package org.apache.camel.converter.saxon;

import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DOMNodeList;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.Type;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;

@Converter(generateLoader = true)
public final class SaxonConverter {

    private SaxonConverter() {
    }

    @Converter
    public static Document toDOMDocument(TinyElementImpl node) throws XPathException {
        return toDOMDocument((NodeInfo) node);
    }

    @Converter
    public static Document toDOMDocument(TinyDocumentImpl node) throws XPathException {
        return toDOMDocument((NodeInfo) node);
    }

    @Converter
    public static Document toDOMDocument(NodeInfo node) throws XPathException {
        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
                // DOCUMENT type nodes can be wrapped directly
                return (Document) NodeOverNodeInfo.wrap(node);
            case Type.ELEMENT:
                // ELEMENT nodes need to build a new DocumentInfo before wrapping
                Configuration config = node.getConfiguration();
                TreeInfo documentInfo = config.buildDocumentTree(node);
                return (Document) NodeOverNodeInfo.wrap(documentInfo.getRootNode());
            default:
                return null;
        }
    }

    @Converter
    public static Node toDOMNode(TinyDocumentImpl node) {
        return toDOMNode((NodeInfo) node);
    }

    @Converter
    public static Node toDOMNode(NodeInfo node) {
        return NodeOverNodeInfo.wrap(node);
    }

    @Converter
    public static DOMSource toDOMSourceFromNodeInfo(TinyDocumentImpl nodeInfo) {
        return new DOMSource(toDOMNode(nodeInfo));
    }

    @Converter
    public static DOMSource toDOMSourceFromNodeInfo(NodeInfo nodeInfo) {
        return new DOMSource(toDOMNode(nodeInfo));
    }

    @Converter
    public static NodeList toDOMNodeList(List<? extends NodeInfo> nodeList) {
        List<Node> domNodeList = new LinkedList<>();
        if (nodeList != null) {
            for (NodeInfo ni : nodeList) {
                domNodeList.add(NodeOverNodeInfo.wrap(ni));
            }
        }
        return new DOMNodeList(domNodeList);
    }

    @Converter(fallback = true)
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        if (NodeInfo.class.isAssignableFrom(value.getClass())) {
            // use a fallback type converter so we can convert the embedded body if the value is NodeInfo
            NodeInfo ni = (NodeInfo) value;
            // first try to find a Converter for Node
            TypeConverter tc = registry.lookup(type, Node.class);
            if (tc != null) {
                Node node = NodeOverNodeInfo.wrap(ni);
                return tc.convertTo(type, exchange, node);
            }
            // if this does not exist we can also try NodeList (there are some type converters for that) as
            // the default Xerces Node implementation also implements NodeList.
            tc = registry.lookup(type, NodeList.class);
            if (tc != null) {
                List<NodeInfo> nil = new LinkedList<>();
                nil.add(ni);
                return tc.convertTo(type, exchange, toDOMNodeList(nil));
            }
        } else if (List.class.isAssignableFrom(value.getClass())) {
            TypeConverter tc = registry.lookup(type, NodeList.class);
            if (tc != null) {
                List<NodeInfo> lion = new LinkedList<>();
                for (Object o : (List<?>) value) {
                    if (o instanceof NodeInfo) {
                        lion.add((NodeInfo) o);
                    }
                }
                if (!lion.isEmpty()) {
                    NodeList nl = toDOMNodeList(lion);
                    return tc.convertTo(type, exchange, nl);
                }
            }
        } else if (NodeOverNodeInfo.class.isAssignableFrom(value.getClass())) {
            // NodeOverNode info is a read-only Node implementation from Saxon. In contrast to the JDK
            // com.sun.org.apache.xerces.internal.dom.NodeImpl class it does not implement NodeList, but
            // many Camel type converters are based on that interface. Therefore we convert to NodeList and
            // try type conversion in the fallback type converter.
            TypeConverter tc = registry.lookup(type, NodeList.class);
            if (tc != null) {
                List<Node> domNodeList = new LinkedList<>();
                domNodeList.add((NodeOverNodeInfo) value);
                return tc.convertTo(type, exchange, new DOMNodeList(domNodeList));
            }
        }

        return null;
    }
}
