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
package org.apache.camel.component.salesforce;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Communicate with Salesforce using Java DTOs.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "salesforce", title = "Salesforce",
             syntax = "salesforce:operationName:topicName", category = { Category.CLOUD, Category.SAAS },
             headersClass = SalesforceConstants.class)
public class SalesforceEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceEndpoint.class);

    @UriPath( label = "common", description = "The operation to use", enums = "getVersions,"
            + "getResources,getGlobalObjects,getBasicInfo,getDescription,getSObject,createSObject,"
            + "updateSObject,deleteSObject,getSObjectWithId,upsertSObject,deleteSObjectWithId,"
            + "getBlobField,query,queryMore,queryAll,search,apexCall,recent,createJob,getJob,"
            + "closeJob,abortJob,createBatch,getBatch,getAllBatches,getRequest,getResults,"
            + "createBatchQuery,getQueryResultIds,getQueryResult,getRecentReports,"
            + "getReportDescription,executeSyncReport,executeAsyncReport,getReportInstances,"
            + "getReportResults,limits,approval,approvals,composite-tree,composite-batch,composite,"
            + "compositeRetrieveSObjectCollections,compositeCreateSObjectCollections,"
            + "compositeUpdateSObjectCollections,compositeUpsertSObjectCollections,"
            + "compositeDeleteSObjectCollections,"
            + "bulk2GetAllJobs,bulk2CreateJob,bulk2GetJob,bulk2CreateBatch,bulk2CloseJob,"
            + "bulk2AbortJob,bulk2DeleteJob,bulk2GetSuccessfulResults,bulk2GetFailedResults,"
            + "bulk2GetUnprocessedRecords,bulk2CreateQueryJob,bulk2GetQueryJob,"
            + "bulk2GetAllQueryJobs,bulk2GetQueryJobResults,bulk2AbortQueryJob,bulk2DeleteQueryJob,"
            + "raw,subscribe,pubSubSubscribe,pubSubPublish")
    @Metadata(required = true)
    private final OperationName operationName;

    @UriPath(label = "consumer,producer", description = "The name of the topic/channel to use")
    private final String topicName;

    @UriParam
    private final SalesforceEndpointConfig configuration;

    @UriParam(label = "consumer", description = "The replayId value to use when subscribing to the Streaming API.")
    private Long replayId;

    @UriParam(label = "consumer", description = "The replayId value to use when subscribing to the Pub/Sub API.")
    private String pubSubReplayId;

    public SalesforceEndpoint(String uri, SalesforceComponent salesforceComponent, SalesforceEndpointConfig configuration,
                              OperationName operationName, String topicName) {
        super(uri, salesforceComponent);

        this.configuration = configuration;
        this.operationName = operationName;
        this.topicName = topicName;
    }

    @Override
    public Producer createProducer() throws Exception {
        // producer requires an operation, topicName must be the invalid
        // operation name
        if (operationName == null) {
            throw new IllegalArgumentException(String.format("Invalid Operation %s", topicName));
        }

        return new SalesforceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = null;
        switch (operationName) {
            case SUBSCRIBE -> {
                final SubscriptionHelper subscriptionHelper = getComponent().getSubscriptionHelper();
                consumer = new StreamingApiConsumer(this, processor, subscriptionHelper);
            }
            case PUBSUB_SUBSCRIBE -> {
                consumer = new PubSubApiConsumer(this, processor);
            }
        }
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public SalesforceComponent getComponent() {
        return (SalesforceComponent) super.getComponent();
    }

    public SalesforceEndpointConfig getConfiguration() {
        return configuration;
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

    public String getPubSubReplayId() {
        return pubSubReplayId;
    }

    public void setPubSubReplayId(String pubSubReplayId) {
        this.pubSubReplayId = pubSubReplayId;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            super.doStart();
        } finally {
            // check if this endpoint has its own http client that needs to be started
            HttpClient httpClient = getConfiguration().getHttpClient();
            if (httpClient == null) {
                httpClient = getComponent().getHttpClient();
            }
            if (httpClient != null && getComponent().getHttpClient() != httpClient) {
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
            HttpClient httpClient = getConfiguration().getHttpClient();
            if (httpClient == null) {
                httpClient = getComponent().getHttpClient();
            }
            if (httpClient != null && getComponent().getHttpClient() != httpClient) {
                final String endpointUri = getEndpointUri();
                LOG.debug("Stopping http client for {} ...", endpointUri);
                httpClient.stop();
                LOG.debug("Stopped http client for {}", endpointUri);
            }
        }
    }
}
