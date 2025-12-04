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

package org.apache.camel.component.aws2.transcribe.client.impl;

import java.net.URI;

import org.apache.camel.component.aws2.transcribe.Transcribe2Configuration;
import org.apache.camel.component.aws2.transcribe.client.Transcribe2InternalClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.TranscribeClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

public class Transcribe2ClientIAMProfileOptimizedImpl implements Transcribe2InternalClient {

    private static final Logger LOG = LoggerFactory.getLogger(Transcribe2ClientIAMProfileOptimizedImpl.class);
    private Transcribe2Configuration configuration;

    public Transcribe2ClientIAMProfileOptimizedImpl(Transcribe2Configuration configuration) {
        this.configuration = configuration;
    }

    public Transcribe2ClientIAMProfileOptimizedImpl(SdkHttpClient httpClient, Transcribe2Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public TranscribeClient getTranscribeClient() {
        TranscribeClient client = null;
        TranscribeClientBuilder clientBuilder = TranscribeClient.builder();
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        if (configuration.isOverrideEndpoint()) {
            clientBuilder.endpointOverride(URI.create(configuration.getUriEndpointOverride()));
        }
        if (ObjectHelper.isNotEmpty(configuration.getProfileCredentialsName())) {
            clientBuilder = clientBuilder.credentialsProvider(
                    ProfileCredentialsProvider.create(configuration.getProfileCredentialsName()));
        } else {
            clientBuilder = clientBuilder.credentialsProvider(ProfileCredentialsProvider.create());
        }
        if (configuration.isTrustAllCertificates()) {
            httpClientBuilder.buildWithDefaults(AttributeMap.builder()
                    .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE)
                    .build());
        }
        clientBuilder.httpClient(httpClientBuilder.build());
        client = clientBuilder.build();
        return client;
    }
}
