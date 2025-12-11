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
package org.apache.camel.component.aws2.s3vectors;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 S3 Vectors module
 */
public interface AWS2S3VectorsConstants {

    @Metadata(description = "The operation to perform", javaType = "String")
    String OPERATION = "CamelAwsS3VectorsOperation";

    // Vector Bucket and Index
    @Metadata(description = "The name of the vector bucket which will be used for the current operation",
              javaType = "String")
    String VECTOR_BUCKET_NAME = "CamelAwsS3VectorsVectorBucketName";
    @Metadata(description = "The name of the vector index which will be used for the current operation",
              javaType = "String")
    String VECTOR_INDEX_NAME = "CamelAwsS3VectorsVectorIndexName";

    // Vector Data
    @Metadata(label = "producer", description = "The unique identifier for a vector", javaType = "String")
    String VECTOR_ID = "CamelAwsS3VectorsVectorId";
    @Metadata(label = "producer",
              description = "The vector embedding data as a list of floats or float array",
              javaType = "List<Float> or float[]")
    String VECTOR_DATA = "CamelAwsS3VectorsVectorData";
    @Metadata(label = "producer", description = "The dimensions of the vector", javaType = "Integer")
    String VECTOR_DIMENSIONS = "CamelAwsS3VectorsVectorDimensions";
    @Metadata(label = "producer",
              description = "The data type of the vector (float32 or float16)",
              javaType = "String")
    String DATA_TYPE = "CamelAwsS3VectorsDataType";
    @Metadata(label = "producer",
              description = "Additional metadata for the vector as a map",
              javaType = "Map<String, String>")
    String VECTOR_METADATA = "CamelAwsS3VectorsVectorMetadata";

    // Query Parameters
    @Metadata(label = "producer",
              description = "The query vector for similarity search as a list of floats or float array",
              javaType = "List<Float> or float[]")
    String QUERY_VECTOR = "CamelAwsS3VectorsQueryVector";
    @Metadata(label = "producer",
              description = "The number of top similar vectors to return",
              javaType = "Integer")
    String TOP_K = "CamelAwsS3VectorsTopK";
    @Metadata(label = "producer",
              description = "The distance metric to use for similarity search (cosine, euclidean, dot-product)",
              javaType = "String")
    String DISTANCE_METRIC = "CamelAwsS3VectorsDistanceMetric";
    @Metadata(label = "producer",
              description = "The minimum similarity threshold for results",
              javaType = "Float")
    String SIMILARITY_THRESHOLD = "CamelAwsS3VectorsSimilarityThreshold";
    @Metadata(label = "producer",
              description = "Optional filter expression for metadata filtering during vector search",
              javaType = "String")
    String METADATA_FILTER = "CamelAwsS3VectorsMetadataFilter";

    // Query Results
    @Metadata(label = "consumer",
              description = "The similarity score of the returned vector",
              javaType = "Float")
    String SIMILARITY_SCORE = "CamelAwsS3VectorsSimilarityScore";
    @Metadata(label = "consumer",
              description = "The number of vectors returned in the result",
              javaType = "Integer")
    String RESULT_COUNT = "CamelAwsS3VectorsResultCount";

    // Bucket/Index Status
    @Metadata(label = "consumer",
              description = "The status of the vector index",
              javaType = "String")
    String INDEX_STATUS = "CamelAwsS3VectorsIndexStatus";
    @Metadata(label = "consumer",
              description = "The ARN of the vector bucket",
              javaType = "String")
    String VECTOR_BUCKET_ARN = "CamelAwsS3VectorsVectorBucketArn";
}
