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
package org.apache.camel.converter.jaxb.mapper;

import java.util.Map;

import org.apache.camel.converter.jaxb.JaxbNamespacePrefixMapper;
import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

/**
 * A namespace prefix mapper which overrides SUN JAXB-RI to allow Camel to control the prefixes
 */
public class DefaultNamespacePrefixMapper extends NamespacePrefixMapper implements JaxbNamespacePrefixMapper {

    private Map<String, String> namespaces;

    @Override
    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public String getRegistrationKey() {
        return "org.glassfish.jaxb.namespacePrefixMapper";
    }

    @Override
    public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
        if (namespaces != null) {
            String prefix = namespaces.get(namespaceUri);
            if (prefix != null) {
                return prefix;
            }
        }
        return suggestion;
    }

}
