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
package org.apache.camel.language.xpath;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.camel.support.builder.xml.XMLConverterHelper;

/**
 * A simple thread-safe {@link NodeList} that is used by XPathBuilder
 * to return thread-safe {@link NodeList} instances as its result.
 * <p/>
 * This is needed to ensure that end users do not hit any concurrency issues while working
 * with xpath expressions using built-in from the JDK or via camel-saxon.
 */
class ThreadSafeNodeList implements NodeList {

    private final List<Node> list = new ArrayList<>();

    public ThreadSafeNodeList(NodeList source) throws Exception {
        init(source);
    }

    @Override
    public Node item(int index) {
        return list.get(index);
    }

    @Override
    public int getLength() {
        return list.size();
    }

    private void init(NodeList source) throws Exception {
        for (int i = 0; i < source.getLength(); i++) {
            Node node = source.item(i);
            if (node != null) {
                // import node must not occur concurrent on the same node (must be its owner)
                // so we need to synchronize on it
                synchronized (node.getOwnerDocument()) {
                    Document doc = new XMLConverterHelper().createDocument();
                    // import node must not occur concurrent on the same node (must be its owner)
                    // so we need to synchronize on it
                    synchronized (node.getOwnerDocument()) {
                        Node clone = doc.importNode(node, true);
                        if (clone instanceof Text) {
                            // basic text node then add as-is
                            list.add(clone);
                        } else {
                            // more complex node, then add as child (yes its a bit weird but this is working)
                            doc.appendChild(clone);
                            list.add(doc.getChildNodes().item(0));
                        }
                    }
                }
            }
        }
    }

}
