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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectCompositeResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTreeResponse;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.Version;
import org.apache.camel.component.salesforce.api.utils.XStreamUtils;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.StringUtil;

public class DefaultCompositeApiClient extends AbstractClientBase implements CompositeApiClient {

    // Composite (non-tree, non-batch) does not support XML format
    private static final XStream NO_XSTREAM = null;

    private final PayloadFormat format;

    private ObjectMapper mapper;

    private final XStream xStreamCompositeBatch;

    private final XStream xStreamCompositeTree;

    public DefaultCompositeApiClient(final SalesforceEndpointConfig configuration, final PayloadFormat format, final String version, final SalesforceSession session,
                                     final SalesforceHttpClient httpClient)
        throws SalesforceException {
        super(version, session, httpClient);
        this.format = format;

        if (configuration.getObjectMapper() != null) {
            mapper = configuration.getObjectMapper();
        } else {
            mapper = JsonUtils.createObjectMapper();
        }

        xStreamCompositeBatch = XStreamUtils.createXStream(SObjectBatch.class, SObjectBatchResponse.class);

        xStreamCompositeTree = XStreamUtils.createXStream(SObjectTree.class, SObjectTreeResponse.class);
        // newer Salesforce API versions return `<SObjectTreeResponse>` element,
        // older versions
        // return `<Result>` element
        xStreamCompositeTree.alias("SObjectTreeResponse", SObjectTreeResponse.class);
    }

    @Override
    public void submitComposite(final SObjectComposite composite, final Map<String, List<String>> headers, final ResponseCallback<SObjectCompositeResponse> callback)
        throws SalesforceException {
        // composite interface supports only json payload
        checkCompositeFormat(format, SObjectComposite.REQUIRED_PAYLOAD_FORMAT);

        final String url = versionUrl() + "composite";

        final Request post = createRequest(HttpMethod.POST, url, headers);

        final ContentProvider content = serialize(NO_XSTREAM, composite, composite.objectTypes());
        post.content(content);

        doHttpRequest(post, (response, responseHeaders, exception) -> callback.onResponse(tryToReadResponse(NO_XSTREAM, SObjectCompositeResponse.class, response), responseHeaders,
                                                                                          exception));
    }

    @Override
    public void submitCompositeBatch(final SObjectBatch batch, final Map<String, List<String>> headers, final ResponseCallback<SObjectBatchResponse> callback)
        throws SalesforceException {
        checkCompositeBatchVersion(version, batch.getVersion());

        final String url = versionUrl() + "composite/batch";

        final Request post = createRequest(HttpMethod.POST, url, headers);

        final ContentProvider content = serialize(xStreamCompositeBatch, batch, batch.objectTypes());
        post.content(content);

        doHttpRequest(post, (response, responseHeaders, exception) -> callback.onResponse(tryToReadResponse(xStreamCompositeBatch, SObjectBatchResponse.class, response),
                                                                                          responseHeaders, exception));
    }

    @Override
    public void submitCompositeTree(final SObjectTree tree, final Map<String, List<String>> headers, final ResponseCallback<SObjectTreeResponse> callback)
        throws SalesforceException {
        final String url = versionUrl() + "composite/tree/" + tree.getObjectType();

        final Request post = createRequest(HttpMethod.POST, url, headers);

        final ContentProvider content = serialize(xStreamCompositeTree, tree, tree.objectTypes());
        post.content(content);

        doHttpRequest(post, (response, responseHeaders, exception) -> callback.onResponse(tryToReadResponse(xStreamCompositeTree, SObjectTreeResponse.class, response),
                                                                                          responseHeaders, exception));
    }

    Request createRequest(final HttpMethod method, final String url, final Map<String, List<String>> headers) {
        final Request request = getRequest(method, url, headers);

        // setup authorization
        setAccessToken(request);

        if (format == PayloadFormat.JSON) {
            request.header(HttpHeader.CONTENT_TYPE, APPLICATION_JSON_UTF8);
            request.header(HttpHeader.ACCEPT, APPLICATION_JSON_UTF8);
        } else {
            // must be XML
            request.header(HttpHeader.CONTENT_TYPE, APPLICATION_XML_UTF8);
            request.header(HttpHeader.ACCEPT, APPLICATION_XML_UTF8);
        }

        request.header(HttpHeader.ACCEPT_CHARSET, StringUtil.__UTF8);

        return request;
    }

    <T> T fromJson(final Class<T> expectedType, final InputStream responseStream) throws IOException {
        return jsonReaderFor(expectedType).readValue(responseStream);
    }

    ObjectReader jsonReaderFor(final Class<?> type) {
        return mapper.readerFor(type);
    }

    ObjectWriter jsonWriterFor(final Object obj) {
        final Class<?> type = obj.getClass();

        return mapper.writerFor(type);
    }

    ContentProvider serialize(final XStream xstream, final Object body, final Class<?>... additionalTypes) throws SalesforceException {
        // input stream as entity content is needed for authentication retries
        if (format == PayloadFormat.JSON) {
            return new InputStreamContentProvider(toJson(body));
        }

        // must be XML
        xstream.processAnnotations(additionalTypes);
        return new InputStreamContentProvider(toXml(xstream, body));
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

    <T> Optional<T> tryToReadResponse(final XStream xstream, final Class<T> expectedType, final InputStream responseStream) {
        if (responseStream == null) {
            return Optional.empty();
        }
        try {
            if (format == PayloadFormat.JSON) {
                return Optional.of(fromJson(expectedType, responseStream));
            }

            // must be XML
            return Optional.of(fromXml(xstream, responseStream));
        } catch (XStreamException | IOException e) {
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
    protected SalesforceException createRestException(final Response response, final InputStream responseContent) {
        final List<RestError> errors;
        try {
            errors = readErrorsFrom(responseContent, format, mapper, xStreamCompositeTree);
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

    static void checkCompositeBatchVersion(final String configuredVersion, final Version batchVersion) throws SalesforceException {
        if (Version.create(configuredVersion).compareTo(batchVersion) < 0) {
            throw new SalesforceException("Component is configured with Salesforce API version " + configuredVersion
                                          + ", but the payload of the Composite API batch operation requires at least " + batchVersion, 0);
        }
    }

    static void checkCompositeFormat(final PayloadFormat configuredFormat, final PayloadFormat requiredFormat) throws SalesforceException {
        if (configuredFormat != requiredFormat) {
            throw new SalesforceException("Component is configured with Salesforce Composite API format " + configuredFormat
                                          + ", but the payload of the Composite API operation requires format " + requiredFormat, 0);
        }
    }

    static <T> T fromXml(final XStream xstream, final InputStream responseStream) {
        @SuppressWarnings("unchecked")
        final T read = (T)xstream.fromXML(responseStream);

        return read;
    }

    static InputStream toXml(final XStream xstream, final Object obj) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        xstream.toXML(obj, out);

        return new ByteArrayInputStream(out.toByteArray());
    }

}
