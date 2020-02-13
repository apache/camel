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
package org.apache.camel.component.salesforce.internal.processor;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.composite.ReferenceId;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTreeResponse;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.client.CompositeApiClient;
import org.apache.camel.component.salesforce.internal.client.DefaultCompositeApiClient;
import org.apache.camel.support.service.ServiceHelper;

public final class CompositeApiProcessor extends AbstractSalesforceProcessor {

    @FunctionalInterface
    interface ResponseHandler<T> {

        void handleResponse(Exchange exchange, Optional<T> body, Map<String, String> headers, SalesforceException exception, AsyncCallback callback);

    }

    private final CompositeApiClient compositeClient;

    private final PayloadFormat format;

    public CompositeApiProcessor(final SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        final SalesforceEndpointConfig configuration = endpoint.getConfiguration();
        final String apiVersion = configuration.getApiVersion();

        format = configuration.getFormat();

        if (!EnumSet.of(PayloadFormat.JSON, PayloadFormat.XML).contains(format)) {
            throw new SalesforceException("Unsupported format: " + format, 0);
        }

        compositeClient = new DefaultCompositeApiClient(configuration, format, apiVersion, session, httpClient);

    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            switch (operationName) {
                case COMPOSITE_TREE:
                    return processInternal(SObjectTree.class, exchange, compositeClient::submitCompositeTree, this::processCompositeTreeResponse, callback);
                case COMPOSITE_BATCH:
                    return processInternal(SObjectBatch.class, exchange, compositeClient::submitCompositeBatch, this::processCompositeBatchResponse, callback);
                case COMPOSITE:
                    return processInternal(SObjectComposite.class, exchange, compositeClient::submitComposite, this::processCompositeResponse, callback);
                default:
                    throw new SalesforceException("Unknown operation name: " + operationName.value(), null);
            }
        } catch (final SalesforceException e) {
            return processException(exchange, callback, e);
        } catch (final RuntimeException e) {
            final SalesforceException exception = new SalesforceException(String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e);
            return processException(exchange, callback, exception);
        }
    }

    @Override
    public void start() {
        ServiceHelper.startService(compositeClient);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(compositeClient);
    }

    void processCompositeBatchResponse(final Exchange exchange, final Optional<SObjectBatchResponse> responseBody, final Map<String, String> headers,
                                       final SalesforceException exception, final AsyncCallback callback) {
        try {
            if (!responseBody.isPresent()) {
                exchange.setException(exception);
            } else {
                final Message in = exchange.getIn();
                final Message out = exchange.getOut();

                final SObjectBatchResponse response = responseBody.get();

                out.copyFromWithNewBody(in, response);
                out.getHeaders().putAll(headers);
            }
        } finally {
            // notify callback that exchange is done
            callback.done(false);
        }
    }

    void processCompositeResponse(final Exchange exchange, final Optional<SObjectCompositeResponse> responseBody, final Map<String, String> headers,
                                  final SalesforceException exception, final AsyncCallback callback) {
        try {
            if (!responseBody.isPresent()) {
                exchange.setException(exception);
            } else {
                final Message in = exchange.getIn();
                final Message out = exchange.getOut();

                final SObjectCompositeResponse response = responseBody.get();

                out.copyFromWithNewBody(in, response);
                out.getHeaders().putAll(headers);
            }
        } finally {
            // notify callback that exchange is done
            callback.done(false);
        }
    }

    void processCompositeTreeResponse(final Exchange exchange, final Optional<SObjectTreeResponse> responseBody, final Map<String, String> headers,
                                      final SalesforceException exception, final AsyncCallback callback) {

        try {
            if (!responseBody.isPresent()) {
                exchange.setException(exception);
            } else {

                final Message in = exchange.getIn();
                final Message out = exchange.getOut();

                final SObjectTree tree = in.getBody(SObjectTree.class);

                final SObjectTreeResponse response = responseBody.get();

                final boolean hasErrors = response.hasErrors();

                for (final ReferenceId referenceId : response.getResults()) {
                    tree.setIdFor(referenceId.getReferenceId(), referenceId.getId());

                    if (hasErrors) {
                        tree.setErrorFor(referenceId.getReferenceId(), referenceId.getErrors());
                    }
                }

                if (hasErrors) {
                    final SalesforceException withErrors = new SalesforceException(response.getAllErrors(), exception.getStatusCode(), exception);
                    exchange.setException(withErrors);
                }

                out.copyFromWithNewBody(in, tree);
                out.getHeaders().putAll(headers);
            }
        } finally {
            // notify callback that exchange is done
            callback.done(false);
        }
    }

    <T, R> boolean processInternal(final Class<T> bodyType, final Exchange exchange, final CompositeApiClient.Operation<T, R> clientOperation,
                                   final ResponseHandler<R> responseHandler, final AsyncCallback callback)
            throws SalesforceException {

        final T body;

        final Message in = exchange.getIn();
        try {
            body = in.getMandatoryBody(bodyType);
        } catch (final InvalidPayloadException e) {
            throw new SalesforceException(e);
        }

        clientOperation.submit(body, determineHeaders(exchange),
            (response, responseHeaders, exception) -> responseHandler.handleResponse(exchange, response, responseHeaders, exception, callback));

        return false;
    }

    static boolean processException(final Exchange exchange, final AsyncCallback callback, final Exception e) {
        exchange.setException(e);
        callback.done(true);

        return true;
    }

}
