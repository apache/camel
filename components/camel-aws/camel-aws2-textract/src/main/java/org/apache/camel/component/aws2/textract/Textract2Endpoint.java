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
package org.apache.camel.component.aws2.textract;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.textract.client.Textract2ClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.textract.TextractClient;

/**
 * Extract text and data from documents using AWS Textract and AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "4.15.0", scheme = "aws2-textract", title = "AWS Textract", syntax = "aws2-textract:label",
             producerOnly = true, category = { Category.CLOUD, Category.DOCUMENT }, headersClass = Textract2Constants.class)
public class Textract2Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private TextractClient textractClient;

    @UriParam
    private Textract2Configuration configuration;

    public Textract2Endpoint(String uri, Component component, Textract2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Textract2Producer(this);
    }

    @Override
    public Textract2Component getComponent() {
        return (Textract2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        textractClient
                = configuration.getTextractClient() != null
                        ? configuration.getTextractClient()
                        : Textract2ClientFactory.getTextractClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getTextractClient())) {
            if (textractClient != null) {
                textractClient.close();
            }
        }
        super.doStop();
    }

    public Textract2Configuration getConfiguration() {
        return configuration;
    }

    public TextractClient getTextractClient() {
        return textractClient;
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
        return "textract";
    }
}
