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
package org.apache.camel.component.aws.config.client.impl;

import java.net.URI;

import org.apache.camel.component.aws.config.AWSConfigConfiguration;
import org.apache.camel.component.aws.config.client.AWSConfigInternalClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.ConfigClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Manage an AWS Config client for all users to use (enabling temporary creds). This implementation is for remote
 * instances to manage the credentials on their own (eliminating credential rotations)
 */
public class AWSConfigClientIAMOptimizedImpl implements AWSConfigInternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(AWSConfigClientIAMOptimizedImpl.class);
    private AWSConfigConfiguration configuration;

    /**
     * Constructor that uses the config file.
     */
    public AWSConfigClientIAMOptimizedImpl(AWSConfigConfiguration configuration) {
        LOG.trace("Creating an AWS Config client for an ec2 instance with IAM temporary credentials (normal for ec2s).");
        this.configuration = configuration;
    }

    /**
     * Getting the Config aws client that is used.
     *
     * @return ConfigClient Client.
     */
    @Override
    public ConfigClient getConfigClient() {
        ConfigClient client = null;
        ConfigClientBuilder clientBuilder = ConfigClient.builder();
        ProxyConfiguration.Builder proxyConfig = null;
        ApacheHttpClient.Builder httpClientBuilder = null;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            proxyConfig = ProxyConfiguration.builder();
            URI proxyEndpoint = URI.create(configuration.getProxyProtocol() + "://" + configuration.getProxyHost() + ":"
                                           + configuration.getProxyPort());
            proxyConfig.endpoint(proxyEndpoint);
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
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
}
