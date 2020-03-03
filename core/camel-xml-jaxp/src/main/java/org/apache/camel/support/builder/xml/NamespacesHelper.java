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
package org.apache.camel.support.builder.xml;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.support.builder.Namespaces;

public final class NamespacesHelper {

    private NamespacesHelper() {       
    }

    public static Namespaces namespaces(Element element) {
        Namespaces namespaces = new Namespaces();
        add(namespaces, element);
        return namespaces;
    }

    private static void add(Namespaces namespaces, Element element) {
        // let's set the parent first in case we overload a prefix here
        Node parentNode = element.getParentNode();
        if (parentNode instanceof org.w3c.dom.Element) {
            add(namespaces, (Element) parentNode);
        }
        NamedNodeMap attributes = element.getAttributes();
        int size = attributes.getLength();
        for (int i = 0; i < size; i++) {
            Attr node = (Attr) attributes.item(i);
            String name = node.getName();
            if (name.startsWith("xmlns:")) {
                String prefix = name.substring("xmlns:".length());
                String uri = node.getValue();
                namespaces.add(prefix, uri);
            }
        }
    }

}
