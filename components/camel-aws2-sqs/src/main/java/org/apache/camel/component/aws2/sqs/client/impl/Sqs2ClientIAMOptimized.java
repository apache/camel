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
package org.apache.camel.component.aws2.sqs.client.impl;

import java.net.URI;

import org.apache.camel.component.aws2.sqs.Sqs2Configuration;
import org.apache.camel.component.aws2.sqs.client.Sqs2InternalClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Manage an AWS SQS client for all users to use. This implementation is for remote instances to manage the credentials
 * on their own (eliminating credential rotations)
 */
public class Sqs2ClientIAMOptimized implements Sqs2InternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(Sqs2ClientIAMOptimized.class);
    private Sqs2Configuration configuration;

    /**
     * Constructor that uses the config file.
     */
    public Sqs2ClientIAMOptimized(Sqs2Configuration configuration) {
        LOG.trace("Creating an AWS SQS client for working on AWS Services");
        this.configuration = configuration;
    }

    /**
     * Getting the sqs aws client that is used.
     * 
     * @return Amazon SQS Client.
     */
    @Override
    public SqsClient getSQSClient() {
        SqsClient client = null;
        SqsClientBuilder clientBuilder = SqsClient.builder();
        ProxyConfiguration.Builder proxyConfig = null;
        ApacheHttpClient.Builder httpClientBuilder = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            proxyConfig = ProxyConfiguration.builder();
            URI proxyEndpoint = URI.create(configuration.getProxyProtocol() + "://" + configuration.getProxyHost() + ":"
                                           + configuration.getProxyPort());
            proxyConfig.endpoint(proxyEndpoint);
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            DefaultCredentialsProvider cred = DefaultCredentialsProvider.create();
            if (isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder)
                        .credentialsProvider(cred);
            } else {
                clientBuilder = clientBuilder.credentialsProvider(cred);
            }
        } else {
            if (!isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
            }
        }

        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        if (configuration.isTrustAllCertificates()) {
            SdkHttpClient ahc = ApacheHttpClient.builder().buildWithDefaults(AttributeMap
                    .builder()
                    .put(
                            SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
                            Boolean.TRUE)
                    .build());
            clientBuilder.httpClient(ahc);
        }
        client = clientBuilder.build();
        return client;
    }
}
