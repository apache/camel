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

package org.apache.camel.xml.io.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Generic information about XML Stream to make later, full parsing easier (or unnecessary if the stream is not
 * recognized for example).
 * </p>
 */
public class XmlStreamInfo {

    /** Indication that there's some critical problem with the stream and it should not be handled normally */
    Throwable problem;

    String rootElementName;
    String rootElementNamespace;

    /** Prefix to namespace mapping. default prefix is available as empty String (and not as null) */
    final Map<String, String> namespaceMapping = new HashMap<>();

    /**
     * Attributes of the root element. Keys are full qualified names of the attributes and each attribute may be
     * available as two keys: {@code prefix:localName} or {@code {namespaceURI}localName}
     */
    final Map<String, String> attributes = new HashMap<>();

    /**
     * Trimmed and unparsed lines starting with Camel-recognized modeline markers (now: {@code camel-k:}).
     */
    final List<String> modelines = new ArrayList<>();

    public boolean isValid() {
        return problem == null;
    }

    public Throwable getProblem() {
        return problem;
    }

    public void setProblem(Throwable problem) {
        this.problem = problem;
    }

    public String getRootElementName() {
        return rootElementName;
    }

    public String getRootElementNamespace() {
        return rootElementNamespace;
    }

    public Map<String, String> getNamespaces() {
        return namespaceMapping;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public List<String> getModelines() {
        return modelines;
    }

}
