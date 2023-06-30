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
package org.apache.camel.component.aws2.athena.client.impl;

import java.net.URI;

import org.apache.camel.component.aws2.athena.Athena2Configuration;
import org.apache.camel.component.aws2.athena.client.Athena2InternalClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.AthenaClientBuilder;

/**
 * Manage an AWS Athena client for all users to use. This implementation is for local instances to use a static and
 * solid credential set.
 */
public class Athena2ClientIAMProfileOptimizedImpl implements Athena2InternalClient {
    private static final Logger LOG = LoggerFactory.getLogger(Athena2ClientIAMProfileOptimizedImpl.class);
    private Athena2Configuration configuration;

    /**
     * Constructor that uses the config file.
     */
    public Athena2ClientIAMProfileOptimizedImpl(Athena2Configuration configuration) {
        LOG.trace("Creating an AWS Athena manager using profile credentials.");
        this.configuration = configuration;
    }

    /**
     * Getting the Lambda aws client that is used.
     *
     * @return Amazon Lambda Client.
     */
    @Override
    public AthenaClient getAthenaClient() {
        AthenaClient client = null;
        AthenaClientBuilder clientBuilder = AthenaClient.builder();
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
        if (configuration.getProfileCredentialsName() != null) {
            clientBuilder = clientBuilder
                    .credentialsProvider(ProfileCredentialsProvider.create(configuration.getProfileCredentialsName()));
        }
        client = clientBuilder.build();
        return client;
    }
}
