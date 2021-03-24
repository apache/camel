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
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.DeleteSObjectResult;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.SaveSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCollection;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.dto.composite.RetrieveSObjectCollectionsDto;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;

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

        final ContentProvider content = serialize(retrieveDto);
        request.content(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(
                        tryToReadListResponse(
                                sobjectType, response),
                        headers,
                        ex);
            }
        });
    }

    @Override
    public void submitCompositeCollections(
            final SObjectCollection collection, final Map<String, List<String>> headers,
            final ResponseCallback<List<SaveSObjectResult>> callback, final String sObjectName,
            final String externalIdFieldName, final String method)
            throws SalesforceException {

        String url = versionUrl() + "composite/sobjects";
        if (sObjectName != null) {
            url = url + "/" + sObjectName;
            if (externalIdFieldName != null) {
                url = url + "/" + externalIdFieldName;
            }
        }
        Request request = createRequest(method, url, headers);

        final ContentProvider content = serialize(collection);
        request.content(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(
                        tryToReadListResponse(SaveSObjectResult.class, response),
                        headers, ex);
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
                callback.onResponse(
                        tryToReadListResponse(DeleteSObjectResult.class, response),
                        headers, ex);
            }
        });
    }

    @Override
    protected SalesforceException createRestException(final Response response, final InputStream responseContent) {
        final List<RestError> errors;
        try {
            errors = readErrorsFrom(responseContent, PayloadFormat.JSON, mapper);
        } catch (final IOException e) {
            return new SalesforceException("Unable to read error response", e);
        }

        final int status = response.getStatus();
        if (status == HttpStatus.NOT_FOUND_404) {
            return new NoSuchSObjectException(errors);
        }
        final String reason = response.getReason();
        return new SalesforceException("Unexpected error: " + reason, status);
    }

    @Override
    protected void setAccessToken(final Request request) {
        request.getHeaders().put("Authorization", "Bearer " + accessToken);
    }

    private Request createRequest(final String method, final String url, final Map<String, List<String>> headers) {
        final Request request = getRequest(method, url, headers);
        return populateRequest(request);
    }

    private Request populateRequest(Request request) {
        // setup authorization
        setAccessToken(request);

        request.header(HttpHeader.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        request.header(HttpHeader.ACCEPT, APPLICATION_JSON_UTF8);

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

    private ContentProvider serialize(final Object body, final Class<?>... additionalTypes)
            throws SalesforceException {
        return new InputStreamContentProvider(toJson(body));
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
