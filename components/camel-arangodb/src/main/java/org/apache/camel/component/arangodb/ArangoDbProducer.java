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
package org.apache.camel.component.arangodb;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.model.AqlQueryOptions;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY;
import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY_BIND_PARAMETERS;
import static org.apache.camel.component.arangodb.ArangoDbConstants.AQL_QUERY_OPTIONS;
import static org.apache.camel.component.arangodb.ArangoDbConstants.ARANGO_KEY;
import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_DELETE;
import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_INSERT;
import static org.apache.camel.component.arangodb.ArangoDbConstants.MULTI_UPDATE;
import static org.apache.camel.component.arangodb.ArangoDbConstants.RESULT_CLASS_TYPE;

public class ArangoDbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ArangoDbProducer.class);
    private final ArangoDbEndpoint endpoint;
    private final Map<ArangoDbOperation, Processor> operations = new HashMap<>();

    {
        bind(ArangoDbOperation.SAVE_DOCUMENT, saveDocument());
        bind(ArangoDbOperation.FIND_DOCUMENT_BY_KEY, findDocumentByKey());
        bind(ArangoDbOperation.UPDATE_DOCUMENT, updateDocument());
        bind(ArangoDbOperation.DELETE_DOCUMENT, deleteDocument());
        bind(ArangoDbOperation.AQL_QUERY, aqlQuery());
    }


    public ArangoDbProducer(ArangoDbEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());
        ArangoDbOperation operation = endpoint.getOperation();
        invokeOperation(operation, exchange);
    }

    private void bind(ArangoDbOperation operation, Function<Exchange, Object> arangoDbFunction) {
        operations.put(operation, wrap(arangoDbFunction));
    }

    private Processor wrap(Function<Exchange, Object> supplier) {
        return exchange -> {
            Object result = supplier.apply(exchange);
            copyHeaders(exchange);
            processAndTransferResult(result, exchange);
        };
    }

    private void copyHeaders(Exchange exchange) {
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), false);
    }

    private void processAndTransferResult(Object result, Exchange exchange) {
        exchange.getMessage().setBody(result);
    }

    /**
     * Entry method that selects the appropriate ArangoDb operation and executes it
     */
    protected void invokeOperation(ArangoDbOperation operation, Exchange exchange) throws Exception {
        Processor processor = operations.get(operation);
        if (processor != null) {
            processor.process(exchange);
        } else {
            throw new RuntimeException("Operation not supported. Value: " + operation);
        }
    }

    private Function<Exchange, Object> saveDocument() {
        return exchange -> {
            try {
                ArangoCollection collection = calculateCollection();
                Boolean isMultiInsert = (Boolean) exchange.getMessage().getHeader(MULTI_INSERT, false);

                // save multiple document
                if (isMultiInsert) {
                    Collection<Object> objects = exchange.getMessage().getMandatoryBody(Collection.class);
                    return collection.insertDocuments(objects);
                }

                // case we insert only one document
                Object obj = exchange.getMessage().getMandatoryBody();
                return collection.insertDocument(obj);
            } catch (InvalidPayloadException e) {
                throw new RuntimeException("Invalid payload for command", e);
            }
        };
    }

    private Function<Exchange, Object> findDocumentByKey() {
        return exchange -> {
            try {
                ArangoCollection collection = calculateCollection();
                // key
                String key = exchange.getIn().getMandatoryBody(String.class);
                // return type
                Class<?> resultClassType = (Class<?>) exchange.getIn().getHeader(RESULT_CLASS_TYPE);
                resultClassType = resultClassType != null ? resultClassType : BaseDocument.class;

                return collection.getDocument(key, resultClassType);
            } catch (InvalidPayloadException e) {
                throw new RuntimeException("Invalid payload for command", e);
            }
        };
    }

    private Function<Exchange, Object> updateDocument() {
        return exchange -> {
            try {
                ArangoCollection collection = calculateCollection();

                Boolean isMultiUpdate = (Boolean) exchange.getMessage().getHeader(MULTI_UPDATE, false);

                // update multiple documents
                if (isMultiUpdate) {
                    Collection<Object> documents = exchange.getMessage().getMandatoryBody(Collection.class);
                    return collection.updateDocuments(documents);
                }

                // update one document
                String key = (String) exchange.getMessage().getHeader(ARANGO_KEY);
                Object document = exchange.getMessage().getMandatoryBody();
                return collection.updateDocument(key, document);
            } catch (InvalidPayloadException e) {
                throw new RuntimeException("Invalid payload for command", e);
            }
        };
    }

    private Function<Exchange, Object> deleteDocument() {
        return exchange -> {
            try {
                ArangoCollection collection = calculateCollection();

                Boolean isMultiUpdate = (Boolean) exchange.getMessage().getHeader(MULTI_DELETE, false);
                // if multiple documents to delete
                if (isMultiUpdate) {
                    Collection<String> keysToDelete = exchange.getMessage().getMandatoryBody(Collection.class);
                    return collection.deleteDocuments(keysToDelete);
                }

                // if one single document to delete
                String singleKey = exchange.getMessage().getMandatoryBody(String.class);
                return collection.deleteDocument(singleKey);
            } catch (InvalidPayloadException e) {
                throw new RuntimeException("Invalid payload for command", e);
            }
        };
    }

    private Function<Exchange, Object> aqlQuery() {
        return exchange -> {
            try {
                ArangoDatabase database = endpoint.getArango().db(endpoint.getDatabase());

                // AQL query
                String query = (String) exchange.getMessage().getHeader(AQL_QUERY);
                if (query == null) {
                    query = exchange.getMessage().getMandatoryBody(String.class);
                }

                // parameters to bind :: can be null if nothing to bind
                Map<String, Object> bindParameters = (Map<String, Object>) exchange.getMessage().getHeader(AQL_QUERY_BIND_PARAMETERS);

                // options :: can be null
                AqlQueryOptions queryOptions = (AqlQueryOptions) exchange.getMessage().getHeader(AQL_QUERY_OPTIONS);

                // Class Type for cursor in return :: by default BaseDocument
                Class<?> resultClassType = (Class<?>) exchange.getIn().getHeader(RESULT_CLASS_TYPE);
                resultClassType = resultClassType != null ? resultClassType : BaseDocument.class;

                // perform query and return ArangoCursor of class Type
                return database.query(query, bindParameters, queryOptions, resultClassType);
            } catch (InvalidPayloadException e) {
                throw new RuntimeException("Invalid payload for command", e);
            }
        };
    }

    /**
     * retrieve collection from endpoints params
     */
    private ArangoCollection calculateCollection() {
        String database = endpoint.getDatabase();
        String collection = endpoint.getCollection();

        // return collection
        return endpoint.getArango().db(database).collection(collection);
    }


}
