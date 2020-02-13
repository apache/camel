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
package org.apache.camel.component.olingo2.api.impl;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.camel.component.olingo2.api.Olingo2App;
import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.olingo.odata2.api.ODataServiceVersion;
import org.apache.olingo.odata2.api.batch.BatchException;
import org.apache.olingo.odata2.api.client.batch.BatchChangeSet;
import org.apache.olingo.odata2.api.client.batch.BatchChangeSetPart;
import org.apache.olingo.odata2.api.client.batch.BatchPart;
import org.apache.olingo.odata2.api.client.batch.BatchQueryPart;
import org.apache.olingo.odata2.api.client.batch.BatchSingleResponse;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.commons.ODataHttpHeaders;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntityContainer;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmProperty;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderException;
import org.apache.olingo.odata2.api.ep.EntityProviderReadProperties;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;
import org.apache.olingo.odata2.api.ep.entry.EntryMetadata;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.apache.olingo.odata2.api.processor.ODataResponse;
import org.apache.olingo.odata2.api.uri.PathSegment;
import org.apache.olingo.odata2.api.uri.UriParser;

/**
 * Application API used by Olingo2 Component.
 */
public final class Olingo2AppImpl implements Olingo2App {

    public static final String METADATA = "$metadata";

    private static final String SEPARATOR = "/";

    private static final String BOUNDARY_PREFIX = "batch_";
    private static final String BOUNDARY_PARAMETER = "; boundary=";

    private static final ContentType METADATA_CONTENT_TYPE = ContentType.create("application/xml", Consts.UTF_8);
    private static final ContentType SERVICE_DOCUMENT_CONTENT_TYPE = ContentType.create("application/atomsvc+xml", Consts.UTF_8);
    private static final String BATCH_CONTENT_TYPE = ContentType.create("multipart/mixed").toString();

    private static final String BATCH = "$batch";
    private static final String MAX_DATA_SERVICE_VERSION = "Max" + ODataHttpHeaders.DATASERVICEVERSION;
    private static final String MULTIPART_MIME_TYPE = "multipart/";
    private static final ContentType TEXT_PLAIN_WITH_CS_UTF_8 = ContentType.TEXT_PLAIN.withCharset(Consts.UTF_8);

    /**
     * Reference to CloseableHttpAsyncClient (default) or CloseableHttpClient
     */
    private final Closeable client;

    private String serviceUri;
    private ContentType contentType;
    private Map<String, String> httpHeaders;

    /**
     * Create Olingo2 Application with default HTTP configuration.
     */
    public Olingo2AppImpl(String serviceUri) {
        // By default create HTTP Asynchronous client
        this(serviceUri, (HttpAsyncClientBuilder)null);
    }

    /**
     * Create Olingo2 Application with custom HTTP Asynchronous client builder.
     *
     * @param serviceUri Service Application base URI.
     * @param builder custom HTTP client builder.
     */
    public Olingo2AppImpl(String serviceUri, HttpAsyncClientBuilder builder) {
        setServiceUri(serviceUri);

        CloseableHttpAsyncClient asyncClient;
        if (builder == null) {
            asyncClient = HttpAsyncClients.createDefault();
        } else {
            asyncClient = builder.build();
        }
        asyncClient.start();
        this.client = asyncClient;
        this.contentType = ContentType.create("application/json", Consts.UTF_8);
    }

    /**
     * Create Olingo2 Application with custom HTTP Synchronous client builder.
     *
     * @param serviceUri Service Application base URI.
     * @param builder Custom HTTP Synchronous client builder.
     */
    public Olingo2AppImpl(String serviceUri, HttpClientBuilder builder) {
        setServiceUri(serviceUri);
        if (builder == null) {
            this.client = HttpClients.createDefault();
        } else {
            this.client = builder.build();
        }
        this.contentType = ContentType.create("application/json", Consts.UTF_8);
    }

    @Override
    public void setServiceUri(String serviceUri) {
        if (serviceUri == null || serviceUri.isEmpty()) {
            throw new IllegalArgumentException("serviceUri is not set");
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
        return contentType.toString();
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = ContentType.parse(contentType);
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (final IOException ignore) {
            }
        }
    }

    @Override
    public <T> void read(final Edm edm, final String resourcePath, final Map<String, String> queryParams, final Map<String, String> endpointHttpHeaders,
                         final Olingo2ResponseHandler<T> responseHandler) {

        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, queryParams);

