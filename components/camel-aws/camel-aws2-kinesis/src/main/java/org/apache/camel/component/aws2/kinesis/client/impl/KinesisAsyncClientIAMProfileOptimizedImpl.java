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
package org.apache.camel.component.aws2.kinesis.client.impl;

import java.net.URI;

import org.apache.camel.component.aws2.kinesis.Kinesis2Configuration;
import org.apache.camel.component.aws2.kinesis.client.KinesisAsyncInternalClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Manage an AWS Kinesis Async client for all users to use (enabling temporary creds). This implementation is for remote
 * instances to manage the credentials on their own (eliminating credential rotations)
 */
public class KinesisAsyncClientIAMProfileOptimizedImpl implements KinesisAsyncInternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(KinesisAsyncClientIAMProfileOptimizedImpl.class);
    private Kinesis2Configuration configuration;

    /**
     * Constructor that uses the config file.
     */
    public KinesisAsyncClientIAMProfileOptimizedImpl(Kinesis2Configuration configuration) {
        LOG.trace("Creating an AWS Kinesis Async client for an ec2 instance with IAM temporary credentials (normal for ec2s).");
        this.configuration = configuration;
    }

    /**
     * Getting the KinesisAsync client that is used.
     *
     * @return Amazon Kinesis Async Client.
     */
    @Override
    public KinesisAsyncClient getKinesisAsyncClient() {
        var clientBuilder = KinesisAsyncClient.builder();
        SdkAsyncHttpClient.Builder httpClientBuilder = null;

        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            var proxyConfig = ProxyConfiguration
                    .builder()
                    .scheme(configuration.getProxyProtocol().toString())
                    .host(configuration.getProxyHost())
                    .port(configuration.getProxyPort())
                    .build();
            httpClientBuilder = NettyNioAsyncHttpClient
                    .builder()
                    .proxyConfiguration(proxyConfig);
            clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
        }
        if (configuration.getProfileCredentialsName() != null) {
            clientBuilder = clientBuilder
                    .credentialsProvider(ProfileCredentialsProvider.create(configuration.getProfileCredentialsName()));
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        if (configuration.isOverrideEndpoint()) {
            clientBuilder.endpointOverride(URI.create(configuration.getUriEndpointOverride()));
        }
        if (configuration.isTrustAllCertificates()) {
            if (httpClientBuilder == null) {
                httpClientBuilder = NettyNioAsyncHttpClient.builder();
            }
            SdkAsyncHttpClient ahc = httpClientBuilder
                    .buildWithDefaults(AttributeMap
                            .builder()
                            .put(
                                    SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
                                    Boolean.TRUE)
                            .build());
            // set created http client to use instead of builder
            clientBuilder.httpClient(ahc);
            clientBuilder.httpClientBuilder(null);
        }
        return clientBuilder.build();
    }

}
