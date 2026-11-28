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

import org.apache.camel.spi.Metadata;

/**
 * Constants for the Google Firestore component.
 */
public final class GoogleFirestoreConstants {

    // Operation header
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.google.firestore.GoogleFirestoreOperations")
    public static final String OPERATION = "CamelGoogleFirestoreOperation";

    // Collection and document identifiers
    @Metadata(label = "producer",
              description = "The name of the collection that the producer uses. It replaces the collection name"
                            + " of the endpoint URI.",
              javaType = "String")
    public static final String COLLECTION_NAME = "CamelGoogleFirestoreCollectionName";

    @Metadata(label = "producer",
              description = "The ID of the document that the producer uses. It replaces the documentId option.",
              javaType = "String")
    public static final String DOCUMENT_ID = "CamelGoogleFirestoreDocumentId";

    // Query parameters
    @Metadata(label = "producer", description = "The field that queryCollection filters on. The filter is applied only when the"
                                                + " CamelGoogleFirestoreQueryOperator and CamelGoogleFirestoreQueryValue"
                                                + " headers are also set.",
              javaType = "String")
    public static final String QUERY_FIELD = "CamelGoogleFirestoreQueryField";

    @Metadata(label = "producer",
              description = "The operator for query filtering: == (or eq), != (or ne), lt (less than), lte (less than or equal),"
                            + " gt (greater than), gte (greater than or equal), array-contains, in, array-contains-any or not-in."
                            + " The lt, lte, gt and gte operators can also be given as their comparison symbols.",
              javaType = "String")
    public static final String QUERY_OPERATOR = "CamelGoogleFirestoreQueryOperator";

    @Metadata(label = "producer", description = "The value that queryCollection compares the field with. For the in,"
                                                + " array-contains-any and not-in operators it must be a java.util.List.",
              javaType = "Object")
    public static final String QUERY_VALUE = "CamelGoogleFirestoreQueryValue";

    @Metadata(label = "producer",
              description = "The maximum number of documents that queryCollection returns. If not set, or 0 or"
                            + " less, all matching documents are returned.",
              javaType = "Integer")
    public static final String QUERY_LIMIT = "CamelGoogleFirestoreQueryLimit";

    @Metadata(label = "producer", description = "The field that queryCollection orders the documents by.", javaType = "String")
    public static final String QUERY_ORDER_BY = "CamelGoogleFirestoreQueryOrderBy";

    @Metadata(label = "producer",
              description = "The direction of the order that CamelGoogleFirestoreQueryOrderBy sets: ASCENDING (the"
                            + " default) or DESCENDING.",
              javaType = "com.google.cloud.firestore.Query$Direction")
    public static final String QUERY_ORDER_DIRECTION = "CamelGoogleFirestoreQueryOrderDirection";

    // Response metadata
    @Metadata(description = "The ID of the document. The consumer sets it for each document, and the producer for"
                            + " setDocument, createDocument (the generated ID), getDocumentById, updateDocument and"
                            + " deleteDocument.",
              javaType = "String")
    public static final String RESPONSE_DOCUMENT_ID = "CamelGoogleFirestoreResponseDocumentId";

    @Metadata(description = "The path of the document, such as users/alice. The consumer sets it for each document, and"
                            + " the producer for setDocument, createDocument, updateDocument, deleteDocument, and"
                            + " getDocumentById when the document exists.",
              javaType = "String")
    public static final String RESPONSE_DOCUMENT_PATH = "CamelGoogleFirestoreResponseDocumentPath";

    @Metadata(description = "The time the document was created. The consumer sets it for each document, and the producer"
                            + " for getDocumentById when the document exists.",
              javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_CREATE_TIME = "CamelGoogleFirestoreResponseCreateTime";

    @Metadata(description = "The time the document was last updated. The consumer sets it for each document, and the"
                            + " producer for getDocumentById when the document exists. For setDocument, createDocument,"
                            + " updateDocument and deleteDocument it is the time of the write.",
              javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_UPDATE_TIME = "CamelGoogleFirestoreResponseUpdateTime";

    @Metadata(description = "The time the document was read. The consumer sets it for each document, and the producer for"
                            + " getDocumentById when the document exists.",
              javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_READ_TIME = "CamelGoogleFirestoreResponseReadTime";

    @Metadata(label = "consumer",
              description = "The type of change reported by the realtime listener: ADDED, MODIFIED or REMOVED."
                            + " Only set when realtimeUpdates is enabled.",
              javaType = "String")
    public static final String CHANGE_TYPE = "CamelGoogleFirestoreChangeType";

    // Set/update options
    @Metadata(label = "producer",
              description = "Whether setDocument merges the fields of the body into the existing document instead"
                            + " of replacing the document.",
              javaType = "Boolean", defaultValue = "false")
    public static final String MERGE = "CamelGoogleFirestoreMerge";

    @Metadata(label = "producer",
              description = "The names of the fields that setDocument merges when CamelGoogleFirestoreMerge is"
                            + " true. A dotted name such as address.city is the nested field city of address; a"
                            + " com.google.cloud.firestore.FieldPath element is used as it is. The other fields of the"
                            + " body are ignored. If not set, all fields of the body are merged.",
              javaType = "java.util.List<String>")
    public static final String MERGE_FIELDS = "CamelGoogleFirestoreMergeFields";

    /**
     * Prevent instantiation.
     */
    private GoogleFirestoreConstants() {
    }
}
