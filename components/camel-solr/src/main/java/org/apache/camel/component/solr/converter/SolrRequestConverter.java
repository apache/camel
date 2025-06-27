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
package org.apache.camel.component.solr.converter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.solr.SolrConfiguration;
import org.apache.camel.component.solr.SolrConstants;
import org.apache.camel.component.solr.SolrOperation;
import org.apache.camel.component.solr.SolrProducer;
import org.apache.camel.component.solr.SolrUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.beans.BindingException;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.SolrPing;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.ContentStreamBase;

@Converter(generateLoader = true)
@SuppressWarnings("unchecked")
public final class SolrRequestConverter {

    public static final String DEFAULT_UPDATE_REQUEST_HANDLER = "/update";

    private SolrRequestConverter() {
    }

    @Converter
    public static SolrPing createSolrPing(Object body, Exchange exchange) {
        return new SolrPing();
    }

    @Converter
    public static QueryRequest createQueryRequest(Object body, Exchange exchange) {
        if (body instanceof QueryRequest queryRequest) {
            return queryRequest;
        }
        SolrQuery solrQuery;
        // set query
        if (body instanceof SolrQuery solrQuery1) {
            solrQuery = solrQuery1;
        } else {
            String queryString = exchange.getMessage().getHeader(SolrConstants.PARAM_QUERY_STRING, String.class);
            if (ObjectHelper.isEmpty(queryString)) {
                queryString = exchange.getMessage().getBody(String.class);
            }
            solrQuery = new SolrQuery(queryString);
        }
        SolrProducer.ActionContext ctx
                = exchange.getProperty(SolrConstants.PROPERTY_ACTION_CONTEXT, SolrProducer.ActionContext.class);
        SolrConfiguration configuration = ctx.configuration();

        // Set size parameter and from parameter for search
        Integer from = exchange.getMessage().getHeader(SolrConstants.PARAM_FROM, configuration.getFrom(), Integer.class);
        if (from != null) {
            solrQuery.setStart(from);
        }
        Integer size = exchange.getMessage().getHeader(SolrConstants.PARAM_SIZE, configuration.getSize(), Integer.class);
        if (size != null) {
            solrQuery.setRows(size);
        }
        // Set requestHandler parameter as solr param qt (search only)
        String requestHandler = ctx.requestHandler();
        if (requestHandler != null) {
            solrQuery.add("qt", requestHandler);
        }
        solrQuery.add(ctx.solrParams());
        return new QueryRequest(solrQuery);
    }

    public static boolean isUseContentStreamUpdateRequest(SolrProducer.ActionContext ctx) {
        if (!SolrOperation.INSERT.equals(ctx.operation())) {
            return false;
        }
        Object body = ctx.exchange().getMessage().getBody();
        if (body instanceof String bodyAsString) {
            // string body --> determine content type to use for the string
            // if not detected, use regular update request
            String contentType = ctx.exchange().getMessage().getHeader(SolrConstants.PARAM_CONTENT_TYPE, String.class);
            if (ObjectHelper.isEmpty(contentType)) {
                contentType = ContentStreamBase.StringStream.detect(bodyAsString);
            }
            if (ObjectHelper.isEmpty(contentType)) {
                // couldn't detect -> use regular update request
                return false;
            }
            appendAddCommandToXML(ctx, bodyAsString, contentType);
            return true;
        }
        return (body instanceof File || body instanceof WrappedFile<?>);
    }

    private static ContentStreamUpdateRequest createNewContentStreamUpdateRequest(SolrProducer.ActionContext ctx) {
        ContentStreamUpdateRequest updateRequest = ctx.requestHandler() != null
                ? new ContentStreamUpdateRequest(ctx.requestHandler())
                : new ContentStreamUpdateRequest(DEFAULT_UPDATE_REQUEST_HANDLER);
        updateRequest.setParams(ctx.solrParams());
        return updateRequest;
    }

