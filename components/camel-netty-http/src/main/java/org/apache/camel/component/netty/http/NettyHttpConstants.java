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
package org.apache.camel.component.netty.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.spi.Metadata;

/**
 * Netty HTTP constants.
 */
public final class NettyHttpConstants {

    public static final String CONTENT_TYPE_JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    public static final String CONTENT_TYPE_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    @Deprecated
    public static final String HTTP_RESPONSE_TEXT = Exchange.HTTP_RESPONSE_TEXT;
    @Metadata(description = "If the user was authenticated using HTTP Basic then this header is added\n" +
                            "with the value `Basic`.",
              javaType = "String")
    public static final String HTTP_AUTHENTICATION = "CamelHttpAuthentication";

    @Metadata(description = "To set the content-type of the HTTP body. For example:\n" +
                            "`text/plain; charset=\"UTF-8\"`.",
              javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "The value of the HTTP header connection to use.", javaType = "String")
    public static final String CONNECTION = HttpHeaderNames.CONNECTION.toString();
    @Metadata(description = "Indicates whether the channel should be closed after complete.", javaType = "Boolean")
    public static final String NETTY_CLOSE_CHANNEL_WHEN_COMPLETE = NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE;
    @Metadata(description = "Allows to set the HTTP Status code to use. By default 200 is used for\n" +
                            "success, and 500 for failure.",
              javaType = "Integer")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The version of the HTTP protocol.", javaType = "String", defaultValue = "HTTP/1.1")
    public static final String HTTP_PROTOCOL_VERSION = Exchange.HTTP_PROTOCOL_VERSION;
    @Metadata(description = "The HTTP method used, such as GET, POST, TRACE etc.", javaType = "String", defaultValue = "GET")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(description = "Any query parameters, such as `foo=bar&beer=yes`", javaType = "String")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(description = "Allows to provide URI context-path and query parameters as a `String`\n" +
                            "value that overrides the endpoint configuration. This allows to reuse\n" +
                            "the same producer for calling same remote http server, but using a\n" +
                            "dynamic context-path and query parameters.",
              javaType = "String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "Any query parameters, such as `foo=bar&beer=yes`. Stored in the raw\n" +
                            "form, as they arrived to the consumer (i.e. before URL decoding).",
              javaType = "String")
    public static final String HTTP_RAW_QUERY = Exchange.HTTP_RAW_QUERY;
    @Metadata(description = "The URL including protocol, host and port, etc: \n" +
                            "`\\http://0.0.0.0:8080/myapp`.",
              javaType = "String")
    public static final String HTTP_URL = Exchange.HTTP_URL;
    @Metadata(description = "The charset from the content-type header.", javaType = "String")
    public static final String HTTP_CHARACTER_ENCODING = Exchange.HTTP_CHARACTER_ENCODING;
    @Metadata(description = "The URI without protocol, host and port, etc:\n" +
                            "`/myapp`.",
              javaType = "String")
    public static final String HTTP_URI = Exchange.HTTP_URI;

    private NettyHttpConstants() {
    }
}
