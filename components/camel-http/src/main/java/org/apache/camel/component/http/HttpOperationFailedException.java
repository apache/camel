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
package org.apache.camel.component.http;

import java.io.InputStream;

import org.apache.camel.CamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.StatusLine;

public class HttpOperationFailedException extends CamelException {    
    private final String redirectLocation;
    private final int statusCode;
    private final StatusLine statusLine;
    private final Header[] responseHeaders;
    private final InputStream responseBody;

    public HttpOperationFailedException(int statusCode, StatusLine statusLine, String location, Header[] responseHeaders, InputStream responseBody) {
        super("HTTP operation failed with statusCode: " + statusCode + ", status: " + statusLine + (location != null ? ", redirectLocation: " + location : ""));
        this.statusCode = statusCode;
        this.statusLine = statusLine;
        this.redirectLocation = location;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
    }

    public HttpOperationFailedException(int statusCode, StatusLine statusLine, Header[] responseHeaders, InputStream responseBody) {
        this(statusCode, statusLine, null, responseHeaders, responseBody);
    }

    public boolean isRedirectError() {
        return statusCode >= 300 && statusCode < 400;
    }

    public boolean hasRedirectLocation() {
        return ObjectHelper.isNotNullAndNonEmpty(redirectLocation);
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public StatusLine getStatusLine() {
        return statusLine;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Header[] getResponseHeaders() {
        return responseHeaders;
    }

    public InputStream getResponseBody() {
        return responseBody;
    }

}