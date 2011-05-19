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
package org.apache.camel.component.aws.s3;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of messages from the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 * 
 */
public class S3Consumer extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(S3Consumer.class);
    
    private volatile ShutdownRunningTask shutdownRunningTask;
    private volatile int pendingExchanges;

    public S3Consumer(S3Endpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        
        String bucketName = getConfiguration().getBucketName();
        LOG.trace("Quering objects in bucket [{}]...", bucketName);
        
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        listObjectsRequest.setBucketName(bucketName);
        listObjectsRequest.setMaxKeys(getMaxMessagesPerPoll());
        
        ObjectListing listObjects = getAmazonS3Client().listObjects(listObjectsRequest);
        
        LOG.trace("Found {} objects in bucket [{}]...", listObjects.getObjectSummaries().size(), bucketName);
        
        Queue<Exchange> exchanges = createExchanges(listObjects.getObjectSummaries());
        return processBatch(CastUtils.cast(exchanges));
    }
    
    protected Queue<Exchange> createExchanges(List<S3ObjectSummary> s3ObjectSummaries) {
        LOG.trace("Received {} messages in this poll", s3ObjectSummaries.size());
        
        Queue<Exchange> answer = new LinkedList<Exchange>();
        for (S3ObjectSummary s3ObjectSummary : s3ObjectSummaries) {
            S3Object s3Object = getAmazonS3Client().getObject(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey());
            Exchange exchange = getEndpoint().createExchange(s3Object);
            answer.add(exchange);
        }

        return answer;
    }
    
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "S3ConsumerOnCompletion";
                }
            });

            LOG.trace("Processing exchange [{}]...", exchange);

            getProcessor().process(exchange);
        }

        return total;
    }
    
    /**
     * Strategy to delete the message after being processed.
     *
     * @param exchange the exchange
     */
    protected void processCommit(Exchange exchange) {
        try {
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(S3Constants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(S3Constants.KEY, String.class);
                
                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);
                
                getAmazonS3Client().deleteObject(bucketName, key);
                
                LOG.trace("Object deleted");
            }
        } catch (AmazonClientException e) {
            LOG.warn("Error occurred during deleting object", e);
            exchange.setException(e);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            LOG.warn("Exchange failed, so rolling back message status: " + exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }
    
    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    public void prepareShutdown() {
     // noop
    }
    
    protected S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    protected AmazonS3Client getAmazonS3Client() {
        return getEndpoint().getS3Client();
    }
    
    @Override
    public S3Endpoint getEndpoint() {
        return (S3Endpoint) super.getEndpoint();
    }
    
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        getEndpoint().setMaxMessagesPerPoll(maxMessagesPerPoll);
    }
    
    public int getMaxMessagesPerPoll() {
        return getEndpoint().getMaxMessagesPerPoll();
    }
    
    @Override
    public String toString() {
        return "S3Consumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }
}