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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.CastUtils;

/**
 * An implementation of {@link NamespaceContext} which uses a simple Map where the keys are the prefixes and the values
 * are the URIs
 */
public class DefaultNamespaceContext implements NamespaceContext, NamespaceAware {

    private final Map<String, String> map;
    private final NamespaceContext parent;

    public DefaultNamespaceContext() {
        this(XPathFactory.newInstance());
    }

    public DefaultNamespaceContext(XPathFactory factory) {
        this.parent = factory.newXPath().getNamespaceContext();
        this.map = new HashMap<>();
    }

    public DefaultNamespaceContext(NamespaceContext parent, Map<String, String> map) {
        this.parent = parent;
        this.map = map;
    }

    /**
     * A helper method to make it easy to create newly populated instances
     */
    public DefaultNamespaceContext add(String prefix, String uri) {
        map.put(prefix, uri);
        return this;
    }

    @Override
    public String getNamespaceURI(String prefix) {
        String answer = map.get(prefix);
        if (answer == null && parent != null) {
            return parent.getNamespaceURI(prefix);
        }
        return answer;
    }

    @Override
    public String getPrefix(String namespaceURI) {
        for (Entry<String, String> entry : map.entrySet()) {
            if (namespaceURI.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        if (parent != null) {
            return parent.getPrefix(namespaceURI);
        }
        return null;
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
        Set<String> set = new HashSet<>();
        for (Entry<String, String> entry : map.entrySet()) {
            if (namespaceURI.equals(entry.getValue())) {
                set.add(entry.getKey());
            }
        }
        if (parent != null) {
            Iterator<String> iter = CastUtils.cast(parent.getPrefixes(namespaceURI));
            while (iter.hasNext()) {
                set.add(iter.next());
            }
        }
        return set.iterator();
    }

    @Override
    public void setNamespaces(Map<String, String> namespaces) {
        map.putAll(namespaces);
    }

    @Override
    public Map<String, String> getNamespaces() {
        return map;
    }

    /**
     * toString() implementation that outputs the namespace mappings with the following format: "[me: {prefix -> value},
     * {prefix -> value}], [parent: {prefix -> value}, {prefix -> value}]. Recurses up the parent's chain.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[me: ");
        for (Entry<String, String> nsEntry : map.entrySet()) {
            sb.append('{').append(nsEntry.getKey()).append(" -> ").append(nsEntry.getValue()).append("},");
        }
        if (!map.isEmpty()) {
            // remove the last comma
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");

        // Get the parent's namespace mappings
        if (parent != null) {
            sb.append(", [parent: ");
            sb.append(parent.toString());
            sb.append("]");
        }
        return sb.toString();
    }

}
