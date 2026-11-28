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
package org.apache.camel.component.rest;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class RestConstants {

    @Metadata(label = "producer", description = "The query string of the request. The producer sets it from the queryParameters"
                                                + " option, with the {name} values filled in. The HTTP producer uses it in"
                                                + " place of the query of its endpoint.",
              javaType = "String")
    public static final String REST_HTTP_QUERY = Exchange.REST_HTTP_QUERY;
    @Metadata(label = "producer", description = "The URI of the request, with the path parameters filled in. The producer sets"
                                                + " it when it fills a path parameter from a header or a variable, and then"
                                                + " removes the CamelHttpPath header. The HTTP producer uses it in place of"
                                                + " the URI of its endpoint.",
              javaType = "String")
    public static final String REST_HTTP_URI = Exchange.REST_HTTP_URI;
    @Metadata(label = "producer", description = "The HTTP method of the request, in upper case. The producer sets it from the"
                                                + " method of the endpoint, and replaces any value that the message has.",
              javaType = "String")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(label = "producer",
              description = "The media type of the request body. The producer sets it to the produces option when"
                            + " the message has no Content-Type header. With binding, the producer reads it to choose"
                            + " between JSON and XML.",
              javaType = "String", important = true)
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(label = "producer",
              description = "The media types that the response may have. The producer sets it to the consumes option"
                            + " when the message has no Accept header.",
              javaType = "String")
    public static final String ACCEPT = "Accept";
    @Metadata(label = "producer", description = "The HTTP status code of the response, which the HTTP producer sets. With"
                                                + " binding, the producer does not read a response with a code of 300 or more"
                                                + " into outType, unless skipBindingOnErrorCode of the rest configuration is"
                                                + " false.",
              javaType = "Integer", important = true)
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;

    private RestConstants() {

    }
}
