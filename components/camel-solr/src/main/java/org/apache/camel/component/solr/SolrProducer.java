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
package org.apache.camel.component.solr;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.activation.MimetypesFileTypeMap;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.DirectXmlRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

/**
 * The Solr producer.
 */
public class SolrProducer extends DefaultProducer {

    public SolrProducer(SolrEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String operation = (String) exchange.getIn().getHeader(SolrConstants.OPERATION);
        if (operation == null) {
            throw new IllegalArgumentException(SolrConstants.OPERATION + " header is missing");
        }

        // solr configuration
        SolrConfiguration solrConfiguration = getEndpoint().getSolrConfiguration(operation);

        // solr client
        SolrClient solrClient = exchange.getIn().getHeader(SolrConstants.CLIENT, SolrClient.class);
        if (solrClient == null) {
            solrClient = getEndpoint().getComponent().getSolrClient(this, solrConfiguration);
        }

        // solr parameters
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                solrParams.add(paramName, entry.getValue().toString());
            }
        }

        // solr collection
        String solrCollection = exchange.getIn().getHeader(SolrConstants.COLLECTION, String.class);

        // solr operations
        if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT)) {
            insert(exchange, solrClient, solrConfiguration, solrParams);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT_STREAMING)) {
            insert(exchange, solrClient, solrConfiguration, solrParams);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_ID)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            updateRequest.deleteById(exchange.getIn().getBody(String.class));
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_QUERY)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            updateRequest.deleteByQuery(exchange.getIn().getBody(String.class));
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ADD_BEAN)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            updateRequest.add(solrClient.getBinder().toSolrInputDocument(exchange.getIn().getBody()));
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ADD_BEANS)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            Collection<Object> body = exchange.getIn().getBody(Collection.class);
            updateRequest.add(body.stream().map(solrClient.getBinder()::toSolrInputDocument).collect(Collectors.toList()));
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_COMMIT)) {
            commit(exchange, solrClient, solrConfiguration, solrParams);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_SOFT_COMMIT)) {
            commit(exchange, solrClient, solrConfiguration, solrParams, true);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ROLLBACK)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            updateRequest.rollback();
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_OPTIMIZE)) {
            UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
            updateRequest.setAction(ACTION.OPTIMIZE, true, true, 1);
            updateRequest.process(solrClient, solrCollection);
            if (solrConfiguration.isAutoCommit()) {
                commit(exchange, solrClient, solrConfiguration, solrParams);
            }
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_QUERY)) {
            query(exchange, solrClient, solrConfiguration, solrParams);
        } else {
            throw new IllegalArgumentException(
                    SolrConstants.OPERATION + " header value '" + operation + "' is not supported");
        }
    }

    private void commit(
            Exchange exchange,
            SolrClient solrClient,
            SolrConfiguration solrConfiguration,
            ModifiableSolrParams solrParams)
            throws SolrServerException, IOException {
        commit(exchange, solrClient, solrConfiguration, solrParams, false);
    }

    private void commit(
            Exchange exchange,
            SolrClient solrClient,
            SolrConfiguration solrConfiguration,
            ModifiableSolrParams solrParams,
            boolean softCommit)
            throws SolrServerException, IOException {
        String solrCollection = exchange.getIn().getHeader(SolrConstants.COLLECTION, String.class);
        UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
        updateRequest.setAction(
                ACTION.COMMIT,
                solrParams.getBool("waitFlush", true),
                solrParams.getBool("waitSearcher", true),
                softCommit);
        updateRequest.process(solrClient, solrCollection);
    }

    private void query(
            Exchange exchange, SolrClient solrClient, SolrConfiguration solrConfiguration, ModifiableSolrParams solrParams)
            throws SolrServerException, IOException {
        String solrCollection = exchange.getIn().getHeader(SolrConstants.COLLECTION, String.class);
        SolrQuery solrQuery = new SolrQuery();
        if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(SolrConstants.QUERY_STRING))) {
            solrQuery.setQuery(exchange.getMessage().getHeader(SolrConstants.QUERY_STRING, String.class));
        } else {
            throw new IllegalArgumentException("Query String needs to be set as header while querying Solr");
        }
        solrQuery.add(solrParams);
        QueryRequest queryRequest = new QueryRequest(solrQuery);
        queryRequest.setBasicAuthCredentials(solrConfiguration.getUsername(), solrConfiguration.getPassword());
        QueryResponse p = queryRequest.process(solrClient, solrCollection);
        exchange.getMessage().setBody(p.getResults());
    }

    private void insert(
            Exchange exchange, SolrClient solrClient, SolrConfiguration solrConfiguration, ModifiableSolrParams solrParams)
            throws Exception {
        String solrCollection = exchange.getIn().getHeader(SolrConstants.COLLECTION, String.class);
        Object body = exchange.getIn().getBody();
        boolean invalid = false;
        if (body instanceof WrappedFile) {
            body = ((WrappedFile<?>) body).getFile();
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(SolrConstants.CONTENT_TYPE, String.class))) {
            String mimeType = exchange.getIn().getHeader(SolrConstants.CONTENT_TYPE, String.class);
            ContentStreamUpdateRequest updateRequest = new ContentStreamUpdateRequest(getRequestHandler(solrConfiguration));
            updateRequest.setParams(solrParams);
            updateRequest.setBasicAuthCredentials(solrConfiguration.getUsername(), solrConfiguration.getPassword());
            updateRequest.addFile((File) body, mimeType);
            updateRequest.process(solrClient, solrCollection);
        } else {
            if (body instanceof File) {
                MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
                String mimeType = mimeTypesMap.getContentType((File) body);
                ContentStreamUpdateRequest updateRequest = new ContentStreamUpdateRequest(getRequestHandler(solrConfiguration));
                updateRequest.setParams(solrParams);
                updateRequest.setBasicAuthCredentials(solrConfiguration.getUsername(), solrConfiguration.getPassword());
                updateRequest.addFile((File) body, mimeType);
                updateRequest.process(solrClient, solrCollection);
            } else if (body instanceof SolrInputDocument) {
                UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
                updateRequest.add((SolrInputDocument) body);
                updateRequest.process(solrClient, solrCollection);
            } else if (body instanceof List<?>) {
                List<?> list = (List<?>) body;
                if (!list.isEmpty() && list.get(0) instanceof SolrInputDocument) {
                    UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
                    updateRequest.add((List<SolrInputDocument>) list);
                    updateRequest.process(solrClient, solrCollection);
                } else {
                    invalid = true;
                }
            } else {
                boolean hasSolrHeaders = false;
                for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                    if (entry.getKey().startsWith(SolrConstants.FIELD)) {
                        hasSolrHeaders = true;
                        break;
                    }
                }
                if (hasSolrHeaders) {
                    UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
                    SolrInputDocument doc = new SolrInputDocument();
                    for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                        if (entry.getKey().startsWith(SolrConstants.FIELD)) {
                            String fieldName = entry.getKey().substring(SolrConstants.FIELD.length());
                            doc.setField(fieldName, entry.getValue());
                        }
                    }
                    updateRequest.add(doc);
                    updateRequest.process(solrClient, solrCollection);
                } else if (body instanceof String) {
                    String bodyAsString = (String) body;
                    if (!bodyAsString.startsWith("<add")) {
                        bodyAsString = "<add>" + bodyAsString + "</add>";
                    }
                    DirectXmlRequest xmlRequest = new DirectXmlRequest(getRequestHandler(solrConfiguration), bodyAsString);
                    xmlRequest.setParams(solrParams);
                    xmlRequest.setBasicAuthCredentials(solrConfiguration.getUsername(), solrConfiguration.getPassword());
                    xmlRequest.process(solrClient, solrCollection);
                } else if (body instanceof Map) {
                    UpdateRequest updateRequest = createUpdateRequest(solrConfiguration, solrParams);
                    SolrInputDocument doc = new SolrInputDocument();
                    Map<String, Object> bodyMap = (Map<String, Object>) body;
                    for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
                        doc.setField(entry.getKey(), entry.getValue());
                    }
                    updateRequest.add(doc);
                    updateRequest.process(solrClient, solrCollection);
                } else {
                    invalid = true;
                }
            }
        }

        if (invalid) {
            throw new SolrException(
                    SolrException.ErrorCode.BAD_REQUEST,
                    "unable to find data in Exchange to update Solr");
        }
    }

    private String getRequestHandler(SolrConfiguration solrConfiguration) {
        String requestHandler = solrConfiguration.getRequestHandler();
        return (requestHandler == null) ? "/update" : requestHandler;
    }

    private UpdateRequest createUpdateRequest(SolrConfiguration solrConfiguration, ModifiableSolrParams solrParams) {
        UpdateRequest updateRequest = new UpdateRequest(getRequestHandler(solrConfiguration));
        updateRequest.setParams(solrParams);
        updateRequest.setBasicAuthCredentials(solrConfiguration.getUsername(), solrConfiguration.getPassword());
        return updateRequest;
    }

    @Override
    public SolrEndpoint getEndpoint() {
        return (SolrEndpoint) super.getEndpoint();
    }

    @Override
    protected void doShutdown() throws Exception {
        getEndpoint().onProducerShutdown(this);
    }

}
