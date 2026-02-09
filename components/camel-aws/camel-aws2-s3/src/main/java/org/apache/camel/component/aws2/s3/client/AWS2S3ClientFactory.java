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
package org.apache.camel.component.aws2.s3.client;

import org.apache.camel.component.aws.common.AwsClientBuilderUtil;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Factory class to create AWS S3 clients using common configuration.
 */
public final class AWS2S3ClientFactory {

    private AWS2S3ClientFactory() {
    }

    /**
     * Create an S3 client based on configuration.
     *
     * @param  configuration The S3 configuration
     * @return               Configured S3Client
     */
    public static S3Client getS3Client(AWS2S3Configuration configuration) {
        return AwsClientBuilderUtil.buildClient(
                configuration,
                S3Client::builder,
                builder -> {
                    // S3-specific configuration
                    if (configuration.isForcePathStyle()) {
                        builder.forcePathStyle(true);
                    }
                });
    }
}
