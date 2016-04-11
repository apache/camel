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

import java.io.IOException;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpEventListenerWrapper;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceSecurityListener extends HttpEventListenerWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceSecurityListener.class);

    private final HttpDestination destination;
    private final HttpExchange exchange;
    private final SalesforceSession session;

    private String currentToken;
    private int retries;
    private boolean retrying;
    private boolean requestComplete;
    private boolean responseComplete;
    private SalesforceException exceptionResponse;

    public SalesforceSecurityListener(HttpDestination destination, HttpExchange exchange,
                                      SalesforceSession session, String accessToken) {
        super(exchange.getEventListener(), true);
        this.destination = destination;
        this.exchange = exchange;
        this.session = session;
        this.currentToken = accessToken;
    }

    @Override
    public void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
        if (status == HttpStatus.UNAUTHORIZED_401 && retries < destination.getHttpClient().maxRetries()) {
            LOG.warn("Retrying on Salesforce authentication error [{}]: [{}]", status, reason);
            setDelegatingRequests(false);
            setDelegatingResponses(false);

            retrying = true;
        }
        super.onResponseStatus(version, status, reason);
    }

    @Override
    public void onRequestComplete() throws IOException {
        requestComplete = true;
        if (checkExchangeComplete()) {
            super.onRequestComplete();
        }
    }

    @Override
    public void onResponseComplete() throws IOException {
        responseComplete = true;

        exceptionResponse = createExceptionResponse();
        if (!retrying && exceptionResponse != null && isInvalidSessionError(exceptionResponse)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Retrying on Salesforce InvalidSessionId error: {}",
                        getRootSalesforceException(exceptionResponse).getMessage());
            }
            retrying = true;
        }

        if (checkExchangeComplete()) {
            super.onResponseComplete();
        }
    }

    private boolean isInvalidSessionError(SalesforceException e) {
        e = getRootSalesforceException(e);
        return e.getErrors() != null && e.getErrors().size() == 1 && "InvalidSessionId".equals(e.getErrors().get(0).getErrorCode());
    }

    private SalesforceException getRootSalesforceException(SalesforceException e) {
        while (e.getCause() instanceof SalesforceException) {
            e = (SalesforceException) e.getCause();
        }
        return e;
    }

    protected SalesforceException createExceptionResponse() {
        return null;
    }

    private boolean checkExchangeComplete() throws IOException {
        if (retrying && requestComplete && responseComplete) {
            LOG.debug("Authentication Error, retrying: {}", exchange);

            requestComplete = false;
            responseComplete = false;
            exceptionResponse = null;

            setDelegatingRequests(true);
            setDelegatingResponses(true);

            try {
                // get a new token and retry
                currentToken = session.login(currentToken);

                if (exchange instanceof SalesforceExchange) {
                    final SalesforceExchange salesforceExchange = (SalesforceExchange) exchange;
                    final AbstractClientBase client = salesforceExchange.getClient();

                    // update client cache for this and future requests
                    client.setAccessToken(currentToken);
                    client.setInstanceUrl(session.getInstanceUrl());
                    client.setAccessToken(exchange);
                } else {
                    exchange.setRequestHeader(HttpHeaders.AUTHORIZATION,
                        "OAuth " + currentToken);
                }

                // TODO handle a change in Salesforce instanceUrl, right now we retry with the same destination
                destination.resend(exchange);

                // resending, exchange is not done
                return false;

            } catch (SalesforceException e) {
                // logging here, since login exception is not propagated!
                LOG.error(e.getMessage(), e);

                // the HTTP status and reason is pushed up
                setDelegationResult(false);
            }
        }

        return true;
    }

    @Override
    public void onRetry() {
        // ignore retries from other interceptors
        if (retrying) {
            retrying = false;
            retries++;

            setDelegatingRequests(true);
            setDelegatingResponses(true);

            requestComplete = false;
            responseComplete = false;
            exceptionResponse = null;
        }
        super.onRetry();
    }

    @Override
    public void onConnectionFailed(Throwable ex) {
        setDelegatingRequests(true);
        setDelegatingResponses(true);
        // delegate connection failures
        super.onConnectionFailed(ex);
    }

    @Override
    public void onException(Throwable ex) {
        setDelegatingRequests(true);
        setDelegatingResponses(true);
        // delegate exceptions
        super.onException(ex);
    }

    public SalesforceException getExceptionResponse() {
        return exceptionResponse;
    }
}
