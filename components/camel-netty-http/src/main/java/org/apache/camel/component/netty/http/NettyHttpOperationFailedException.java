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
package org.apache.camel.component.netty.http;

import org.apache.camel.CamelException;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Exception when a Netty HTTP operation failed.
 */
public class NettyHttpOperationFailedException extends CamelException {
    private static final long serialVersionUID = 1L;
    private final String uri;
    private final String redirectLocation;
    private final int statusCode;
    private final String statusText;
    private final transient HttpResponse response;

    public NettyHttpOperationFailedException(String uri, int statusCode, String statusText, String location, HttpResponse response) {
        super("Netty HTTP operation failed invoking " + uri + " with statusCode: " + statusCode + (location != null ? ", redirectLocation: " + location : ""));
        this.uri = uri;
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.redirectLocation = location;
        this.response = response;
    }

    public String getUri() {
        return uri;
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

    /**
     * Gets the {@link HttpResponse}.
     * <p/>
     * Notice this may be <tt>null</tt> if this exception has been serialized,
     * as the {@link HttpResponse} instance is marked as transient in this class.
     */
    public HttpResponse getResponse() {
        return response;
    }
}