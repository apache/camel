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

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.s3vectors.client.AWS2S3VectorsClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

/**
 * Store and query vector embeddings using AWS S3 Vectors with similarity search.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "aws2-s3-vectors", title = "AWS S3 Vectors",
             syntax = "aws2-s3-vectors://vectorBucketName", category = { Category.CLOUD, Category.AI },
             headersClass = AWS2S3VectorsConstants.class)
public class AWS2S3VectorsEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3VectorsEndpoint.class);

    private S3VectorsClient s3VectorsClient;

    @UriPath(description = "Vector bucket name or ARN")
    @Metadata(required = true)
    private String vectorBucketName;
    @UriParam
    private AWS2S3VectorsConfiguration configuration;

    public AWS2S3VectorsEndpoint(String uri, Component comp, AWS2S3VectorsConfiguration configuration) {
        super(uri, comp);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            return getServiceProtocol() + "." + configuration.getRegion() + ".amazonaws.com";
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "s3vectors";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getRegion() != null) {
            return Map.of("region", configuration.getRegion());
        }
        return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AWS2S3VectorsConsumer consumer = new AWS2S3VectorsConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AWS2S3VectorsProducer(this);
    }

    @Override
    public AWS2S3VectorsComponent getComponent() {
        return (AWS2S3VectorsComponent) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        s3VectorsClient = configuration.getS3VectorsClient() != null
                ? configuration.getS3VectorsClient()
                : AWS2S3VectorsClientFactory.getS3VectorsClient(configuration);

        LOG.trace("Using vector bucket [{}]", vectorBucketName);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getS3VectorsClient())) {
            if (s3VectorsClient != null) {
                s3VectorsClient.close();
            }
        }
        super.doStop();
    }

    public AWS2S3VectorsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AWS2S3VectorsConfiguration configuration) {
        this.configuration = configuration;
    }

    public S3VectorsClient getS3VectorsClient() {
        return s3VectorsClient;
    }

    public void setS3VectorsClient(S3VectorsClient s3VectorsClient) {
        this.s3VectorsClient = s3VectorsClient;
    }

    public String getVectorBucketName() {
        return vectorBucketName;
    }

    public void setVectorBucketName(String vectorBucketName) {
        this.vectorBucketName = vectorBucketName;
    }
}
