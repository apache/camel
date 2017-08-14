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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.TreeMarshallingStrategy;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AnnotationFieldKeySorter;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatchResponse;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTreeResponse;
import org.apache.camel.component.salesforce.api.utils.DateTimeConverter;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.Version;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCompositeApiClient extends AbstractClientBase implements CompositeApiClient {

    private static final Class[] ADDITIONAL_TYPES = new Class[] {SObjectTree.class, SObjectTreeResponse.class,
        SObjectBatchResponse.class};

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCompositeApiClient.class);

    private final PayloadFormat format;

    private ObjectMapper mapper;

    private final Map<Class<?>, ObjectReader> readers = new HashMap<>();

    private final Map<Class<?>, ObjectWriter> writters = new HashMap<>();

    private final XStream xStream;

    public DefaultCompositeApiClient(final SalesforceEndpointConfig configuration, final PayloadFormat format,
        final String version, final SalesforceSession session, final SalesforceHttpClient httpClient)
        throws SalesforceException {
        super(version, session, httpClient);
        this.format = format;

        if (configuration.getObjectMapper() != null) {
            mapper = configuration.getObjectMapper();
        } else {
            mapper = JsonUtils.createObjectMapper();
        }

        xStream = configureXStream();
    }

    static XStream configureXStream() {
        final PureJavaReflectionProvider reflectionProvider = new PureJavaReflectionProvider(
            new FieldDictionary(new AnnotationFieldKeySorter()));

        final XppDriver hierarchicalStreamDriver = new XppDriver(new NoNameCoder()) {
            @Override
            public HierarchicalStreamWriter createWriter(final Writer out) {
                return new CompactWriter(out, getNameCoder());
            }

        };

        final XStream xStream = new XStream(reflectionProvider, hierarchicalStreamDriver);
        xStream.aliasSystemAttribute(null, "class");
        xStream.ignoreUnknownElements();
        XStreamUtils.addDefaultPermissions(xStream);
        xStream.registerConverter(new DateTimeConverter());
        xStream.setMarshallingStrategy(new TreeMarshallingStrategy());
        xStream.processAnnotations(ADDITIONAL_TYPES);

        return xStream;
    }

    @Override
    public void submitCompositeBatch(final SObjectBatch batch, final ResponseCallback<SObjectBatchResponse> callback)
        throws SalesforceException {
        checkCompositeBatchVersion(version, batch.getVersion());

        final String url = versionUrl() + "composite/batch";

        final Request post = createRequest(HttpMethod.POST, url);

        final ContentProvider content = serialize(batch, batch.objectTypes());
        post.content(content);

        doHttpRequest(post, (response, exception) -> callback
            .onResponse(tryToReadResponse(SObjectBatchResponse.class, response), exception));
    }

    @Override
    public void submitCompositeTree(final SObjectTree tree, final ResponseCallback<SObjectTreeResponse> callback)
        throws SalesforceException {
        final String url = versionUrl() + "composite/tree/" + tree.getObjectType();

        final Request post = createRequest(HttpMethod.POST, url);

        final ContentProvider content = serialize(tree, tree.objectTypes());
        post.content(content);

        doHttpRequest(post, (response, exception) -> callback
            .onResponse(tryToReadResponse(SObjectTreeResponse.class, response), exception));
    }

    static void checkCompositeBatchVersion(final String configuredVersion, final Version batchVersion)
        throws SalesforceException {
        if (Version.create(configuredVersion).compareTo(batchVersion) < 0) {
            throw new SalesforceException("Component is configured with Salesforce API version " + configuredVersion
                + ", but the payload of the Composite API batch operation requires at least " + batchVersion, 0);
        }
    }

    Request createRequest(final HttpMethod method, final String url) {
        final Request request = getRequest(method, url);

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

    <T> T fromXml(final InputStream responseStream) {
        @SuppressWarnings("unchecked")
        final T read = (T) xStream.fromXML(responseStream);

        return read;
    }

    ObjectReader jsonReaderFor(final Class<?> type) {
        return Optional.ofNullable(readers.get(type)).orElseGet(() -> mapper.readerFor(type));
    }

    ObjectWriter jsonWriterFor(final Object obj) {
        final Class<?> type = obj.getClass();

        return Optional.ofNullable(writters.get(type)).orElseGet(() -> mapper.writerFor(type));
    }

    ContentProvider serialize(final Object body, final Class<?>... additionalTypes) throws SalesforceException {
        final InputStream stream;
        if (format == PayloadFormat.JSON) {
            stream = toJson(body);
        } else {
            // must be XML
            stream = toXml(body, additionalTypes);
        }

        // input stream as entity content is needed for authentication retries
        return new InputStreamContentProvider(stream);
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

    InputStream toXml(final Object obj, final Class<?>... additionalTypes) {
        xStream.processAnnotations(additionalTypes);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        xStream.toXML(obj, out);

        return new ByteArrayInputStream(out.toByteArray());
    }

    <T> Optional<T> tryToReadResponse(final Class<T> expectedType, final InputStream responseStream) {
        try {
            if (format == PayloadFormat.JSON) {
                return Optional.of(fromJson(expectedType, responseStream));
            } else {
                // must be XML
                return Optional.of(fromXml(responseStream));
            }
        } catch (XStreamException | IOException e) {
            LOG.warn("Unable to read response from the Composite API", e);
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
            errors = readErrorsFrom(responseContent, format, mapper, xStream);
        } catch (IOException e) {
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

}
