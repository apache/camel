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

    @Metadata(label = "producer", description = "The query parameters for the rest call to be used", javaType = "String")
    public static final String REST_HTTP_QUERY = Exchange.REST_HTTP_QUERY;
    @Metadata(label = "producer", description = "The http uri for the rest call to be used", javaType = "String")
    public static final String REST_HTTP_URI = Exchange.REST_HTTP_URI;
    @Metadata(label = "producer", description = "The method should be in upper case", javaType = "String")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(label = "producer",
              description = "The media type such as: 'text/xml', or 'application/json' this REST service returns.",
              javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(label = "producer",
              description = "The media type such as: 'text/xml', or 'application/json' this REST service accepts.",
              javaType = "String")
    public static final String ACCEPT = "Accept";
    @Metadata(label = "producer", description = "The http response code", javaType = "Integer")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;

    private RestConstants() {

    }
}
