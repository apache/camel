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
package org.apache.camel.component.google.firestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for Google Firestore operations.
 */
public class GoogleFirestoreProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleFirestoreProducer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GoogleFirestoreEndpoint endpoint;

    public GoogleFirestoreProducer(GoogleFirestoreEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        GoogleFirestoreOperations operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            // Default operation: set document
            setDocument(getFirestore(), exchange);
        } else {
            switch (operation) {
                case setDocument:
                    setDocument(getFirestore(), exchange);
                    break;
                case getDocumentById:
                    getDocumentById(getFirestore(), exchange);
                    break;
                case updateDocument:
                    updateDocument(getFirestore(), exchange);
                    break;
                case deleteDocument:
                    deleteDocument(getFirestore(), exchange);
                    break;
                case queryCollection:
                    queryCollection(getFirestore(), exchange);
                    break;
                case listDocuments:
                    listDocuments(getFirestore(), exchange);
                    break;
                case listCollections:
                    listCollections(getFirestore(), exchange);
                    break;
                case createDocument:
                    createDocument(getFirestore(), exchange);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
        }
    }

    private void setDocument(Firestore firestore, Exchange exchange)
            throws ExecutionException, InterruptedException, InvalidPayloadException {
        String collectionName = determineCollectionName(exchange);
        String documentId = determineDocumentId(exchange);
        Map<String, Object> data = getDocumentData(exchange);

        DocumentReference docRef = firestore.collection(collectionName).document(documentId);

        ApiFuture<WriteResult> future;
        Boolean merge = exchange.getIn().getHeader(GoogleFirestoreConstants.MERGE, Boolean.class);

        if (Boolean.TRUE.equals(merge)) {
            @SuppressWarnings("unchecked")
            List<String> mergeFields = exchange.getIn().getHeader(GoogleFirestoreConstants.MERGE_FIELDS, List.class);
            if (mergeFields != null && !mergeFields.isEmpty()) {
                List<FieldPath> fieldPaths = mergeFields.stream()
                        .map(FieldPath::of)
                        .toList();
                future = docRef.set(data, SetOptions.mergeFieldPaths(fieldPaths));
            } else {
                future = docRef.set(data, SetOptions.merge());
            }
        } else {
            future = docRef.set(data);
        }

        WriteResult result = future.get();
        LOG.debug("Document set at path: {} at time: {}", docRef.getPath(), result.getUpdateTime());

        Message message = getMessageForResponse(exchange);
        message.setBody(data);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, documentId);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, docRef.getPath());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, result.getUpdateTime());
    }

    private void createDocument(Firestore firestore, Exchange exchange)
            throws ExecutionException, InterruptedException, InvalidPayloadException {
        String collectionName = determineCollectionName(exchange);
        Map<String, Object> data = getDocumentData(exchange);

        // Create document with auto-generated ID
        DocumentReference docRef = firestore.collection(collectionName).document();
        WriteResult result = docRef.set(data).get();

        LOG.debug("Document created at path: {} at time: {}", docRef.getPath(), result.getUpdateTime());

        Message message = getMessageForResponse(exchange);
        message.setBody(data);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, docRef.getId());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, docRef.getPath());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, result.getUpdateTime());
    }

    private void getDocumentById(Firestore firestore, Exchange exchange) throws ExecutionException, InterruptedException {
        String collectionName = determineCollectionName(exchange);
        String documentId = determineDocumentId(exchange);

        DocumentReference docRef = firestore.collection(collectionName).document(documentId);
        DocumentSnapshot document = docRef.get().get();

        Message message = getMessageForResponse(exchange);

        if (document.exists()) {
            LOG.debug("Document found: {}", document.getId());
            message.setBody(document.getData());
            message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, document.getId());
            message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, docRef.getPath());
            message.setHeader(GoogleFirestoreConstants.RESPONSE_CREATE_TIME, document.getCreateTime());
            message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, document.getUpdateTime());
            message.setHeader(GoogleFirestoreConstants.RESPONSE_READ_TIME, document.getReadTime());
        } else {
            LOG.debug("Document not found: {}", documentId);
            message.setBody(null);
            message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, documentId);
        }
    }

    private void updateDocument(Firestore firestore, Exchange exchange)
            throws ExecutionException, InterruptedException, InvalidPayloadException {
        String collectionName = determineCollectionName(exchange);
        String documentId = determineDocumentId(exchange);
        Map<String, Object> updates = getDocumentData(exchange);

        DocumentReference docRef = firestore.collection(collectionName).document(documentId);
        WriteResult result = docRef.update(updates).get();

        LOG.debug("Document updated at path: {} at time: {}", docRef.getPath(), result.getUpdateTime());

        Message message = getMessageForResponse(exchange);
        message.setBody(updates);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, documentId);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, docRef.getPath());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, result.getUpdateTime());
    }

    private void deleteDocument(Firestore firestore, Exchange exchange) throws ExecutionException, InterruptedException {
        String collectionName = determineCollectionName(exchange);
        String documentId = determineDocumentId(exchange);

        DocumentReference docRef = firestore.collection(collectionName).document(documentId);
        WriteResult result = docRef.delete().get();

        LOG.debug("Document deleted at path: {} at time: {}", docRef.getPath(), result.getUpdateTime());

        Message message = getMessageForResponse(exchange);
        message.setBody(true);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, documentId);
        message.setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_PATH, docRef.getPath());
        message.setHeader(GoogleFirestoreConstants.RESPONSE_UPDATE_TIME, result.getUpdateTime());
    }

    private void queryCollection(Firestore firestore, Exchange exchange) throws ExecutionException, InterruptedException {
        String collectionName = determineCollectionName(exchange);
        CollectionReference collection = firestore.collection(collectionName);

        Query query = collection;

        // Apply filter if provided
        String queryField = exchange.getIn().getHeader(GoogleFirestoreConstants.QUERY_FIELD, String.class);
        String queryOperator = exchange.getIn().getHeader(GoogleFirestoreConstants.QUERY_OPERATOR, String.class);
        Object queryValue = exchange.getIn().getHeader(GoogleFirestoreConstants.QUERY_VALUE);

        if (ObjectHelper.isNotEmpty(queryField) && ObjectHelper.isNotEmpty(queryOperator) && queryValue != null) {
            query = applyWhereClause(query, queryField, queryOperator, queryValue);
        }

        // Apply ordering if provided
        String orderBy = exchange.getIn().getHeader(GoogleFirestoreConstants.QUERY_ORDER_BY, String.class);
        if (ObjectHelper.isNotEmpty(orderBy)) {
            Query.Direction direction = exchange.getIn().getHeader(
                    GoogleFirestoreConstants.QUERY_ORDER_DIRECTION, Query.Direction.ASCENDING, Query.Direction.class);
            query = query.orderBy(orderBy, direction);
        }

        // Apply limit if provided
        Integer limit = exchange.getIn().getHeader(GoogleFirestoreConstants.QUERY_LIMIT, Integer.class);
        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        QuerySnapshot querySnapshot = query.get().get();
        List<Map<String, Object>> results = new ArrayList<>();

        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> docData = document.getData();
            docData.put("_id", document.getId());
            docData.put("_path", document.getReference().getPath());
            results.add(docData);
        }

        LOG.debug("Query returned {} documents from collection: {}", results.size(), collectionName);

        Message message = getMessageForResponse(exchange);
        message.setBody(results);
    }

    private Query applyWhereClause(Query query, String field, String operator, Object value) {
        return switch (operator.toLowerCase()) {
            case "==", "eq", "equals" -> query.whereEqualTo(field, value);
            case "!=", "ne", "notequals" -> query.whereNotEqualTo(field, value);
            case "<", "lt" -> query.whereLessThan(field, value);
            case "<=", "lte" -> query.whereLessThanOrEqualTo(field, value);
            case ">", "gt" -> query.whereGreaterThan(field, value);
            case ">=", "gte" -> query.whereGreaterThanOrEqualTo(field, value);
            case "array-contains" -> query.whereArrayContains(field, value);
            case "in" -> {
                @SuppressWarnings("unchecked")
                List<Object> inValues = (List<Object>) value;
                yield query.whereIn(field, inValues);
            }
            case "array-contains-any" -> {
                @SuppressWarnings("unchecked")
                List<Object> arrayValues = (List<Object>) value;
                yield query.whereArrayContainsAny(field, arrayValues);
            }
            case "not-in" -> {
                @SuppressWarnings("unchecked")
                List<Object> notInValues = (List<Object>) value;
                yield query.whereNotIn(field, notInValues);
            }
            default -> throw new IllegalArgumentException("Unsupported query operator: " + operator);
        };
    }

    private void listDocuments(Firestore firestore, Exchange exchange) throws ExecutionException, InterruptedException {
        String collectionName = determineCollectionName(exchange);
        CollectionReference collection = firestore.collection(collectionName);

        QuerySnapshot querySnapshot = collection.get().get();
        List<Map<String, Object>> results = new ArrayList<>();

        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> docData = document.getData();
            docData.put("_id", document.getId());
            docData.put("_path", document.getReference().getPath());
            results.add(docData);
        }

        LOG.debug("Listed {} documents from collection: {}", results.size(), collectionName);

        Message message = getMessageForResponse(exchange);
        message.setBody(results);
    }

    private void listCollections(Firestore firestore, Exchange exchange) throws Exception {
        String documentId = exchange.getIn().getHeader(GoogleFirestoreConstants.DOCUMENT_ID, String.class);
        List<String> collectionIds = new ArrayList<>();

        Iterable<CollectionReference> collections;
        if (ObjectHelper.isNotEmpty(documentId)) {
            String collectionName = determineCollectionName(exchange);
            DocumentReference docRef = firestore.collection(collectionName).document(documentId);
            collections = docRef.listCollections();
        } else {
            collections = firestore.listCollections();
        }

        for (CollectionReference collection : collections) {
            collectionIds.add(collection.getId());
        }

        LOG.debug("Listed {} collections", collectionIds.size());

        Message message = getMessageForResponse(exchange);
        message.setBody(collectionIds);
    }

    /**
     * Extracts document data from the exchange body. Supports:
     * <ul>
     * <li>Map&lt;String, Object&gt; - used directly</li>
     * <li>JSON String - parsed to Map</li>
     * <li>Any object convertible to Map via Camel's type converter</li>
     * </ul>
     *
     * @param  exchange                the exchange containing the document data
     * @return                         the document data as a Map
     * @throws InvalidPayloadException if the body cannot be converted to a Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getDocumentData(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getIn().getBody();

        if (body == null) {
            throw new InvalidPayloadException(exchange, Map.class);
        }

        // If already a Map, use it directly
        if (body instanceof Map) {
            return (Map<String, Object>) body;
        }

        // If it's a String, try to parse as JSON
        if (body instanceof String jsonString) {
            String trimmed = jsonString.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    return OBJECT_MAPPER.readValue(trimmed, new TypeReference<HashMap<String, Object>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new InvalidPayloadException(exchange, Map.class, exchange.getIn(), e);
                }
            }
        }

        // Try Camel's type converter as fallback
        Map<String, Object> converted = exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, body);
        if (converted != null) {
            return converted;
        }

        throw new InvalidPayloadException(exchange, Map.class);
    }

    private String determineCollectionName(Exchange exchange) {
        String collectionName = exchange.getIn().getHeader(GoogleFirestoreConstants.COLLECTION_NAME, String.class);
        if (ObjectHelper.isEmpty(collectionName)) {
            collectionName = getConfiguration().getCollectionName();
        }
        if (ObjectHelper.isEmpty(collectionName)) {
            throw new IllegalArgumentException("Collection name must be specified.");
        }
        return collectionName;
    }

    private String determineDocumentId(Exchange exchange) {
        String documentId = exchange.getIn().getHeader(GoogleFirestoreConstants.DOCUMENT_ID, String.class);
        if (ObjectHelper.isEmpty(documentId)) {
            documentId = getConfiguration().getDocumentId();
        }
        if (ObjectHelper.isEmpty(documentId)) {
            throw new IllegalArgumentException("Document ID must be specified for this operation.");
        }
        return documentId;
    }

    private GoogleFirestoreOperations determineOperation(Exchange exchange) {
        GoogleFirestoreOperations operation = exchange.getIn().getHeader(
                GoogleFirestoreConstants.OPERATION, GoogleFirestoreOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private Firestore getFirestore() {
        return endpoint.getFirestoreClient();
    }

    private GoogleFirestoreConfiguration getConfiguration() {
        return endpoint.getConfiguration();
    }

    @Override
    public GoogleFirestoreEndpoint getEndpoint() {
        return (GoogleFirestoreEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
