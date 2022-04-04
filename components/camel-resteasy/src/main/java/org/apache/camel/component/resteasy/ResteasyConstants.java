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
package org.apache.camel.component.resteasy;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Constants used in the Resteasy component
 *
 */
public final class ResteasyConstants {
    @Metadata(label = "producer", description = "The resteasy method to process the request", javaType = "String")
    public static final String RESTEASY_PROXY_METHOD = "CamelResteasyProxyMethod";
    @Metadata(label = "producer", description = "The proxy method params", javaType = "java.util.ArrayList")
    public static final String RESTEASY_PROXY_METHOD_PARAMS = "CamelResteasyProxyMethodArgs";
    @Metadata(label = "producer", description = "The username", javaType = "String")
    public static final String RESTEASY_USERNAME = "CamelResteasyLogin";
    @Metadata(label = "producer", description = "The password", javaType = "String")
    public static final String RESTEASY_PASSWORD = "CamelResteasyPassword";
    @Metadata(description = "The context path", javaType = "String")
    public static final String RESTEASY_CONTEXT_PATH = "CamelResteasyContextPath";
    public static final String RESTEASY_RESPONSE = "CamelResteasyResponse";
    @Metadata(label = "producer", description = "The resteasy method to process the request", javaType = "String")
    public static final String RESTEASY_HTTP_METHOD = "CamelResteasyHttpMethod";
    @Metadata(description = "The http request", javaType = "String")
    public static final String RESTEASY_HTTP_REQUEST = "CamelResteasyHttpRequest";
    @Metadata(label = "producer", description = "The proxy client exception", javaType = "Exception")
    public static final String RESTEASY_PROXY_PRODUCER_EXCEPTION = "CamelResteasyProxyProducerException";
    @Metadata(label = "producer", description = "The http query", javaType = "String")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(label = "producer", description = "The content type", javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "The http path", javaType = "String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;

    /**
     * Utility classes should not have a public constructor.
     */
    private ResteasyConstants() {
    }
}
