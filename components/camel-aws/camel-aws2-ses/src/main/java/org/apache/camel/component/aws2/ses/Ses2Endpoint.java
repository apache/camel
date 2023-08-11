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
package org.apache.camel.component.aws2.ses;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.ses.client.Ses2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Send e-mails through AWS SES service.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ses", title = "AWS Simple Email Service (SES)", syntax = "aws2-ses:from",
             producerOnly = true, category = { Category.CLOUD, Category.MAIL }, headersClass = Ses2Constants.class)
public class Ses2Endpoint extends DefaultEndpoint {

    private SesClient sesClient;

    @UriParam
    private Ses2Configuration configuration;

    public Ses2Endpoint(String uri, Component component, Ses2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Ses2Component getComponent() {
        return (Ses2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        sesClient = configuration.getAmazonSESClient() != null
                ? configuration.getAmazonSESClient()
                : Ses2ClientFactory.getSesClient(configuration).getSesClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonSESClient())) {
            if (sesClient != null) {
                sesClient.close();
            }
        }
        super.doStop();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Ses2Producer(this);
    }

    public Ses2Configuration getConfiguration() {
        return configuration;
    }

    public SesClient getSESClient() {
        return sesClient;
    }
}
