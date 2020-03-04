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
package org.apache.camel.component.aws.swf;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClientBuilder;
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-swf component is used for managing workflows from Amazon Simple Workflow.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "aws-swf", title = "AWS Simple Workflow", syntax = "aws-swf:type",
    label = "cloud,workflow")
public class SWFEndpoint extends DefaultEndpoint {

    private AmazonSimpleWorkflowClient amazonSWClient;

    @UriParam
    private SWFConfiguration configuration;

    public SWFEndpoint() {
    }

    public SWFEndpoint(String uri, SWFComponent component, SWFConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return isWorkflow()
                ? new SWFWorkflowProducer(this, new CamelSWFWorkflowClient(this, configuration)) : new SWFActivityProducer(this, new CamelSWFActivityClient(configuration));
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = isWorkflow()
                ? new SWFWorkflowConsumer(this, processor, configuration) : new SWFActivityConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getAmazonSWClient() == null) {
            amazonSWClient = (AmazonSimpleWorkflowClient) createSWClient();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonSWClient())) {
            if (amazonSWClient != null) {
                amazonSWClient.shutdown();
                amazonSWClient = null;
            }
        }
        super.doStop();
    }

    public AmazonSimpleWorkflow getSWClient() {
        return configuration.getAmazonSWClient() != null ? configuration.getAmazonSWClient() : amazonSWClient;
    }

    private AmazonSimpleWorkflow createSWClient() throws Exception {
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        if (!configuration.getClientConfigurationParameters().isEmpty()) {
            setProperties(clientConfiguration, configuration.getClientConfigurationParameters());
        }

        AmazonSimpleWorkflowClientBuilder builder = AmazonSimpleWorkflowClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            builder = builder.withRegion(Regions.valueOf(configuration.getRegion()));
        }
        AmazonSimpleWorkflow client = builder.build();
        if (!configuration.getSWClientParameters().isEmpty()) {
            setProperties(client, configuration.getSWClientParameters());
        }
        return client;
    }

    public StartWorkflowOptions getStartWorkflowOptions() {
        StartWorkflowOptions startWorkflowOptions = new StartWorkflowOptions();
        try {
            PropertyBindingSupport.bindProperties(getCamelContext(), startWorkflowOptions, configuration.getStartWorkflowOptionsParameters());
            PropertyBindingSupport.bindProperties(getCamelContext(), startWorkflowOptions, configuration.getStartWorkflowOptionsParameters());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return startWorkflowOptions;
    }

    private boolean isWorkflow() {
        return configuration.getType().equalsIgnoreCase("workflow");
    }

    public Exchange createExchange(Object request, String action) {
        Exchange exchange = createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody(request);
        exchange.getIn().setHeader(SWFConstants.ACTION, action);
        return exchange;
    }

    public Object getResult(Exchange exchange) {
        return exchange.getMessage();
    }

    public void setResult(Exchange exchange, Object result) {
        exchange.getMessage().setBody(result);
    }

    public void setConfiguration(SWFConfiguration configuration) {
        this.configuration = configuration;
    }

    public SWFConfiguration getConfiguration() {
        return configuration;
    }
}


