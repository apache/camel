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
package org.apache.camel.component.http4;

import java.util.Locale;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;

/**
 * To convert between camel-http4 and camel-http-common for the http methods enums
 */
@Converter
public final class HttpMethodsConverter {

    private HttpMethodsConverter() {
    }

    @Converter
    public static HttpMethods toHttp4Methods(org.apache.camel.http.common.HttpMethods method, Exchange exchange) throws Exception {
        String name = method.name();
        name = name.toUpperCase(Locale.ENGLISH);
        return HttpMethods.valueOf(name);
    }

}