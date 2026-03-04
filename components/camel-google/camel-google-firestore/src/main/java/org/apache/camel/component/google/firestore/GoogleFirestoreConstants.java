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
    @Metadata(label = "producer", description = "The operation to perform.",
              javaType = "org.apache.camel.component.google.firestore.GoogleFirestoreOperations")
    public static final String OPERATION = "CamelGoogleFirestoreOperation";

    // Collection and document identifiers
    @Metadata(label = "producer", description = "The collection name to use for the operation.", javaType = "String")
    public static final String COLLECTION_NAME = "CamelGoogleFirestoreCollectionName";

    @Metadata(label = "producer", description = "The document ID to use for the operation.", javaType = "String")
    public static final String DOCUMENT_ID = "CamelGoogleFirestoreDocumentId";

    // Query parameters
    @Metadata(label = "producer", description = "The field name for query filtering.", javaType = "String")
    public static final String QUERY_FIELD = "CamelGoogleFirestoreQueryField";

    @Metadata(label = "producer",
              description = "The operator for query filtering (e.g., ==, <, >, <=, >=, !=, array-contains, in, array-contains-any, not-in).",
              javaType = "String")
    public static final String QUERY_OPERATOR = "CamelGoogleFirestoreQueryOperator";

    @Metadata(label = "producer", description = "The value for query filtering.", javaType = "Object")
    public static final String QUERY_VALUE = "CamelGoogleFirestoreQueryValue";

    @Metadata(label = "producer", description = "The maximum number of documents to return in a query.", javaType = "Integer")
    public static final String QUERY_LIMIT = "CamelGoogleFirestoreQueryLimit";

    @Metadata(label = "producer", description = "The field to order the query results by.", javaType = "String")
    public static final String QUERY_ORDER_BY = "CamelGoogleFirestoreQueryOrderBy";

    @Metadata(label = "producer", description = "The direction to order the query results (ASCENDING or DESCENDING).",
              javaType = "com.google.cloud.firestore.Query$Direction")
    public static final String QUERY_ORDER_DIRECTION = "CamelGoogleFirestoreQueryOrderDirection";

    // Response metadata
    @Metadata(label = "consumer", description = "The document ID from the response.", javaType = "String")
    public static final String RESPONSE_DOCUMENT_ID = "CamelGoogleFirestoreResponseDocumentId";

    @Metadata(label = "consumer", description = "The document path from the response.", javaType = "String")
    public static final String RESPONSE_DOCUMENT_PATH = "CamelGoogleFirestoreResponseDocumentPath";

    @Metadata(label = "consumer", description = "The document create time.", javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_CREATE_TIME = "CamelGoogleFirestoreResponseCreateTime";

    @Metadata(label = "consumer", description = "The document update time.", javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_UPDATE_TIME = "CamelGoogleFirestoreResponseUpdateTime";

    @Metadata(label = "consumer", description = "The document read time.", javaType = "com.google.cloud.Timestamp")
    public static final String RESPONSE_READ_TIME = "CamelGoogleFirestoreResponseReadTime";

    // Set/update options
    @Metadata(label = "producer", description = "When true, merge the data with existing document data instead of overwriting.",
              javaType = "Boolean", defaultValue = "false")
    public static final String MERGE = "CamelGoogleFirestoreMerge";

    @Metadata(label = "producer", description = "List of field paths to merge when using merge option.",
              javaType = "java.util.List<String>")
    public static final String MERGE_FIELDS = "CamelGoogleFirestoreMergeFields";

    /**
     * Prevent instantiation.
     */
    private GoogleFirestoreConstants() {
    }
}
