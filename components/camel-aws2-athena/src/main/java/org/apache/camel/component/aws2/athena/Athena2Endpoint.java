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
package org.apache.camel.component.aws2.athena;

import java.net.URI;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.AthenaClientBuilder;

/**
 * Access AWS Athena service using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.4.0", scheme = "aws2-athena", title = "AWS 2 Athena", syntax = "aws2-athena:label",
             producerOnly = true, category = {
                     Category.CLOUD, Category.DATABASE })
public class Athena2Endpoint extends DefaultEndpoint {

    private AthenaClient athenaClient;

    @UriParam
    private Athena2Configuration configuration;

    public Athena2Endpoint(String uri, Component component, Athena2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Athena2Producer(this);
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();

        athenaClient = configuration.getAmazonAthenaClient() != null
                ? configuration.getAmazonAthenaClient()
                : createAthenaClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonAthenaClient())) {
            if (athenaClient != null) {
                athenaClient.close();
            }
        }
        super.doStop();
    }

    public Athena2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Athena2Configuration configuration) {
        this.configuration = configuration;
    }

    public AthenaClient getAthenaClient() {
        return athenaClient;
    }

    public void setAthenaClient(AthenaClient athenaClient) {
        this.athenaClient = athenaClient;
    }

    AthenaClient createAthenaClient() {
        AthenaClient client = null;
        AthenaClientBuilder clientBuilder = AthenaClient.builder();
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
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
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
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        client = clientBuilder.build();
        return client;
    }
}
