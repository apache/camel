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
package org.apache.camel.component.aws2.eks;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.eks.client.EKS2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.eks.EksClient;

/**
 * Manage AWS EKS cluster instances.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-eks", title = "AWS Elastic Kubernetes Service (EKS)",
             syntax = "aws2-eks:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = EKS2Constants.class)
public class EKS2Endpoint extends ScheduledPollEndpoint {

    private EksClient eksClient;

    @UriParam
    private EKS2Configuration configuration;

    public EKS2Endpoint(String uri, Component component, EKS2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EKS2Producer(this);
    }

    @Override
    public EKS2Component getComponent() {
        return (EKS2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        eksClient = configuration.getEksClient() != null
                ? configuration.getEksClient() : EKS2ClientFactory.getEksClient(configuration).getEksClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getEksClient())) {
            if (eksClient != null) {
                eksClient.close();
            }
        }
        super.doStop();
    }

    public EKS2Configuration getConfiguration() {
        return configuration;
    }

    public EksClient getEksClient() {
        return eksClient;
    }
}
