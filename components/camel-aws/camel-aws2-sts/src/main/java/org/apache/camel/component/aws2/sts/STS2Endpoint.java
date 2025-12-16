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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.sts.client.STS2ClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Manage AWS STS cluster instances.
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "aws2-sts", title = "AWS Security Token Service (STS)",
             syntax = "aws2-sts:label", producerOnly = true, category = {
                     Category.CLOUD,
                     Category.MANAGEMENT },
             headersClass = STS2Constants.class)
public class STS2Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private StsClient stsClient;

    @UriParam
    private STS2Configuration configuration;

    public STS2Endpoint(String uri, Component component, STS2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new STS2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        stsClient = configuration.getStsClient() != null
                ? configuration.getStsClient()
                : STS2ClientFactory.getStsClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getStsClient())) {
            if (stsClient != null) {
                stsClient.close();
            }
        }
        super.doStop();
    }

    public STS2Configuration getConfiguration() {
        return configuration;
    }

    public StsClient getStsClient() {
        return stsClient;
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
        return "sts";
    }
}
