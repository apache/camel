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
package org.apache.camel.support.builder;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.ObjectHelper;

/**
 * A helper class for working with namespaces.
 */
public class Namespaces {
    public static final String DEFAULT_NAMESPACE = "http://camel.apache.org/schema/spring";
    public static final String IN_NAMESPACE = "http://camel.apache.org/xml/in/";
    public static final String OUT_NAMESPACE = "http://camel.apache.org/xml/out/";
    public static final String FUNCTION_NAMESPACE = "http://camel.apache.org/xml/function/";
    public static final String SYSTEM_PROPERTIES_NAMESPACE = "http://camel.apache.org/xml/variables/system-properties";
    public static final String ENVIRONMENT_VARIABLES = "http://camel.apache.org/xml/variables/environment-variables";
    public static final String EXCHANGE_PROPERTY = "http://camel.apache.org/xml/variables/exchange-property";

    private final Map<String, String> namespaces = new HashMap<>();

    /**
     * Creates an empty namespaces object
     */
    public Namespaces() {
    }

    /**
     * Creates a namespace context with a single prefix and URI
     */
    public Namespaces(String prefix, String uri) {
        add(prefix, uri);
    }

    /**
     * Creates a namespace context from prefix to URI mappings in a single string, such as
     * {@code p1=http://acme.com/one,p2=http://acme.com/two}. The mappings are separated by commas or new lines, and the
     * prefix and the URI by the first {@code =}. Whitespace around them is ignored, and an empty string has no
     * mappings. This allows to configure several namespaces from one property, for example a bean referred to by the
     * {@code namespacesRef} of an xpath.
     *
     * @param  mappings                 the mappings, such as {@code p1=uri1,p2=uri2}
     * @throws IllegalArgumentException if a mapping has no prefix or no URI
     */
    public Namespaces(String mappings) {
        if (mappings == null) {
            return;
        }
        for (String mapping : mappings.split("[,\\r\\n]")) {
            String entry = mapping.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            String prefix = separator > 0 ? entry.substring(0, separator).trim() : "";
            String uri = separator > 0 ? entry.substring(separator + 1).trim() : "";
            if (prefix.isEmpty() || uri.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid namespace mapping: " + entry + ". Use prefix=uri, such as p1=http://acme.com/one");
            }
            add(prefix, uri);
        }
    }

    /**
     * Returns true if the given namespaceURI is empty or if it matches the given expected namespace
     */
    public static boolean isMatchingNamespaceOrEmptyNamespace(String namespaceURI, String expectedNamespace) {
        return ObjectHelper.isEmpty(namespaceURI) || namespaceURI.equals(expectedNamespace);
    }

    public Namespaces add(String prefix, String uri) {
        namespaces.put(prefix, uri);
        return this;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    /**
     * Configures the namespace aware object
     */
    public void configure(NamespaceAware namespaceAware) {
        namespaceAware.setNamespaces(getNamespaces());
    }
}
