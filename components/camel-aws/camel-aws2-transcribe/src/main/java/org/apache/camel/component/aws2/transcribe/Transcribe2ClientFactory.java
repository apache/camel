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

package org.apache.camel.component.aws2.transcribe;

import java.net.URI;

import org.apache.camel.component.aws2.transcribe.client.Transcribe2InternalClient;
import org.apache.camel.component.aws2.transcribe.client.impl.Transcribe2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.transcribe.client.impl.Transcribe2ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.transcribe.client.impl.Transcribe2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.transcribe.client.impl.Transcribe2ClientStandardImpl;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

public final class Transcribe2ClientFactory {

    private Transcribe2ClientFactory() {}

    public static TranscribeClient getTranscribeClient(Transcribe2Configuration configuration) {
        TranscribeClient client = null;
        Transcribe2InternalClient transcribeInternalClient = null;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost())
                && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            ProxyConfiguration.Builder proxyConfig = ProxyConfiguration.builder();
            proxyConfig = proxyConfig.endpoint(URI.create(configuration.getProxyProtocol() + "://"
                    + configuration.getProxyHost() + ":" + configuration.getProxyPort()));
            if (ObjectHelper.isNotEmpty(configuration.getProxyUsername())
                    && ObjectHelper.isNotEmpty(configuration.getProxyPassword())) {
                proxyConfig.username(configuration.getProxyUsername());
                proxyConfig.password(configuration.getProxyPassword());
            }
            ApacheHttpClient.Builder httpClientBuilder =
                    ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            if (configuration.isUseDefaultCredentialsProvider()) {
                transcribeInternalClient =
                        new Transcribe2ClientIAMOptimizedImpl(httpClientBuilder.build(), configuration);
            } else if (configuration.isUseProfileCredentialsProvider()) {
                transcribeInternalClient =
                        new Transcribe2ClientIAMProfileOptimizedImpl(httpClientBuilder.build(), configuration);
            } else if (configuration.isUseSessionCredentials()) {
                transcribeInternalClient =
                        new Transcribe2ClientSessionTokenImpl(httpClientBuilder.build(), configuration);
            } else {
                transcribeInternalClient = new Transcribe2ClientStandardImpl(httpClientBuilder.build(), configuration);
            }
        } else {
            if (configuration.isUseDefaultCredentialsProvider()) {
                transcribeInternalClient = new Transcribe2ClientIAMOptimizedImpl(configuration);
            } else if (configuration.isUseProfileCredentialsProvider()) {
                transcribeInternalClient = new Transcribe2ClientIAMProfileOptimizedImpl(configuration);
            } else if (configuration.isUseSessionCredentials()) {
                transcribeInternalClient = new Transcribe2ClientSessionTokenImpl(configuration);
            } else {
                transcribeInternalClient = new Transcribe2ClientStandardImpl(configuration);
            }
        }
        client = transcribeInternalClient.getTranscribeClient();
        return client;
    }
}