    @Converter
    public static ContentStreamUpdateRequest createContentStreamUpdateRequest(Object body, Exchange exchange)
            throws NoTypeConversionAvailableException {
        SolrProducer.ActionContext ctx
                = exchange.getProperty(SolrConstants.PROPERTY_ACTION_CONTEXT, SolrProducer.ActionContext.class);
        String contentType = ctx.exchange().getMessage().getHeader(SolrConstants.PARAM_CONTENT_TYPE, String.class);
        ContentStreamUpdateRequest streamUpdateRequest = createNewContentStreamUpdateRequest(ctx);
        if (body instanceof WrappedFile<?> wrappedFile) {
            body = wrappedFile.getFile();
        }
        if (body instanceof File file) {
            ContentStreamBase.FileStream stream = new ContentStreamBase.FileStream(file);
            if (ObjectHelper.isEmpty(contentType)) {
                contentType = stream.getContentType();
            }
            stream.setContentType(contentType);
            streamUpdateRequest.addContentStream(stream);
            return streamUpdateRequest;
        }
        if (body instanceof String string) {
            ContentStreamBase.StringStream stream;
            if (ObjectHelper.isEmpty(contentType)) {
                stream = new ContentStreamBase.StringStream(string);
            } else {
                stream = new ContentStreamBase.StringStream(string, contentType);
            }
            streamUpdateRequest.addContentStream(stream);
            return streamUpdateRequest;
        }
        throw new NoTypeConversionAvailableException(body, SolrRequestConverter.class);
    }

    private static UpdateRequest createNewUpdateRequest(SolrProducer.ActionContext ctx) {
        UpdateRequest updateRequest = ctx.requestHandler() != null
                ? new UpdateRequest(ctx.requestHandler())
                : new UpdateRequest();
        updateRequest.setParams(ctx.solrParams());
        return updateRequest;
    }

    @Converter
    public static UpdateRequest createUpdateRequest(Object body, Exchange exchange) throws NoTypeConversionAvailableException {
        SolrProducer.ActionContext ctx
                = exchange.getProperty(SolrConstants.PROPERTY_ACTION_CONTEXT, SolrProducer.ActionContext.class);
        switch (ctx.operation()) {
            case DELETE -> {
                return createUpdateRequestForDelete(body, exchange, ctx);
            }
            case INSERT -> {
                return createUpdateRequestForInsert(body, exchange, ctx);
            }
            default -> throw new IllegalArgumentException(
                    SolrConstants.PARAM_OPERATION + " value '" + ctx.operation() + "' is not implemented");
        }
    }

    private static UpdateRequest createUpdateRequestForDelete(Object body, Exchange exchange, SolrProducer.ActionContext ctx) {
        UpdateRequest updateRequest = createNewUpdateRequest(ctx);
        boolean deleteByQuery = ctx.exchange().getMessage()
                .getHeader(SolrConstants.PARAM_DELETE_BY_QUERY, ctx.configuration().isDeleteByQuery(), Boolean.class);
        // for now, keep old operation supported until deprecation
        deleteByQuery = deleteByQuery
                || SolrConstants.OPERATION_DELETE_BY_QUERY
                        .equalsIgnoreCase(exchange.getMessage()
                                .getHeader(SolrConstants.PARAM_OPERATION, "", String.class));
        if (deleteByQuery) {
            if (SolrUtils.isCollectionOfType(body, String.class)) {
                updateRequest.setDeleteQuery(SolrUtils.convertToList((Collection<String>) body));
                return updateRequest;
            } else {
                return updateRequest.deleteByQuery(String.valueOf(body));
            }
        }
        if (SolrUtils.isCollectionOfType(body, String.class)) {
            return updateRequest.deleteById(SolrUtils.convertToList((Collection<String>) body));
        }
        return updateRequest.deleteById(String.valueOf(body));
    }

