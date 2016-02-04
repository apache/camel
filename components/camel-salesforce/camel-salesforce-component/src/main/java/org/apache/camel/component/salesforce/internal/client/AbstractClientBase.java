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
package org.apache.camel.component.salesforce.internal.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Service;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpEventListenerWrapper;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClientBase implements SalesforceSession.SalesforceSessionListener, Service {

    protected static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";
    protected static final String APPLICATION_XML_UTF8 = "application/xml;charset=utf-8";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient;
    protected final SalesforceSession session;
    protected final String version;

    protected String accessToken;
    protected String instanceUrl;

    public AbstractClientBase(String version,
                              SalesforceSession session, HttpClient httpClient) throws SalesforceException {

        this.version = version;
        this.session = session;
        this.httpClient = httpClient;
    }

    public void start() throws Exception {
        // local cache
        accessToken = session.getAccessToken();
        if (accessToken == null) {
            // lazy login here!
            accessToken = session.login(accessToken);
        }
        instanceUrl = session.getInstanceUrl();

        // also register this client as a session listener
        session.addListener(this);
    }

    @Override
    public void stop() throws Exception {
        // deregister listener
        session.removeListener(this);
    }

    @Override
    public void onLogin(String accessToken, String instanceUrl) {
        if (!accessToken.equals(this.accessToken)) {
            this.accessToken = accessToken;
            this.instanceUrl = instanceUrl;
        }
    }

    @Override
    public void onLogout() {
        // ignore, if this client makes another request with stale token,
        // SalesforceSecurityListener will auto login!
    }

    protected SalesforceExchange getContentExchange(String method, String url) {
        SalesforceExchange get = new SalesforceExchange();
        get.setMethod(method);
        get.setURL(url);
        get.setClient(this);
        return get;
    }

    protected interface ClientResponseCallback {
        void onResponse(InputStream response, SalesforceException ex);
    }

    protected void doHttpRequest(final ContentExchange request, final ClientResponseCallback callback) {

        // use SalesforceSecurityListener for security login retries
        final SalesforceSecurityListener securityListener;
        try {
            final boolean isHttps = HttpSchemes.HTTPS.equals(String.valueOf(request.getScheme()));
            securityListener = new SalesforceSecurityListener(
                    httpClient.getDestination(request.getAddress(), isHttps),
                    request, session, accessToken) {

                private String reason;

                @Override
                public void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
                    super.onResponseStatus(version, status, reason);
                    // remember status reason
                    this.reason = reason.toString(StringUtil.__ISO_8859_1);
                }

                @Override
                protected SalesforceException createExceptionResponse() {
                    final int responseStatus = request.getResponseStatus();
                    if (responseStatus < HttpStatus.OK_200 || responseStatus >= HttpStatus.MULTIPLE_CHOICES_300) {
                        final String msg = String.format("Error {%s:%s} executing {%s:%s}",
                                responseStatus, reason, request.getMethod(), request.getRequestURI());
                        return new SalesforceException(msg, responseStatus, createRestException(request, reason));
                    } else {
                        return super.createExceptionResponse();
                    }
                }
            };
        } catch (IOException e) {
            // propagate exception
            callback.onResponse(null, new SalesforceException(
                    String.format("Error registering security listener: %s", e.getMessage()),
                    e));
            return;
        }

        // use HttpEventListener for lifecycle events
        request.setEventListener(new HttpEventListenerWrapper(request.getEventListener(), true) {

            @Override
            public void onConnectionFailed(Throwable ex) {
                super.onConnectionFailed(ex);
                callback.onResponse(null,
                        new SalesforceException("Connection error: " + ex.getMessage(), ex));
            }

            @Override
            public void onException(Throwable ex) {
                super.onException(ex);
                callback.onResponse(null,
                        new SalesforceException("Unexpected exception: " + ex.getMessage(), ex));
            }

            @Override
            public void onExpire() {
                super.onExpire();
                callback.onResponse(null,
                        new SalesforceException("Request expired", null));
            }

            @Override
            public void onResponseComplete() throws IOException {
                super.onResponseComplete();

                SalesforceException e = securityListener.getExceptionResponse();
                if (e != null) {
                    callback.onResponse(null, e);
                } else {
                    // TODO not memory efficient for large response messages,
                    // doesn't seem to be possible in Jetty 7 to directly stream to response parsers
                    final byte[] bytes = request.getResponseContentBytes();
                    callback.onResponse(bytes != null ? new ByteArrayInputStream(bytes) : null, null);
                }

            }
        });

        // wrap the above lifecycle event listener with SalesforceSecurityListener
        securityListener.setEventListener(request.getEventListener());
        request.setEventListener(securityListener);

        // execute the request
        try {
            httpClient.send(request);
        } catch (IOException e) {
            String msg = "Unexpected Error: " + e.getMessage();
            // send error through callback
            callback.onResponse(null, new SalesforceException(msg, e));
        }

    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    protected abstract void setAccessToken(HttpExchange httpExchange);

    protected abstract SalesforceException createRestException(ContentExchange httpExchange, String reason);

}
