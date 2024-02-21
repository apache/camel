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
package org.apache.camel.component.vertx.http;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class VertxHttpConstants {

    public static final String CONTENT_TYPE_JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    public static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";

    @Metadata(description = "The http method", javaType = "io.vertx.core.http.HttpMethod")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(description = "The HTTP response code from the external server.", javaType = "Integer")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The HTTP response text from the external server.", javaType = "String")
    public static final String HTTP_RESPONSE_TEXT = Exchange.HTTP_RESPONSE_TEXT;
    @Metadata(description = "The HTTP content type. Is set on both the IN and OUT message to provide\n" +
                            "a content type, such as `text/html`.",
              javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "URI parameters. Will override existing URI parameters set directly on\n" +
                            "the endpoint.",
              javaType = "String")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(description = "URI to call. Will override the existing URI set directly on the endpoint.\n" +
                            "This URI is the URI of the http server to call. Its not the same as the\n" +
                            "Camel endpoint URI, where you can configure endpoint options such as\n" +
                            "security etc. This header does not support that, its only the URI of the\n" +
                            "http server.",
              javaType = "String")
    public static final String HTTP_URI = Exchange.HTTP_URI;
    @Metadata(description = "Request URI's path, the header will be used to build the request URI\n" +
                            "with the HTTP_URI.",
              javaType = "String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The HTTP content encoding. Is set to provide a content encoding, such as `gzip`.",
              javaType = "String")
    public static final String CONTENT_ENCODING = Exchange.CONTENT_ENCODING;

    private VertxHttpConstants() {
    }
}
