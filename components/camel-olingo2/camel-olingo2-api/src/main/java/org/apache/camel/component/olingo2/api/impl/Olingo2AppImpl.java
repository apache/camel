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
package org.apache.camel.component.olingo2.api.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.component.olingo2.api.Olingo2App;
import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.nio.client.util.HttpAsyncClientUtils;
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
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmProperty;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderException;
import org.apache.olingo.odata2.api.ep.EntityProviderReadProperties;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.apache.olingo.odata2.api.processor.ODataResponse;
import org.apache.olingo.odata2.api.uri.PathSegment;
import org.apache.olingo.odata2.api.uri.UriParser;
import org.apache.olingo.odata2.core.ODataPathSegmentImpl;
import org.apache.olingo.odata2.core.commons.ContentType;
import org.apache.olingo.odata2.core.uri.UriInfoImpl;

/**
 * Application API used by Olingo2 Component.
 */
public final class Olingo2AppImpl implements Olingo2App {

    public static final ContentType APPLICATION_FORM_URL_ENCODED = ContentType.create("application", "x-www-form-urlencoded");

    public static final String METADATA = "$metadata";

    private static final String SEPARATOR = "/";

    private static final String BOUNDARY_PREFIX = "batch_";
    private static final String BOUNDARY_PARAMETER = "boundary";

    private static final ContentType METADATA_CONTENT_TYPE = ContentType.APPLICATION_XML_CS_UTF_8;
    private static final ContentType SERVICE_DOCUMENT_CONTENT_TYPE = ContentType.APPLICATION_ATOM_SVC_CS_UTF_8;
    private static final ContentType BATCH_CONTENT_TYPE =
        ContentType.MULTIPART_MIXED.receiveWithCharsetParameter(ContentType.CHARSET_UTF_8);
    private static final String BATCH = "$batch";
    private static final String MAX_DATA_SERVICE_VERSION = "Max" + ODataHttpHeaders.DATASERVICEVERSION;

    private final CloseableHttpAsyncClient client;

    private String serviceUri;
    private ContentType contentType;
    private Map<String, String> httpHeaders;

    /**
     * Create Olingo2 Application with default HTTP configuration.
     */
    public Olingo2AppImpl(String serviceUri) {
        this(serviceUri, null);
    }

    /**
     * Create Olingo2 Application with custom HTTP client builder.
     *
     * @param serviceUri Service Application base URI.
     * @param builder custom HTTP client builder.
     */
    public Olingo2AppImpl(String serviceUri, HttpAsyncClientBuilder builder) {
        if (serviceUri == null) {
            throw new IllegalArgumentException("serviceUri");
        }
        this.serviceUri = serviceUri;

        if (builder == null) {
            this.client = HttpAsyncClients.createDefault();
        } else {
            this.client = builder.build();
        }
        this.client.start();
        this.contentType = ContentType.APPLICATION_JSON_CS_UTF_8;
    }

    @Override
    public void setServiceUri(String serviceUri) {
        this.serviceUri = serviceUri;
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
        HttpAsyncClientUtils.closeQuietly(client);
    }

    @Override
    public <T> void read(final Edm edm, final String resourcePath, final Map<String, String> queryParams,
                         final Olingo2ResponseHandler<T> responseHandler) {

        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, queryParams);

