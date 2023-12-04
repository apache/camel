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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.DeleteSObjectResult;
import org.apache.camel.component.salesforce.api.dto.SaveSObjectResult;
import org.apache.camel.component.salesforce.api.dto.UpsertSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCollection;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.dto.composite.RetrieveSObjectCollectionsDto;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpHeader;

public class DefaultCompositeSObjectCollectionsApiClient extends AbstractClientBase
        implements CompositeSObjectCollectionsApiClient {

    private final ObjectMapper mapper;

    public DefaultCompositeSObjectCollectionsApiClient(final SalesforceEndpointConfig configuration,
                                                       final String version, final SalesforceSession session,
                                                       final SalesforceHttpClient httpClient,
                                                       final SalesforceLoginConfig loginConfig)
                                                                                                throws SalesforceException {
        super(version, session, httpClient, loginConfig);

        if (configuration.getObjectMapper() != null) {
            mapper = configuration.getObjectMapper();
        } else {
            mapper = JsonUtils.createObjectMapper();
        }
    }

    @Override
    public <T> void submitRetrieveCompositeCollections(
            final RetrieveSObjectCollectionsDto retrieveDto, final Map<String, List<String>> headers,
            final ResponseCallback<List<T>> callback, final String sObjectName,
            Class<T> sobjectType)
            throws SalesforceException {

        String url = versionUrl() + "composite/sobjects/" + sObjectName;
        Request request = createRequest("POST", url, headers);

        final Request.Content content = serialize(retrieveDto);
        request.body(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                Optional<List<T>> body = Optional.empty();
                if (ex == null) {
                    body = tryToReadListResponse(sobjectType, response);
                }
                callback.onResponse(body, headers, ex);
            }
        });
    }

    @Override
    public void createCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<SaveSObjectResult>> callback)
            throws SalesforceException {
        createUpdateCompositeCollections(collection, headers, callback, "POST");
    }

    @Override
    public void updateCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<SaveSObjectResult>> callback)
            throws SalesforceException {
        createUpdateCompositeCollections(collection, headers, callback, "PATCH");
    }

    private void createUpdateCompositeCollections(
            SObjectCollection collection, Map<String, List<String>> headers,
            ResponseCallback<List<SaveSObjectResult>> callback, String method)
            throws SalesforceException {

        String url = versionUrl() + "composite/sobjects";
        Request request = createRequest(method, url, headers);

        final Request.Content content = serialize(collection);
        request.body(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                Optional<List<SaveSObjectResult>> body = Optional.empty();
                if (ex == null) {
                    body = tryToReadListResponse(SaveSObjectResult.class, response);
                }
                callback.onResponse(body, headers, ex);
            }
        });
    }

    @Override
    public void upsertCompositeCollections(
            final SObjectCollection collection, final Map<String, List<String>> headers,
            final ResponseCallback<List<UpsertSObjectResult>> callback, final String sObjectName,
            final String externalIdFieldName)
            throws SalesforceException {

        String url = versionUrl() + "composite/sobjects";
        url = url + "/" + sObjectName;
        if (externalIdFieldName != null) {
            url = url + "/" + externalIdFieldName;
        }

        Request request = createRequest("PATCH", url, headers);

        final Request.Content content = serialize(collection);
        request.body(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                Optional<List<UpsertSObjectResult>> body = Optional.empty();
                if (ex == null) {
                    body = tryToReadListResponse(UpsertSObjectResult.class, response);
                }
                callback.onResponse(body, headers, ex);
            }
        });
    }

    @Override
    public void submitDeleteCompositeCollections(
            List<String> ids, Boolean allOrNone, final Map<String, List<String>> headers,
            final ResponseCallback<List<DeleteSObjectResult>> callback) {

        String url = versionUrl() + "composite/sobjects";
        Request request = createRequest("DELETE", url, headers)
                .param("ids", String.join(",", ids))
                .param("allOrNone", allOrNone.toString());

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                Optional<List<DeleteSObjectResult>> body = Optional.empty();
                if (ex == null) {
                    body = tryToReadListResponse(DeleteSObjectResult.class, response);
                }
                callback.onResponse(body, headers, ex);
            }
        });
    }

    @Override
    protected void setAccessToken(final Request request) {
        request.headers(h -> h.add("Authorization", "Bearer " + accessToken));
    }

    private Request createRequest(final String method, final String url, final Map<String, List<String>> headers) {
        final Request request = getRequest(method, url, headers);
        return populateRequest(request);
    }

    private Request populateRequest(Request request) {
        // setup authorization
        setAccessToken(request);

        request.headers(h -> h.add(HttpHeader.CONTENT_TYPE, APPLICATION_JSON_UTF8));
        request.headers(h -> h.add(HttpHeader.ACCEPT, APPLICATION_JSON_UTF8));

        return request;
    }

    private <T> List<T> fromJsonList(final Class<T> expectedType, final InputStream responseStream) throws IOException {
        final CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(List.class, expectedType);
        return mapper.readValue(responseStream, collectionType);
    }

    private ObjectWriter jsonWriterFor(final Object obj) {
        final Class<?> type = obj.getClass();

        return mapper.writerFor(type);
    }

    private Request.Content serialize(final Object body)
            throws SalesforceException {
        return new InputStreamRequestContent(toJson(body));
    }

    private String servicesDataUrl() {
        return instanceUrl + "/services/data/";
    }

    private InputStream toJson(final Object obj) throws SalesforceException {
        byte[] jsonBytes;
        try {
            jsonBytes = jsonWriterFor(obj).writeValueAsBytes(obj);
        } catch (final JsonProcessingException e) {
            throw new SalesforceException("Unable to serialize given SObjectTree to JSON", e);
        }

        return new ByteArrayInputStream(jsonBytes);
    }

    private <T> Optional<List<T>> tryToReadListResponse(
            final Class<T> expectedType, final InputStream responseStream) {
        if (responseStream == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromJsonList(expectedType, responseStream));
        } catch (IOException e) {
            log.warn("Unable to read response from the Composite API", e);
            return Optional.empty();
        } finally {
            IOHelper.close(responseStream);
        }
    }

    private String versionUrl() {
        ObjectHelper.notNull(version, "version");
        return servicesDataUrl() + "v" + version + "/";
    }
}
