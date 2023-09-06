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
package org.apache.camel.component.aws2.stepfunctions;

import org.apache.camel.*;
import org.apache.camel.component.aws2.stepfunctions.client.StepFunctions2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.sfn.SfnClient;

/**
 * Manage and invoke AWS Step functions.
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = "aws2-step-functions", title = "AWS StepFunctions",
             syntax = "aws2-step-functions:label",
             producerOnly = true, category = { Category.CLOUD, Category.SERVERLESS },
             headersClass = StepFunctions2Constants.class)
public class StepFunctions2Endpoint extends DefaultEndpoint {

    private SfnClient awsSfnClient;

    @UriParam
    private StepFunctions2Configuration configuration;

    public StepFunctions2Endpoint(String uri, Component component, StepFunctions2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public StepFunctions2Component getComponent() {
        return (StepFunctions2Component) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StepFunctions2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsSfnClient = configuration.getAwsSfnClient() != null
                ? configuration.getAwsSfnClient()
                : StepFunctions2ClientFactory.getSfnClient(configuration).getSfnClient();
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(configuration.getAwsSfnClient())) {
            if (awsSfnClient != null) {
                awsSfnClient.close();
            }
        }
        super.doStop();
    }

    public StepFunctions2Configuration getConfiguration() {
        return configuration;
    }

    public SfnClient getAwsSfnClient() {
        return awsSfnClient;
    }

}
