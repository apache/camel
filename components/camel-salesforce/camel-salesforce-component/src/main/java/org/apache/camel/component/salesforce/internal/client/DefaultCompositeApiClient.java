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
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTreeResponse;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.Version;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.StringUtil;

public class DefaultCompositeApiClient extends AbstractClientBase implements CompositeApiClient {

    private ObjectMapper mapper;

    public DefaultCompositeApiClient(
                                     final SalesforceEndpointConfig configuration,
                                     final String version, final SalesforceSession session,
                                     final SalesforceHttpClient httpClient, final SalesforceLoginConfig loginConfig)
                                                                                                                     throws SalesforceException {
        super(version, session, httpClient, loginConfig);

        if (configuration.getObjectMapper() != null) {
            mapper = configuration.getObjectMapper();
        } else {
            mapper = JsonUtils.createObjectMapper();
        }
    }

    public void submitCompositeRaw(
            final InputStream raw, final Map<String, List<String>> headers,
            final ResponseCallback<InputStream> callback, String compositeMethod)
            throws SalesforceException {

        final String url = versionUrl() + "composite";

        if (compositeMethod == null) {
            compositeMethod = HttpMethod.POST.asString();
        }
        Request request = createRequest(compositeMethod, url, headers);

        final ContentProvider content = new InputStreamContentProvider(raw);
        request.content(content);

        doHttpRequest(request, new ClientResponseCallback() {
            @Override
            public void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex) {
                callback.onResponse(Optional.ofNullable(response), headers, ex);
            }
        });
    }

    @Override
    public void submitComposite(
            final SObjectComposite composite, final Map<String, List<String>> headers,
            final ResponseCallback<SObjectCompositeResponse> callback)
            throws SalesforceException {
        final String url = versionUrl() + "composite";
        final Request post = createRequest(HttpMethod.POST, url, headers);
        final ContentProvider content = serialize(composite, composite.objectTypes());
        post.content(content);

        doHttpRequest(post,
                (response, responseHeaders, exception) -> callback.onResponse(
                        tryToReadResponse(SObjectCompositeResponse.class, response), responseHeaders,
                        exception));
    }

    @Override
    public void submitCompositeBatch(
            final SObjectBatch batch, final Map<String, List<String>> headers,
            final ResponseCallback<SObjectBatchResponse> callback)
            throws SalesforceException {
        checkCompositeBatchVersion(version, batch.getVersion());

        final String url = versionUrl() + "composite/batch";

        final Request post = createRequest(HttpMethod.POST, url, headers);

        final ContentProvider content = serialize(batch, batch.objectTypes());
        post.content(content);

        doHttpRequest(post,
                (response, responseHeaders, exception) -> callback.onResponse(
                        tryToReadResponse(SObjectBatchResponse.class, response),
                        responseHeaders, exception));
    }

    @Override
    public void submitCompositeTree(
            final SObjectTree tree, final Map<String, List<String>> headers,
            final ResponseCallback<SObjectTreeResponse> callback)
            throws SalesforceException {
        final String url = versionUrl() + "composite/tree/" + tree.getObjectType();

        final Request post = createRequest(HttpMethod.POST, url, headers);

        final ContentProvider content = serialize(tree, tree.objectTypes());
        post.content(content);

        doHttpRequest(post,
                (response, responseHeaders, exception) -> callback.onResponse(
                        tryToReadResponse(SObjectTreeResponse.class, response),
                        responseHeaders, exception));
    }

    Request createRequest(final String method, final String url, final Map<String, List<String>> headers) {
        final Request request = getRequest(method, url, headers);
        return populateRequest(request);
    }

    Request createRequest(final HttpMethod method, final String url, final Map<String, List<String>> headers) {
        final Request request = getRequest(method, url, headers);
        return populateRequest(request);
    }

    private Request populateRequest(Request request) {
        // setup authorization
        setAccessToken(request);

        request.header(HttpHeader.CONTENT_TYPE, APPLICATION_JSON_UTF8);
        request.header(HttpHeader.ACCEPT, APPLICATION_JSON_UTF8);
        request.header(HttpHeader.ACCEPT_CHARSET, StringUtil.__UTF8);

        return request;
    }

    <T> T fromJson(final Class<T> expectedType, final InputStream responseStream) throws IOException {
        return jsonReaderFor(expectedType).readValue(responseStream);
    }

    <T> List<T> fromJsonList(final Class<T> expectedType, final InputStream responseStream) throws IOException {
        final CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(List.class, expectedType);
        return mapper.readValue(responseStream, collectionType);
    }

    ObjectReader jsonReaderFor(final Class<?> type) {
        return mapper.readerFor(type);
    }

    ObjectWriter jsonWriterFor(final Object obj) {
        final Class<?> type = obj.getClass();

        return mapper.writerFor(type);
    }

    ContentProvider serialize(final Object body, final Class<?>... additionalTypes)
            throws SalesforceException {
        // input stream as entity content is needed for authentication retries
        return new InputStreamContentProvider(toJson(body));
    }

    String servicesDataUrl() {
        return instanceUrl + "/services/data/";
    }

    InputStream toJson(final Object obj) throws SalesforceException {
        byte[] jsonBytes;
        try {
            jsonBytes = jsonWriterFor(obj).writeValueAsBytes(obj);
        } catch (final JsonProcessingException e) {
            throw new SalesforceException("Unable to serialize given SObjectTree to JSON", e);
        }

        return new ByteArrayInputStream(jsonBytes);
    }

    <T> Optional<T> tryToReadResponse(final Class<T> expectedType, final InputStream responseStream) {
        if (responseStream == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromJson(expectedType, responseStream));
        } catch (IOException e) {
            log.warn("Unable to read response from the Composite API", e);
            return Optional.empty();
        } finally {
            IOHelper.close(responseStream);
        }
    }

    String versionUrl() {
        ObjectHelper.notNull(version, "version");

        return servicesDataUrl() + "v" + version + "/";
    }

    @Override
    protected void setAccessToken(final Request request) {
        request.header("Authorization", null);
        request.header("Authorization", "Bearer " + accessToken);
    }

    static void checkCompositeBatchVersion(final String configuredVersion, final Version batchVersion)
            throws SalesforceException {
        if (Version.create(configuredVersion).compareTo(batchVersion) < 0) {
            throw new SalesforceException(
                    "Component is configured with Salesforce API version " + configuredVersion
                                          + ", but the payload of the Composite API batch operation requires at least "
                                          + batchVersion,
                    0);
        }
    }
}
