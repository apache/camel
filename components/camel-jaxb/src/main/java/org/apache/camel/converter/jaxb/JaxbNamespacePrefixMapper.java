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
package org.apache.camel.converter.jaxb;

import java.util.Map;

/**
 * A prefix mapper for namespaces to control namespaces during JAXB marshalling.
 */
public interface JaxbNamespacePrefixMapper {

    /**
     * JAXB requires the mapper to be registered as a property on the {@link javax.xml.bind.JAXBContext}.
     */
    String getRegistrationKey();

    /**
     * Sets the namespace prefix mapping.
     * <p/>
     * The key is the namespace, the value is the prefix to use.
     *
     * @param namespaces  namespace mappings
     */
    void setNamespaces(Map<String, String> namespaces);

    /**
     * Used by JAXB to obtain the preferred prefix.
     */
    String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix);

}
