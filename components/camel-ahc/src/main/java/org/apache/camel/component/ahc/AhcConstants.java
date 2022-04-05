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
package org.apache.camel.component.ahc;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 *
 */
public final class AhcConstants {

    public static final String CONTENT_TYPE_JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    public static final String CONTENT_TYPE_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    @Metadata(description = "The HTTP response code from the external server. Is 200 for OK.",
              javaType = "int")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The Http response status text", javaType = "java.lang.String")
    public static final String HTTP_RESPONSE_TEXT = Exchange.HTTP_RESPONSE_TEXT;
    @Metadata(description = "The content length of the response", javaType = "int")
    public static final String CONTENT_LENGTH = Exchange.CONTENT_LENGTH;
    @Metadata(description = "The http method to execute", javaType = "java.lang.String")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(description = "The HTTP content encoding. Is set on both the IN and OUT message to provide a content encoding, such as `gzip`.",
              javaType = "java.lang.String")
    public static final String CONTENT_ENCODING = Exchange.CONTENT_ENCODING;
    @Metadata(description = "The HTTP content type. Is set on both the IN and OUT message to provide a content type, such as `text/html`",
              javaType = "java.lang.String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "The redirect location", javaType = "java.lang.String")
    public static final String LOCATION = "Location";
    @Metadata(description = "URI to call. Will override existing URI set directly on the endpoint.",
              javaType = "java.lang.String")
    public static final String HTTP_URI = Exchange.HTTP_URI;
    @Metadata(description = "Request URI's path, the header will be used to build the request URI with the HTTP_URI. " +
                            "If the path is start with \"/\", http producer will try to find the relative path based on the Exchange.HTTP_BASE_URI header or the `exchange.getFromEndpoint().getEndpointUri()`.",
              javaType = "java.lang.String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The base of the path to append to the URI", javaType = "java.lang.String")
    public static final String HTTP_BASE_URI = Exchange.HTTP_BASE_URI;
    @Metadata(description = "*Camel 2.11 onwards:* URI parameters. Will override existing URI parameters set directly on the endpoint.",
              javaType = "java.lang.String")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;

    private AhcConstants() {
    }

}
