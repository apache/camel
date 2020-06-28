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

/**
 * Creates MinIO client object according to the
 * given endpoint, port, access key, secret key, region and secure option.
 */
public class GetMinioClient implements MinioCamelInternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(GetMinioClient.class);
    private final MinioConfiguration configuration;

    /**
     * Constructor that uses the config file.
     */
    public GetMinioClient(MinioConfiguration configuration) {
        LOG.trace("Creating an Minio client.");
        this.configuration = configuration;
    }

    /**
     * Getting the minio client.
     *
     * @return Minio Client.
     */
    @Override
    public MinioClient getMinioClient() throws Exception {
        assert configuration.getEndpoint() != null;
        try {
            if (configuration.getCustomHttpClient() != null) {
                return new MinioClient(configuration.getEndpoint(),
                        configuration.getProxyPort(),
                        configuration.getAccessKey(),
                        configuration.getSecretKey(),
                        configuration.getRegion(),
                        configuration.isSecure(),
                        configuration.getCustomHttpClient());
            } else {
                if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
                    if (configuration.getRegion() != null) {
                        if (configuration.getProxyPort() != null) {
                            return new MinioClient(configuration.getEndpoint(),
                                    configuration.getProxyPort(),
                                    configuration.getAccessKey(),
                                    configuration.getSecretKey(),
                                    configuration.getRegion(),
                                    configuration.isSecure());
                        } else {
                            return new MinioClient(configuration.getEndpoint(),
                                    configuration.getAccessKey(),
                                    configuration.getSecretKey(),
                                    configuration.getRegion());
                        }
                    } else {
                        if (configuration.getProxyPort() != null) {
                            return new MinioClient(configuration.getEndpoint(),
                                    configuration.getProxyPort(),
                                    configuration.getAccessKey(),
                                    configuration.getSecretKey(),
                                    configuration.isSecure());
                        } else {
                            return new MinioClient(configuration.getEndpoint(),
                                    configuration.getAccessKey(),
                                    configuration.getSecretKey(),
                                    configuration.isSecure());
                        }
                    }
                } else {
                    return new MinioClient(configuration.getEndpoint());
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error Creating an Minio client, due {}", e.getMessage());
            throw e;
        }
    }
}
