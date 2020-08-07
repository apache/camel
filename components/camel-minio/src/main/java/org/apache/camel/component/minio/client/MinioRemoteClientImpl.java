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

import io.minio.MinioClient;
import org.apache.camel.component.minio.MinioConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Creates MinIO client object according to the
 * given endpoint, port, access key, secret key, region and secure option.
 */
public class MinioRemoteClientImpl implements MinioCamelInternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(MinioRemoteClientImpl.class);
    private final MinioConfiguration configuration;

    /**
     * Constructor that uses the config file.
     */
    public MinioRemoteClientImpl(MinioConfiguration configuration) {
        LOG.trace("Creating an Minio client.");
        this.configuration = configuration;
    }

    /**
     * Getting the minio client.
     *
     * @return Minio Client.
     */
    @Override
    public MinioClient getMinioClient() {
        if (isNotEmpty(configuration.getEndpoint())) {
            MinioClient.Builder minioClientRequest = MinioClient.builder();

            if (isNotEmpty(configuration.getProxyPort())) {
                minioClientRequest.endpoint(configuration.getEndpoint(), configuration.getProxyPort(), configuration.isSecure());
            } else {
                minioClientRequest.endpoint(configuration.getEndpoint());
            }
            if (isNotEmpty(configuration.getAccessKey()) && isNotEmpty(configuration.getSecretKey())) {
                minioClientRequest.credentials(configuration.getAccessKey(), configuration.getSecretKey());
            }
            if (isNotEmpty(configuration.getRegion())) {
                minioClientRequest.region(configuration.getRegion());
            }
            if (isNotEmpty(configuration.getCustomHttpClient())) {
                minioClientRequest.httpClient(configuration.getCustomHttpClient());
            }
            return minioClientRequest.build();

        } else {
            throw new IllegalArgumentException("Endpoint must be specified");
        }
    }
}
