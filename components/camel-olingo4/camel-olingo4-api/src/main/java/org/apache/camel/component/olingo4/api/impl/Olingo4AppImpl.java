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
package org.apache.camel.component.olingo4.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.component.olingo4.api.Olingo4App;
import org.apache.camel.component.olingo4.api.Olingo4ResponseHandler;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchChangeRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchQueryRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchRequest;
import org.apache.camel.component.olingo4.api.batch.Olingo4BatchResponse;
import org.apache.camel.component.olingo4.api.batch.Operation;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultHttpResponseParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicLineParser;
import org.apache.olingo.client.api.ODataBatchConstants;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.ODataStreamer;
import org.apache.olingo.client.api.communication.request.batch.ODataBatchLineIterator;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.client.api.domain.ClientProperty;
import org.apache.olingo.client.api.serialization.ODataReader;
import org.apache.olingo.client.api.serialization.ODataWriter;
import org.apache.olingo.client.api.uri.SegmentType;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.communication.request.batch.ODataBatchController;
import org.apache.olingo.client.core.communication.request.batch.ODataBatchLineIteratorImpl;
import org.apache.olingo.client.core.communication.request.batch.ODataBatchUtilities;
import org.apache.olingo.client.core.http.HttpMerge;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmReturnType;
import org.apache.olingo.commons.api.edm.constants.ODataServiceVersion;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriInfoKind;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceKind;
import org.apache.olingo.server.core.uri.UriResourceFunctionImpl;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.validator.UriValidationException;

/**
 * Application API used by Olingo4 Component.
 */
public final class Olingo4AppImpl implements Olingo4App {

    private static final String SEPARATOR = "/";
    private static final String BOUNDARY_PREFIX = "batch_";
    private static final String BOUNDARY_PARAMETER = "; boundary=";
    private static final String BOUNDARY_DOUBLE_DASH = "--";
    private static final String MULTIPART_MIME_TYPE = "multipart/";
    private static final String CONTENT_ID_HEADER = "Content-ID";
    private static final String CLIENT_ENTITY_FAKE_MARKER = "('X')";

    private static final ContentType DEFAULT_CONTENT_TYPE = ContentType.create(ContentType.APPLICATION_JSON, ContentType.PARAMETER_CHARSET, Constants.UTF8);
    private static final ContentType METADATA_CONTENT_TYPE = ContentType.create(ContentType.APPLICATION_XML, ContentType.PARAMETER_CHARSET, Constants.UTF8);
    private static final ContentType TEXT_PLAIN_WITH_CS_UTF_8 = ContentType.create(ContentType.TEXT_PLAIN, ContentType.PARAMETER_CHARSET, Constants.UTF8);
    private static final ContentType SERVICE_DOCUMENT_CONTENT_TYPE = ContentType.APPLICATION_JSON;
    private static final ContentType BATCH_CONTENT_TYPE = ContentType.MULTIPART_MIXED;

    /**
     * Reference to CloseableHttpAsyncClient (default) or CloseableHttpClient
     */
    private final Closeable client;

    /**
     * Reference to ODataClient reader and writer
     */
    private final ODataClient odataClient = ODataClientFactory.getClient();
    private final ODataReader odataReader = odataClient.getReader();
    private final ODataWriter odataWriter = odataClient.getWriter();

    private String serviceUri;
    private ContentType contentType;
    private Map<String, String> httpHeaders;

    /**
     * Create Olingo4 Application with default HTTP configuration.
     */
    public Olingo4AppImpl(String serviceUri) {
        // By default create HTTP asynchronous client
        this(serviceUri, (HttpClientBuilder)null);
    }

    /**
     * Create Olingo4 Application with custom HTTP Asynchronous client builder.
     *
     * @param serviceUri Service Application base URI.
     * @param builder custom HTTP client builder.
     */
    public Olingo4AppImpl(String serviceUri, HttpAsyncClientBuilder builder) {
        setServiceUri(serviceUri);

        CloseableHttpAsyncClient asyncClient;
        if (builder == null) {
            asyncClient = HttpAsyncClients.createDefault();
        } else {
            asyncClient = builder.build();
        }
        asyncClient.start();
        this.client = asyncClient;
        this.contentType = DEFAULT_CONTENT_TYPE;
    }

