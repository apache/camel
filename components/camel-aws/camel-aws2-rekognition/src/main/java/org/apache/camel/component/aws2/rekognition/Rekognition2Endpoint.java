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
package org.apache.camel.component.aws2.rekognition;

import org.apache.camel.*;
import org.apache.camel.component.aws2.rekognition.client.Rekognition2ClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

/**
 * Manage and invoke AWS Rekognition.
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = "aws2-rekognition", title = "AWS Rekognition",
             syntax = "aws2-rekognition:label",
             producerOnly = true, category = { Category.CLOUD, Category.SERVERLESS },
             headersClass = Rekognition2Constants.class)
public class Rekognition2Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private RekognitionClient awsRekognitionClient;

    @UriParam
    private Rekognition2Configuration configuration;

    public Rekognition2Endpoint(String uri, Component component, Rekognition2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Rekognition2Component getComponent() {
        return (Rekognition2Component) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Rekognition2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsRekognitionClient = configuration.getAwsRekognitionClient() != null
                ? configuration.getAwsRekognitionClient()
                : Rekognition2ClientFactory.getRekognitionClient(configuration);
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(configuration.getAwsRekognitionClient())) {
            if (awsRekognitionClient != null) {
                awsRekognitionClient.close();
            }
        }
        super.doStop();
    }

    public Rekognition2Configuration getConfiguration() {
        return configuration;
    }

    public RekognitionClient getAwsRekognitionClient() {
        return awsRekognitionClient;
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
        return "rekognition";
    }

}
