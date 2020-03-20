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
package org.apache.camel.component.aws2.ecs;

import java.net.URI;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.EcsClientBuilder;

/**
 * The aws2-ecs is used for managing Amazon ECS
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ecs", title = "AWS 2 ECS", syntax = "aws2-ecs:label", producerOnly = true, label = "cloud,management")
public class ECS2Endpoint extends ScheduledPollEndpoint {

    private EcsClient ecsClient;

    @UriParam
    private ECS2Configuration configuration;

    public ECS2Endpoint(String uri, Component component, ECS2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ECS2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ecsClient = configuration.getEcsClient() != null ? configuration.getEcsClient() : createECSClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getEcsClient())) {
            if (ecsClient != null) {
                ecsClient.close();
            }
        }
        super.doStop();
    }

    public ECS2Configuration getConfiguration() {
        return configuration;
    }

    public EcsClient getEcsClient() {
        return ecsClient;
    }

    EcsClient createECSClient() {
        EcsClient client = null;
        EcsClientBuilder clientBuilder = EcsClient.builder();
        ProxyConfiguration.Builder proxyConfig = null;
        ApacheHttpClient.Builder httpClientBuilder = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            proxyConfig = ProxyConfiguration.builder();
            URI proxyEndpoint = URI.create(configuration.getProxyProtocol() + "://" + configuration.getProxyHost() + ":" + configuration.getProxyPort());
            proxyConfig.endpoint(proxyEndpoint);
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            if (isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder).credentialsProvider(StaticCredentialsProvider.create(cred));
            } else {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            }
        } else {
            if (!isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        client = clientBuilder.build();
        return client;
    }
}
