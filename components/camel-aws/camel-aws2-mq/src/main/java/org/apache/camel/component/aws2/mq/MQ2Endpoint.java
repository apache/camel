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
package org.apache.camel.component.aws2.mq;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.mq.client.MQ2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.mq.MqClient;

/**
 * Send messages to AWS MQ.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-mq", title = "AWS MQ", syntax = "aws2-mq:label", producerOnly = true,
             category = { Category.CLOUD, Category.MESSAGING }, headersClass = MQ2Constants.class)
public class MQ2Endpoint extends ScheduledPollEndpoint {

    private MqClient mqClient;

    @UriParam
    private MQ2Configuration configuration;

    public MQ2Endpoint(String uri, Component component, MQ2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public MQ2Component getComponent() {
        return (MQ2Component) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MQ2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        mqClient = configuration.getAmazonMqClient() != null
                ? configuration.getAmazonMqClient()
                : MQ2ClientFactory.getMqClient(configuration).getMqClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonMqClient())) {
            if (mqClient != null) {
                mqClient.close();
            }
        }
        super.doStop();
    }

    public MQ2Configuration getConfiguration() {
        return configuration;
    }

    public MqClient getAmazonMqClient() {
        return mqClient;
    }
}