        execute(new HttpGet(createUri(resourcePath, queryParams)), getResourceContentType(uriInfo),
            new AbstractFutureCallback<T>(responseHandler) {

                @Override
                @SuppressWarnings("unchecked")
                public void onCompleted(HttpResponse result) throws IOException {

                    readContent(uriInfo, result.getEntity() != null ? result.getEntity().getContent() : null,
                        responseHandler);
                }

            });
    }

    private ContentType getResourceContentType(UriInfoImpl uriInfo) {
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
                resourceContentType = ContentType.TEXT_PLAIN_CS_UTF_8;
            } else {
                resourceContentType = contentType;
            }
            break;
        case URI15:
        case URI16:
        case URI50A:
        case URI50B:
            // $count
            resourceContentType = ContentType.TEXT_PLAIN_CS_UTF_8;
            break;
        default:
            resourceContentType = contentType;
        }
        return resourceContentType;
    }

    @Override
    public <T> void create(Edm edm, String resourcePath, Object data, Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPost(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public <T> void update(Edm edm, String resourcePath, Object data, Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPut(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public <T> void patch(Edm edm, String resourcePath, Object data, Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpPatch(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public <T> void merge(Edm edm, String resourcePath, Object data, Olingo2ResponseHandler<T> responseHandler) {
        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, null);

        writeContent(edm, new HttpMerge(createUri(resourcePath, null)), uriInfo, data, responseHandler);
    }

    @Override
    public void batch(Edm edm, Object data, Olingo2ResponseHandler<List<Olingo2BatchResponse>> responseHandler) {
        final UriInfoImpl uriInfo = parseUri(edm, BATCH, null);

        writeContent(edm, new HttpPost(createUri(BATCH, null)), uriInfo, data, responseHandler);
    }

    @Override
    public void delete(String resourcePath, final Olingo2ResponseHandler<HttpStatusCodes> responseHandler) {

        execute(new HttpDelete(createUri(resourcePath)), contentType,
            new AbstractFutureCallback<HttpStatusCodes>(responseHandler) {
                @Override
                public void onCompleted(HttpResponse result) {
                    final StatusLine statusLine = result.getStatusLine();
                    responseHandler.onResponse(HttpStatusCodes.fromStatusCode(statusLine.getStatusCode()));
                }
            });
    }

    private <T> void readContent(UriInfoImpl uriInfo, InputStream content, Olingo2ResponseHandler<T> responseHandler) {
        try {
            responseHandler.onResponse(this.<T>readContent(uriInfo, content));
        } catch (EntityProviderException e) {
            responseHandler.onException(e);
        } catch (ODataApplicationException e) {
            responseHandler.onException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T readContent(UriInfoImpl uriInfo, InputStream content)
        throws EntityProviderException, ODataApplicationException {
        T response;
        switch (uriInfo.getUriType()) {
        case URI0:
            // service document
            response = (T) EntityProvider.readServiceDocument(content, SERVICE_DOCUMENT_CONTENT_TYPE.toString());
            break;

        case URI8:
            // $metadata
            response = (T) EntityProvider.readMetadata(content, false);
            break;

        case URI7A:
            // link
            response = (T) EntityProvider.readLink(getContentType(), uriInfo.getTargetEntitySet(), content);
            break;

        case URI7B:
            // links
            response = (T) EntityProvider.readLinks(getContentType(), uriInfo.getTargetEntitySet(), content);
            break;

        case URI3:
            // complex property
            final List<EdmProperty> complexPropertyPath = uriInfo.getPropertyPath();
            final EdmProperty complexProperty = complexPropertyPath.get(complexPropertyPath.size() - 1);
            response = (T) EntityProvider.readProperty(getContentType(),
                complexProperty, content, EntityProviderReadProperties.init().build());
            break;

        case URI4:
        case URI5:
            // simple property
            final List<EdmProperty> simplePropertyPath = uriInfo.getPropertyPath();
            final EdmProperty simpleProperty = simplePropertyPath.get(simplePropertyPath.size() - 1);
            if (uriInfo.isValue()) {
                response = (T) EntityProvider.readPropertyValue(simpleProperty, content);
            } else {
                response = (T) EntityProvider.readProperty(getContentType(),
                    simpleProperty, content, EntityProviderReadProperties.init().build());
            }
            break;

        case URI15:
        case URI16:
        case URI50A:
        case URI50B:
            // $count
            try {
                final String stringCount = new String(EntityProvider.readBinary(content), ContentType.CHARSET_UTF_8);
                response = (T) Long.valueOf(stringCount);
            } catch (UnsupportedEncodingException e) {
                throw new EntityProviderException(EntityProviderException.EXCEPTION_OCCURRED, e);
            }
            break;

        case URI1:
        case URI6B:
            if (uriInfo.getCustomQueryOptions().containsKey("!deltaToken")) {
                // ODataDeltaFeed
                response = (T) EntityProvider.readDeltaFeed(
                    getContentType(),
                    uriInfo.getTargetEntitySet(), content,
                    EntityProviderReadProperties.init().build());
            } else {
                // ODataFeed
                response = (T) EntityProvider.readFeed(
                    getContentType(),
                    uriInfo.getTargetEntitySet(), content,
                    EntityProviderReadProperties.init().build());
            }
            break;

        case URI2:
        case URI6A:
            response = (T) EntityProvider.readEntry(
                getContentType(),
                uriInfo.getTargetEntitySet(),
                content,
                EntityProviderReadProperties.init().build());
            break;

        default:
            throw new ODataApplicationException("Unsupported resource type " + uriInfo.getTargetType(),
                Locale.ENGLISH);
        }

        return response;
    }

    private <T> void writeContent(final Edm edm, HttpEntityEnclosingRequestBase httpEntityRequest,
                                  final UriInfoImpl uriInfo, final Object content,
                                  final Olingo2ResponseHandler<T> responseHandler) {

        try {
            // process resource by UriType
            final ODataResponse response = writeContent(edm, uriInfo, content);

            // copy all response headers
            for (String header : response.getHeaderNames()) {
                httpEntityRequest.setHeader(header, response.getHeader(header));
            }

            // get (http) entity which is for default Olingo2 implementation an InputStream
            if (response.getEntity() instanceof InputStream) {
                httpEntityRequest.setEntity(new InputStreamEntity((InputStream) response.getEntity()));
/*
                // avoid sending it without a header field set
                if (!httpEntityRequest.containsHeader(HttpHeaders.CONTENT_TYPE)) {
                    httpEntityRequest.addHeader(HttpHeaders.CONTENT_TYPE, getContentType());
                }
*/
            }

            // execute HTTP request
            execute(httpEntityRequest, contentType, new AbstractFutureCallback<T>(responseHandler) {
                @SuppressWarnings("unchecked")
                @Override
                public void onCompleted(HttpResponse result)
                    throws IOException, EntityProviderException, BatchException, ODataApplicationException {

                    // if a entity is created (via POST request) the response body contains the new created entity
                    HttpStatusCodes statusCode = HttpStatusCodes.fromStatusCode(result.getStatusLine().getStatusCode());
                    if (statusCode != HttpStatusCodes.NO_CONTENT) {

                        // TODO do we need to handle response based on other UriTypes???
                        switch (uriInfo.getUriType()) {
                        case URI9:
                            // $batch
                            final List<BatchSingleResponse> singleResponses = EntityProvider.parseBatchResponse(
                                result.getEntity().getContent(),
                                result.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue());

                            // parse batch response bodies
                            final List<Olingo2BatchResponse> responses = new ArrayList<Olingo2BatchResponse>();
                            Map<String, String> contentIdLocationMap = new HashMap<String, String>();

                            final List<Olingo2BatchRequest> batchRequests = (List<Olingo2BatchRequest>) content;
                            final Iterator<Olingo2BatchRequest> iterator = batchRequests.iterator();

                            for (BatchSingleResponse response : singleResponses) {
                                final Olingo2BatchRequest request = iterator.next();

                                if (request instanceof Olingo2BatchChangeRequest
                                    && ((Olingo2BatchChangeRequest)request).getContentId() != null) {

                                    contentIdLocationMap.put("$" + ((Olingo2BatchChangeRequest)request).getContentId(),
                                        response.getHeader(HttpHeaders.LOCATION));
                                }

                                try {
                                    responses.add(parseResponse(edm, contentIdLocationMap, request, response));
                                } catch (Exception e) {
                                    // report any parsing errors as error response
                                    responses.add(new Olingo2BatchResponse(
                                        Integer.parseInt(response.getStatusCode()),
                                        response.getStatusInfo(), response.getContentId(), response.getHeaders(),
                                        new ODataApplicationException(
                                            "Error parsing response for " + request + ": " + e.getMessage(),
                                            Locale.ENGLISH, e)));
                                }
                            }
                            responseHandler.onResponse((T) responses);
                            break;

                        default:
                            // get the response content as an ODataEntry object
                            responseHandler.onResponse((T) EntityProvider.readEntry(response.getContentHeader(),
                                uriInfo.getTargetEntitySet(),
                                result.getEntity().getContent(),
                                EntityProviderReadProperties.init().build()));
                            break;
                        }

                    } else {
                        responseHandler.onResponse(
                            (T) HttpStatusCodes.fromStatusCode(result.getStatusLine().getStatusCode()));
                    }
                }
            });
        } catch (ODataException e) {
            responseHandler.onException(e);
        } catch (URISyntaxException e) {
            responseHandler.onException(e);
        } catch (UnsupportedEncodingException e) {
            responseHandler.onException(e);
        } catch (IOException e) {
            responseHandler.onException(e);
        }
    }

    private ODataResponse writeContent(Edm edm, UriInfoImpl uriInfo, Object content)
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
                responseContentType = ContentType.TEXT_PLAIN_CS_UTF_8.toString();
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
            final URI rootLinkUri = new URI(targetLinkEntitySet.getName());
            EntityProviderWriteProperties linkProperties =
                EntityProviderWriteProperties.serviceRoot(rootLinkUri).build();
            @SuppressWarnings("unchecked")
            final Map<String, Object> linkMap = (Map<String, Object>) content;
            response = EntityProvider.writeLink(responseContentType, targetLinkEntitySet, linkMap, linkProperties);
            break;

        case URI7B:
            // $links with * cardinality property
            final EdmEntitySet targetLinksEntitySet = uriInfo.getTargetEntitySet();
            final URI rootLinksUri = new URI(targetLinksEntitySet.getName());
            EntityProviderWriteProperties linksProperties =
                EntityProviderWriteProperties.serviceRoot(rootLinksUri).build();
            @SuppressWarnings("unchecked")
            final Map<String, Object> linksMap = (Map<String, Object>) content;
            response = EntityProvider.writeLink(responseContentType, targetLinksEntitySet, linksMap, linksProperties);
            break;

        case URI1:
        case URI2:
        case URI6A:
        case URI6B:
            // Entity
            final EdmEntitySet targetEntitySet = uriInfo.getTargetEntitySet();
            final URI rootUri = new URI(targetEntitySet.getName());
            EntityProviderWriteProperties properties = EntityProviderWriteProperties.serviceRoot(rootUri).build();
            @SuppressWarnings("unchecked")
            final Map<String, Object> objectMap = (Map<String, Object>) content;
            response = EntityProvider.writeEntry(responseContentType, targetEntitySet, objectMap, properties);
            break;

        case URI9:
            // $batch
            @SuppressWarnings("unchecked")
            final List<Olingo2BatchRequest> batchParts = (List<Olingo2BatchRequest>) content;
            response = parseBatchRequest(edm, batchParts);
            break;

        default:
            // notify exception and return!!!
            throw new ODataApplicationException("Unsupported resource type " + uriInfo.getTargetType(),
                Locale.ENGLISH);
        }

        return response.getContentHeader() != null ? response
            : ODataResponse.fromResponse(response).contentHeader(responseContentType).build();
    }

    private ODataResponse parseBatchRequest(final Edm edm, final List<Olingo2BatchRequest> batchParts)
        throws IOException, EntityProviderException, ODataApplicationException, EdmException, URISyntaxException {

        // create Batch request from parts
        final ArrayList<BatchPart> parts = new ArrayList<BatchPart>();
        final ArrayList<BatchChangeSetPart> changeSetParts = new ArrayList<BatchChangeSetPart>();

        final Map<String, String> contentIdMap = new HashMap<String, String>();

        for (Olingo2BatchRequest batchPart : batchParts) {

            if (batchPart instanceof Olingo2BatchQueryRequest) {

                // need to add change set parts collected so far??
                if (!changeSetParts.isEmpty()) {
                    addChangeSetParts(parts, changeSetParts);
                    changeSetParts.clear();
                    contentIdMap.clear();
                }

                // add to request parts
                final UriInfoImpl uriInfo = parseUri(edm, batchPart.getResourcePath(), null);
                parts.add(createBatchQueryPart(uriInfo, (Olingo2BatchQueryRequest) batchPart));

            } else {

                // add to change set parts
                final BatchChangeSetPart changeSetPart = createBatchChangeSetPart(
                    edm, contentIdMap, (Olingo2BatchChangeRequest) batchPart);
                changeSetParts.add(changeSetPart);
            }
        }

        // add any remaining change set parts
        if (!changeSetParts.isEmpty()) {
            addChangeSetParts(parts, changeSetParts);
        }

        final String boundary = BOUNDARY_PREFIX + UUID.randomUUID();
        InputStream batchRequest = EntityProvider.writeBatchRequest(parts, boundary);
        // add two blank lines before all --batch boundaries
        // otherwise Olingo2 EntityProvider parser barfs in the server!!!
        final byte[] bytes = EntityProvider.readBinary(batchRequest);
        final String batchRequestBody = new String(bytes, ContentType.CHARSET_UTF_8);
        batchRequest = new ByteArrayInputStream(batchRequestBody.replaceAll(
            "--(batch_)", "\r\n\r\n--$1").getBytes(ContentType.CHARSET_UTF_8));

        final String contentHeader = ContentType.create(BATCH_CONTENT_TYPE, BOUNDARY_PARAMETER, boundary).toString();
        return ODataResponse.entity(batchRequest).contentHeader(contentHeader).build();
    }

    private void addChangeSetParts(ArrayList<BatchPart> parts, ArrayList<BatchChangeSetPart> changeSetParts) {
        final BatchChangeSet changeSet = BatchChangeSet.newBuilder().build();
        for (BatchChangeSetPart changeSetPart : changeSetParts) {
            changeSet.add(changeSetPart);
        }
        parts.add(changeSet);
    }

    private BatchChangeSetPart createBatchChangeSetPart(Edm edm, Map<String, String> contentIdMap,
                                                        Olingo2BatchChangeRequest batchRequest)
        throws EdmException, URISyntaxException, EntityProviderException, IOException, ODataApplicationException {

        // build body string
        String resourcePath = batchRequest.getResourcePath();
        // is it a referenced entity?
        if (resourcePath.startsWith("$")) {
            resourcePath = replaceContentId(edm, resourcePath, contentIdMap);
        }

        final UriInfoImpl uriInfo = parseUri(edm, resourcePath, null);

        // serialize data into ODataResponse object, if set in request and this is not a DELETE request
        final Map<String, String> headers = new HashMap<String, String>();
        byte[] body = null;

        if (batchRequest.getBody() != null
            && !Operation.DELETE.equals(batchRequest.getOperation())) {

            final ODataResponse response = writeContent(edm, uriInfo, batchRequest.getBody());
            // copy response headers
            for (String header : response.getHeaderNames()) {
                headers.put(header, response.getHeader(header));
            }

            // get (http) entity which is for default Olingo2 implementation an InputStream
            body = response.getEntity() instanceof InputStream
                ? EntityProvider.readBinary((InputStream) response.getEntity()) : null;
            if (body != null) {
                headers.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
            }
        }

        headers.put(HttpHeaders.ACCEPT, getResourceContentType(uriInfo).toString());
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.put(HttpHeaders.CONTENT_TYPE, getContentType());
        }

        // add request headers
        headers.putAll(batchRequest.getHeaders());

        final String contentId = batchRequest.getContentId();
        if (contentId != null) {
            contentIdMap.put("$" + contentId, resourcePath);
        }
        return BatchChangeSetPart.uri(createBatchUri(batchRequest))
            .method(batchRequest.getOperation().getHttpMethod())
            .contentId(contentId)
            .headers(headers)
            .body(body == null ? null : new String(body, ContentType.CHARSET_UTF_8)).build();
    }

    private BatchQueryPart createBatchQueryPart(UriInfoImpl uriInfo, Olingo2BatchQueryRequest batchRequest) {

        final Map<String, String> headers = new HashMap<String, String>(batchRequest.getHeaders());
        if (!headers.containsKey(HttpHeaders.ACCEPT)) {
            headers.put(HttpHeaders.ACCEPT, getResourceContentType(uriInfo).toString());
        }

        return BatchQueryPart.method("GET")
            .uri(createBatchUri(batchRequest))
            .headers(headers)
            .build();
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
        final EdmEntitySet entitySet = edm.getDefaultEntityContainer().getEntitySet(referencedEntity.toString());
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

        return pathSeparator == -1 ? referencedEntity.toString()
            : referencedEntity.append(entityReference.substring(pathSeparator)).toString();
    }

    private Olingo2BatchResponse parseResponse(Edm edm, Map<String, String> contentIdLocationMap,
                                              Olingo2BatchRequest request, BatchSingleResponse response)
        throws EntityProviderException, ODataApplicationException {

        // validate HTTP status
        final int statusCode = Integer.parseInt(response.getStatusCode());
        final String statusInfo = response.getStatusInfo();

        final BasicHttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
            statusCode, statusInfo));
        final Map<String, String> headers = response.getHeaders();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpResponse.setHeader(entry.getKey(), entry.getValue());
        }

        ByteArrayInputStream content = null;
        try {
            if (response.getBody() != null) {
                final ContentType partContentType = ContentType.create(
                    headers.get(HttpHeaders.CONTENT_TYPE)).receiveWithCharsetParameter(ContentType.CHARSET_UTF_8);
                final String charset = partContentType.getParameters().get(ContentType.PARAMETER_CHARSET);

                final String body = response.getBody();
                content = body != null ? new ByteArrayInputStream(body.getBytes(charset)) : null;

                httpResponse.setEntity(new StringEntity(body, charset));
            }

            AbstractFutureCallback.checkStatus(httpResponse);
        } catch (ODataApplicationException e) {
            return new Olingo2BatchResponse(
                statusCode, statusInfo, response.getContentId(),
                response.getHeaders(), e);
        } catch (UnsupportedEncodingException e) {
            return new Olingo2BatchResponse(
                statusCode, statusInfo, response.getContentId(),
                response.getHeaders(), e);
        }

        // resolve resource path and query params and parse batch part uri
        final String resourcePath = request.getResourcePath();
        final String resolvedResourcePath;
        if (resourcePath.startsWith("$") && !(METADATA.equals(resourcePath) || BATCH.equals(resourcePath))) {
            resolvedResourcePath = findLocation(resourcePath, contentIdLocationMap);
        } else {
            final String resourceLocation = response.getHeader(HttpHeaders.LOCATION);
            resolvedResourcePath = resourceLocation != null
                ? resourceLocation.substring(serviceUri.length()) : resourcePath;
        }
        final Map<String, String> resolvedQueryParams = request instanceof Olingo2BatchQueryRequest
            ? ((Olingo2BatchQueryRequest) request).getQueryParams() : null;
        final UriInfoImpl uriInfo = parseUri(edm, resolvedResourcePath, resolvedQueryParams);

        // resolve response content
        final Object resolvedContent = content != null ? readContent(uriInfo, content) : null;

        return new Olingo2BatchResponse(statusCode, statusInfo, response.getContentId(), response.getHeaders(),
            resolvedContent);
    }

    private String findLocation(String resourcePath, Map<String, String> contentIdLocationMap) {
        final int pathSeparator = resourcePath.indexOf('/');
        if (pathSeparator == -1) {
            return contentIdLocationMap.get(resourcePath);
        } else {
            return contentIdLocationMap.get(resourcePath.substring(0, pathSeparator))
                + resourcePath.substring(pathSeparator);
        }

    }

    private String createBatchUri(Olingo2BatchRequest part) {
        String result;
        if (part instanceof Olingo2BatchQueryRequest) {
            final Olingo2BatchQueryRequest queryPart = (Olingo2BatchQueryRequest) part;
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
            absolutUri.append("/?");
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

    private static UriInfoImpl parseUri(Edm edm, String resourcePath, Map<String, String> queryParams) {
        UriInfoImpl result;
        try {
            final List<PathSegment> pathSegments = new ArrayList<PathSegment>();
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

                    Map<String, List<String>> matrixParams = new HashMap<String, List<String>>();
                    for (int i = 1; i < splitSegment.length; i++) {
                        final String[] param = splitSegment[i].split("=");
                        List<String> values = matrixParams.get(param[0]);
                        if (values == null) {
                            values = new ArrayList<String>();
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
            result = (UriInfoImpl) UriParser.parse(edm, pathSegments, queryParams);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("resourcePath: " + e.getMessage(), e);
        } catch (ODataException e) {
            throw new IllegalArgumentException("resourcePath: " + e.getMessage(), e);
        }

        return result;
    }

    // public for unit test, not to be used otherwise
    public void execute(HttpUriRequest httpUriRequest, ContentType contentType,
                        FutureCallback<HttpResponse> callback) {

        // add accept header when its not a form or multipart
        final String contentTypeString = contentType.toString();
        if (!APPLICATION_FORM_URL_ENCODED.equals(contentType)
            && !contentType.getType().equals(ContentType.MULTIPART_MIXED.getType())) {
            // otherwise accept what is being sent
            httpUriRequest.addHeader(HttpHeaders.ACCEPT, contentTypeString);
        }
        // is something being sent?
        if (httpUriRequest instanceof HttpEntityEnclosingRequestBase) {
            httpUriRequest.addHeader(HttpHeaders.CONTENT_TYPE, contentTypeString);
        }

        // set user specified custom headers
        if (httpHeaders != null && !httpHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
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
        client.execute(httpUriRequest, callback);
    }

}
