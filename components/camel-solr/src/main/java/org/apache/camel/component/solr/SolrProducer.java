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
package org.apache.camel.component.solr;

import java.io.File;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.impl.DefaultProducer;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.DirectXmlRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

/**
 * The Solr producer.
 */
public class SolrProducer extends DefaultProducer {
    private HttpSolrServer solrServer;
    private ConcurrentUpdateSolrServer streamingSolrServer;

    public SolrProducer(SolrEndpoint endpoint, HttpSolrServer solrServer, ConcurrentUpdateSolrServer streamingSolrServer) {
        super(endpoint);
        this.solrServer = solrServer;
        this.streamingSolrServer = streamingSolrServer;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String operation = (String) exchange.getIn().getHeader(SolrConstants.OPERATION);

        if (operation == null) {
            throw new IllegalArgumentException(SolrConstants.OPERATION + " header is missing");
        }

        if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT)) {
            insert(exchange, false);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT_STREAMING)) {
            insert(exchange, true);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_ID)) {
            solrServer.deleteById(exchange.getIn().getBody(String.class));
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_QUERY)) {
            solrServer.deleteByQuery(exchange.getIn().getBody(String.class));
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ADD_BEAN)) {
            solrServer.addBean(exchange.getIn().getBody());
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_COMMIT)) {
            solrServer.commit();
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ROLLBACK)) {
            solrServer.rollback();
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_OPTIMIZE)) {
            solrServer.optimize();
        } else {
            throw new IllegalArgumentException(SolrConstants.OPERATION + " header value '" + operation + "' is not supported");
        }
    }

    private void insert(Exchange exchange, boolean isStreaming) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof WrappedFile) {
            body = ((WrappedFile<?>)body).getFile();
        }

        if (body instanceof File) {
            MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
            String mimeType = mimeTypesMap.getContentType((File)body);
            ContentStreamUpdateRequest updateRequest = new ContentStreamUpdateRequest(getRequestHandler());
            updateRequest.addFile((File) body, null);

            for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                    String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                    updateRequest.setParam(paramName, entry.getValue().toString());
                }
            }

            if (isStreaming) {
                updateRequest.process(streamingSolrServer);
            } else {
                updateRequest.process(solrServer);
            }

        } else if (body instanceof SolrInputDocument) {

            UpdateRequest updateRequest = new UpdateRequest(getRequestHandler());
            updateRequest.add((SolrInputDocument) body);

            if (isStreaming) {
                updateRequest.process(streamingSolrServer);
            } else {
                updateRequest.process(solrServer);
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

                UpdateRequest updateRequest = new UpdateRequest(getRequestHandler());

                SolrInputDocument doc = new SolrInputDocument();
                for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                    if (entry.getKey().startsWith(SolrConstants.FIELD)) {
                        String fieldName = entry.getKey().substring(SolrConstants.FIELD.length());
                        doc.setField(fieldName, entry.getValue());
                    }
                }
                updateRequest.add(doc);

                if (isStreaming) {
                    updateRequest.process(streamingSolrServer);
                } else {
                    updateRequest.process(solrServer);
                }

            } else if (body instanceof String) {

                String bodyAsString = (String) body;

                if (!bodyAsString.startsWith("<add")) {
                    bodyAsString = "<add>" + bodyAsString + "</add>";
                }

                DirectXmlRequest xmlRequest = new DirectXmlRequest(getRequestHandler(), bodyAsString);

                if (isStreaming) {
                    streamingSolrServer.request(xmlRequest);
                } else {
                    solrServer.request(xmlRequest);
                }
            } else {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "unable to find data in Exchange to update Solr");
            }
        }
    }

    private String getRequestHandler() {
        String requestHandler = getEndpoint().getRequestHandler();
        return (requestHandler == null) ? "/update" : requestHandler;
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
