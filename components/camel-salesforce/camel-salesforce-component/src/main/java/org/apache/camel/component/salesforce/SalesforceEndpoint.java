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
package org.apache.camel.component.salesforce;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The salesforce component is used for integrating Camel with the massive Salesforce API.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "salesforce", title = "Salesforce", syntax = "salesforce:operationName:topicName", label = "api,cloud,crm", consumerClass = SalesforceConsumer.class)
public class SalesforceEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceEndpoint.class);

    @UriPath(label = "producer", description = "The operation to use", enums = "getVersions,getResources,"
        + "getGlobalObjects,getBasicInfo,getDescription,getSObject,createSObject,updateSObject,deleteSObject,"
        + "getSObjectWithId,upsertSObject,deleteSObjectWithId,getBlobField,query,queryMore,queryAll,search,apexCall,"
        + "recent,createJob,getJob,closeJob,abortJob,createBatch,getBatch,getAllBatches,getRequest,getResults,"
        + "createBatchQuery,getQueryResultIds,getQueryResult,getRecentReports,getReportDescription,executeSyncReport,"
        + "executeAsyncReport,getReportInstances,getReportResults,limits,approval,approvals,composite-tree,"
        + "composite-batch,composite")
    private final OperationName operationName;
    @UriPath(label = "consumer", description = "The name of the topic to use")
    private final String topicName;
    @UriParam
    private final SalesforceEndpointConfig config;

    @UriParam(label = "consumer", description = "The replayId value to use when subscribing")
    private Long replayId;

    public SalesforceEndpoint(String uri, SalesforceComponent salesforceComponent,
                              SalesforceEndpointConfig config, OperationName operationName, String topicName) {
        super(uri, salesforceComponent);

        this.config = config;
        this.operationName = operationName;
        this.topicName = topicName;
    }

    public Producer createProducer() throws Exception {
        // producer requires an operation, topicName must be the invalid operation name
        if (operationName == null) {
            throw new IllegalArgumentException(String.format("Invalid Operation %s", topicName));
        }

        SalesforceProducer producer = new SalesforceProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(producer);
        } else {
            return producer;
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // consumer requires a topicName, operation name must be the invalid topic name
        if (topicName == null) {
            throw new IllegalArgumentException(String.format("Invalid topic name %s, matches a producer operation name",
                    operationName.value()));
        }

        final SubscriptionHelper subscriptionHelper = getComponent().getSubscriptionHelper();
        final SalesforceConsumer consumer = new SalesforceConsumer(this, processor, subscriptionHelper);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public SalesforceComponent getComponent() {
        return (SalesforceComponent) super.getComponent();
    }

    public boolean isSingleton() {
        // re-use endpoint instance across multiple threads
        // the description of this method is a little confusing
        return true;
    }

    public SalesforceEndpointConfig getConfiguration() {
        return config;
    }

    public OperationName getOperationName() {
        return operationName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setReplayId(final Long replayId) {
        this.replayId = replayId;
    }

    public Long getReplayId() {
        return replayId;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            super.doStart();
        } finally {
            // check if this endpoint has its own http client that needs to be started
            final HttpClient httpClient = getConfiguration().getHttpClient();
            if (httpClient != null && getComponent().getConfig().getHttpClient() != httpClient) {
                final String endpointUri = getEndpointUri();
                LOG.debug("Starting http client for {} ...", endpointUri);
                httpClient.start();
                LOG.debug("Started http client for {}", endpointUri);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        try {
            super.doStop();
        } finally {
            // check if this endpoint has its own http client that needs to be stopped
            final HttpClient httpClient = getConfiguration().getHttpClient();
            if (httpClient != null && getComponent().getConfig().getHttpClient() != httpClient) {
                final String endpointUri = getEndpointUri();
                LOG.debug("Stopping http client for {} ...", endpointUri);
                httpClient.stop();
                LOG.debug("Stopped http client for {}", endpointUri);
            }
        }
    }
}
