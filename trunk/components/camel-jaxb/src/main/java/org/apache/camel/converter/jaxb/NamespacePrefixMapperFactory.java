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

import org.apache.camel.CamelContext;

/**
 * Factory for creating {@link JaxbNamespacePrefixMapper} which supports various JAXB-RI implementations.
 */
public final class NamespacePrefixMapperFactory {

    private static final String SUN_JAXB_21_MAPPER = "org.apache.camel.converter.jaxb.mapper.SunJaxb21NamespacePrefixMapper";

    private NamespacePrefixMapperFactory() {
    }

    static JaxbNamespacePrefixMapper newNamespacePrefixMapper(CamelContext camelContext, Map<String, String> namespaces) {

        // try to load the Sun JAXB 2.1 based
        Class<?> clazz = camelContext.getClassResolver().resolveClass(SUN_JAXB_21_MAPPER);
        if (clazz != null) {
            JaxbNamespacePrefixMapper mapper = (JaxbNamespacePrefixMapper) camelContext.getInjector().newInstance(clazz);
            mapper.setNamespaces(namespaces);
            return mapper;
        }

        throw new IllegalStateException("Cannot load CamelNamespacePrefixMapper class");
    }
}
