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

    private Map<String, String> namespaces = new HashMap<>();

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
     * Returns true if the given namespaceURI is empty or if it matches the
     * given expected namespace
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
