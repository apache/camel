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
package org.apache.camel.component.aws2.ec2;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.ec2.client.AWS2EC2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.ec2.Ec2Client;

/**
 * Manage AWS EC2 instances.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ec2", title = "AWS Elastic Compute Cloud (EC2)",
             syntax = "aws2-ec2:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = AWS2EC2Constants.class)
public class AWS2EC2Endpoint extends DefaultEndpoint {

    private Ec2Client ec2Client;

    @UriParam
    private AWS2EC2Configuration configuration;

    public AWS2EC2Endpoint(String uri, Component component, AWS2EC2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AWS2EC2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ec2Client = configuration.getAmazonEc2Client() != null
                ? configuration.getAmazonEc2Client() : AWS2EC2ClientFactory.getEc2Client(configuration).getEc2Client();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonEc2Client())) {
            if (ec2Client != null) {
                ec2Client.close();
            }
        }

        super.doStop();
    }

    public AWS2EC2Configuration getConfiguration() {
        return configuration;
    }

    public Ec2Client getEc2Client() {
        return ec2Client;
    }

    @Override
    public AWS2EC2Component getComponent() {
        return (AWS2EC2Component) super.getComponent();
    }
}
