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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.DirectXmlRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

/**
 * The Solr producer.
 */
public class SolrProducer extends DefaultProducer {
    private SolrClient httpServer;
    private SolrClient concSolrServer;
    private SolrClient cloudSolrServer;

    public SolrProducer(SolrEndpoint endpoint, SolrClient solrServer, SolrClient concSolrServer,
            SolrClient cloudSolrServer) {
        super(endpoint);
        this.httpServer = solrServer;
        this.concSolrServer = concSolrServer;
        this.cloudSolrServer = cloudSolrServer;
    }

    private SolrClient getBestSolrServer(String operation) {
        if (this.cloudSolrServer != null) {
            return this.cloudSolrServer;
        } else if (SolrConstants.OPERATION_INSERT_STREAMING.equals(operation)) {
            return this.concSolrServer;
        } else {
            return this.httpServer;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String operation = (String) exchange.getIn().getHeader(SolrConstants.OPERATION);

        if (operation == null) {
            throw new IllegalArgumentException(SolrConstants.OPERATION + " header is missing");
        }

        SolrClient serverToUse = getBestSolrServer(operation);

        if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT)) {
            insert(exchange, serverToUse);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_INSERT_STREAMING)) {
            insert(exchange, serverToUse);
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_ID)) {
            serverToUse.deleteById(exchange.getIn().getBody(String.class));
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_DELETE_BY_QUERY)) {
            serverToUse.deleteByQuery(exchange.getIn().getBody(String.class));
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ADD_BEAN)) {
            serverToUse.addBean(exchange.getIn().getBody());
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ADD_BEANS)) {
            serverToUse.addBeans(exchange.getIn().getBody(Collection.class));
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_COMMIT)) {
            serverToUse.commit();
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_ROLLBACK)) {
            serverToUse.rollback();
        } else if (operation.equalsIgnoreCase(SolrConstants.OPERATION_OPTIMIZE)) {
            serverToUse.optimize();
        } else {
            throw new IllegalArgumentException(
                    SolrConstants.OPERATION + " header value '" + operation + "' is not supported");
        }
    }

    private void insert(Exchange exchange, SolrClient solrServer) throws Exception {
        Object body = exchange.getIn().getBody();
        boolean invalid = false;
        if (body instanceof WrappedFile) {
            body = ((WrappedFile<?>) body).getFile();
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class))) {
            String mimeType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            ContentStreamUpdateRequest updateRequest = new ContentStreamUpdateRequest(getRequestHandler());
            updateRequest.addFile((File) body, mimeType);

            for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                    String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                    updateRequest.setParam(paramName, entry.getValue().toString());
                }
            }

            updateRequest.process(solrServer);
        } else {

            if (body instanceof File) {
                MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
                String mimeType = mimeTypesMap.getContentType((File) body);
                ContentStreamUpdateRequest updateRequest = new ContentStreamUpdateRequest(getRequestHandler());
                updateRequest.addFile((File) body, mimeType);

                for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                    if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                        String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                        updateRequest.setParam(paramName, entry.getValue().toString());
                    }
                }

                updateRequest.process(solrServer);

            } else if (body instanceof SolrInputDocument) {

                UpdateRequest updateRequest = new UpdateRequest(getRequestHandler());
                updateRequest.add((SolrInputDocument) body);

                for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                    if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                        String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                        updateRequest.setParam(paramName, entry.getValue().toString());
                    }
                }

                updateRequest.process(solrServer);

            } else if (body instanceof List<?>) {
                List<?> list = (List<?>) body;

                if (list.size() > 0 && list.get(0) instanceof SolrInputDocument) {
                    UpdateRequest updateRequest = new UpdateRequest(getRequestHandler());
                    updateRequest.add((List<SolrInputDocument>) list);

                    for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                        if (entry.getKey().startsWith(SolrConstants.PARAM)) {
                            String paramName = entry.getKey().substring(SolrConstants.PARAM.length());
                            updateRequest.setParam(paramName, entry.getValue().toString());
                        }
                    }

                    updateRequest.process(solrServer);
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

                    UpdateRequest updateRequest = new UpdateRequest(getRequestHandler());

                    SolrInputDocument doc = new SolrInputDocument();
                    for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
                        if (entry.getKey().startsWith(SolrConstants.FIELD)) {
                            String fieldName = entry.getKey().substring(SolrConstants.FIELD.length());
                            doc.setField(fieldName, entry.getValue());
                        }
                    }
                    updateRequest.add(doc);
                    updateRequest.process(solrServer);

                } else if (body instanceof String) {

                    String bodyAsString = (String) body;

                    if (!bodyAsString.startsWith("<add")) {
                        bodyAsString = "<add>" + bodyAsString + "</add>";
                    }

                    DirectXmlRequest xmlRequest = new DirectXmlRequest(getRequestHandler(), bodyAsString);

                    solrServer.request(xmlRequest);
                } else {
                    invalid = true;
                }
            }
        }

        if (invalid) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "unable to find data in Exchange to update Solr");
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
