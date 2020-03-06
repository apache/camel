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

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Mange the required actions of an s3 client for either local or remote.
 */
public interface AWS2CamelS3InternalClient {

    /**
     * Returns an s3 client after a factory method determines which one to
     * return.
     * 
     * @return AmazonS3 AmazonS3
     */
    S3Client getS3Client();
}
