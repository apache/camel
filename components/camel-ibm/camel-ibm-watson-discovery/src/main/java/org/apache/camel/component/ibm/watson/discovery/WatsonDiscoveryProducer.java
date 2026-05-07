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
package org.apache.camel.component.ibm.watson.discovery;

import java.io.InputStream;

import com.ibm.watson.discovery.v2.Discovery;
import com.ibm.watson.discovery.v2.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatsonDiscoveryProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonDiscoveryProducer.class);

    public WatsonDiscoveryProducer(WatsonDiscoveryEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonDiscoveryOperations operation = determineOperation(exchange);

        switch (operation) {
            case query:
                query(exchange);
                break;
            case listCollections:
                listCollections(exchange);
                break;
            case createCollection:
                createCollection(exchange);
                break;
            case deleteCollection:
                deleteCollection(exchange);
                break;
            case addDocument:
                addDocument(exchange);
                break;
            case updateDocument:
                updateDocument(exchange);
                break;
            case deleteDocument:
                deleteDocument(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonDiscoveryEndpoint getEndpoint() {
        return (WatsonDiscoveryEndpoint) super.getEndpoint();
    }

    private WatsonDiscoveryOperations determineOperation(Exchange exchange) {
        WatsonDiscoveryOperations operation
                = exchange.getIn().getHeader(WatsonDiscoveryConstants.OPERATION, WatsonDiscoveryOperations.class);

        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        return operation;
    }

    private void query(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String queryText = exchange.getIn().getHeader(WatsonDiscoveryConstants.QUERY, String.class);
        if (queryText == null) {
            queryText = exchange.getIn().getBody(String.class);
        }

        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("Query text must be specified");
        }

        LOG.trace("Executing query: {}", queryText);

        QueryOptions.Builder builder = new QueryOptions.Builder()
                .projectId(projectId)
                .naturalLanguageQuery(queryText);

        // Optional parameters
        Integer count = exchange.getIn().getHeader(WatsonDiscoveryConstants.COUNT, Integer.class);
        if (count != null) {
            builder.count(count);
        }

        String filter = exchange.getIn().getHeader(WatsonDiscoveryConstants.FILTER, String.class);
        if (filter != null) {
            builder.filter(filter);
        }

        String collectionId = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_ID, String.class);
        if (collectionId == null) {
            collectionId = getEndpoint().getConfiguration().getCollectionId();
        }
        if (collectionId != null) {
            builder.collectionIds(java.util.Collections.singletonList(collectionId));
        }

        QueryResponse result = discovery.query(builder.build()).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listCollections(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        LOG.trace("Listing collections for project: {}", projectId);

        ListCollectionsOptions options = new ListCollectionsOptions.Builder()
                .projectId(projectId)
                .build();

        ListCollectionsResponse result = discovery.listCollections(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createCollection(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String name = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_NAME, String.class);
        if (name == null) {
            throw new IllegalArgumentException("Collection name must be specified");
        }

        LOG.trace("Creating collection: {}", name);

        CreateCollectionOptions.Builder builder = new CreateCollectionOptions.Builder()
                .projectId(projectId)
                .name(name);

        String description = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_DESCRIPTION, String.class);
        if (description != null) {
            builder.description(description);
        }

        CollectionDetails result = discovery.createCollection(builder.build()).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        // The CollectionDetails object contains the collection ID in the body
        // We'll extract it from the result using reflection or standard methods if available
    }

    private void deleteCollection(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String collectionId = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_ID, String.class);
        if (collectionId == null) {
            collectionId = getEndpoint().getConfiguration().getCollectionId();
        }

        if (collectionId == null) {
            throw new IllegalArgumentException("Collection ID must be specified");
        }

        LOG.trace("Deleting collection: {}", collectionId);

        DeleteCollectionOptions options = new DeleteCollectionOptions.Builder()
                .projectId(projectId)
                .collectionId(collectionId)
                .build();

        discovery.deleteCollection(options).execute();

        Message message = getMessageForResponse(exchange);
        message.setBody("Collection deleted successfully");
    }

    private void addDocument(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String collectionId = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_ID, String.class);
        if (collectionId == null) {
            collectionId = getEndpoint().getConfiguration().getCollectionId();
        }

        if (collectionId == null) {
            throw new IllegalArgumentException("Collection ID must be specified");
        }

        InputStream file = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_FILE, InputStream.class);
        if (file == null) {
            file = exchange.getIn().getBody(InputStream.class);
        }

        if (file == null) {
            throw new IllegalArgumentException("Document file must be specified");
        }

        String filename = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_FILENAME, String.class);
        if (filename == null) {
            filename = "document";
        }

        String contentType = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_CONTENT_TYPE, String.class);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        LOG.trace("Adding document to collection: {}", collectionId);

        AddDocumentOptions options = new AddDocumentOptions.Builder()
                .projectId(projectId)
                .collectionId(collectionId)
                .file(file)
                .filename(filename)
                .fileContentType(contentType)
                .build();

        DocumentAccepted result = discovery.addDocument(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        message.setHeader(WatsonDiscoveryConstants.DOCUMENT_ID, result.getDocumentId());
    }

    private void updateDocument(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String collectionId = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_ID, String.class);
        if (collectionId == null) {
            collectionId = getEndpoint().getConfiguration().getCollectionId();
        }

        if (collectionId == null) {
            throw new IllegalArgumentException("Collection ID must be specified");
        }

        String documentId = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_ID, String.class);
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID must be specified");
        }

        InputStream file = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_FILE, InputStream.class);
        if (file == null) {
            file = exchange.getIn().getBody(InputStream.class);
        }

        if (file == null) {
            throw new IllegalArgumentException("Document file must be specified");
        }

        String filename = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_FILENAME, String.class);
        if (filename == null) {
            filename = "document";
        }

        String contentType = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_CONTENT_TYPE, String.class);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        LOG.trace("Updating document: {} in collection: {}", documentId, collectionId);

        UpdateDocumentOptions options = new UpdateDocumentOptions.Builder()
                .projectId(projectId)
                .collectionId(collectionId)
                .documentId(documentId)
                .file(file)
                .filename(filename)
                .fileContentType(contentType)
                .build();

        DocumentAccepted result = discovery.updateDocument(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteDocument(Exchange exchange) {
        Discovery discovery = getEndpoint().getDiscoveryClient();
        String projectId = getEndpoint().getConfiguration().getProjectId();

        String collectionId = exchange.getIn().getHeader(WatsonDiscoveryConstants.COLLECTION_ID, String.class);
        if (collectionId == null) {
            collectionId = getEndpoint().getConfiguration().getCollectionId();
        }

        if (collectionId == null) {
            throw new IllegalArgumentException("Collection ID must be specified");
        }

        String documentId = exchange.getIn().getHeader(WatsonDiscoveryConstants.DOCUMENT_ID, String.class);
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID must be specified");
        }

        LOG.trace("Deleting document: {} from collection: {}", documentId, collectionId);

        DeleteDocumentOptions options = new DeleteDocumentOptions.Builder()
                .projectId(projectId)
                .collectionId(collectionId)
                .documentId(documentId)
                .build();

        DeleteDocumentResponse result = discovery.deleteDocument(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
