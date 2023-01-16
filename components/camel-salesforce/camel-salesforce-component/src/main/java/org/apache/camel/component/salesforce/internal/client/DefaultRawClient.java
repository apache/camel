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
package org.apache.camel.component.salesforce.internal.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.StringUtil;

public class DefaultRawClient extends AbstractClientBase implements RawClient {

    private static final String BULK_TOKEN_HEADER = "X-SFDC-Session";
    private static final String REST_TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    public DefaultRawClient(final SalesforceHttpClient httpClient, final String version,
                            final SalesforceSession session,
                            final SalesforceLoginConfig loginConfig) throws SalesforceException {
        super(version, session, httpClient, loginConfig);
    }

    @Override
    protected void setAccessToken(Request request) {
        // replace old token
        request.header(BULK_TOKEN_HEADER, null);
        request.header(REST_TOKEN_HEADER, null);
        request.header(BULK_TOKEN_HEADER, accessToken);
        request.header(REST_TOKEN_HEADER, TOKEN_PREFIX + accessToken);
    }

    @Override
    protected SalesforceException createRestException(Response response, InputStream responseContent) {
        String message = null;
        try {
            message = IOUtils.toString(responseContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            message = "Unable to read exception message: " + e.getMessage();
        }
        return new SalesforceException(message, response.getStatus());
    }

    /**
     * Make a raw HTTP request to salesforce
     * 
     * @param method   HTTP method. "GET", "POST", etc.
     * @param path     The path of the URL. Must begin with a "/"
     * @param format   Encoding format
     * @param body     Optional HTTP body
     * @param headers  HTTP headers
     * @param callback Callback instance that will be invoked when the HTTP call returns
     */
    @Override
    public void makeRequest(
            String method, String path, PayloadFormat format, InputStream body, Map<String, List<String>> headers,
            ResponseCallback callback) {
        final Request request = getRequest(method, instanceUrl + path, headers);
        final String contentType = PayloadFormat.JSON.equals(format) ? APPLICATION_JSON_UTF8 : APPLICATION_XML_UTF8;
        if (!request.getHeaders().contains(HttpHeader.ACCEPT)) {
            request.header(HttpHeader.ACCEPT, contentType);
        }
        request.header(HttpHeader.ACCEPT_CHARSET, StringUtil.__UTF8);
        if (!request.getHeaders().contains(HttpHeader.CONTENT_TYPE)) {
            request.header(HttpHeader.CONTENT_TYPE, contentType);
        }
        if (body != null) {
            request.content(new InputStreamContentProvider(body));
        }
        setAccessToken(request);
        doHttpRequest(request, new DelegatingClientCallback(callback));
    }

    private static class DelegatingClientCallback implements ClientResponseCallback {
        private final ResponseCallback callback;

        DelegatingClientCallback(ResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
            callback.onResponse(response, headers, ex);
        }
    }
}
