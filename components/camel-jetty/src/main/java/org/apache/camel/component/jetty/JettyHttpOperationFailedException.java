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
package org.apache.camel.component.jetty;

import java.util.Map;

import org.apache.camel.CamelException;

/**
 * @version $Revision$
 */
public class JettyHttpOperationFailedException extends CamelException {

    private final String url;
    private final int statusCode;
    private final String redirectLocation;
    private final Map<String, Object> responseHeaders;
    private final String responseBody;

    public JettyHttpOperationFailedException(String url, int statusCode, Map<String, Object> headers, String responseBody) {
        this(url, statusCode, null, headers, responseBody);
    }

    public JettyHttpOperationFailedException(String url, int statusCode, String location, Map<String, Object> headers, String responseBody) {
        super("HTTP operation failed invoking " + url + " with statusCode: " + statusCode + (location != null ? ", redirectLocation: " + location : ""));
        this.url = url;
        this.statusCode = statusCode;
        this.redirectLocation = location;
        this.responseHeaders = headers;
        this.responseBody = responseBody;
    }

    public boolean isRedirectError() {
        return statusCode >= 300 && statusCode < 400;
    }

    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public Map<String, Object> getResponseHeaders() {
        return responseHeaders;
    }
}
