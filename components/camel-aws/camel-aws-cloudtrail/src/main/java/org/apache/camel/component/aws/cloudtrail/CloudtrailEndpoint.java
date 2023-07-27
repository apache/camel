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
package org.apache.camel.component.aws.cloudtrail;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws.cloudtrail.client.CloudtrailClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;

/**
 * Consume events from Amazon Cloudtrail using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.19.0", scheme = "aws-cloudtrail", title = "AWS Cloudtrail", syntax = "aws-cloudtrail:label",
             consumerOnly = true,
             category = { Category.CLOUD, Category.MANAGEMENT, Category.MONITORING }, headersClass = CloudtrailConstants.class)
public class CloudtrailEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private CloudtrailConfiguration configuration;

    private CloudTrailClient cloudTrailClient;

    public CloudtrailEndpoint(String uri, CloudtrailConfiguration configuration, CloudtrailComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        cloudTrailClient = configuration.getCloudTrailClient() != null
                ? configuration.getCloudTrailClient()
                : CloudtrailClientFactory.getCloudtrailClient(configuration).getCloudtrailClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getCloudTrailClient())) {
            if (cloudTrailClient != null) {
                cloudTrailClient.close();
            }
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("You cannot produce messages to this endpoint");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final CloudtrailConsumer consumer = new CloudtrailConsumer(this, processor);
        consumer.setSchedulerProperties(getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public CloudtrailComponent getComponent() {
        return (CloudtrailComponent) super.getComponent();
    }

    public CloudTrailClient getClient() {
        return cloudTrailClient;
    }

    public CloudtrailConfiguration getConfiguration() {
        return configuration;
    }
}
