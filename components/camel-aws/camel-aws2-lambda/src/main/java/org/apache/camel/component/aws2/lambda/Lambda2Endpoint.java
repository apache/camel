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
import org.apache.camel.spi.*;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * Manage and invoke AWS Lambda functions.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "aws2-lambda", title = "AWS Lambda", syntax = "aws2-lambda:function",
             producerOnly = true, category = { Category.CLOUD, Category.SERVERLESS },
             headersClass = Lambda2Constants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  dependsOn = "operation",
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "invokeFunction",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "createFunction",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL }),
                                          @BodyType.When(option = "operation", value = "updateFunction",
                                                         carriers = {
                                                                 Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL }) },
                                  description = "The payload of invokeFunction, the default, as JSON text: bytes, a stream or a"
                                                + " file are read as text, a Map or a POJO is sent as its toString(), so"
                                                + " marshal it first, and no body fails. createFunction and updateFunction take"
                                                + " the zip of the code as bytes, a stream or a file, or no body with the"
                                                + " CamelAwsLambdaZipFile header or the S3 headers. The other operations take"
                                                + " their input from headers, or with pojoRequest=true from the request of the"
                                                + " AWS SDK in the body"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", dependsOn = "operation",
                                   byOption = @BodyType.When(option = "operation", value = "invokeFunction",
                                                             carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                   description = "The payload of the response of invokeFunction, the default, as text: the JSON"
                                                 + " the function returns, or the JSON of its error with the"
                                                 + " CamelAwsLambdaFunctionError header set, as an error of the function does"
                                                 + " not fail the exchange. The response of the AWS SDK for the other"
                                                 + " operations, such as a ListFunctionsResponse"))
public class Lambda2Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

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
        awsLambdaClient = ObjectHelper.isNotEmpty(configuration.getAwsLambdaClient())
                ? configuration.getAwsLambdaClient()
                : Lambda2ClientFactory.getLambdaClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAwsLambdaClient())) {
            if (ObjectHelper.isNotEmpty(awsLambdaClient)) {
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

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "lambda";
    }
}
