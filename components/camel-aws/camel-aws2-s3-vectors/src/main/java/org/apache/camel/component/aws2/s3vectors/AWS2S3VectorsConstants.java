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
    @Metadata(label = "producer",
              description = "The name of the vector bucket to use for the current operation instead of the vector bucket"
                            + " of the endpoint. An input only: neither the producer nor the consumer sets it.",
              javaType = "String")
    String VECTOR_BUCKET_NAME = "CamelAwsS3VectorsVectorBucketName";
    @Metadata(label = "producer",
              description = "The name of the vector index to use for the current operation instead of the"
                            + " vectorIndexName option of the endpoint. An input only: neither the producer nor the"
                            + " consumer sets it.",
              javaType = "String")
    String VECTOR_INDEX_NAME = "CamelAwsS3VectorsVectorIndexName";

    // Vector Data
    @Metadata(description = "The key of a vector. putVectors puts the vector under this key (the exchange id when it is"
                            + " not set) and sets it to the key it used. getVectors and deleteVectors take their key, or"
                            + " a list of keys, from this header only when the message body holds no keys. The"
                            + " consumer sets it to the key of the consumed vector.",
              javaType = "String or List<String>")
    String VECTOR_ID = "CamelAwsS3VectorsVectorId";
    @Metadata(label = "producer",
              description = "The vector embedding data as a list of floats or float array",
              javaType = "List<Float> or float[]")
    String VECTOR_DATA = "CamelAwsS3VectorsVectorData";
    @Metadata(label = "producer", description = "The dimensions of the vector", javaType = "Integer")
    String VECTOR_DIMENSIONS = "CamelAwsS3VectorsVectorDimensions";
    @Metadata(label = "producer",
              description = "The data type of the vectors of the index to create (float32)",
              javaType = "String")
    String DATA_TYPE = "CamelAwsS3VectorsDataType";
    @Metadata(label = "producer",
              description = "Additional metadata for the vector as a map. The values may be Strings, numbers, booleans"
                            + " or lists of these.",
              javaType = "Map<String, Object>")
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
              description = "The distance metric of the index to create (cosine or euclidean)",
              javaType = "String")
    String DISTANCE_METRIC = "CamelAwsS3VectorsDistanceMetric";
    /**
     * @deprecated S3 Vectors has no similarity threshold, the component never used this header. Use the
     *             {@link #DISTANCE} header of the results instead.
     */
    @Metadata(label = "producer",
              description = "(deprecated) Never used: S3 Vectors has no similarity threshold.",
              javaType = "Float",
              deprecationNote = "S3 Vectors has no similarity threshold, the component never used this header. Filter"
                                + " the results on their CamelAwsS3VectorsDistance header instead.")
    @Deprecated
    String SIMILARITY_THRESHOLD = "CamelAwsS3VectorsSimilarityThreshold";
    @Metadata(label = "producer",
              description = "The metadata filter of queryVectors, as a JSON object with the filter operators of S3"
                            + " Vectors, such as $eq, $in or $and. Only the vectors whose metadata match the filter are"
                            + " returned.",
              javaType = "String")
    String METADATA_FILTER = "CamelAwsS3VectorsMetadataFilter";

    // Query Results
    /**
     * @deprecated S3 Vectors returns a distance, not a similarity score, and the component never set this header. Use
     *             {@link #DISTANCE} instead.
     */
    @Metadata(label = "consumer",
              description = "(deprecated) Never set: S3 Vectors returns a distance, see CamelAwsS3VectorsDistance.",
              javaType = "Float",
              deprecationNote = "S3 Vectors returns a distance, not a similarity score, and the component never set this"
                                + " header. Use the CamelAwsS3VectorsDistance header instead.")
    @Deprecated
    String SIMILARITY_SCORE = "CamelAwsS3VectorsSimilarityScore";
    @Metadata(label = "consumer",
              description = "The distance of the consumed vector from the query vector, computed with the distance"
                            + " metric of the index: the lower, the closer.",
              javaType = "Float")
    String DISTANCE = "CamelAwsS3VectorsDistance";
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
