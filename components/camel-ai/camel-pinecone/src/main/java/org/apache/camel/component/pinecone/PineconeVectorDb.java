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
package org.apache.camel.component.pinecone;

import org.apache.camel.spi.Metadata;

public class PineconeVectorDb {
    public static final String SCHEME = "pinecone";
    public static final int DEFAULT_COLLECTION_DIMENSION = 1536;

    private PineconeVectorDb() {
    }

    public static class Headers {
        @Metadata(description = "The action to be performed.", javaType = "String",
                  enums = "CREATE_COLLECTION,CREATE_INDEX,UPSERT,INSERT,SEARCH,DELETE,UPDATE,QUERY,QUERY_BY_ID")
        public static final String ACTION = "CamelPineconeAction";

        @Metadata(description = "Text Field Name for Insert/Upsert operation", javaType = "String")
        public static final String TEXT_FIELD_NAME = "CamelPineconeTextFieldName";

        @Metadata(description = "Vector Field Name for Insert/Upsert operation", javaType = "String")
        public static final String VECTOR_FIELD_NAME = "CamelPineconeVectorFieldName";

        @Metadata(description = "Index Name", javaType = "String")
        public static final String INDEX_NAME = "CamelPineconeIndexName";

        @Metadata(description = "Index Pod Type", javaType = "String")
        public static final String INDEX_POD_TYPE = "CamelPineconeIndexPodType";

        @Metadata(description = "Index Pod Environment", javaType = "String")
        public static final String INDEX_POD_ENVIRONMENT = "CamelPineconeIndexPodEnvironment";

        @Metadata(description = "Collection Name for Insert/Upsert operation", javaType = "String")
        public static final String COLLECTION_NAME = "CamelPineconeCollectionName";

        @Metadata(description = "Collection Similarity Metric", javaType = "String", enums = "cosine,euclidean,dotproduct")
        public static final String COLLECTION_SIMILARITY_METRIC = "CamelPineconeCollectionSimilarityMetric";

        @Metadata(description = "Collection Dimension", javaType = "int")
        public static final String COLLECTION_DIMENSION = "CamelPineconeCollectionDimension";

        @Metadata(description = "Collection Cloud Vendor", javaType = "String", enums = "aws,gcp,azure")
        public static final String COLLECTION_CLOUD = "CamelPineconeCollectionCloud";

        @Metadata(description = "Collection Cloud Vendor Region", javaType = "String", enums = "aws,gcp,azure")
        public static final String COLLECTION_CLOUD_REGION = "CamelPineconeCollectionCloudRegion";

        @Metadata(description = "Index Upsert Id", javaType = "String")
        public static final String INDEX_ID = "CamelPineconeIndexId";

        @Metadata(description = "Query Top K", javaType = "Integer")
        public static final String QUERY_TOP_K = "CamelPineconeQueryTopK";

        @Metadata(description = "Query Namespace", javaType = "String")
        public static final String QUERY_NAMESPACE = "CamelPineconeQueryNamespace";

        @Metadata(description = "Query Filter", javaType = "String")
        public static final String QUERY_FILTER = "CamelPineconeQueryFilter";

        @Metadata(description = "Query Include Values", javaType = "boolean")
        public static final String QUERY_INCLUDE_VALUES = "CamelPineconeQueryIncludeValues";

        @Metadata(description = "Query Include Metadata", javaType = "com.google.protobuf.Struct")
        public static final String QUERY_INCLUDE_METADATA = "CamelPineconeQueryIncludeMetadata";

    }
}
