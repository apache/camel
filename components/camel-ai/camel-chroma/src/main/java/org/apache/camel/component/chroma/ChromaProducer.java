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
package org.apache.camel.component.chroma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.Embedding;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.model.QueryEmbedding;

public class ChromaProducer extends DefaultProducer {

    private Client client;
    private EmbeddingFunction embeddingFunction;

    public ChromaProducer(ChromaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public ChromaEndpoint getEndpoint() {
        return (ChromaEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        this.client = getEndpoint().getClient();
        this.embeddingFunction = getEndpoint().getConfiguration().getEmbeddingFunction();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();
        final ChromaAction action = in.getHeader(ChromaHeaders.ACTION, ChromaAction.class);

        try {
            if (action == null) {
                throw new NoSuchHeaderException("The action is a required header", exchange, ChromaHeaders.ACTION);
            }

            switch (action) {
                case CREATE_COLLECTION:
                    createCollection(exchange);
                    break;
                case DELETE_COLLECTION:
                    deleteCollection(exchange);
                    break;
                case GET_COLLECTION:
                    getCollection(exchange);
                    break;
                case ADD:
                    add(exchange);
                    break;
                case QUERY:
                    query(exchange);
                    break;
                case GET:
                    get(exchange);
                    break;
                case UPDATE:
                    update(exchange);
                    break;
                case UPSERT:
                    upsert(exchange);
                    break;
                case DELETE:
                    delete(exchange);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported action: " + action.name());
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    // ***************************************
    //
    // Actions
    //
    // ***************************************

    private void createCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);

        Collection collection = this.client.createCollection(collectionName, null, true, embeddingFunction);
        in.setBody(collection);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    private void deleteCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);

        this.client.deleteCollection(collectionName);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    private void getCollection(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);

        Collection collection = this.client.getCollection(collectionName, embeddingFunction);
        in.setBody(collection);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void add(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        List<String> ids = in.getHeader(ChromaHeaders.IDS, List.class);
        List<Embedding> embeddings = getEmbeddings(in);
        List<Map<String, String>> metadatas = in.getHeader(ChromaHeaders.METADATAS, List.class);
        List<String> documents = in.getBody(List.class);

        collection.add(embeddings, metadatas, documents, ids);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void query(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        Integer nResults
                = in.getHeader(ChromaHeaders.N_RESULTS, getEndpoint().getConfiguration().getMaxResults(), Integer.class);
        Map<String, Object> where = in.getHeader(ChromaHeaders.WHERE, Map.class);
        Map<String, Object> whereDocument = in.getHeader(ChromaHeaders.WHERE_DOCUMENT, Map.class);
        List<QueryEmbedding.IncludeEnum> include = getIncludeEnums(in);

        // Use text query from body
        List<String> queryTexts = in.getBody(List.class);
        Collection.QueryResponse response = collection.query(queryTexts, nResults, where, whereDocument, include);

        in.setBody(response);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void get(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        List<String> ids = in.getHeader(ChromaHeaders.IDS, List.class);
        Map<String, String> where = in.getHeader(ChromaHeaders.WHERE, Map.class);
        Map<String, Object> whereDocument = in.getHeader(ChromaHeaders.WHERE_DOCUMENT, Map.class);

        Collection.GetResult result = collection.get(ids, where, whereDocument);

        in.setBody(result);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void update(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        List<String> ids = in.getHeader(ChromaHeaders.IDS, List.class);
        List<Embedding> embeddings = getEmbeddings(in);
        List<Map<String, String>> metadatas = in.getHeader(ChromaHeaders.METADATAS, List.class);
        List<String> documents = in.getBody(List.class);

        collection.updateEmbeddings(embeddings, metadatas, documents, ids);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void upsert(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        List<String> ids = in.getHeader(ChromaHeaders.IDS, List.class);
        List<Embedding> embeddings = getEmbeddings(in);
        List<Map<String, String>> metadatas = in.getHeader(ChromaHeaders.METADATAS, List.class);
        List<String> documents = in.getBody(List.class);

        collection.upsert(embeddings, metadatas, documents, ids);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private void delete(Exchange exchange) throws Exception {
        final Message in = exchange.getMessage();

        String collectionName = getCollectionName(in);
        Collection collection = this.client.getCollection(collectionName, embeddingFunction);

        List<String> ids = in.getHeader(ChromaHeaders.IDS, List.class);
        Map<String, Object> where = in.getHeader(ChromaHeaders.WHERE, Map.class);
        Map<String, Object> whereDocument = in.getHeader(ChromaHeaders.WHERE_DOCUMENT, Map.class);

        collection.delete(ids, where, whereDocument);
        in.setHeader(ChromaHeaders.OPERATION_STATUS, "SUCCESS");
    }

    // ***************************************
    //
    // Helpers
    //
    // ***************************************

    private String getCollectionName(Message in) {
        String collectionName = in.getHeader(ChromaHeaders.COLLECTION_NAME, String.class);
        if (collectionName == null) {
            collectionName = getEndpoint().getCollection();
        }
        return collectionName;
    }

    @SuppressWarnings("unchecked")
    private List<Embedding> getEmbeddings(Message in) {
        List<List<Float>> embeddingsList = in.getHeader(ChromaHeaders.EMBEDDINGS, List.class);
        if (embeddingsList == null) {
            return null;
        }
        return embeddingsList.stream()
                .map(Embedding::fromList)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<QueryEmbedding.IncludeEnum> getIncludeEnums(Message in) {
        List<String> includeStrings = in.getHeader(ChromaHeaders.INCLUDE, List.class);
        if (includeStrings == null) {
            return null;
        }
        List<QueryEmbedding.IncludeEnum> result = new ArrayList<>();
        for (String s : includeStrings) {
            result.add(QueryEmbedding.IncludeEnum.fromValue(s));
        }
        return result;
    }
}
