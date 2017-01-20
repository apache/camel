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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Service;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClientBase implements SalesforceSession.SalesforceSessionListener, Service {

    protected static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";
    protected static final String APPLICATION_XML_UTF8 = "application/xml;charset=utf-8";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final SalesforceHttpClient httpClient;
    protected final SalesforceSession session;
    protected final String version;

    protected String accessToken;
    protected String instanceUrl;

    public AbstractClientBase(String version, SalesforceSession session,
                              SalesforceHttpClient httpClient) throws SalesforceException {

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

    protected Request getRequest(HttpMethod method, String url) {
        return getRequest(method.asString(), url);
    }

    protected Request getRequest(String method, String url) {
        SalesforceHttpRequest request = (SalesforceHttpRequest) httpClient.newRequest(url)
            .method(method)
            .timeout(session.getTimeout(), TimeUnit.MILLISECONDS);
        request.getConversation().setAttribute(SalesforceSecurityHandler.CLIENT_ATTRIBUTE, this);
        return request;
    }

    protected interface ClientResponseCallback {
        void onResponse(InputStream response, SalesforceException ex);
    }

    protected void doHttpRequest(final Request request, final ClientResponseCallback callback) {
        // Highly memory inefficient,
        // but buffer the request content to allow it to be replayed for authentication retries
        final ContentProvider content = request.getContent();
        if (content instanceof InputStreamContentProvider) {
            final List<ByteBuffer> buffers = new ArrayList<>();
            for (ByteBuffer buffer : content) {
                buffers.add(buffer);
            }
            request.content(new ByteBufferContentProvider(buffers.toArray(new ByteBuffer[buffers.size()])));
            buffers.clear();
        }

        // execute the request
        request.send(new BufferingResponseListener(httpClient.getMaxContentLength()) {
            @Override
            public void onComplete(Result result) {
                Response response = result.getResponse();
                if (result.isFailed()) {

                    // Failure!!!
                    // including Salesforce errors reported as exception from SalesforceSecurityHandler
                    Throwable failure = result.getFailure();
                    if (failure instanceof SalesforceException) {
                        callback.onResponse(null, (SalesforceException) failure);
                    } else {
                        final String msg = String.format("Unexpected error {%s:%s} executing {%s:%s}",
                            response.getStatus(), response.getReason(), request.getMethod(), request.getURI());
                        callback.onResponse(null, new SalesforceException(msg, response.getStatus(), failure));
                    }
                } else {

                    // HTTP error status
                    final int status = response.getStatus();
                    SalesforceHttpRequest request = (SalesforceHttpRequest) ((SalesforceHttpRequest) result.getRequest())
                        .getConversation()
                        .getAttribute(SalesforceSecurityHandler.AUTHENTICATION_REQUEST_ATTRIBUTE);

                    if (status == HttpStatus.BAD_REQUEST_400 && request != null) {
                        // parse login error
                        ContentResponse contentResponse = new HttpContentResponse(response, getContent(), getMediaType(), getEncoding());
                        try {

                            session.parseLoginResponse(contentResponse, getContentAsString());
                            final String msg = String.format("Unexpected Error {%s:%s} executing {%s:%s}",
                                status, response.getReason(), request.getMethod(), request.getURI());
                            callback.onResponse(null, new SalesforceException(msg, null));

                        } catch (SalesforceException e) {

                            final String msg = String.format("Error {%s:%s} executing {%s:%s}",
                                status, response.getReason(), request.getMethod(), request.getURI());
                            callback.onResponse(null, new SalesforceException(msg, response.getStatus(), e));

                        }
                    } else if (status < HttpStatus.OK_200 || status >= HttpStatus.MULTIPLE_CHOICES_300) {

                        // Salesforce HTTP failure!
                        request = (SalesforceHttpRequest) result.getRequest();
                        final String msg = String.format("Error {%s:%s} executing {%s:%s}",
                            status, response.getReason(), request.getMethod(), request.getURI());
                        final SalesforceException cause = createRestException(response, getContentAsInputStream());

                        // for APIs that return body on status 400, such as Composite API we need content as well
                        callback.onResponse(getContentAsInputStream(), new SalesforceException(msg, response.getStatus(), cause));

                    } else {

                        // Success!!!
                        callback.onResponse(getContentAsInputStream(), null);
                    }
                }
            }

            @Override
            public InputStream getContentAsInputStream() {
                if (getContent().length == 0) {
                    return null;
                }
                return super.getContentAsInputStream();
            }
        });
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    protected abstract void setAccessToken(Request request);

    protected abstract SalesforceException createRestException(Response response, InputStream responseContent);

}
