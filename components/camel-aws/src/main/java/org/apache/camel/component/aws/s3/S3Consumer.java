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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of messages from the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class S3Consumer extends ScheduledBatchPollingConsumer {
    
    private static final Logger LOG = LoggerFactory.getLogger(S3Consumer.class);
    private String marker;
    private transient String s3ConsumerToString;

    public S3Consumer(S3Endpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        
        String fileName = getConfiguration().getFileName();
        String bucketName = getConfiguration().getBucketName();
        Queue<Exchange> exchanges;
        
        if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            S3Object s3Object = getAmazonS3Client().getObject(new GetObjectRequest(bucketName, fileName));
            exchanges = createExchanges(s3Object);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
            listObjectsRequest.setBucketName(bucketName);
            listObjectsRequest.setPrefix(getConfiguration().getPrefix());
            if (maxMessagesPerPoll > 0) {
                listObjectsRequest.setMaxKeys(maxMessagesPerPoll);
            }
            // if there was a marker from previous poll then use that to continue from where we left last time
            if (marker != null) {
                LOG.trace("Resuming from marker: {}", marker);
                listObjectsRequest.setMarker(marker);
            }

            ObjectListing listObjects = getAmazonS3Client().listObjects(listObjectsRequest);
            if (listObjects.isTruncated()) {
                marker = listObjects.getNextMarker();
                LOG.trace("Returned list is truncated, so setting next marker: {}", marker);
            } else {
                // no more data so clear marker
                marker = null;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", listObjects.getObjectSummaries().size(), bucketName);
            }

            exchanges = createExchanges(listObjects.getObjectSummaries());
        }
        return processBatch(CastUtils.cast(exchanges));
    }
    
    protected Queue<Exchange> createExchanges(S3Object s3Object) {
        Queue<Exchange> answer = new LinkedList<Exchange>();
        Exchange exchange = getEndpoint().createExchange(s3Object);
        answer.add(exchange);
        return answer;
    }
    
    protected Queue<Exchange> createExchanges(List<S3ObjectSummary> s3ObjectSummaries) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", s3ObjectSummaries.size());
        }

        Collection<S3Object> s3Objects = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<Exchange>();
        try {
            for (S3ObjectSummary s3ObjectSummary : s3ObjectSummaries) {
                S3Object s3Object = getAmazonS3Client().getObject(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey());
                s3Objects.add(s3Object);

                Exchange exchange = getEndpoint().createExchange(s3Object);
                answer.add(exchange);
            }
        } catch (Throwable e) {
            LOG.warn("Error getting S3Object due: " + e.getMessage(), e);
            // ensure all previous gathered s3 objects are closed
            // if there was an exception creating the exchanges in this batch
            s3Objects.forEach(IOHelper::close);
            throw e;
        }

        return answer;
    }
    
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
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
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange [{}] done.", exchange);
                }
            });
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

                LOG.trace("Deleted object from bucket {} with key {}...", bucketName, key);
            }
        } catch (AmazonClientException e) {
            getExceptionHandler().handleException("Error occurred during deleting object. This exception is ignored.", exchange, e);
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

    protected S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    protected AmazonS3 getAmazonS3Client() {
        return getEndpoint().getS3Client();
    }
    
    @Override
    public S3Endpoint getEndpoint() {
        return (S3Endpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (s3ConsumerToString == null) {
            s3ConsumerToString = "S3Consumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return s3ConsumerToString;
    }
}