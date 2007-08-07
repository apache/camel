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
package org.apache.camel.builder.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A helper class for creating namespaces which can then be used to create XPath expressions
 *
 * @version $Revision: 1.1 $
 */
public class NamespaceBuilder {
    private Map<String, String> namespaces = new HashMap<String, String>();

    public static NamespaceBuilder namespaceContext() {
        return new NamespaceBuilder();
    }

    public static NamespaceBuilder namespaceContext(String prefix, String uri) {
        return new NamespaceBuilder().namespace(prefix, uri);
    }

    public NamespaceBuilder namespace(String prefix, String uri) {
        namespaces.put(prefix, uri);
        return this;
    }

    /**
     * Creates a new XPath expression using the current namespaces
     *
     * @param xpath the XPath expression
     * @return a new XPath expression
     */
    public XPathBuilder xpath(String xpath) {
        XPathBuilder answer = XPathBuilder.xpath(xpath);
        Set<Map.Entry<String, String>> entries = namespaces.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            answer.namespace(entry.getKey(), entry.getValue());
        }
        return answer;
    }
}