        execute(new HttpGet(createUri(resourcePath, encodeQueryParams(queryParams))), getResourceContentType(uriInfo), endpointHttpHeaders,
                new AbstractFutureCallback<T>(responseHandler) {

                    @Override
                    public void onCompleted(HttpResponse result) throws IOException {
                        readContent(uriInfo, headersToMap(result.getAllHeaders()), result.getEntity() != null ? result.getEntity().getContent() : null, responseHandler);
                    }

                });
    }

    @Override
    public void uread(final Edm edm, final String resourcePath, final Map<String, String> queryParams, final Map<String, String> endpointHttpHeaders,
                      final Olingo2ResponseHandler<InputStream> responseHandler) {

        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, queryParams);

        execute(new HttpGet(createUri(resourcePath, encodeQueryParams(queryParams))), getResourceContentType(uriInfo), endpointHttpHeaders,
                new AbstractFutureCallback<InputStream>(responseHandler) {

                    @Override
                    public void onCompleted(HttpResponse result) throws IOException {
                        responseHandler.onResponse((result.getEntity() != null) ? result.getEntity().getContent() : null, headersToMap(result.getAllHeaders()));
                    }

                });
    }

    private Map<String, String> encodeQueryParams(Map<String, String> queryParams) {
        Map<String, String> encodedQueryParams = queryParams;
        if (queryParams != null) {
            encodedQueryParams = new HashMap<>(queryParams.size());
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                encodedQueryParams.put(entry.getKey(), URLEncoder.encode(entry.getValue()));
            }
        }
        return encodedQueryParams;
    }

    private ContentType getResourceContentType(UriInfoWithType uriInfo) {
        ContentType resourceContentType;
        switch (uriInfo.getUriType()) {
            case URI0:
                // service document
                resourceContentType = SERVICE_DOCUMENT_CONTENT_TYPE;
                break;
            case URI8:
                // metadata
                resourceContentType = METADATA_CONTENT_TYPE;
                break;
            case URI4:
            case URI5:
                // is it a $value URI??
                if (uriInfo.isValue()) {
                    // property value and $count
                    resourceContentType = TEXT_PLAIN_WITH_CS_UTF_8;
                } else {
                    resourceContentType = contentType;
                }
                break;
            case URI15:
            case URI16:
            case URI50A:
            case URI50B:
                // $count
                resourceContentType = TEXT_PLAIN_WITH_CS_UTF_8;
                break;
            default:
                resourceContentType = contentType;
        }
        return resourceContentType;
    }

    @Override
    public <T> void create(final Edm edm, final String resourcePath, final Map<String, String> endpointHttpHeaders, final Object data,
                           final Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPost(createUri(resourcePath, null)), uriInfo, endpointHttpHeaders, data, responseHandler);
    }

    @Override
    public <T> void update(final Edm edm, final String resourcePath, final Map<String, String> endpointHttpHeaders, final Object data,
                           final Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, null);

        augmentWithETag(edm, resourcePath, endpointHttpHeaders, new HttpPut(createUri(resourcePath, null)),
            request -> writeContent(edm, (HttpPut)request, uriInfo, endpointHttpHeaders, data, responseHandler), responseHandler);
    }

    @Override
    public <T> void patch(final Edm edm, final String resourcePath, final Map<String, String> endpointHttpHeaders, final Object data,
                          final Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, null);

        augmentWithETag(edm, resourcePath, endpointHttpHeaders, new HttpPatch(createUri(resourcePath, null)),
            request -> writeContent(edm, (HttpPatch)request, uriInfo, endpointHttpHeaders, data, responseHandler), responseHandler);
    }

    @Override
    public <T> void merge(final Edm edm, final String resourcePath, final Map<String, String> endpointHttpHeaders, final Object data,
                          final Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, null);

        augmentWithETag(edm, resourcePath, endpointHttpHeaders, new HttpMerge(createUri(resourcePath, null)),
            request -> writeContent(edm, (HttpMerge)request, uriInfo, endpointHttpHeaders, data, responseHandler), responseHandler);
    }

    @Override
    public void batch(final Edm edm, final Map<String, String> endpointHttpHeaders, final Object data, final Olingo2ResponseHandler<List<Olingo2BatchResponse>> responseHandler) {
        final UriInfoWithType uriInfo = parseUri(edm, BATCH, null);

        writeContent(edm, new HttpPost(createUri(BATCH, null)), uriInfo, endpointHttpHeaders, data, responseHandler);
    }

    @Override
    public void delete(final String resourcePath, final Map<String, String> endpointHttpHeaders, final Olingo2ResponseHandler<HttpStatusCodes> responseHandler) {
        HttpDelete deleteRequest = new HttpDelete(createUri(resourcePath));

        Consumer<HttpRequestBase> deleteFunction = request -> {
            execute(request, contentType, endpointHttpHeaders, new AbstractFutureCallback<HttpStatusCodes>(responseHandler) {
                @Override
                public void onCompleted(HttpResponse result) {
                    final StatusLine statusLine = result.getStatusLine();
                    responseHandler.onResponse(HttpStatusCodes.fromStatusCode(statusLine.getStatusCode()), headersToMap(result.getAllHeaders()));
                }
            });
        };

        augmentWithETag(null, resourcePath, endpointHttpHeaders, deleteRequest, deleteFunction, responseHandler);
    }

    /**
     * On occasion, some resources are protected with Optimistic Concurrency via
     * the use of eTags. This will first conduct a read on the given entity
     * resource, find its eTag then perform the given delegate request function,
     * augmenting the request with the eTag, if appropriate. Since read
     * operations may be asynchronous, it is necessary to chain together the
     * methods via the use of a {@link Consumer} function. Only when the
     * response from the read returns will this delegate function be executed.
     *
     * @param edm the Edm object to be interrogated
     * @param resourcePath the resource path of the entity to be operated on
     * @param endpointHttpHeaders the headers provided from the endpoint which
     *            may be required for the read operation
     * @param httpRequest the request to be updated, if appropriate, with the
     *            eTag and provided to the delegate request function
     * @param delegateRequestFn the function to be invoked in response to the
     *            read operation
     * @param delegateResponseHandler the response handler to respond if any
     *            errors occur during the read operation
     */
    private <T> void augmentWithETag(final Edm edm, final String resourcePath, final Map<String, String> endpointHttpHeaders, final HttpRequestBase httpRequest,
                                     final Consumer<HttpRequestBase> delegateRequestFn, final Olingo2ResponseHandler<T> delegateResponseHandler) {

        if (edm == null) {
            // Can be the case if calling a delete then need to do a metadata
            // call first
            final Olingo2ResponseHandler<Edm> edmResponseHandler = new Olingo2ResponseHandler<Edm>() {
                @Override
                public void onResponse(Edm response, Map<String, String> responseHeaders) {
                    //
                    // Call this method again with an intact edm object
                    //
                    augmentWithETag(response, resourcePath, endpointHttpHeaders, httpRequest, delegateRequestFn, delegateResponseHandler);
                }

                @Override
                public void onException(Exception ex) {
                    delegateResponseHandler.onException(ex);
                }

                @Override
                public void onCanceled() {
                    delegateResponseHandler.onCanceled();
                }
            };

            //
            // Reads the metadata to establish an Edm object
            // then the response handler invokes this method again with the new
            // edm object
            //
            read(null, "$metadata", null, null, edmResponseHandler);

        } else {

            //
            // The handler that responds to the read operation and supplies an
            // ETag if necessary
            // and invokes the delegate request function
            //
            Olingo2ResponseHandler<T> eTagReadHandler = new Olingo2ResponseHandler<T>() {

                @Override
                public void onResponse(T response, Map<String, String> responseHeaders) {
                    if (response instanceof ODataEntry) {
                        ODataEntry e = (ODataEntry)response;
                        Optional.ofNullable(e.getMetadata()).map(EntryMetadata::getEtag).ifPresent(v -> httpRequest.addHeader("If-Match", v));
                    }

                    // Invoke the delegate request function providing the
                    // modified request
                    delegateRequestFn.accept(httpRequest);
                }

                @Override
                public void onException(Exception ex) {
                    delegateResponseHandler.onException(ex);
                }

                @Override
                public void onCanceled() {
                    delegateResponseHandler.onCanceled();
                }
            };

            read(edm, resourcePath, null, endpointHttpHeaders, eTagReadHandler);
        }
    }

    private <T> void readContent(UriInfoWithType uriInfo, Map<String, String> responseHeaders, InputStream content, Olingo2ResponseHandler<T> responseHandler) {
        try {
            responseHandler.onResponse(this.<T> readContent(uriInfo, content), responseHeaders);
        } catch (Exception e) {
            responseHandler.onException(e);
        } catch (Error e) {
            responseHandler.onException(new ODataApplicationException("Runtime Error Occurred", Locale.ENGLISH, e));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T readContent(UriInfoWithType uriInfo, InputStream content) throws EntityProviderException, ODataApplicationException {
        T response;
        switch (uriInfo.getUriType()) {
            case URI0:
                // service document
                response = (T)EntityProvider.readServiceDocument(content, SERVICE_DOCUMENT_CONTENT_TYPE.toString());
                break;

            case URI8:
                // $metadata
                response = (T)EntityProvider.readMetadata(content, false);
                break;

            case URI7A:
                // link
                response = (T)EntityProvider.readLink(getContentType(), uriInfo.getTargetEntitySet(), content);
                break;

            case URI7B:
                // links
                response = (T)EntityProvider.readLinks(getContentType(), uriInfo.getTargetEntitySet(), content);
                break;

            case URI3:
                // complex property
                final List<EdmProperty> complexPropertyPath = uriInfo.getPropertyPath();
                final EdmProperty complexProperty = complexPropertyPath.get(complexPropertyPath.size() - 1);
                response = (T)EntityProvider.readProperty(getContentType(), complexProperty, content, EntityProviderReadProperties.init().build());
                break;

            case URI4:
            case URI5:
                // simple property
                final List<EdmProperty> simplePropertyPath = uriInfo.getPropertyPath();
                final EdmProperty simpleProperty = simplePropertyPath.get(simplePropertyPath.size() - 1);
                if (uriInfo.isValue()) {
                    response = (T)EntityProvider.readPropertyValue(simpleProperty, content);
                } else {
                    response = (T)EntityProvider.readProperty(getContentType(), simpleProperty, content, EntityProviderReadProperties.init().build());
                }
                break;

            case URI15:
            case URI16:
            case URI50A:
            case URI50B:
                // $count
                final String stringCount = new String(EntityProvider.readBinary(content), Consts.UTF_8);
                response = (T)Long.valueOf(stringCount);
                break;

            case URI1:
            case URI6B:
                if (uriInfo.getCustomQueryOptions().containsKey("!deltatoken")) {
                    // ODataDeltaFeed
                    response = (T)EntityProvider.readDeltaFeed(getContentType(), uriInfo.getTargetEntitySet(), content, EntityProviderReadProperties.init().build());
                } else {
                    // ODataFeed
                    response = (T)EntityProvider.readFeed(getContentType(), uriInfo.getTargetEntitySet(), content, EntityProviderReadProperties.init().build());
                }
                break;

            case URI2:
            case URI6A:
                response = (T)EntityProvider.readEntry(getContentType(), uriInfo.getTargetEntitySet(), content, EntityProviderReadProperties.init().build());
                break;

            // Function Imports
            case URI10:
            case URI11:
            case URI12:
            case URI13:
            case URI14:
                response = (T)EntityProvider.readFunctionImport(getContentType(), uriInfo.getFunctionImport(), content, EntityProviderReadProperties.init().build());
                break;

            default:
                throw new ODataApplicationException("Unsupported resource type " + uriInfo.getTargetType(), Locale.ENGLISH);
        }

        return response;
    }

    private <T> void writeContent(final Edm edm, final HttpEntityEnclosingRequestBase httpEntityRequest, final UriInfoWithType uriInfo,
                                  final Map<String, String> endpointHttpHeaders, final Object content, final Olingo2ResponseHandler<T> responseHandler) {

        try {
            // process resource by UriType
            final ODataResponse response = writeContent(edm, uriInfo, content);

            // copy all response headers
            for (String header : response.getHeaderNames()) {
                httpEntityRequest.setHeader(header, response.getHeader(header));
            }

            // get (http) entity which is for default Olingo2 implementation an
            // InputStream
            if (response.getEntity() instanceof InputStream) {
                httpEntityRequest.setEntity(new InputStreamEntity((InputStream)response.getEntity()));
                /*
                 * // avoid sending it without a header field set if
                 * (!httpEntityRequest.containsHeader(HttpHeaders.CONTENT_TYPE))
                 * { httpEntityRequest.addHeader(HttpHeaders.CONTENT_TYPE,
                 * getContentType()); }
                 */
            }

            // execute HTTP request
            final Header requestContentTypeHeader = httpEntityRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            final ContentType requestContentType = requestContentTypeHeader != null ? ContentType.parse(requestContentTypeHeader.getValue()) : contentType;
            execute(httpEntityRequest, requestContentType, endpointHttpHeaders, new AbstractFutureCallback<T>(responseHandler) {
                @SuppressWarnings("unchecked")
                @Override
                public void onCompleted(HttpResponse result) throws IOException, EntityProviderException, BatchException, ODataApplicationException {

                    // if a entity is created (via POST request) the response
                    // body contains the new created entity
                    HttpStatusCodes statusCode = HttpStatusCodes.fromStatusCode(result.getStatusLine().getStatusCode());

                    // look for no content, or no response body!!!
                    final boolean noEntity = result.getEntity() == null || result.getEntity().getContentLength() == 0;
                    if (statusCode == HttpStatusCodes.NO_CONTENT || noEntity) {
                        responseHandler.onResponse((T)HttpStatusCodes.fromStatusCode(result.getStatusLine().getStatusCode()), headersToMap(result.getAllHeaders()));
                    } else {

                        switch (uriInfo.getUriType()) {
                            case URI9:
                                // $batch
                                String type = result.containsHeader(HttpHeaders.CONTENT_TYPE) ? result.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue() : null;
                                final List<BatchSingleResponse> singleResponses = EntityProvider.parseBatchResponse(result.getEntity().getContent(), type);

                                // parse batch response bodies
                                final List<Olingo2BatchResponse> responses = new ArrayList<>();
                                Map<String, String> contentIdLocationMap = new HashMap<>();

                                final List<Olingo2BatchRequest> batchRequests = (List<Olingo2BatchRequest>)content;
                                final Iterator<Olingo2BatchRequest> iterator = batchRequests.iterator();

                                for (BatchSingleResponse response : singleResponses) {
                                    final Olingo2BatchRequest request = iterator.next();

                                    if (request instanceof Olingo2BatchChangeRequest && ((Olingo2BatchChangeRequest)request).getContentId() != null) {

                                        contentIdLocationMap.put("$" + ((Olingo2BatchChangeRequest)request).getContentId(), response.getHeader(HttpHeaders.LOCATION));
                                    }

                                    try {
                                        responses.add(parseResponse(edm, contentIdLocationMap, request, response));
                                    } catch (Exception e) {
                                        // report any parsing errors as error
                                        // response
                                        responses.add(new Olingo2BatchResponse(Integer.parseInt(response.getStatusCode()), response.getStatusInfo(), response.getContentId(), response
                                                .getHeaders(), new ODataApplicationException("Error parsing response for " + request + ": " + e.getMessage(), Locale.ENGLISH, e)));
                                    }
                                }
                                responseHandler.onResponse((T)responses, headersToMap(result.getAllHeaders()));
                                break;

                            case URI4:
                            case URI5:
                                // simple property
                                // get the response content as Object for $value or
                                // Map<String, Object> otherwise
                                final List<EdmProperty> simplePropertyPath = uriInfo.getPropertyPath();
                                final EdmProperty simpleProperty = simplePropertyPath.get(simplePropertyPath.size() - 1);
                                if (uriInfo.isValue()) {
                                    responseHandler.onResponse((T)EntityProvider.readPropertyValue(simpleProperty, result.getEntity().getContent()),
                                            headersToMap(result.getAllHeaders()));
                                } else {
                                    responseHandler.onResponse((T)EntityProvider.readProperty(getContentType(), simpleProperty, result.getEntity().getContent(),
                                            EntityProviderReadProperties.init().build()),
                                            headersToMap(result.getAllHeaders()));
                                }
                                break;

                            case URI3:
                                // complex property
                                // get the response content as Map<String, Object>
                                final List<EdmProperty> complexPropertyPath = uriInfo.getPropertyPath();
                                final EdmProperty complexProperty = complexPropertyPath.get(complexPropertyPath.size() - 1);
                                responseHandler.onResponse((T)EntityProvider.readProperty(getContentType(), complexProperty, result.getEntity().getContent(),
                                        EntityProviderReadProperties.init().build()),
                                        headersToMap(result.getAllHeaders()));
                                break;

                            case URI7A:
                                // $links with 0..1 cardinality property
                                // get the response content as String
                                final EdmEntitySet targetLinkEntitySet = uriInfo.getTargetEntitySet();
                                responseHandler.onResponse((T)EntityProvider.readLink(getContentType(), targetLinkEntitySet, result.getEntity().getContent()),
                                        headersToMap(result.getAllHeaders()));
                                break;

                            case URI7B:
                                // $links with * cardinality property
                                // get the response content as
                                // java.util.List<String>
                                final EdmEntitySet targetLinksEntitySet = uriInfo.getTargetEntitySet();
                                responseHandler.onResponse((T)EntityProvider.readLinks(getContentType(), targetLinksEntitySet, result.getEntity().getContent()),
                                        headersToMap(result.getAllHeaders()));
                                break;

                            case URI1:
                            case URI2:
                            case URI6A:
                            case URI6B:
                                // Entity
                                // get the response content as an ODataEntry object
                                responseHandler.onResponse((T)EntityProvider.readEntry(response.getContentHeader(), uriInfo.getTargetEntitySet(), result.getEntity().getContent(),
                                        EntityProviderReadProperties.init().build()),
                                        headersToMap(result.getAllHeaders()));
                                break;

                            default:
                                throw new ODataApplicationException("Unsupported resource type " + uriInfo.getTargetType(), Locale.ENGLISH);
                        }

                    }
                }
            });
        } catch (Exception e) {
            responseHandler.onException(e);
        } catch (Error e) {
            responseHandler.onException(new ODataApplicationException("Runtime Error Occurred", Locale.ENGLISH, e));
        }
    }

    private ODataResponse writeContent(Edm edm, UriInfoWithType uriInfo, Object content)
            throws ODataApplicationException, EdmException, EntityProviderException, URISyntaxException, IOException {

        String responseContentType = getContentType();
        ODataResponse response;

        switch (uriInfo.getUriType()) {
            case URI4:
            case URI5:
                // simple property
                final List<EdmProperty> simplePropertyPath = uriInfo.getPropertyPath();
                final EdmProperty simpleProperty = simplePropertyPath.get(simplePropertyPath.size() - 1);
                responseContentType = simpleProperty.getMimeType();
                if (uriInfo.isValue()) {
                    response = EntityProvider.writePropertyValue(simpleProperty, content);
                    responseContentType = TEXT_PLAIN_WITH_CS_UTF_8.toString();
                } else {
                    response = EntityProvider.writeProperty(getContentType(), simpleProperty, content);
                }
                break;

            case URI3:
                // complex property
                final List<EdmProperty> complexPropertyPath = uriInfo.getPropertyPath();
                final EdmProperty complexProperty = complexPropertyPath.get(complexPropertyPath.size() - 1);
                response = EntityProvider.writeProperty(responseContentType, complexProperty, content);
                break;

            case URI7A:
                // $links with 0..1 cardinality property
                final EdmEntitySet targetLinkEntitySet = uriInfo.getTargetEntitySet();
                EntityProviderWriteProperties linkProperties = EntityProviderWriteProperties.serviceRoot(new URI(serviceUri + SEPARATOR)).build();
                @SuppressWarnings("unchecked")
                final Map<String, Object> linkMap = (Map<String, Object>)content;
                response = EntityProvider.writeLink(responseContentType, targetLinkEntitySet, linkMap, linkProperties);
                break;

            case URI7B:
                // $links with * cardinality property
                final EdmEntitySet targetLinksEntitySet = uriInfo.getTargetEntitySet();
                EntityProviderWriteProperties linksProperties = EntityProviderWriteProperties.serviceRoot(new URI(serviceUri + SEPARATOR)).build();
                @SuppressWarnings("unchecked")
                final List<Map<String, Object>> linksMap = (List<Map<String, Object>>)content;
                response = EntityProvider.writeLinks(responseContentType, targetLinksEntitySet, linksMap, linksProperties);
                break;

            case URI1:
            case URI2:
            case URI6A:
            case URI6B:
                // Entity
                final EdmEntitySet targetEntitySet = uriInfo.getTargetEntitySet();
                EntityProviderWriteProperties properties = EntityProviderWriteProperties.serviceRoot(new URI(serviceUri + SEPARATOR)).build();
                @SuppressWarnings("unchecked")
                final Map<String, Object> objectMap = (Map<String, Object>)content;
                response = EntityProvider.writeEntry(responseContentType, targetEntitySet, objectMap, properties);
                break;

            case URI9:
                // $batch
                @SuppressWarnings("unchecked")
                final List<Olingo2BatchRequest> batchParts = (List<Olingo2BatchRequest>)content;
                response = parseBatchRequest(edm, batchParts);
                break;

            default:
                // notify exception and return!!!
                throw new ODataApplicationException("Unsupported resource type " + uriInfo.getTargetType(), Locale.ENGLISH);
        }

        return response.getContentHeader() != null ? response : ODataResponse.fromResponse(response).contentHeader(responseContentType).build();
    }

    private ODataResponse parseBatchRequest(final Edm edm, final List<Olingo2BatchRequest> batchParts)
            throws IOException, EntityProviderException, ODataApplicationException, EdmException, URISyntaxException {

        // create Batch request from parts
        final ArrayList<BatchPart> parts = new ArrayList<>();
        final ArrayList<BatchChangeSetPart> changeSetParts = new ArrayList<>();

        final Map<String, String> contentIdMap = new HashMap<>();

        for (Olingo2BatchRequest batchPart : batchParts) {

            if (batchPart instanceof Olingo2BatchQueryRequest) {

                // need to add change set parts collected so far??
                if (!changeSetParts.isEmpty()) {
                    addChangeSetParts(parts, changeSetParts);
                    changeSetParts.clear();
                    contentIdMap.clear();
                }

                // add to request parts
                final UriInfoWithType uriInfo = parseUri(edm, batchPart.getResourcePath(), null);
                parts.add(createBatchQueryPart(uriInfo, (Olingo2BatchQueryRequest)batchPart));

            } else {

                // add to change set parts
                final BatchChangeSetPart changeSetPart = createBatchChangeSetPart(edm, contentIdMap, (Olingo2BatchChangeRequest)batchPart);
                changeSetParts.add(changeSetPart);
            }
        }

        // add any remaining change set parts
        if (!changeSetParts.isEmpty()) {
            addChangeSetParts(parts, changeSetParts);
        }

        final String boundary = BOUNDARY_PREFIX + UUID.randomUUID();
        InputStream batchRequest = EntityProvider.writeBatchRequest(parts, boundary);
        // two blank lines are already added. No need to add extra blank lines
        final String contentHeader = BATCH_CONTENT_TYPE + BOUNDARY_PARAMETER + boundary;
        return ODataResponse.entity(batchRequest).contentHeader(contentHeader).build();
    }

    private void addChangeSetParts(ArrayList<BatchPart> parts, ArrayList<BatchChangeSetPart> changeSetParts) {
        final BatchChangeSet changeSet = BatchChangeSet.newBuilder().build();
        for (BatchChangeSetPart changeSetPart : changeSetParts) {
            changeSet.add(changeSetPart);
        }
        parts.add(changeSet);
    }

    private BatchChangeSetPart createBatchChangeSetPart(Edm edm, Map<String, String> contentIdMap, Olingo2BatchChangeRequest batchRequest)
            throws EdmException, URISyntaxException, EntityProviderException, IOException, ODataApplicationException {

        // build body string
        String resourcePath = batchRequest.getResourcePath();
        // is it a referenced entity?
        if (resourcePath.startsWith("$")) {
            resourcePath = replaceContentId(edm, resourcePath, contentIdMap);
        }

        final UriInfoWithType uriInfo = parseUri(edm, resourcePath, null);

        // serialize data into ODataResponse object, if set in request and this
        // is not a DELETE request
        final Map<String, String> headers = new HashMap<>();
        byte[] body = null;

        if (batchRequest.getBody() != null && !Operation.DELETE.equals(batchRequest.getOperation())) {

            final ODataResponse response = writeContent(edm, uriInfo, batchRequest.getBody());
            // copy response headers
            for (String header : response.getHeaderNames()) {
                headers.put(header, response.getHeader(header));
            }

            // get (http) entity which is for default Olingo2 implementation an
            // InputStream
            body = response.getEntity() instanceof InputStream ? EntityProvider.readBinary((InputStream)response.getEntity()) : null;
            if (body != null) {
                headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            }
        }

        // Olingo is sensitive to batch part charset case!!
        final ContentType contentType = getResourceContentType(uriInfo);
        headers.put(HttpHeaders.ACCEPT, contentType.withCharset("").toString().toLowerCase());
        final Charset charset = contentType.getCharset();
        if (null != charset) {
            headers.put(HttpHeaders.ACCEPT_CHARSET, charset.name().toLowerCase());
        }
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.put(HttpHeaders.CONTENT_TYPE, getContentType());
        }

        // add request headers
        headers.putAll(batchRequest.getHeaders());

        final String contentId = batchRequest.getContentId();
        if (contentId != null) {
            contentIdMap.put("$" + contentId, resourcePath);
        }
        return BatchChangeSetPart.uri(createBatchUri(batchRequest)).method(batchRequest.getOperation().getHttpMethod()).contentId(contentId).headers(headers)
                .body(body == null ? null : new String(body, Consts.UTF_8)).build();
    }

    private BatchQueryPart createBatchQueryPart(UriInfoWithType uriInfo, Olingo2BatchQueryRequest batchRequest) {

        final Map<String, String> headers = new HashMap<>(batchRequest.getHeaders());
        final ContentType contentType = getResourceContentType(uriInfo);
        final Charset charset = contentType.getCharset();
        if (!headers.containsKey(HttpHeaders.ACCEPT)) {
            // Olingo is sensitive to batch part charset case!!
            headers.put(HttpHeaders.ACCEPT, contentType.withCharset("").toString().toLowerCase());
        }
        if (!headers.containsKey(HttpHeaders.ACCEPT_CHARSET) && (null != charset)) {
            headers.put(HttpHeaders.ACCEPT_CHARSET, charset.name().toLowerCase());
        }

        return BatchQueryPart.method("GET").uri(createBatchUri(batchRequest)).headers(headers).build();
    }

    private static String replaceContentId(Edm edm, String entityReference, Map<String, String> contentIdMap) throws EdmException {
        final int pathSeparator = entityReference.indexOf('/');
        final StringBuilder referencedEntity;
        if (pathSeparator == -1) {
            referencedEntity = new StringBuilder(contentIdMap.get(entityReference));
        } else {
            referencedEntity = new StringBuilder(contentIdMap.get(entityReference.substring(0, pathSeparator)));
        }

        // create a dummy entity location by adding a dummy key predicate
        // look for a Container name if available
        String referencedEntityName = referencedEntity.toString();
        final int containerSeparator = referencedEntityName.lastIndexOf('.');
        final EdmEntityContainer entityContainer;
        if (containerSeparator != -1) {
            final String containerName = referencedEntityName.substring(0, containerSeparator);
            referencedEntityName = referencedEntityName.substring(containerSeparator + 1);
            entityContainer = edm.getEntityContainer(containerName);
            if (entityContainer == null) {
                throw new IllegalArgumentException("EDM does not have entity container " + containerName);
            }
        } else {
            entityContainer = edm.getDefaultEntityContainer();
            if (entityContainer == null) {
                throw new IllegalArgumentException("EDM does not have a default entity container" + ", use a fully qualified entity set name");
            }
        }
        final EdmEntitySet entitySet = entityContainer.getEntitySet(referencedEntityName);
        final List<EdmProperty> keyProperties = entitySet.getEntityType().getKeyProperties();

        if (keyProperties.size() == 1) {
            referencedEntity.append("('dummy')");
        } else {
            referencedEntity.append("(");
            for (EdmProperty keyProperty : keyProperties) {
                referencedEntity.append(keyProperty.getName()).append('=').append("'dummy',");
            }
            referencedEntity.deleteCharAt(referencedEntity.length() - 1);
            referencedEntity.append(')');
        }

        return pathSeparator == -1 ? referencedEntityName : referencedEntity.append(entityReference, pathSeparator, entityReference.length()).toString();
    }

    private Olingo2BatchResponse parseResponse(Edm edm, Map<String, String> contentIdLocationMap, Olingo2BatchRequest request, BatchSingleResponse response)
            throws EntityProviderException, ODataApplicationException {

        // validate HTTP status
        final int statusCode = Integer.parseInt(response.getStatusCode());
        final String statusInfo = response.getStatusInfo();

        final BasicHttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, statusCode, statusInfo));
        final Map<String, String> headers = response.getHeaders();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpResponse.setHeader(entry.getKey(), entry.getValue());
        }

        ByteArrayInputStream content = null;
        try {
            if (response.getBody() != null) {
                String charset = Consts.UTF_8.toString();
                try {
                    final ContentType partContentType = receiveWithCharsetParameter(ContentType.parse(headers.get(HttpHeaders.CONTENT_TYPE)), Consts.UTF_8);
                    charset = partContentType.getCharset().toString();
                } catch (ParseException | UnsupportedCharsetException ex) {
                    // Use default charset of UTF-8.
                }

                final String body = response.getBody();
                content = body != null ? new ByteArrayInputStream(body.getBytes(charset)) : null;

                httpResponse.setEntity(new StringEntity(body, charset));
            }

            AbstractFutureCallback.checkStatus(httpResponse);
        } catch (ODataApplicationException e) {
            return new Olingo2BatchResponse(statusCode, statusInfo, response.getContentId(), response.getHeaders(), e);
        } catch (UnsupportedEncodingException e) {
            return new Olingo2BatchResponse(statusCode, statusInfo, response.getContentId(), response.getHeaders(), e);
        }

        // resolve resource path and query params and parse batch part uri
        final String resourcePath = request.getResourcePath();
        final String resolvedResourcePath;
        if (resourcePath.startsWith("$") && !(METADATA.equals(resourcePath) || BATCH.equals(resourcePath))) {
            resolvedResourcePath = findLocation(resourcePath, contentIdLocationMap);
        } else {
            final String resourceLocation = response.getHeader(HttpHeaders.LOCATION);
            resolvedResourcePath = resourceLocation != null ? resourceLocation.substring(serviceUri.length()) : resourcePath;
        }
        final Map<String, String> resolvedQueryParams = request instanceof Olingo2BatchQueryRequest ? ((Olingo2BatchQueryRequest)request).getQueryParams() : null;
        final UriInfoWithType uriInfo = parseUri(edm, resolvedResourcePath, resolvedQueryParams);

        // resolve response content
        final Object resolvedContent = content != null ? readContent(uriInfo, content) : null;

        return new Olingo2BatchResponse(statusCode, statusInfo, response.getContentId(), response.getHeaders(), resolvedContent);
    }

    private ContentType receiveWithCharsetParameter(ContentType contentType, Charset charset) {
        if (contentType.getCharset() != null) {
            return contentType;
        }
        final String mimeType = contentType.getMimeType();
        if (mimeType.equals(ContentType.TEXT_PLAIN.getMimeType()) || AbstractFutureCallback.ODATA_MIME_TYPE.matcher(mimeType).matches()) {
            return contentType.withCharset(charset);
        }
        return contentType;
    }

    private String findLocation(String resourcePath, Map<String, String> contentIdLocationMap) {
        final int pathSeparator = resourcePath.indexOf('/');
        if (pathSeparator == -1) {
            return contentIdLocationMap.get(resourcePath);
        } else {
            return contentIdLocationMap.get(resourcePath.substring(0, pathSeparator)) + resourcePath.substring(pathSeparator);
        }

    }

    private String createBatchUri(Olingo2BatchRequest part) {
        String result;
        if (part instanceof Olingo2BatchQueryRequest) {
            final Olingo2BatchQueryRequest queryPart = (Olingo2BatchQueryRequest)part;
            result = createUri(queryPart.getResourcePath(), queryPart.getQueryParams());
        } else {
            result = createUri(part.getResourcePath());
        }
        // strip base URI
        return result.substring(serviceUri.length() + 1);
    }

    private String createUri(String resourcePath) {
        return createUri(resourcePath, null);
    }

    private String createUri(String resourcePath, Map<String, String> queryParams) {

        final StringBuilder absolutUri = new StringBuilder(serviceUri).append(SEPARATOR).append(resourcePath);
        if (queryParams != null && !queryParams.isEmpty()) {
            absolutUri.append("?");
            int nParams = queryParams.size();
            int index = 0;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                absolutUri.append(entry.getKey()).append('=').append(entry.getValue());
                if (++index < nParams) {
                    absolutUri.append('&');
                }
            }
        }
        return absolutUri.toString();
    }

    private static UriInfoWithType parseUri(Edm edm, String resourcePath, Map<String, String> queryParams) {
        UriInfoWithType result;
        try {
            final List<PathSegment> pathSegments = new ArrayList<>();
            final String[] segments = new URI(resourcePath).getPath().split(SEPARATOR);
            if (queryParams == null) {
                queryParams = Collections.emptyMap();
            }
            for (String segment : segments) {
                if (segment.indexOf(';') == -1) {

                    pathSegments.add(new ODataPathSegmentImpl(segment, null));
                } else {

                    // handle matrix params in path segment
                    final String[] splitSegment = segment.split(";");
                    segment = splitSegment[0];

                    Map<String, List<String>> matrixParams = new HashMap<>();
                    for (int i = 1; i < splitSegment.length; i++) {
                        final String[] param = splitSegment[i].split("=");
                        List<String> values = matrixParams.get(param[0]);
                        if (values == null) {
                            values = new ArrayList<>();
                            matrixParams.put(param[0], values);
                        }
                        if (param[1].indexOf(',') == -1) {
                            values.add(param[1]);
                        } else {
                            values.addAll(Arrays.asList(param[1].split(",")));
                        }
                    }
                    pathSegments.add(new ODataPathSegmentImpl(segment, matrixParams));
                }
            }
            result = new UriInfoWithType(UriParser.parse(edm, pathSegments, queryParams), resourcePath);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("resourcePath: " + e.getMessage(), e);
        } catch (ODataException e) {
            throw new IllegalArgumentException("resourcePath: " + e.getMessage(), e);
        }

        return result;
    }

    private static Map<String, String> headersToMap(final Header[] headers) {
        final Map<String, String> responseHeaders = new HashMap<>();
        for (Header header : headers) {
            responseHeaders.put(header.getName(), header.getValue());
        }
        return responseHeaders;
    }

    /**
     * public for unit test, not to be used otherwise
     */
    public void execute(final HttpUriRequest httpUriRequest, final ContentType contentType, final Map<String, String> endpointHttpHeaders,
                        final FutureCallback<HttpResponse> callback) {

        // add accept header when its not a form or multipart
        if (!ContentType.APPLICATION_FORM_URLENCODED.getMimeType().equals(contentType.getMimeType()) && !contentType.getMimeType().startsWith(MULTIPART_MIME_TYPE)) {
            // otherwise accept what is being sent
            httpUriRequest.addHeader(HttpHeaders.ACCEPT, contentType.withCharset("").toString().toLowerCase());
            final Charset charset = contentType.getCharset();
            if (null != charset) {
                httpUriRequest.addHeader(HttpHeaders.ACCEPT_CHARSET, charset.name().toLowerCase());
            }
        }
        // is something being sent?
        if (httpUriRequest instanceof HttpEntityEnclosingRequestBase && httpUriRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE) == null) {
            httpUriRequest.addHeader(HttpHeaders.CONTENT_TYPE, contentType.toString());
        }

        // set user specified custom headers
        if (httpHeaders != null && !httpHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
                httpUriRequest.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // set user specified endpoint headers
        if ((endpointHttpHeaders != null) && !endpointHttpHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : endpointHttpHeaders.entrySet()) {
                httpUriRequest.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // add client protocol version if not specified
        if (!httpUriRequest.containsHeader(ODataHttpHeaders.DATASERVICEVERSION)) {
            httpUriRequest.addHeader(ODataHttpHeaders.DATASERVICEVERSION, ODataServiceVersion.V20);
        }
        if (!httpUriRequest.containsHeader(MAX_DATA_SERVICE_VERSION)) {
            httpUriRequest.addHeader(MAX_DATA_SERVICE_VERSION, ODataServiceVersion.V30);
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
