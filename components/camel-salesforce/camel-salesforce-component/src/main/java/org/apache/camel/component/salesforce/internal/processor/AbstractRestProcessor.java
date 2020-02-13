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

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.salesforce.NotFoundBehaviour;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequests;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.APEX_METHOD;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.APEX_QUERY_PARAM_PREFIX;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.APEX_URL;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_BLOB_FIELD_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_CLASS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_FIELDS;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_ID;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_NAME;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_QUERY;
import static org.apache.camel.component.salesforce.SalesforceEndpointConfig.SOBJECT_SEARCH;

public abstract class AbstractRestProcessor extends AbstractSalesforceProcessor {

    protected static final String RESPONSE_CLASS = AbstractRestProcessor.class.getName() + ".responseClass";
    private static final Pattern URL_TEMPLATE = Pattern.compile("\\{([^\\{\\}]+)\\}");

    private RestClient restClient;
    private Map<String, Class<?>> classMap;

    private final NotFoundBehaviour notFoundBehaviour;

    public AbstractRestProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);

        final SalesforceEndpointConfig configuration = endpoint.getConfiguration();
        notFoundBehaviour = configuration.getNotFoundBehaviour();

        final SalesforceComponent salesforceComponent = endpoint.getComponent();

        this.restClient = salesforceComponent.createRestClientFor(endpoint);

        this.classMap = endpoint.getComponent().getClassMap();
    }

    // used in unit tests
    AbstractRestProcessor(final SalesforceEndpoint endpoint, final RestClient restClient, final Map<String, Class<?>> classMap) {
        super(endpoint);
        this.restClient = restClient;
        this.classMap = classMap;
        final SalesforceEndpointConfig configuration = endpoint.getConfiguration();
        notFoundBehaviour = configuration.getNotFoundBehaviour();
    }

    @Override
    public void start() {
        ServiceHelper.startService(restClient);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(restClient);
    }

    @Override
    public final boolean process(final Exchange exchange, final AsyncCallback callback) {
        // pre-process request message
        try {
            processRequest(exchange);
        } catch (SalesforceException e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        } catch (RuntimeException e) {
            exchange.setException(new SalesforceException(e.getMessage(), e));
            callback.done(true);
            return true;
        }

        // call Salesforce asynchronously
        try {
            // call Operation using REST client
            switch (operationName) {
                case GET_VERSIONS:
                    processGetVersions(exchange, callback);
                    break;
                case GET_RESOURCES:
                    processGetResources(exchange, callback);
                    break;
                case GET_GLOBAL_OBJECTS:
                    processGetGlobalObjects(exchange, callback);
                    break;
                case GET_BASIC_INFO:
                    processGetBasicInfo(exchange, callback);
                    break;
                case GET_DESCRIPTION:
                    processGetDescription(exchange, callback);
                    break;
                case GET_SOBJECT:
                    processGetSobject(exchange, callback);
                    break;
                case CREATE_SOBJECT:
                    processCreateSobject(exchange, callback);
                    break;
                case UPDATE_SOBJECT:
                    processUpdateSobject(exchange, callback);
                    break;
                case DELETE_SOBJECT:
                    processDeleteSobject(exchange, callback);
                    break;
                case GET_SOBJECT_WITH_ID:
                    processGetSobjectWithId(exchange, callback);
                    break;
                case UPSERT_SOBJECT:
                    processUpsertSobject(exchange, callback);
                    break;
                case DELETE_SOBJECT_WITH_ID:
                    processDeleteSobjectWithId(exchange, callback);
                    break;
                case GET_BLOB_FIELD:
                    processGetBlobField(exchange, callback);
                    break;
                case QUERY:
                    processQuery(exchange, callback);
                    break;
                case QUERY_MORE:
                    processQueryMore(exchange, callback);
                    break;
                case QUERY_ALL:
                    processQueryAll(exchange, callback);
                    break;
                case SEARCH:
                    processSearch(exchange, callback);
                    break;
                case APEX_CALL:
                    processApexCall(exchange, callback);
                    break;
                case RECENT:
                    processRecent(exchange, callback);
                    break;
                case LIMITS:
                    processLimits(exchange, callback);
                    break;
                case APPROVAL:
                    processApproval(exchange, callback);
                    break;
                case APPROVALS:
                    processApprovals(exchange, callback);
                    break;
                default:
                    throw new SalesforceException("Unknown operation name: " + operationName.value(), null);
            }
        } catch (SalesforceException e) {
            exchange.setException(new SalesforceException(String.format("Error processing %s: [%s] \"%s\"", operationName.value(), e.getStatusCode(), e.getMessage()), e));
            callback.done(true);
            return true;
        } catch (RuntimeException e) {
            exchange.setException(new SalesforceException(String.format("Unexpected Error processing %s: \"%s\"", operationName.value(), e.getMessage()), e));
            callback.done(true);
            return true;
        }

        // continue routing asynchronously
        return false;
    }

    final void processApproval(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        final TypeConverter converter = exchange.getContext().getTypeConverter();

        final ApprovalRequest approvalRequestFromHeader = getParameter(SalesforceEndpointConfig.APPROVAL, exchange, IGNORE_BODY, IS_OPTIONAL, ApprovalRequest.class);
        final boolean requestGivenInHeader = approvalRequestFromHeader != null;

        // find if there is a ApprovalRequest as `approval` in the message
        // header
        final ApprovalRequest approvalHeader = Optional.ofNullable(approvalRequestFromHeader).orElse(new ApprovalRequest());

        final Message incomingMessage = exchange.getIn();

        final Map<String, Object> incomingHeaders = incomingMessage.getHeaders();

        final boolean requestGivenInParametersInHeader = processApprovalHeaderValues(approvalHeader, incomingHeaders);

        final boolean nothingInheader = !requestGivenInHeader && !requestGivenInParametersInHeader;

        final Object approvalBody = incomingMessage.getBody();

        final boolean bodyIsIterable = approvalBody instanceof Iterable;
        final boolean bodyIsIterableButEmpty = bodyIsIterable && !((Iterable)approvalBody).iterator().hasNext();

        // body contains nothing of interest if it's null, holds an empty
        // iterable or cannot be converted to
        // ApprovalRequest
        final boolean nothingInBody = !(approvalBody != null && !bodyIsIterableButEmpty);

        // we found nothing in the headers or the body
        if (nothingInheader && nothingInBody) {
            throw new SalesforceException("Missing " + SalesforceEndpointConfig.APPROVAL + " parameter in header or ApprovalRequest or List of ApprovalRequests body", 0);
        }

        // let's try to resolve the request body to send
        final ApprovalRequests requestsBody;
        if (nothingInBody) {
            // nothing in body use the header values only
            requestsBody = new ApprovalRequests(approvalHeader);
        } else if (bodyIsIterable) {
            // multiple ApprovalRequests are found
            final Iterable<?> approvalRequests = (Iterable<?>)approvalBody;

            // use header values as template and apply them to the body
            final List<ApprovalRequest> requests = StreamSupport.stream(approvalRequests.spliterator(), false).map(value -> converter.convertTo(ApprovalRequest.class, value))
                    .map(request -> request.applyTemplate(approvalHeader)).collect(Collectors.toList());

            requestsBody = new ApprovalRequests(requests);
        } else {
            // we've looked at the body, and are expecting to see something
            // resembling ApprovalRequest in there
            // but lets see if that is so
            final ApprovalRequest given = converter.tryConvertTo(ApprovalRequest.class, approvalBody);

            final ApprovalRequest request = Optional.ofNullable(given).orElse(new ApprovalRequest()).applyTemplate(approvalHeader);

            requestsBody = new ApprovalRequests(request);
        }

        final InputStream request = getRequestStream(incomingMessage, requestsBody);

        restClient.approval(request, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    final boolean processApprovalHeaderValues(final ApprovalRequest approvalRequest, final Map<String, Object> incomingHeaderValues) {
        // loop trough all header values, find those that start with `approval.`
        // set the property value to the given approvalRequest and return if
        // any value was set
        return incomingHeaderValues.entrySet().stream().filter(kv -> kv.getKey().startsWith("approval.")).map(kv -> {
            final String property = kv.getKey().substring(9);
            Object value = kv.getValue();

            if (value != null) {
                try {
                    setPropertyValue(approvalRequest, property, value);

                    return true;
                } catch (SalesforceException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            return false;
        }).reduce(false, (a, b) -> a || b);
    }

    private void processApprovals(final Exchange exchange, final AsyncCallback callback) {
        restClient.approvals(determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetVersions(final Exchange exchange, final AsyncCallback callback) {
        restClient.getVersions(determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetResources(final Exchange exchange, final AsyncCallback callback) {
        restClient.getResources(determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetGlobalObjects(final Exchange exchange, final AsyncCallback callback) {
        restClient.getGlobalObjects(determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetBasicInfo(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName = getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL);
        restClient.getBasicInfo(sObjectName, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetDescription(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        sObjectName = getParameter(SOBJECT_NAME, exchange, USE_BODY, NOT_OPTIONAL);
        restClient.getDescription(sObjectName, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processGetSobject(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        String sObjectIdValue;
        // determine parameters from input AbstractSObject
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            sObjectIdValue = sObjectBase.getId();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        final String sObjectId = sObjectIdValue;

        // use sObject name to load class
        setResponseClass(exchange, sObjectName);

        // get optional field list
        String fieldsValue = getParameter(SOBJECT_FIELDS, exchange, IGNORE_BODY, IS_OPTIONAL);
        String[] fields = null;
        if (fieldsValue != null) {
            fields = fieldsValue.split(",");
        }

        restClient.getSObject(sObjectName, sObjectId, fields, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processCreateSobject(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        // determine parameters from input AbstractSObject
        AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
        }

        restClient.createSObject(sObjectName, getRequestStream(exchange), determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processUpdateSobject(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        // determine parameters from input AbstractSObject
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        String sObjectId;
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            // remember the sObject Id
            sObjectId = sObjectBase.getId();
            // clear base object fields, which cannot be updated
            sObjectBase.clearBaseFields();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectId = getParameter(SOBJECT_ID, exchange, IGNORE_BODY, NOT_OPTIONAL);
        }

        final String finalsObjectId = sObjectId;
        restClient.updateSObject(sObjectName, sObjectId, getRequestStream(exchange), determineHeaders(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, finalsObjectId, null, null);
            }
        });
    }

    private void processDeleteSobject(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        // determine parameters from input AbstractSObject
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        String sObjectIdValue;
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            sObjectIdValue = sObjectBase.getId();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        final String sObjectId = sObjectIdValue;

        restClient.deleteSObject(sObjectName, sObjectId, determineHeaders(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, sObjectId, null, null);
            }
        });
    }

    private void processGetSobjectWithId(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        Object oldValue = null;
        String sObjectExtIdValue;
        final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);

        // determine parameters from input AbstractSObject
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
            sObjectExtIdValue = oldValue.toString();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, USE_BODY, NOT_OPTIONAL);
        }

        // use sObject name to load class
        setResponseClass(exchange, sObjectName);

        final Object finalOldValue = oldValue;
        restClient.getSObjectWithId(sObjectName, sObjectExtIdName, sObjectExtIdValue, determineHeaders(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
            }
        });
    }

    private void processUpsertSobject(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        String sObjectExtIdValue;
        final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);

        // determine parameters from input AbstractSObject
        Object oldValue = null;
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
            sObjectExtIdValue = oldValue.toString();
            // clear base object fields, which cannot be updated
            sObjectBase.clearBaseFields();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, IGNORE_BODY, NOT_OPTIONAL);
        }

        final Object finalOldValue = oldValue;
        restClient.upsertSObject(sObjectName, sObjectExtIdName, sObjectExtIdValue, determineHeaders(exchange), getRequestStream(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
            }
        });
    }

    private void processDeleteSobjectWithId(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        final String sObjectExtIdName = getParameter(SOBJECT_EXT_ID_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);

        // determine parameters from input AbstractSObject
        Object oldValue = null;
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        String sObjectExtIdValue;
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            oldValue = getAndClearPropertyValue(sObjectBase, sObjectExtIdName);
            sObjectExtIdValue = oldValue.toString();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectExtIdValue = getParameter(SOBJECT_EXT_ID_VALUE, exchange, USE_BODY, NOT_OPTIONAL);
        }

        final Object finalOldValue = oldValue;
        restClient.deleteSObjectWithId(sObjectName, sObjectExtIdName, sObjectExtIdValue, determineHeaders(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, null, sObjectExtIdName, finalOldValue);
            }
        });
    }

    private void processGetBlobField(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        String sObjectName;
        // get blob field name
        final String sObjectBlobFieldName = getParameter(SOBJECT_BLOB_FIELD_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);

        // determine parameters from input AbstractSObject
        final AbstractSObjectBase sObjectBase = exchange.getIn().getBody(AbstractSObjectBase.class);
        String sObjectIdValue;
        if (sObjectBase != null) {
            sObjectName = sObjectBase.getClass().getSimpleName();
            sObjectIdValue = sObjectBase.getId();
        } else {
            sObjectName = getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, NOT_OPTIONAL);
            sObjectIdValue = getParameter(SOBJECT_ID, exchange, USE_BODY, NOT_OPTIONAL);
        }
        final String sObjectId = sObjectIdValue;

        restClient.getBlobField(sObjectName, sObjectId, sObjectBlobFieldName, determineHeaders(exchange), new RestClient.ResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException exception) {
                processResponse(exchange, response, headers, exception, callback);
                restoreFields(exchange, sObjectBase, sObjectId, null, null);
            }
        });
    }

    private void processQuery(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        final String sObjectQuery = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);

        // use custom response class property
        setResponseClass(exchange, null);

        restClient.query(sObjectQuery, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processQueryMore(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        // reuse SOBJECT_QUERY parameter name for nextRecordsUrl
        final String nextRecordsUrl = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);

        // use custom response class property
        setResponseClass(exchange, null);

        restClient.queryMore(nextRecordsUrl, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processQueryAll(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        final String sObjectQuery = getParameter(SOBJECT_QUERY, exchange, USE_BODY, NOT_OPTIONAL);

        // use custom response class property
        setResponseClass(exchange, null);

        restClient.queryAll(sObjectQuery, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processSearch(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {
        final String sObjectSearch = getParameter(SOBJECT_SEARCH, exchange, USE_BODY, NOT_OPTIONAL);

        restClient.search(sObjectSearch, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processApexCall(final Exchange exchange, final AsyncCallback callback) throws SalesforceException {

        // HTTP method, URL and query params for APEX call
        final String apexUrl = getApexUrl(exchange);
        String apexMethod = getParameter(APEX_METHOD, exchange, IGNORE_BODY, IS_OPTIONAL);
        // default to GET
        if (apexMethod == null) {
            apexMethod = "GET";
            log.debug("Using HTTP GET method by default for APEX REST call for {}", apexUrl);
        }
        final Map<String, Object> queryParams = getQueryParams(exchange);

        // set response class
        setResponseClass(exchange, getParameter(SOBJECT_NAME, exchange, IGNORE_BODY, IS_OPTIONAL));

        // set request stream
        final Object requestBody = exchange.getIn().getBody();
        final InputStream requestDto = (requestBody != null && !(requestBody instanceof Map)) ? getRequestStream(exchange) : null;

        restClient.apexCall(apexMethod, apexUrl, queryParams, requestDto, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private String getApexUrl(Exchange exchange) throws SalesforceException {
        final String apexUrl = getParameter(APEX_URL, exchange, IGNORE_BODY, NOT_OPTIONAL);

        final Matcher matcher = URL_TEMPLATE.matcher(apexUrl);
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            // append part before parameter template
            result.append(apexUrl, start, matcher.start());
            start = matcher.end();

            // append template value from exchange header
            final String parameterName = matcher.group(1);
            final Object value = exchange.getIn().getHeader(parameterName);
            if (value == null) {
                throw new IllegalArgumentException("Missing APEX URL template header " + parameterName);
            }
            try {
                result.append(URLEncoder.encode(String.valueOf(value), "UTF-8").replace("+", "%20"));
            } catch (UnsupportedEncodingException e) {
                throw new SalesforceException("Unexpected error: " + e.getMessage(), e);
            }
        }
        if (start != 0) {
            // append remaining URL
            result.append(apexUrl, start, apexUrl.length());
            final String resolvedUrl = result.toString();
            log.debug("Resolved APEX URL {} to {}", apexUrl, resolvedUrl);
            return resolvedUrl;
        }
        return apexUrl;
    }

    private void processRecent(Exchange exchange, AsyncCallback callback) throws SalesforceException {
        final Integer limit = getParameter(SalesforceEndpointConfig.LIMIT, exchange, true, true, Integer.class);

        restClient.recent(limit, determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    private void processLimits(Exchange exchange, AsyncCallback callback) {
        restClient.limits(determineHeaders(exchange), processWithResponseCallback(exchange, callback));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getQueryParams(Exchange exchange) {

        // use endpoint map
        Map<String, Object> queryParams = new HashMap<>(endpoint.getConfiguration().getApexQueryParams());

        // look for individual properties, allowing endpoint properties to be
        // overridden
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getKey().startsWith(APEX_QUERY_PARAM_PREFIX)) {
                queryParams.put(entry.getKey().substring(APEX_QUERY_PARAM_PREFIX.length()), entry.getValue());
            }
        }
        // add params from body if it's a map
        final Object body = exchange.getIn().getBody();
        if (body instanceof Map) {
            queryParams.putAll((Map<String, Object>)body);
        }

        log.debug("Using APEX query params {}", queryParams);
        return queryParams;
    }

    private void restoreFields(Exchange exchange, AbstractSObjectBase sObjectBase, String sObjectId, String sObjectExtIdName, Object oldValue) {
        // restore fields
        if (sObjectBase != null) {
            // restore the Id if it was cleared
            if (sObjectId != null) {
                sObjectBase.setId(sObjectId);
            }
            // restore the external id if it was cleared
            if (sObjectExtIdName != null && oldValue != null) {
                try {
                    setPropertyValue(sObjectBase, sObjectExtIdName, oldValue);
                } catch (SalesforceException e) {
                    // YES, the exchange may fail if the property cannot be
                    // reset!!!
                    exchange.setException(e);
                }
            }
        }
    }

    private void setPropertyValue(Object sObjectBase, String name, Object value) throws SalesforceException {
        try {
            // set the value with the set method
            Method setMethod = sObjectBase.getClass().getMethod("set" + name, value.getClass());
            setMethod.invoke(sObjectBase, value);
        } catch (NoSuchMethodException e) {
            throw new SalesforceException(String.format("SObject %s does not have a field %s", sObjectBase.getClass().getName(), name), e);
        } catch (InvocationTargetException e) {
            throw new SalesforceException(String.format("Error setting value %s.%s", sObjectBase.getClass().getSimpleName(), name), e);
        } catch (IllegalAccessException e) {
            throw new SalesforceException(String.format("Error accessing value %s.%s", sObjectBase.getClass().getSimpleName(), name), e);
        }
    }

    private Object getAndClearPropertyValue(AbstractSObjectBase sObjectBase, String propertyName) throws SalesforceException {
        try {
            // obtain the value using the get method
            Method getMethod = sObjectBase.getClass().getMethod("get" + propertyName);
            Object value = getMethod.invoke(sObjectBase);

            // clear the value with the set method
            Method setMethod = sObjectBase.getClass().getMethod("set" + propertyName, getMethod.getReturnType());
            setMethod.invoke(sObjectBase, new Object[] {null});

            return value;
        } catch (NoSuchMethodException e) {
            throw new SalesforceException(String.format("SObject %s does not have a field %s", sObjectBase.getClass().getSimpleName(), propertyName), e);
        } catch (InvocationTargetException e) {
            throw new SalesforceException(String.format("Error getting/setting value %s.%s", sObjectBase.getClass().getSimpleName(), propertyName), e);
        } catch (IllegalAccessException e) {
            throw new SalesforceException(String.format("Error accessing value %s.%s", sObjectBase.getClass().getSimpleName(), propertyName), e);
        }
    }

    // pre-process request message
    protected abstract void processRequest(Exchange exchange) throws SalesforceException;

    // get request stream from In message
    protected abstract InputStream getRequestStream(Exchange exchange) throws SalesforceException;

    /**
     * Returns {@link InputStream} to serialized form of the given object.
     *
     * @param object object to serialize
     * @return stream to read serialized object from
     */
    protected abstract InputStream getRequestStream(Message in, Object object) throws SalesforceException;

    private void setResponseClass(Exchange exchange, String sObjectName) throws SalesforceException {

        // nothing to do if using rawPayload
        if (rawPayload) {
            return;
        }

        Class<?> sObjectClass;

        if (sObjectName != null) {
            // lookup class from class map
            sObjectClass = classMap.get(sObjectName);
            if (null == sObjectClass) {
                throw new SalesforceException(String.format("No class found for SObject %s", sObjectName), null);
            }

        } else {

            // use custom response class property
            final String className = getParameter(SOBJECT_CLASS, exchange, IGNORE_BODY, NOT_OPTIONAL);
            try {
                sObjectClass = endpoint.getComponent().getCamelContext().getClassResolver().resolveMandatoryClass(className);
            } catch (ClassNotFoundException e) {
                throw new SalesforceException(String.format("SObject class not found %s, %s", className, e.getMessage()), e);
            }
        }
        exchange.setProperty(RESPONSE_CLASS, sObjectClass);
    }

    final ResponseCallback processWithResponseCallback(final Exchange exchange, final AsyncCallback callback) {
        return (response, headers, exception) -> processResponse(exchange, response, headers, exception, callback);
    }

    // process response entity and set out message in exchange
    protected abstract void processResponse(Exchange exchange, InputStream responseEntity, Map<String, String> headers, SalesforceException ex, AsyncCallback callback);

    final boolean shouldReport(SalesforceException ex) {
        return !(ex instanceof NoSuchSObjectException && notFoundBehaviour == NotFoundBehaviour.NULL);
    }

}