    /**
     * Create Olingo4 Application with custom HTTP Synchronous client builder.
     *
     * @param serviceUri Service Application base URI.
     * @param builder Custom HTTP Synchronous client builder.
     */
    public Olingo4AppImpl(String serviceUri, HttpClientBuilder builder) {
        setServiceUri(serviceUri);

        if (builder == null) {
            this.client = HttpClients.createDefault();
        } else {
            this.client = builder.build();
        }
        this.contentType = DEFAULT_CONTENT_TYPE;
    }

    @Override
    public void setServiceUri(String serviceUri) {
        if (serviceUri == null || serviceUri.isEmpty()) {
            throw new IllegalArgumentException("serviceUri");
        }
        this.serviceUri = serviceUri.endsWith(SEPARATOR) ? serviceUri.substring(0, serviceUri.length() - 1) : serviceUri;
    }

    @Override
    public String getServiceUri() {
        return serviceUri;
    }

    @Override
    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    @Override
    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    @Override
    public String getContentType() {
        return contentType.toContentTypeString();
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = ContentType.parse(contentType);
    }

    @Override
    public void close() {

    }

    @Override
    public <T> void read(final Edm edm, final String resourcePath, final Map<String, String> queryParams, final Olingo4ResponseHandler<T> responseHandler) {
        final String queryOptions = concatQueryParams(queryParams);
        final UriInfo uriInfo = parseUri(edm, resourcePath, queryOptions);

        execute(new HttpGet(createUri(resourcePath, queryOptions)), getResourceContentType(uriInfo), new AbstractFutureCallback<T>(responseHandler) {

            @Override
            public void onCompleted(HttpResponse result) throws IOException {
                readContent(uriInfo, result.getEntity() != null ? result.getEntity().getContent() : null, responseHandler);
            }

        });
    }

    @Override
    public void uread(final Edm edm, final String resourcePath, final Map<String, String> queryParams, final Olingo4ResponseHandler<InputStream> responseHandler) {
        final String queryOptions = concatQueryParams(queryParams);
        final UriInfo uriInfo = parseUri(edm, resourcePath, queryOptions);

        execute(new HttpGet(createUri(resourcePath, queryOptions)), getResourceContentType(uriInfo), new AbstractFutureCallback<InputStream>(responseHandler) {

            @Override
            public void onCompleted(HttpResponse result) throws IOException {
                InputStream responseStream = result.getEntity() != null ? result.getEntity().getContent() : null;
                if (responseStream != null && result.getEntity() instanceof DecompressingEntity) {
                    // In case of GZIP compression it's necessary to create
                    // InputStream from the source byte array
                    responseHandler.onResponse(new ByteArrayInputStream(IOUtils.toByteArray(responseStream)));
                } else {
                    responseHandler.onResponse(responseStream);
                }
            }
        });
    }

