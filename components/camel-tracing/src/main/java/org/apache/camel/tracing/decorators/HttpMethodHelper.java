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
package org.apache.camel.tracing.decorators;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class HttpMethodHelper {

    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile("(?i)httpMethod=([A-Z]+)");

    /**
     * This method searches for the httpMethod param on the endpoint and return it.
     *
     * @param  exchange
     * @param  endpoint
     * @return
     */
    public static String getHttpMethodFromParameters(Exchange exchange, Endpoint endpoint) {
        String queryStringHeader = (String) exchange.getIn().getHeader(Exchange.HTTP_QUERY);
        if (queryStringHeader != null) {
            String methodFromQuery = getMethodFromQueryString(queryStringHeader);
            if (methodFromQuery != null) {
                return methodFromQuery;
            }
        }

        // try to get the httpMethod parameter from the the query string in the uri
        int queryIndex = endpoint.getEndpointUri().indexOf('?');
        if (queryIndex != -1) {
            String queryString = endpoint.getEndpointUri().substring(queryIndex + 1);
            String methodFromQuery = getMethodFromQueryString(queryString);
            if (methodFromQuery != null) {
                return methodFromQuery;
            }
        }
        return null;
    }

    private static String getMethodFromQueryString(String queryString) {
        Matcher m = HTTP_METHOD_PATTERN.matcher(queryString);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

}
