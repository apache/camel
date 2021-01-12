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
package org.apache.camel.component.aws2.firehose;

import java.net.URI;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.FirehoseClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

import static software.amazon.awssdk.core.SdkSystemSetting.CBOR_ENABLED;

/**
 * Produce data to AWS Kinesis Firehose streams using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-kinesis-firehose", title = "AWS 2 Kinesis Firehose",
             syntax = "aws2-kinesis-firehose:streamName", producerOnly = true, category = {
                     Category.CLOUD,
                     Category.MESSAGING })
public class KinesisFirehose2Endpoint extends DefaultEndpoint {

    @UriParam
    private KinesisFirehose2Configuration configuration;

    private FirehoseClient kinesisFirehoseClient;

    public KinesisFirehose2Endpoint(String uri, KinesisFirehose2Configuration configuration,
                                    KinesisFirehose2Component component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisFirehose2Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume messages from this endpoint");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!configuration.isCborEnabled()) {
            System.setProperty(CBOR_ENABLED.property(), "false");
        }
        kinesisFirehoseClient = configuration.getAmazonKinesisFirehoseClient() != null
                ? configuration.getAmazonKinesisFirehoseClient() : createKinesisFirehoseClient();

    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonKinesisFirehoseClient())) {
            if (kinesisFirehoseClient != null) {
                kinesisFirehoseClient.close();
            }
        }
        if (!configuration.isCborEnabled()) {
            System.clearProperty(CBOR_ENABLED.property());
        }
        super.doStop();
    }

    FirehoseClient createKinesisFirehoseClient() {
        FirehoseClient client = null;
        FirehoseClientBuilder clientBuilder = FirehoseClient.builder();
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

    public FirehoseClient getClient() {
        return kinesisFirehoseClient;
    }

    public KinesisFirehose2Configuration getConfiguration() {
        return configuration;
    }
}
