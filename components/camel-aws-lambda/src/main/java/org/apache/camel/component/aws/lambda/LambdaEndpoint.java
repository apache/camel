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
package org.apache.camel.component.aws.lambda;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-lambda is used for managing and invoking functions from Amazon Lambda.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "aws-lambda", title = "AWS Lambda",
    syntax = "aws-lambda:function", producerOnly = true, label = "cloud,computing,serverless")
public class LambdaEndpoint extends DefaultEndpoint {

    private AWSLambda awsLambdaClient;

    @UriPath
    @Metadata(required = true)
    private String function;
    @UriParam
    private LambdaConfiguration configuration;

    public LambdaEndpoint(String uri, Component component, LambdaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LambdaProducer(this);
    }

    public String getFunction() {
        return function;
    }

    /**
     * Name of the Lambda function.
     */
    public void setFunction(String function) {
        this.function = function;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsLambdaClient = configuration.getAwsLambdaClient() != null ? configuration.getAwsLambdaClient() : createLambdaClient();
    }
    
    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAwsLambdaClient())) {
            if (awsLambdaClient != null) {
                awsLambdaClient.shutdown();
            }
        }
        super.doStop();
    }

    public LambdaConfiguration getConfiguration() {
        return configuration;
    }

    public AWSLambda getAwsLambdaClient() {
        return awsLambdaClient;
    }

    AWSLambda createLambdaClient() {
        AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard();

        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyProtocol(configuration.getProxyProtocol());
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            builder = builder.withClientConfiguration(clientConfiguration);
        }

        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            builder = builder.withRegion(Regions.valueOf(configuration.getRegion()));
        }

        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
            builder = builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
        }

        return builder.build();
    }
}
