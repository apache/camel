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
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Manage an AWS SQS client for all users to use. This implementation is for local instances to use a static and solid
 * credential set.
 */
public class Sqs2ClientSessionTokenImpl implements Sqs2InternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(Sqs2ClientStandardImpl.class);
    private Sqs2Configuration configuration;

    /**
     * Constructor that uses the config file.
     */
    public Sqs2ClientSessionTokenImpl(Sqs2Configuration configuration) {
        LOG.trace("Creating an AWS SQS manager using static credentials.");
        this.configuration = configuration;
    }

    /**
     * Getting the s3 aws client that is used.
     *
     * @return Amazon S3 Client.
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
            AwsSessionCredentials cred = AwsSessionCredentials.create(configuration.getAccessKey(),
                    configuration.getSecretKey(), configuration.getSessionToken());
            if (isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder)
                        .credentialsProvider(StaticCredentialsProvider.create(cred));
            } else {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            }
        } else {
            if (!isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
            }
        }

        if (!isDefaultAwsHost()) {
            String endpointOverrideUri = getAwsEndpointUri();
            clientBuilder.endpointOverride(URI.create(endpointOverrideUri));
        }

        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        if (configuration.isOverrideEndpoint()) {
            clientBuilder.endpointOverride(URI.create(configuration.getUriEndpointOverride()));
        }
        if (configuration.isTrustAllCertificates()) {
            if (httpClientBuilder == null) {
                httpClientBuilder = ApacheHttpClient.builder();
            }
            SdkHttpClient ahc = httpClientBuilder.buildWithDefaults(AttributeMap
                    .builder()
                    .put(
                            SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
                            Boolean.TRUE)
                    .build());
            // set created http client to use instead of builder
            clientBuilder.httpClient(ahc);
            clientBuilder.httpClientBuilder(null);
        }
        client = clientBuilder.build();
        return client;
    }

    private boolean isDefaultAwsHost() {
        return configuration.getAmazonAWSHost().equals("amazonaws.com");
    }

    /*
     * Gets the base endpoint for AWS (ie.: http(s)://host:port.
     *
     * Do not confuse with other Camel endpoint methods: this one is named after AWS'
     * own endpoint terminology and can also be used for the endpoint override in the
     * client builder.
     */
    private String getAwsEndpointUri() {
        return configuration.getProtocol() + "://" + getFullyQualifiedAWSHost();
    }

    /*
     * If using a different AWS host, do not assume specific parts of the AWS
     * host and, instead, just return whatever is provided as the host.
     */
    private String getFullyQualifiedAWSHost() {
        String host = configuration.getAmazonAWSHost();
        host = FileUtil.stripTrailingSeparator(host);

        if (isDefaultAwsHost()) {
            return "sqs." + Region.of(configuration.getRegion()).id() + "." + host;
        }

        return host;
    }
}
