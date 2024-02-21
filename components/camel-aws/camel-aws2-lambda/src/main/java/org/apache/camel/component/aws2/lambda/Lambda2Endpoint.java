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
package org.apache.camel.component.aws2.lambda;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.lambda.client.Lambda2ClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Manage and invoke AWS Lambda functions.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-lambda", title = "AWS Lambda", syntax = "aws2-lambda:function",
             producerOnly = true, category = { Category.CLOUD, Category.SERVERLESS },
             headersClass = Lambda2Constants.class)
public class Lambda2Endpoint extends DefaultEndpoint {

    private LambdaClient awsLambdaClient;

    @UriPath
    @Metadata(required = true)
    private String function;
    @UriParam
    private Lambda2Configuration configuration;

    public Lambda2Endpoint(String uri, Component component, Lambda2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Lambda2Producer(this);
    }

    @Override
    public Lambda2Component getComponent() {
        return (Lambda2Component) super.getComponent();
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
        awsLambdaClient = configuration.getAwsLambdaClient() != null
                ? configuration.getAwsLambdaClient()
                : Lambda2ClientFactory.getLambdaClient(configuration).getLambdaClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAwsLambdaClient())) {
            if (awsLambdaClient != null) {
                awsLambdaClient.close();
            }
        }
        super.doStop();
    }

    public Lambda2Configuration getConfiguration() {
        return configuration;
    }

    public LambdaClient getAwsLambdaClient() {
        return awsLambdaClient;
    }
}
