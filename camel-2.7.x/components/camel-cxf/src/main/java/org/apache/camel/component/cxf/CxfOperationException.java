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

package org.apache.camel.component.cxf;

import java.io.Serializable;
import java.util.Map;

import org.apache.camel.CamelException;

public class CxfOperationException extends CamelException implements Serializable {

    private static final long serialVersionUID = 803109169584916327L;
    private final String uri;
    private final String redirectLocation;
    private final int statusCode;
    private final String statusText;
    private final Map<String, String> responseHeaders;
    private final String responseBody;

    public CxfOperationException(String uri, int statusCode, String statusText, String location, Map<String, String> responseHeaders,
                                 String responseBody) {
        super("JAXRS operation failed invoking " + uri + " with statusCode: " + statusCode + (location != null ? ", redirectLocation: " + location
            : ""));
        this.uri = uri;
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.redirectLocation = location;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getUri() {
        return uri;
    }
}