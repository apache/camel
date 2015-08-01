/**
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
package org.apache.camel.component.aws.cw;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * Defines the <a href="http://aws.amazon.com/cloudwatch/">AWS CloudWatch Endpoint</a>
 */
@UriEndpoint(scheme = "aws-cw", title = "AWS CloudWatch", syntax = "aws-cw:namespace", producerOnly = true, label = "cloud,monitoring")
public class CwEndpoint extends DefaultEndpoint {

    @UriParam
    private CwConfiguration configuration;
    private AmazonCloudWatch cloudWatchClient;

    @Deprecated
    public CwEndpoint(String uri, CamelContext context, CwConfiguration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }

    public CwEndpoint(String uri, Component component, CwConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new CwProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        
        cloudWatchClient = configuration.getAmazonCwClient() != null
            ? configuration.getAmazonCwClient() : createCloudWatchClient();
            
        if (ObjectHelper.isNotEmpty(configuration.getAmazonCwEndpoint())) {
            cloudWatchClient.setEndpoint(configuration.getAmazonCwEndpoint());
        }
    }

    public CwConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(CwConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setCloudWatchClient(AmazonCloudWatch cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    public AmazonCloudWatch getCloudWatchClient() {
        return cloudWatchClient;
    }

    AmazonCloudWatch createCloudWatchClient() {
        AmazonCloudWatch client = null;
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            client = new AmazonCloudWatchClient(credentials, clientConfiguration);
        } else {
            client = new AmazonCloudWatchClient(credentials);
        }
        return client;
    }
}