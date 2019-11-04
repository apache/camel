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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;

public class UndertowRestHeaderFilterStrategy extends UndertowHeaderFilterStrategy {

    private final String templateUri;
    private final String queryParameters;

    public UndertowRestHeaderFilterStrategy(String templateUri, String queryParameters) {
        this.templateUri = templateUri;
        this.queryParameters = queryParameters;
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        boolean answer = super.applyFilterToExternalHeaders(headerName, headerValue, exchange);
        // using rest producer then headers are mapping to uri and query parameters using {key} syntax
        // if there is a match to an existing Camel Message header, then we should filter (=true) this
        // header as its already been mapped by the RestProducer from camel-core, and we do not want
        // the header to included as HTTP header also (eg as duplicate value)
        if (!answer) {
            if (templateUri != null) {
                String token = "{" + headerName + "}";
                if (templateUri.contains(token)) {
                    answer = true;
                }
            }
            if (!answer && queryParameters != null) {
                String token = "=%7B" + headerName + "%7D"; // encoded values for { }
                if (queryParameters.contains(token)) {
                    answer = true;
                }
            }
        }
        return answer;
    }

}
