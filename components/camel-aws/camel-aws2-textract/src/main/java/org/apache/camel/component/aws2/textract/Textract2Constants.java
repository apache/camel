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
package org.apache.camel.component.aws2.textract;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 Textract module
 */
public interface Textract2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsTextractOperation";
    @Metadata(description = "The S3 bucket name", javaType = "String")
    String S3_BUCKET = "CamelAwsTextractS3Bucket";
    @Metadata(description = "The S3 object name", javaType = "String")
    String S3_OBJECT = "CamelAwsTextractS3Object";
    @Metadata(description = "The S3 object version", javaType = "String")
    String S3_OBJECT_VERSION = "CamelAwsTextractS3ObjectVersion";
    @Metadata(description = "The job ID for async operations", javaType = "String")
    String JOB_ID = "CamelAwsTextractJobId";
    @Metadata(description = "The max results to return", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsTextractMaxResults";
    @Metadata(description = "The next token for pagination", javaType = "String")
    String NEXT_TOKEN = "CamelAwsTextractNextToken";
    @Metadata(description = "The feature types for document analysis", javaType = "List<FeatureType>")
    String FEATURE_TYPES = "CamelAwsTextractFeatureTypes";
}
