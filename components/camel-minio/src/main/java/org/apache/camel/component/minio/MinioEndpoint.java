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
package org.apache.camel.component.minio;

import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Represents a Minio endpoint.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "minio", title = "Minio", syntax = "minio:url", consumerClass = MinioConsumer.class, label = "storage,cloud,file")
public class MinioEndpoint extends ScheduledPollEndpoint {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MinioEndpoint.class);

    @UriPath
    @Metadata(required = true)
    private String name;
    @UriPath(description = "Bucket name or ARN")
    @Metadata(required = true)
    private String bucketNameOrArn;
    @UriParam
    private MinioConfiguration configuration;
    private MinioClient minioClient;

    public MinioEndpoint() {
    }

    public MinioEndpoint(final String uri, final MinioComponent component,
                         final MinioConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new MinioProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new MinioConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getName() {
        return name;
    }

    /**
     * Some description of this option, and what it does
     */
    public void setName(String name) {
        this.name = name;
    }

    public MinioConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinioConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public MinioClient getMinioClient() {
        return minioClient;
    }

    private MinioClient createClient() throws InvalidPortException, InvalidEndpointException {
        final MinioClient minioClient = new MinioClient("https://play.minio.io/minio", this.configuration.getAccessKey(),
                this.configuration.getSecretKey());
        return minioClient;

    }

}
