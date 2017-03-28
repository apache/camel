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

import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.ProtocolHandler;
import org.eclipse.jetty.client.ResponseNotifier;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceSecurityHandler implements ProtocolHandler {

    static final String CLIENT_ATTRIBUTE = SalesforceSecurityHandler.class.getName().concat("camel-salesforce-client");
    static final String AUTHENTICATION_REQUEST_ATTRIBUTE = SalesforceSecurityHandler.class.getName().concat(".request");

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceSecurityHandler.class);

    private static final String AUTHENTICATION_RETRIES_ATTRIBUTE = SalesforceSecurityHandler.class.getName().concat(".retries");

    private final SalesforceHttpClient httpClient;
    private final SalesforceSession session;
    private final int maxAuthenticationRetries;
    private final int maxContentLength;
    private final ResponseNotifier notifier;

    public SalesforceSecurityHandler(SalesforceHttpClient httpClient) {

        this.httpClient = httpClient;
        this.session = httpClient.getSession();

        this.maxAuthenticationRetries = httpClient.getMaxRetries();
        this.maxContentLength = httpClient.getMaxContentLength();
        this.notifier = new ResponseNotifier();
    }

    @Override
    public boolean accept(Request request, Response response) {

        HttpConversation conversation = ((SalesforceHttpRequest) request).getConversation();
        Integer retries = (Integer) conversation.getAttribute(AUTHENTICATION_RETRIES_ATTRIBUTE);

        // is this an authentication response for a previously handled conversation?
        if (conversation.getAttribute(AUTHENTICATION_REQUEST_ATTRIBUTE) != null
            && (retries == null || retries <= maxAuthenticationRetries)) {
            return true;
        }

        final int status = response.getStatus();
        // handle UNAUTHORIZED and BAD_REQUEST for Bulk API,
        // the actual InvalidSessionId Bulk API error is checked and handled in the listener
        // also check retries haven't exceeded maxAuthenticationRetries
        return (status == HttpStatus.UNAUTHORIZED_401 || status == HttpStatus.BAD_REQUEST_400)
            && (retries == null || retries <= maxAuthenticationRetries);
    }

    @Override
    public Response.Listener getResponseListener() {
        return new SecurityListener(maxContentLength);
    }

    private class SecurityListener extends BufferingResponseListener {

        SecurityListener(int maxLength) {
            super(maxLength);
        }

        @Override
        public void onComplete(Result result) {

            SalesforceHttpRequest request = (SalesforceHttpRequest)result.getRequest();
            ContentResponse response = new HttpContentResponse(result.getResponse(), getContent(), getMediaType(), getEncoding());

            // get number of retries
            HttpConversation conversation = request.getConversation();
            Integer retries = (Integer) conversation.getAttribute(AUTHENTICATION_RETRIES_ATTRIBUTE);
            if (retries == null) {
                retries = 0;
            }

            // get AbstractClientBase if request originated from one, for updating token and setting auth header
            final AbstractClientBase client = (AbstractClientBase) conversation.getAttribute(CLIENT_ATTRIBUTE);

            // exception response
            if (result.isFailed()) {
                Throwable failure = result.getFailure();
                retryOnFailure(request, conversation, retries, client, failure);
                return;
            }

            // response to a re-login request
            SalesforceHttpRequest origRequest = (SalesforceHttpRequest) conversation.getAttribute(AUTHENTICATION_REQUEST_ATTRIBUTE);
            if (origRequest != null) {

                // parse response
                try {
                    session.parseLoginResponse(response, response.getContentAsString());
                } catch (SalesforceException e) {
                    // retry login request on error if we have login attempts left
                    if (retries < maxAuthenticationRetries) {
                        retryOnFailure(request, conversation, retries, client, e);
                    } else {
                        forwardFailureComplete(origRequest, null, response, e);
                    }
                    return;
                }

                // retry original request on success
                conversation.removeAttribute(AUTHENTICATION_REQUEST_ATTRIBUTE);
                retryRequest(origRequest, client, retries, conversation, true);
                return;
            }

            // response to an original request
            final int status = response.getStatus();
            final String reason = response.getReason();

            // check if login retries left
            if (retries >= maxAuthenticationRetries) {
                // forward current response
                forwardSuccessComplete(request, response);
                return;
            }

            // request failed authentication?
            if (status == HttpStatus.UNAUTHORIZED_401) {

                // REST token expiry
                LOG.warn("Retrying on Salesforce authentication error [{}]: [{}]", status, reason);

                // remember original request and send a relogin request in current conversation
                retryLogin(request, retries);

            } else if (status < HttpStatus.OK_200 || status >= HttpStatus.MULTIPLE_CHOICES_300) {

                // HTTP failure status
                // get detailed cause, if request comes from an AbstractClientBase
                final InputStream inputStream = getContent().length == 0 ? null : getContentAsInputStream();
                final SalesforceException cause = client != null
                    ? client.createRestException(response, inputStream) : null;

                if (status == HttpStatus.BAD_REQUEST_400 && cause != null && isInvalidSessionError(cause)) {

                    // retry Bulk API call
                    LOG.warn("Retrying on Bulk API Salesforce authentication error [{}]: [{}]", status, reason);
                    retryLogin(request, retries);

                } else {

                    // forward Salesforce HTTP failure!
                    forwardSuccessComplete(request, response);
                }
            }
        }

        protected void retryOnFailure(SalesforceHttpRequest request, HttpConversation conversation, Integer retries, AbstractClientBase client, Throwable failure) {
            LOG.warn("Retrying on Salesforce authentication failure " + failure.getMessage(), failure);

            // retry request
            retryRequest(request, client, retries, conversation, true);
        }

        private boolean isInvalidSessionError(SalesforceException e) {
            return e.getErrors() != null && e.getErrors().size() == 1
                && "InvalidSessionId".equals(e.getErrors().get(0).getErrorCode());
        }

        private void retryLogin(SalesforceHttpRequest request, Integer retries) {

            final HttpConversation conversation = request.getConversation();
            // remember the original request to resend
            conversation.setAttribute(AUTHENTICATION_REQUEST_ATTRIBUTE, request);

            retryRequest((SalesforceHttpRequest)session.getLoginRequest(conversation), null, retries, conversation, false);
        }

        private void retryRequest(SalesforceHttpRequest request, AbstractClientBase client, Integer retries, HttpConversation conversation,
                                  boolean copy) {
            // copy the request to resend
            // TODO handle a change in Salesforce instanceUrl, right now we retry with the same destination
            final Request newRequest;
            if (copy) {
                newRequest = httpClient.copyRequest(request, request.getURI());
                newRequest.method(request.getMethod());
                HttpFields headers = newRequest.getHeaders();
                // copy cookies and host for subscriptions to avoid '403::Unknown Client' errors
                for (HttpField field : request.getHeaders()) {
                    HttpHeader header = field.getHeader();
                    if (HttpHeader.COOKIE.equals(header) || HttpHeader.HOST.equals(header)) {
                        headers.add(header, field.getValue());
                    }
                }
            } else {
                newRequest = request;
            }

            conversation.setAttribute(AUTHENTICATION_RETRIES_ATTRIBUTE, ++retries);

            Object originalRequest = conversation.getAttribute(AUTHENTICATION_REQUEST_ATTRIBUTE);
            LOG.debug("Retry attempt {} on authentication error for {}", retries, originalRequest != null ? originalRequest : newRequest);

            // update currentToken for original request
            if (originalRequest == null) {

                String currentToken = session.getAccessToken();
                if (client != null) {
                    // update client cache for this and future requests
                    client.setAccessToken(currentToken);
                    client.setInstanceUrl(session.getInstanceUrl());
                    client.setAccessToken(newRequest);
                } else {
                    // plain request not made by an AbstractClientBase
                    newRequest.header(HttpHeader.AUTHORIZATION, "OAuth " + currentToken);
                }
            }

            // send new async request with a new delegate
            conversation.updateResponseListeners(null);
            newRequest.onRequestBegin(getRequestAbortListener(request));
            newRequest.send(null);
        }

        private Request.BeginListener getRequestAbortListener(final SalesforceHttpRequest request) {
            return new Request.BeginListener() {
                @Override
                public void onBegin(Request redirect) {
                    Throwable cause = request.getAbortCause();
                    if (cause != null) {
                        redirect.abort(cause);
                    }
                }
            };
        }

        private void forwardSuccessComplete(SalesforceHttpRequest request, Response response) {
            HttpConversation conversation = request.getConversation();
            conversation.updateResponseListeners(null);
            notifier.forwardSuccessComplete(conversation.getResponseListeners(), request, response);
        }

        private void forwardFailureComplete(SalesforceHttpRequest request, Throwable requestFailure,
                                            Response response, Throwable responseFailure) {
            HttpConversation conversation = request.getConversation();
            conversation.updateResponseListeners(null);
            notifier.forwardFailureComplete(conversation.getResponseListeners(), request, requestFailure,
                response, responseFailure);
        }
    }

    // no @Override annotation here to keep it compatible with Jetty 9.2, getName was added in 9.3
    public String getName() {
        return "CamelSalesforceSecurityHandler";
    }
}