    private static UpdateRequest createUpdateRequestForInsert(Object body, Exchange exchange, SolrProducer.ActionContext ctx)
            throws NoTypeConversionAvailableException {
        UpdateRequest updateRequest = createNewUpdateRequest(ctx);
        // SolrInputDocument
        if (body instanceof SolrInputDocument solrInputDocument) {
            updateRequest.add(solrInputDocument);
            return updateRequest;
        }
        // Collection<SolrInputDocument>
        if (SolrUtils.isCollectionOfType(body, SolrInputDocument.class)) {
            updateRequest.add((Collection<SolrInputDocument>) body);
            return updateRequest;
        }
        // Collection<Map>
        if (SolrUtils.isCollectionOfType(body, Map.class)) {
            Optional<Collection<SolrInputDocument>> docs
                    = getOptionalCollectionOfSolrInputDocument((Collection<Map<?, ?>>) body, exchange);
            docs.ifPresent(updateRequest::add);
            return updateRequest;
        }
        // Map: gather solr fields from body and merge with solr fields from headers (gathered from SolrField.xxx headers)
        //      The header solr fields have priority
        Map<String, Object> map = new LinkedHashMap<>(getMapFromBody(body));
        map.putAll(getMapFromHeaderSolrFields(exchange));
        if (!map.isEmpty()) {
            body = map;
        }
        // Map: translate to SolrInputDocument (possibly gathered from SolrField.xxx headers
        Optional<SolrInputDocument> doc = getOptionalSolrInputDocumentFromMap(body, exchange);
        if (doc.isPresent()) {
            updateRequest.add(doc.get());
            return updateRequest;
        }
        // beans
        try {
            DocumentObjectBinder binder = new DocumentObjectBinder();
            if (SolrUtils.isCollectionOfType(body, Object.class)) {
                Collection<?> objects = (Collection<?>) body;
                objects.forEach(o -> updateRequest.add(binder.toSolrInputDocument(o)));
                return updateRequest;
            }
            updateRequest.add(binder.toSolrInputDocument(body));
            return updateRequest;
        } catch (BindingException ignored) {
        }
        // when "invalid" body with solr params, allow processing (e.g. commit) without "body"
        if (ctx.solrParams().size() > 0) {
            return updateRequest;
        }
        throw new NoTypeConversionAvailableException(body, SolrRequestConverter.class);
    }

    private static void appendAddCommandToXML(SolrProducer.ActionContext ctx, String bodyAsString, String contentType) {
        if ((contentType.startsWith(ContentStreamBase.TEXT_XML)
                || contentType.startsWith(ContentStreamBase.APPLICATION_XML))
                && !(bodyAsString.startsWith("<add"))) {
            ctx.exchange().getMessage().setBody("<add>" + bodyAsString + "</add>");
        }
    }

    private static Map<String, Object> getMapFromBody(Object body) {
        if (body instanceof Map) {
            return ((Map<?, ?>) body).entrySet().stream()
                    .collect(
                            Collectors.toMap(
                                    entry -> String.valueOf(entry.getKey()),
                                    Map.Entry::getValue));
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> getMapFromHeaderSolrFields(Exchange exchange) {
        return exchange.getMessage().getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(SolrConstants.HEADER_FIELD_PREFIX))
                .collect(
                        Collectors.toMap(
                                entry -> entry.getKey().substring(SolrConstants.HEADER_FIELD_PREFIX.length()),
                                Map.Entry::getValue));
    }

    private static Optional<Collection<SolrInputDocument>> getOptionalCollectionOfSolrInputDocument(
            Collection<Map<?, ?>> maps, Exchange exchange) {
        Collection<SolrInputDocument> docs = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            Optional<SolrInputDocument> doc = getOptionalSolrInputDocumentFromMap(map, exchange);
            doc.ifPresent(docs::add);
        }
        return docs.isEmpty() ? Optional.empty() : Optional.of(docs);
    }

    private static Optional<SolrInputDocument> getOptionalSolrInputDocumentFromMap(Object body, Exchange exchange) {
        Map<String, Object> map = getMapFromBody(body);
        if (!map.isEmpty()) {
            SolrInputDocument doc = new SolrInputDocument();
            map.forEach(doc::setField);
            return Optional.of(doc);
        }
        return Optional.empty();
    }

}
