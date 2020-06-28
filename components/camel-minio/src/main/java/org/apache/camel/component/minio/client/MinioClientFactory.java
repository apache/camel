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
package org.apache.camel.component.minio.client;

import org.apache.camel.component.minio.MinioConfiguration;

/**
 * Factory class to return the correct type of MinioClient.
 */
public final class MinioClientFactory {

    private MinioClientFactory() {
        // Prevent instantiation of this factory class.
        throw new RuntimeException("Do not instantiate a Factory class! Refer to the class " + "to learn how to properly use this factory implementation.");
    }

    /**
     * Return the correct minio client (based on remote vs local).
     * 
     * @param configuration configuration
     * @return MinioClient
     */
    public static MinioCamelInternalClient getClient(MinioConfiguration configuration) {
        return new GetMinioClient(configuration);
    }
}