    @Override
    public <T> void create(Edm edm, String resourcePath, Object data, Olingo4ResponseHandler<T> responseHandler) {
        final UriInfo uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPost(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public <T> void update(Edm edm, String resourcePath, Object data, Olingo4ResponseHandler<T> responseHandler) {
        final UriInfo uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPut(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public void delete(String resourcePath, final Olingo4ResponseHandler<HttpStatusCode> responseHandler) {
        execute(new HttpDelete(createUri(resourcePath)), contentType, new AbstractFutureCallback<HttpStatusCode>(responseHandler) {
            @Override
            public void onCompleted(HttpResponse result) {
                final StatusLine statusLine = result.getStatusLine();
                responseHandler.onResponse(HttpStatusCode.fromStatusCode(statusLine.getStatusCode()));
            }
        });
    }

    @Override
    public <T> void patch(Edm edm, String resourcePath, Object data, Olingo4ResponseHandler<T> responseHandler) {
        final UriInfo uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPatch(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public <T> void merge(Edm edm, String resourcePath, Object data, Olingo4ResponseHandler<T> responseHandler) {
        final UriInfo uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpMerge(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public void batch(Edm edm, Object data, Olingo4ResponseHandler<List<Olingo4BatchResponse>> responseHandler) {
        final UriInfo uriInfo = parseUri(edm, SegmentType.BATCH.getValue(), null);

        writeContent(edm, new HttpPost(createUri(SegmentType.BATCH.getValue(), null)), uriInfo, data, responseHandler);
    }

    private ContentType getResourceContentType(UriInfo uriInfo) {
        ContentType resourceContentType;
        switch (uriInfo.getKind()) {
        case service:
            // service document
            resourceContentType = SERVICE_DOCUMENT_CONTENT_TYPE;
            break;
        case metadata:
            // metadata
            resourceContentType = METADATA_CONTENT_TYPE;
            break;
        case resource:
            List<UriResource> listResource = uriInfo.getUriResourceParts();
            UriResourceKind lastResourceKind = listResource.get(listResource.size() - 1).getKind();
            // is it a $value or $count URI??
            if (lastResourceKind == UriResourceKind.count || lastResourceKind == UriResourceKind.value) {
                resourceContentType = TEXT_PLAIN_WITH_CS_UTF_8;
            } else {
                resourceContentType = contentType;
            }
            break;
        default:
            resourceContentType = contentType;
        }
        return resourceContentType;
    }

    private <T> void readContent(UriInfo uriInfo, InputStream content, Olingo4ResponseHandler<T> responseHandler) {
        try {
            responseHandler.onResponse(this.<T> readContent(uriInfo, content));
        } catch (ODataException e) {
            responseHandler.onException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T readContent(UriInfo uriInfo, InputStream content) throws ODataException {
        T response = null;
        switch (uriInfo.getKind()) {
        case service:
            // service document
            response = (T)odataReader.readServiceDocument(content, SERVICE_DOCUMENT_CONTENT_TYPE);
            break;

        case metadata:
            // $metadata
            response = (T)odataReader.readMetadata(content);
            break;
        case resource:
            // any resource entity
            List<UriResource> listResource = uriInfo.getUriResourceParts();
            UriResourceKind lastResourceKind = listResource.get(listResource.size() - 1).getKind();
            switch (lastResourceKind) {
            case entitySet:
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet)listResource.get(listResource.size() - 1);
                List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
                // Check result type: single Entity or EntitySet based
                // on key predicate detection
                if (keyPredicates.size() == 1) {
                    response = (T)odataReader.readEntity(content, getResourceContentType(uriInfo));
                } else {
                    response = (T)odataReader.readEntitySet(content, getResourceContentType(uriInfo));
                }
                break;
            case count:
                String stringCount = null;
                try {
                    stringCount = IOUtils.toString(content, Consts.UTF_8);
                    response = (T)Long.valueOf(stringCount);
                } catch (IOException e) {
                    throw new ODataException("Error during $count value deserialization", e);
                } catch (NumberFormatException e) {
                    throw new ODataException("Error during $count value conversion: " + stringCount, e);
                }
                break;
            case value:
                try {
                    ClientPrimitiveValue value = odataClient.getObjectFactory().newPrimitiveValueBuilder().setType(EdmPrimitiveTypeKind.String)
                        .setValue(IOUtils.toString(content, Consts.UTF_8)).build();
                    response = (T)value;
                } catch (IOException e) {
                    throw new ODataException("Error during $value deserialization", e);
                }
                break;
            case primitiveProperty:
            case complexProperty:
                ClientProperty property = odataReader.readProperty(content, getResourceContentType(uriInfo));
                if (property.hasPrimitiveValue()) {
                    response = (T)property.getPrimitiveValue();
                } else if (property.hasComplexValue()) {
                    response = (T)property.getComplexValue();
                } else {
                    throw new ODataException("Unsupported property: " + property.getName());
                }
                break;
            case function:
                UriResourceFunctionImpl uriResourceFunction = (UriResourceFunctionImpl)listResource.get(listResource.size() - 1);
                EdmReturnType functionReturnType = uriResourceFunction.getFunction().getReturnType();

                switch (functionReturnType.getType().getKind()) {
                case ENTITY:
                    if (functionReturnType.isCollection()) {
                        response = (T)odataReader.readEntitySet(content, getResourceContentType(uriInfo));
                    } else {
                        response = (T)odataReader.readEntity(content, getResourceContentType(uriInfo));
                    }
                    break;
                case PRIMITIVE:
                case COMPLEX:
                    ClientProperty functionProperty = odataReader.readProperty(content, getResourceContentType(uriInfo));
                    if (functionProperty.hasPrimitiveValue()) {
                        response = (T)functionProperty.getPrimitiveValue();
                    } else if (functionProperty.hasComplexValue()) {
                        response = (T)functionProperty.getComplexValue();
                    } else {
                        throw new ODataException("Unsupported property: " + functionProperty.getName());
                    }
                    break;
                default:
                    throw new ODataException("Unsupported function return type " + uriInfo.getKind().name());
                }
                break;
            default:
                throw new ODataException("Unsupported resource type: " + lastResourceKind.name());
            }
            break;

        default:
            throw new ODataException("Unsupported resource type " + uriInfo.getKind().name());
        }

        return response;
    }

    private <T> void writeContent(final Edm edm, HttpEntityEnclosingRequestBase httpEntityRequest, final UriInfo uriInfo, final Object content,
                                  final Olingo4ResponseHandler<T> responseHandler) {

        try {
            httpEntityRequest.setEntity(writeContent(edm, uriInfo, content));

            final Header requestContentTypeHeader = httpEntityRequest.getEntity().getContentType();
            final ContentType requestContentType = requestContentTypeHeader != null ? ContentType.parse(requestContentTypeHeader.getValue()) : contentType;

            execute(httpEntityRequest, requestContentType, new AbstractFutureCallback<T>(responseHandler) {
                @SuppressWarnings("unchecked")
                @Override
                public void onCompleted(HttpResponse result) throws IOException, ODataException {

                    // if a entity is created (via POST request) the response
                    // body contains the new created entity
                    HttpStatusCode statusCode = HttpStatusCode.fromStatusCode(result.getStatusLine().getStatusCode());

                    // look for no content, or no response body!!!
                    final boolean noEntity = result.getEntity() == null || result.getEntity().getContentLength() == 0;
                    if (statusCode == HttpStatusCode.NO_CONTENT || noEntity) {
                        responseHandler.onResponse((T)HttpStatusCode.fromStatusCode(result.getStatusLine().getStatusCode()));
                    } else {
                        if (uriInfo.getKind() == UriInfoKind.resource) {
                            List<UriResource> listResource = uriInfo.getUriResourceParts();
                            UriResourceKind lastResourceKind = listResource.get(listResource.size() - 1).getKind();
                            switch (lastResourceKind) {
                            case entitySet:
                                if (content instanceof ClientEntity) {
                                    ClientEntity entity = odataReader.readEntity(result.getEntity().getContent(),
                                                                                 ContentType.parse(result.getEntity().getContentType().getValue()));
                                    responseHandler.onResponse((T)entity);
                                } else {
                                    throw new ODataException("Unsupported content type: " + content);
                                }
                                break;
                            default:
                                break;
                            }
                        } else if (uriInfo.getKind() == UriInfoKind.batch) {
                            List<Olingo4BatchResponse> batchResponse = parseBatchResponse(edm, result, (List<Olingo4BatchRequest>)content);
                            responseHandler.onResponse((T)batchResponse);
                        } else {
                            throw new ODataException("Unsupported resource type: " + uriInfo.getKind().name());
                        }
                    }
                }
            });

        } catch (ODataException e) {
            responseHandler.onException(e);
        }
    }

    private AbstractHttpEntity writeContent(final Edm edm, final UriInfo uriInfo, final Object content) throws ODataException {
        InputStream requestStream = null;
        AbstractHttpEntity httpEntity = null;
        if (uriInfo.getKind() == UriInfoKind.resource) {
            // any resource entity
            List<UriResource> listResource = uriInfo.getUriResourceParts();
            UriResourceKind lastResourceKind = listResource.get(listResource.size() - 1).getKind();
            switch (lastResourceKind) {
            case entitySet:
                if (content instanceof ClientEntity) {
                    requestStream = odataWriter.writeEntity((ClientEntity)content, getResourceContentType(uriInfo));
                } else {
                    throw new ODataException("Unsupported content type: " + content);
                }
                break;
            default:
                throw new ODataException("Unsupported resource type: " + lastResourceKind);
            }
            try {
                httpEntity = new ByteArrayEntity(IOUtils.toByteArray(requestStream));
            } catch (IOException e) {
                throw new ODataException("Error during converting input stream to byte array", e);
            }
            httpEntity.setChunked(false);

        } else if (uriInfo.getKind() == UriInfoKind.batch) {
            final String boundary = BOUNDARY_PREFIX + UUID.randomUUID();
            final String contentHeader = BATCH_CONTENT_TYPE + BOUNDARY_PARAMETER + boundary;
            final List<Olingo4BatchRequest> batchParts = (List<Olingo4BatchRequest>)content;

            requestStream = serializeBatchRequest(edm, batchParts, BOUNDARY_DOUBLE_DASH + boundary);
            try {
                httpEntity = new ByteArrayEntity(IOUtils.toByteArray(requestStream));
            } catch (IOException e) {
                throw new ODataException("Error during converting input stream to byte array", e);
            }
            httpEntity.setChunked(false);
            httpEntity.setContentType(contentHeader);
        } else {
            throw new ODataException("Unsupported resource type: " + uriInfo.getKind().name());
        }

        return httpEntity;
    }

    private InputStream serializeBatchRequest(final Edm edm, final List<Olingo4BatchRequest> batchParts, String boundary) throws ODataException {
        final ByteArrayOutputStream batchRequestHeaderOutputStream = new ByteArrayOutputStream();

        try {
            batchRequestHeaderOutputStream.write(boundary.getBytes(Constants.UTF8));
            batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);

            for (Olingo4BatchRequest batchPart : batchParts) {
                writeHttpHeader(batchRequestHeaderOutputStream, HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_HTTP.toContentTypeString());
                writeHttpHeader(batchRequestHeaderOutputStream, ODataBatchConstants.ITEM_TRANSFER_ENCODING_LINE, null);

                if (batchPart instanceof Olingo4BatchQueryRequest) {
                    final Olingo4BatchQueryRequest batchQueryPart = (Olingo4BatchQueryRequest)batchPart;
                    final String batchQueryUri = createUri(StringUtils.isBlank(batchQueryPart.getResourceUri()) ? serviceUri : batchQueryPart.getResourceUri(),
                                                           batchQueryPart.getResourcePath(), concatQueryParams(batchQueryPart.getQueryParams()));
                    final UriInfo uriInfo = parseUri(edm, batchQueryPart.getResourcePath(), concatQueryParams(batchQueryPart.getQueryParams()));
                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);

                    batchRequestHeaderOutputStream.write((HttpGet.METHOD_NAME + " " + batchQueryUri + " " + HttpVersion.HTTP_1_1).getBytes(Constants.UTF8));
                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    writeHttpHeader(batchRequestHeaderOutputStream, HttpHeaders.ACCEPT, getResourceContentType(uriInfo).toContentTypeString());

                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    batchRequestHeaderOutputStream.write(boundary.getBytes(Constants.UTF8));
                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                } else if (batchPart instanceof Olingo4BatchChangeRequest) {
                    final Olingo4BatchChangeRequest batchChangePart = (Olingo4BatchChangeRequest)batchPart;
                    final String batchChangeUri = createUri(StringUtils.isBlank(batchChangePart.getResourceUri()) ? serviceUri : batchChangePart.getResourceUri(),
                                                            batchChangePart.getResourcePath(), null);
                    final UriInfo uriInfo = parseUri(edm, batchChangePart.getResourcePath(), null);

                    if (batchChangePart.getOperation() != Operation.DELETE) {
                        writeHttpHeader(batchRequestHeaderOutputStream, CONTENT_ID_HEADER, batchChangePart.getContentId());
                    }

                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    batchRequestHeaderOutputStream
                        .write((batchChangePart.getOperation().getHttpMethod() + " " + batchChangeUri + " " + HttpVersion.HTTP_1_1).getBytes(Constants.UTF8));
                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    writeHttpHeader(batchRequestHeaderOutputStream, HttpHeader.ODATA_VERSION, ODataServiceVersion.V40.toString());
                    writeHttpHeader(batchRequestHeaderOutputStream, HttpHeaders.ACCEPT, getResourceContentType(uriInfo).toContentTypeString());
                    writeHttpHeader(batchRequestHeaderOutputStream, HttpHeaders.CONTENT_TYPE, getResourceContentType(uriInfo).toContentTypeString());

                    if (batchChangePart.getOperation() != Operation.DELETE) {
                        batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                        AbstractHttpEntity httpEnity = writeContent(edm, uriInfo, batchChangePart.getBody());

                        batchRequestHeaderOutputStream.write(IOUtils.toByteArray(httpEnity.getContent()));
                        batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                        batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    } else {
                        batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                    }

                    batchRequestHeaderOutputStream.write(boundary.getBytes(Constants.UTF8));
                    batchRequestHeaderOutputStream.write(ODataStreamer.CRLF);
                } else {
                    throw new ODataException("Unsupported batch part request object type: " + batchPart);
                }
            }
        } catch (Exception e) {
            throw new ODataException("Error during batch request serialization", e);
        }
        return new ByteArrayInputStream(batchRequestHeaderOutputStream.toByteArray());
    }

    private void writeHttpHeader(ByteArrayOutputStream headerOutputStream, String headerName, String headerValue) throws IOException {
        headerOutputStream.write(createHttpHeader(headerName, headerValue).getBytes(Constants.UTF8));
        headerOutputStream.write(ODataStreamer.CRLF);
    }

    private String createHttpHeader(String headerName, String headerValue) {
        return headerName + (StringUtils.isBlank(headerValue) ? "" : (": " + headerValue));
    }

    private List<Olingo4BatchResponse> parseBatchResponse(final Edm edm, HttpResponse response, List<Olingo4BatchRequest> batchRequest) throws ODataException {
        List<Olingo4BatchResponse> batchResponse = new <Olingo4BatchResponse> ArrayList();
        try {
            final Header[] contentHeaders = response.getHeaders(HttpHeader.CONTENT_TYPE);
            final ODataBatchLineIterator batchLineIterator = new ODataBatchLineIteratorImpl(IOUtils.lineIterator(response.getEntity().getContent(), Constants.UTF8));
            final String batchBoundary = ODataBatchUtilities.getBoundaryFromHeader(getHeadersCollection(contentHeaders));
            final ODataBatchController batchController = new ODataBatchController(batchLineIterator, batchBoundary);

            batchController.getBatchLineIterator().next();
            int batchRequestIndex = 0;
            while (batchController.getBatchLineIterator().hasNext()) {
                OutputStream os = new ByteArrayOutputStream();
                ODataBatchUtilities.readBatchPart(batchController, os, false);
                Object content = null;
                final Olingo4BatchRequest batchPartRequest = (Olingo4BatchRequest)batchRequest.get(batchRequestIndex);
                final HttpResponse batchPartHttpResponse = constructBatchPartHttpResponse(new ByteArrayInputStream(((ByteArrayOutputStream)os).toByteArray()));
                final StatusLine batchPartStatusLine = batchPartHttpResponse.getStatusLine();
                final int batchPartLineStatusCode = batchPartStatusLine.getStatusCode();
                Map<String, String> batchPartHeaders = getHeadersValueMap(batchPartHttpResponse.getAllHeaders());
                if (batchPartRequest instanceof Olingo4BatchQueryRequest) {
                    Olingo4BatchQueryRequest batchPartQueryRequest = (Olingo4BatchQueryRequest)batchPartRequest;
                    final UriInfo uriInfo = parseUri(edm, batchPartQueryRequest.getResourcePath(), null);

                    if (HttpStatusCode.BAD_REQUEST.getStatusCode() <= batchPartLineStatusCode && batchPartLineStatusCode <= AbstractFutureCallback.NETWORK_CONNECT_TIMEOUT_ERROR) {
                        final ContentType responseContentType = ContentType.parse(batchPartHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue());
                        content = odataReader.readError(batchPartHttpResponse.getEntity().getContent(), responseContentType);
                    } else if (batchPartLineStatusCode == HttpStatusCode.NO_CONTENT.getStatusCode()) {
                        // nothing to do if NO_CONTENT returning
                    } else {
                        content = readContent(uriInfo, batchPartHttpResponse.getEntity().getContent());
                    }

                    Olingo4BatchResponse batchPartResponse = new Olingo4BatchResponse(batchPartStatusLine.getStatusCode(), batchPartStatusLine.getReasonPhrase(), null,
                                                                                      batchPartHeaders, content);
                    batchResponse.add(batchPartResponse);
                } else if (batchPartRequest instanceof Olingo4BatchChangeRequest) {
                    Olingo4BatchChangeRequest batchPartChangeRequest = (Olingo4BatchChangeRequest)batchPartRequest;

                    if (batchPartLineStatusCode != HttpStatusCode.NO_CONTENT.getStatusCode()) {
                        if (HttpStatusCode.BAD_REQUEST.getStatusCode() <= batchPartLineStatusCode
                            && batchPartLineStatusCode <= AbstractFutureCallback.NETWORK_CONNECT_TIMEOUT_ERROR) {
                            final ContentType responseContentType = ContentType.parse(batchPartHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue());
                            content = odataReader.readError(response.getEntity().getContent(), responseContentType);
                        } else {
                            final UriInfo uriInfo = parseUri(edm, batchPartChangeRequest.getResourcePath()
                                                                  + (batchPartChangeRequest.getOperation() == Operation.CREATE ? CLIENT_ENTITY_FAKE_MARKER : ""),
                                                             null);
                            content = readContent(uriInfo, batchPartHttpResponse.getEntity().getContent());
                        }
                    }
                    Olingo4BatchResponse batchPartResponse = new Olingo4BatchResponse(batchPartStatusLine.getStatusCode(), batchPartStatusLine.getReasonPhrase(),
                                                                                      batchPartChangeRequest.getContentId(), batchPartHeaders, content);
                    batchResponse.add(batchPartResponse);
                } else {
                    throw new ODataException("Unsupported batch part request object type: " + batchPartRequest);
                }
                batchRequestIndex++;
            }

        } catch (IOException | HttpException e) {
            throw new ODataException(e);
        }
        return batchResponse;
    }

    private HttpResponse constructBatchPartHttpResponse(InputStream batchPartStream) throws IOException, HttpException {
        final LineIterator lines = IOUtils.lineIterator(batchPartStream, Constants.UTF8);
        final ByteArrayOutputStream headerOutputStream = new ByteArrayOutputStream();
        final ByteArrayOutputStream bodyOutputStream = new ByteArrayOutputStream();

        boolean startBatchPartHeader = false;
        boolean startBatchPartBody = false;

        // Iterate through lines in the batch part
        while (lines.hasNext()) {
            String line = lines.nextLine().trim();
            // Ignore all lines below HTTP/1.1 line
            if (line.startsWith(HttpVersion.HTTP)) {
                // This is the first header line
                startBatchPartHeader = true;
            }
            // Body starts with empty string after header lines
            if (startBatchPartHeader && StringUtils.isBlank(line)) {
                startBatchPartHeader = false;
                startBatchPartBody = true;
            }
            if (startBatchPartHeader) {
                // Write header to the output stream
                headerOutputStream.write(line.getBytes(Constants.UTF8));
                headerOutputStream.write(ODataStreamer.CRLF);
            } else if (startBatchPartBody && StringUtils.isNotBlank(line)) {
                // Write body to the output stream
                bodyOutputStream.write(line.getBytes(Constants.UTF8));
                bodyOutputStream.write(ODataStreamer.CRLF);
            }
        }

        // Prepare for parsing headers in to the HttpResponse object
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
        SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(metrics, 2048);
        HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();

        sessionInputBuffer.bind(new ByteArrayInputStream(headerOutputStream.toByteArray()));
        DefaultHttpResponseParser responseParser = new DefaultHttpResponseParser(sessionInputBuffer, new BasicLineParser(), responseFactory, MessageConstraints.DEFAULT);

        // Parse HTTP response and headers
        HttpResponse response = responseParser.parse();
        // Set body inside entity
        response.setEntity(new ByteArrayEntity(bodyOutputStream.toByteArray()));

        return response;
    }

    private Collection<String> getHeadersCollection(Header[] headers) {
        Collection<String> headersCollection = new ArrayList();
        for (Header header : Arrays.asList(headers)) {
            headersCollection.add(header.getValue());
        }
        return headersCollection;
    }

    private Map<String, String> getHeadersValueMap(Header[] headers) {
        Map<String, String> headersValueMap = new HashMap();
        for (Header header : Arrays.asList(headers)) {
            headersValueMap.put(header.getName(), header.getValue());
        }
        return headersValueMap;
    }

    private String createUri(String resourcePath) {
        return createUri(serviceUri, resourcePath, null);
    }

    private String createUri(String resourcePath, String queryOptions) {
        return createUri(serviceUri, resourcePath, queryOptions);
    }

    private String createUri(String resourceUri, String resourcePath, String queryOptions) {

        final StringBuilder absolutUri = new StringBuilder(resourceUri).append(SEPARATOR).append(resourcePath);
        if (queryOptions != null && !queryOptions.isEmpty()) {
            absolutUri.append("/?" + queryOptions);
        }
        return absolutUri.toString();

    }

    private String concatQueryParams(final Map<String, String> queryParams) {
        final StringBuilder concatQuery = new StringBuilder("");
        if (queryParams != null && !queryParams.isEmpty()) {
            int nParams = queryParams.size();
            int index = 0;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                concatQuery.append(entry.getKey()).append('=').append(entry.getValue());
                if (++index < nParams) {
                    concatQuery.append('&');
                }
            }
        }
        return concatQuery.toString().replaceAll("  *", "%20");
    }

    private static UriInfo parseUri(Edm edm, String resourcePath, String queryOptions) {
        Parser parser = new Parser(edm, OData.newInstance());
        UriInfo result;

        try {
            result = parser.parseUri(resourcePath, queryOptions, null);
        } catch (UriParserException | UriValidationException e) {
            throw new IllegalArgumentException("parseUri (" + resourcePath + "," + queryOptions + "): " + e.getMessage(), e);
        }
        return result;
    }

    public void execute(HttpUriRequest httpUriRequest, ContentType contentType, FutureCallback<HttpResponse> callback) {
        // add accept header when its not a form or multipart
        final String contentTypeString = contentType.toString();
        if (!ContentType.APPLICATION_FORM_URLENCODED.equals(contentType) && !contentType.toContentTypeString().startsWith(MULTIPART_MIME_TYPE)) {
            // otherwise accept what is being sent
            httpUriRequest.addHeader(HttpHeaders.ACCEPT, contentTypeString);
        }

        // is something being sent?
        if (httpUriRequest instanceof HttpEntityEnclosingRequestBase && httpUriRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE) == null) {
            httpUriRequest.addHeader(HttpHeaders.CONTENT_TYPE, contentTypeString);
        }

        // set user specified custom headers
        if (httpHeaders != null && !httpHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
                httpUriRequest.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // add 'Accept-Charset' header to avoid BOM marker presents inside
        // response stream
        if (!httpUriRequest.containsHeader(HttpHeaders.ACCEPT_CHARSET)) {
            httpUriRequest.addHeader(HttpHeaders.ACCEPT_CHARSET, Constants.UTF8);
        }

        // add client protocol version if not specified
        if (!httpUriRequest.containsHeader(HttpHeader.ODATA_VERSION)) {
            httpUriRequest.addHeader(HttpHeader.ODATA_VERSION, ODataServiceVersion.V40.toString());
        }
        if (!httpUriRequest.containsHeader(HttpHeader.ODATA_MAX_VERSION)) {
            httpUriRequest.addHeader(HttpHeader.ODATA_MAX_VERSION, ODataServiceVersion.V40.toString());
        }

        // execute request
        if (client instanceof CloseableHttpAsyncClient) {
            ((CloseableHttpAsyncClient)client).execute(httpUriRequest, callback);
        } else {
            // invoke the callback methods explicitly after executing the
            // request synchronously
            try {
                CloseableHttpResponse result = ((CloseableHttpClient)client).execute(httpUriRequest);
                callback.completed(result);
            } catch (IOException e) {
                callback.failed(e);
            }
        }
    }
}
