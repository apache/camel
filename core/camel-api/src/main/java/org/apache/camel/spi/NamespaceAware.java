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
package org.apache.camel.spi;

import java.util.Map;

/**
 * Represents an object which is aware of the namespaces in which its used such as
 * XPath and XQuery type expressions so that the current namespace context can be injected
 */
public interface NamespaceAware {

    /**
     * Injects the XML Namespaces of prefix -> uri mappings
     *
     * @param namespaces the XML namespaces with the key of prefixes and the value the URIs
     */
    void setNamespaces(Map<String, String> namespaces);

    /**
     * Gets the XML Namespaces
     */
    Map<String, String> getNamespaces();
}
