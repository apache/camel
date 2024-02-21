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
package org.apache.camel.component.aws2.msk;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.msk.client.MSK2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.kafka.KafkaClient;

/**
 * Manage AWS MSK instances.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-msk", title = "AWS Managed Streaming for Apache Kafka (MSK)",
             syntax = "aws2-msk:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = MSK2Constants.class)
public class MSK2Endpoint extends ScheduledPollEndpoint {

    private KafkaClient mskClient;

    @UriParam
    private MSK2Configuration configuration;

    public MSK2Endpoint(String uri, Component component, MSK2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MSK2Producer(this);
    }

    @Override
    public MSK2Component getComponent() {
        return (MSK2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        mskClient = configuration.getMskClient() != null
                ? configuration.getMskClient()
                : MSK2ClientFactory.getKafkaClient(configuration).getKafkaClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getMskClient())) {
            if (mskClient != null) {
                mskClient.close();
            }
        }
        super.doStop();
    }

    public MSK2Configuration getConfiguration() {
        return configuration;
    }

    public KafkaClient getMskClient() {
        return mskClient;
    }
}
