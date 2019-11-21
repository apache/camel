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
package org.apache.camel.component.aws.s3.client;

import org.apache.camel.component.aws.s3.S3Configuration;
import org.apache.camel.component.aws.s3.client.impl.S3ClientIAMOptimizedImpl;
import org.apache.camel.component.aws.s3.client.impl.S3ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS S3 aws.
 */
public final class S3ClientFactory {

    private S3ClientFactory() {
        // Prevent instantiation of this factory class.
        throw new RuntimeException("Do not instantiate a Factory class! Refer to the class " + "to learn how to properly use this factory implementation.");
    }

    /**
     * Return the correct aws s3 client (based on remote vs local).
     * 
     * @param maxConnections max connections
     * @return AWSS3Client
     */
    public static S3Client getAWSS3Client(S3Configuration configuration, int maxConnections) {
        return configuration.isUseIAMCredentials() ? new S3ClientIAMOptimizedImpl(configuration, maxConnections) : new S3ClientStandardImpl(configuration, maxConnections);
    }
}
