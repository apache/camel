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
package org.apache.camel.component.weaviate;

import org.apache.camel.spi.Metadata;

public class WeaviateVectorDb {
    public static final String SCHEME = "weaviate";

    private WeaviateVectorDb() {
    }

    public static class Headers {
        @Metadata(description = "The action to be performed.", javaType = "String",
                  enums = "CREATE_COLLECTION,CREATE_INDEX,UPSERT,INSERT,SEARCH,DELETE,UPDATE,QUERY,QUERY_BY_ID")
        public static final String ACTION = "CamelWeaviateAction";

        @Metadata(description = "Text Field Name for Insert/Upsert operation", javaType = "String")
        public static final String TEXT_FIELD_NAME = "CamelWeaviateTextFieldName";

        @Metadata(description = "Vector Field Name for Insert/Upsert operation", javaType = "String")
        public static final String VECTOR_FIELD_NAME = "CamelweaviateVectorFieldName";

        @Metadata(description = "Collection Name for Insert/Upsert operation", javaType = "String")
        public static final String COLLECTION_NAME = "CamelWeaviateCollectionName";

        @Metadata(description = "Collection Similarity Metric", javaType = "String", enums = "cosine,euclidean,dotproduct")
        public static final String COLLECTION_SIMILARITY_METRIC = "CamelWeaviateCollectionSimilarityMetric";

        @Metadata(description = "Collection Dimension", javaType = "int")
        public static final String COLLECTION_DIMENSION = "CamelWeaviateCollectionDimension";

        @Metadata(description = "Collection Cloud Vendor", javaType = "String", enums = "aws,gcp,azure")
        public static final String COLLECTION_CLOUD = "CamelWeaviateCollectionCloud";

        @Metadata(description = "Collection Cloud Vendor Region", javaType = "String", enums = "aws,gcp,azure")
        public static final String COLLECTION_CLOUD_REGION = "CamelWeaviateCollectionCloudRegion";

        @Metadata(description = "Index Name", javaType = "String")
        public static final String INDEX_NAME = "CamelWeaviateIndexName";

        @Metadata(description = "Weaviate Object fields", javaType = "HashMap")
        public static final String FIELDS = "CamelWeaviateFields";

        @Metadata(description = "Weaviate Object properties", javaType = "HashMap")
        public static final String PROPERTIES = "CamelWeaviateProperties";

        @Metadata(description = "Index Id", javaType = "String")
        public static final String INDEX_ID = "CamelWeaviateIndexId";

        @Metadata(description = "Query Top K", javaType = "Integer")
        public static final String QUERY_TOP_K = "CamelWeaviateQueryTopK";
    }
}
