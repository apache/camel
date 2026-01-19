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
package org.apache.camel.component.aws2.polly;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.polly.client.Polly2ClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.polly.PollyClient;

/**
 * Synthesize speech using AWS Polly and AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "aws2-polly", title = "AWS Polly", syntax = "aws2-polly:label",
             producerOnly = true, category = { Category.CLOUD, Category.AI }, headersClass = Polly2Constants.class)
public class Polly2Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private PollyClient pollyClient;

    @UriParam
    private Polly2Configuration configuration;

    public Polly2Endpoint(String uri, Component component, Polly2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Polly2Producer(this);
    }

    @Override
    public Polly2Component getComponent() {
        return (Polly2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        pollyClient
                = configuration.getPollyClient() != null
                        ? configuration.getPollyClient()
                        : Polly2ClientFactory.getPollyClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getPollyClient())) {
            if (pollyClient != null) {
                pollyClient.close();
            }
        }
        super.doStop();
    }

    public Polly2Configuration getConfiguration() {
        return configuration;
    }

    public PollyClient getPollyClient() {
        return pollyClient;
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
        return "polly";
    }
}
