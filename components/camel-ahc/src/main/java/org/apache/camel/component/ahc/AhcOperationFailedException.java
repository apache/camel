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

import java.util.Map;

import org.apache.camel.CamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

public class AhcOperationFailedException extends CamelException {
    private static final long serialVersionUID = -6731281444593522633L;
    private final String url;
    private final String redirectLocation;
    private final int statusCode;
    private final String statusText;
    private final Map<String, String> responseHeaders;
    private final String responseBody;

    public AhcOperationFailedException(String url, int statusCode, String statusText, String location, Map<String, String> responseHeaders, String responseBody) {
        // sanitize url so we do not show sensitive information such as passwords
        super("HTTP operation failed invoking " + URISupport.sanitizeUri(url) + " with statusCode: " + statusCode + (location != null ? ", redirectLocation: " + location : ""));
        this.url = URISupport.sanitizeUri(url);
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.redirectLocation = location;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
    }

    public String getUrl() {
        return url;
    }

    public boolean isRedirectError() {
        return statusCode >= 300 && statusCode < 400;
    }

    public boolean hasRedirectLocation() {
        return ObjectHelper.isNotEmpty(redirectLocation);
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

}
