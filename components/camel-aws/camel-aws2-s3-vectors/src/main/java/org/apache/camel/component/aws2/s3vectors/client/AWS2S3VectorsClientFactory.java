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
package org.apache.camel.component.aws2.s3vectors.client;

import org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsConfiguration;
import org.apache.camel.component.aws2.s3vectors.client.impl.AWS2S3VectorsClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.s3vectors.client.impl.AWS2S3VectorsClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.s3vectors.client.impl.AWS2S3VectorsClientSessionTokenImpl;
import org.apache.camel.component.aws2.s3vectors.client.impl.AWS2S3VectorsClientStandardImpl;

/**
 * Factory class to return the correct type of AWS S3 Vectors client.
 */
public final class AWS2S3VectorsClientFactory {

    private AWS2S3VectorsClientFactory() {
    }

    /**
     * Return the correct AWS S3 Vectors client (based on configuration).
     *
     * @param  configuration configuration
     * @return               AWS2CamelS3VectorsInternalClient
     */
    public static AWS2CamelS3VectorsInternalClient getS3VectorsClient(AWS2S3VectorsConfiguration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new AWS2S3VectorsClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new AWS2S3VectorsClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new AWS2S3VectorsClientSessionTokenImpl(configuration);
        } else {
            return new AWS2S3VectorsClientStandardImpl(configuration);
        }
    }
}
