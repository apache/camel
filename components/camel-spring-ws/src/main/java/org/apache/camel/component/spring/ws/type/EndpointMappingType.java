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
package org.apache.camel.component.spring.ws.type;

/**
 * Endpoint mappings supported by consumer through uri configuration.
 */
public enum EndpointMappingType {
    ROOT_QNAME("rootqname:"),
    ACTION("action:"),
    TO("to:"),
    SOAP_ACTION("soapaction:"),
    XPATHRESULT("xpathresult:"),
    URI("uri:"),
    URI_PATH("uripath:"),
    BEANNAME("beanname:");

    private String prefix;

    EndpointMappingType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Find {@link EndpointMappingType} that corresponds with the prefix of the
     * given uri. Matching of uri prefix against enum values is case-insensitive
     *
     * @param uri remaining uri part of Spring-WS component
     * @return EndpointMappingType corresponding to uri prefix
     */
    public static EndpointMappingType getTypeFromUriPrefix(String uri) {
        if (uri != null) {
            for (EndpointMappingType type : EndpointMappingType.values()) {
                if (uri.toLowerCase().startsWith(type.getPrefix())) {
                    return type;
                }
            }
        }
        return null;
    }
}