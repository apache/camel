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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCollection;
import org.apache.camel.component.salesforce.internal.client.CompositeSObjectCollectionsApiClient;
import org.apache.camel.component.salesforce.internal.client.DefaultCompositeSObjectCollectionsApiClient;
import org.apache.camel.component.salesforce.internal.dto.composite.RetrieveSObjectCollectionsDto;
import org.apache.camel.support.service.ServiceHelper;

public class CompositeSObjectCollectionsProcessor extends AbstractSalesforceProcessor {

    private CompositeSObjectCollectionsApiClient compositeClient;
    private Map<String, Class<?>> classMap;

    public CompositeSObjectCollectionsProcessor(final SalesforceEndpoint endpoint) {
        super(endpoint);

    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final SalesforceEndpointConfig configuration = endpoint.getConfiguration();
        final String apiVersion = configuration.getApiVersion();

        compositeClient = new DefaultCompositeSObjectCollectionsApiClient(
                configuration, apiVersion, session, httpClient, loginConfig);

        if (classMap == null) {
            this.classMap = endpoint.getComponent().getClassMap();
        }
        ServiceHelper.startService(compositeClient);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(compositeClient);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            switch (operationName) {
                case COMPOSITE_CREATE_SOBJECT_COLLECTIONS:
                    return processCreateSObjectCollections(exchange, callback);
                case COMPOSITE_UPDATE_SOBJECT_COLLECTIONS:
                    return processUpdateSObjectCollections(exchange, callback);
                case COMPOSITE_UPSERT_SOBJECT_COLLECTIONS:
                    return processUpsertSObjectCollections(exchange, callback);
                case COMPOSITE_RETRIEVE_SOBJECT_COLLECTIONS:
                    return processRetrieveSObjectCollections(exchange, callback);
                case COMPOSITE_DELETE_SOBJECT_COLLECTIONS:
                    return processDeleteSObjectCollections(exchange, callback);
                default:
                    throw new SalesforceException("Unknown operation name: " + operationName.value(), null);
            }
        } catch (final SalesforceException e) {
            return processException(exchange, callback, e);
        } catch (final RuntimeException e) {
            final SalesforceException exception = new SalesforceException(
                    String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e);
            return processException(exchange, callback, exception);
        }
    }

    private boolean processRetrieveSObjectCollections(Exchange exchange, AsyncCallback callback)
            throws SalesforceException {
        List<String> ids = getListParameter(SalesforceEndpointConfig.SOBJECT_IDS, exchange, IGNORE_BODY, NOT_OPTIONAL);
        List<String> fields = getListParameter(SalesforceEndpointConfig.SOBJECT_FIELDS, exchange, IGNORE_BODY, NOT_OPTIONAL);
        String sObjectName = getParameter(SalesforceEndpointConfig.SOBJECT_NAME, exchange, IGNORE_BODY, IS_OPTIONAL);

        // gets class by sObjectName if not null, otherwise tries the SOBJECT_CLASS option
        Class<?> sObjectClass = getSObjectClass(exchange);

        RetrieveSObjectCollectionsDto request = new RetrieveSObjectCollectionsDto(ids, fields);
        compositeClient.submitRetrieveCompositeCollections(request, determineHeaders(exchange),
                (response, responseHeaders, exception) -> processResponse(exchange, response,
                        responseHeaders, exception, callback),
                sObjectName, sObjectClass);
        return false;
    }

    private boolean processCreateSObjectCollections(final Exchange exchange, final AsyncCallback callback)
            throws SalesforceException {
        SObjectCollection collection = buildSObjectCollection(exchange);
        compositeClient.createCompositeCollections(collection, determineHeaders(exchange),
                (response, responseHeaders, exception) -> processResponse(exchange, response, responseHeaders,
                        exception, callback));
        return false;
    }

    private boolean processUpdateSObjectCollections(final Exchange exchange, final AsyncCallback callback)
            throws SalesforceException {
        final SObjectCollection collection = buildSObjectCollection(exchange);
        compositeClient.updateCompositeCollections(collection, determineHeaders(exchange),
                (response, responseHeaders, exception) -> processResponse(exchange, response, responseHeaders,
                        exception, callback));
        return false;
    }

    private boolean processUpsertSObjectCollections(final Exchange exchange, final AsyncCallback callback)
            throws SalesforceException {
        final SObjectCollection collection = buildSObjectCollection(exchange);
        String externalIdFieldName
                = getParameter(SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
        String sObjectName = getParameter(SalesforceEndpointConfig.SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
        compositeClient.upsertCompositeCollections(collection, determineHeaders(exchange),
                (response, responseHeaders, exception) -> processResponse(exchange, response, responseHeaders,
                        exception, callback),
                sObjectName, externalIdFieldName);

        return false;
    }

    private boolean processDeleteSObjectCollections(final Exchange exchange, final AsyncCallback callback)
            throws SalesforceException {
        List<String> ids = getListParameter(SalesforceEndpointConfig.SOBJECT_IDS, exchange, USE_BODY, NOT_OPTIONAL);
        boolean allOrNone = Boolean.parseBoolean(
                getParameter(SalesforceEndpointConfig.ALL_OR_NONE, exchange, IGNORE_BODY, IS_OPTIONAL));
        compositeClient.submitDeleteCompositeCollections(ids, allOrNone, determineHeaders(exchange),
                (response, responseHeaders, exception) -> processResponse(exchange, response,
                        responseHeaders, exception, callback));
        return false;
    }

    @SuppressWarnings("unchecked")
    private SObjectCollection buildSObjectCollection(Exchange exchange) throws SalesforceException {
        final List<AbstractDescribedSObjectBase> body;
        final Message in = exchange.getIn();
        try {
            body = (List<AbstractDescribedSObjectBase>) in.getMandatoryBody();
        } catch (final InvalidPayloadException e) {
            throw new SalesforceException(e);
        }
        SObjectCollection collection = new SObjectCollection();
        collection.setRecords(body);
        boolean allOrNone = Boolean.parseBoolean(
                getParameter(SalesforceEndpointConfig.ALL_OR_NONE, exchange, USE_BODY, IS_OPTIONAL));
        collection.setAllOrNone(allOrNone);
        return collection;
    }

    private void processResponse(
            final Exchange exchange, final Optional<? extends List<?>> responseBody, final Map<String, String> headers,
            final SalesforceException exception, final AsyncCallback callback) {
        try {
            if (exception != null) {
                exchange.setException(exception);
            } else {
                Message in = exchange.getIn();
                Message out = exchange.getOut();
                List<?> response = responseBody.get();
                out.copyFromWithNewBody(in, response);
                out.getHeaders().putAll(headers);
            }
        } finally {
            callback.done(false);
        }
    }

    private boolean processException(final Exchange exchange, final AsyncCallback callback, final Exception e) {
        exchange.setException(e);
        callback.done(true);
        return true;
    }
}
