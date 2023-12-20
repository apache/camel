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
package org.apache.camel.component.huaweicloud.obs;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.BucketMetadataInfoRequest;
import com.obs.services.model.BucketMetadataInfoResult;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObsObject;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.huaweicloud.obs.constants.OBSHeaders;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OBSConsumer extends ScheduledBatchPollingConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(OBSConsumer.class.getName());

    private final OBSEndpoint endpoint;
    private ObsClient obsClient;
    private String marker;
    private boolean destinationBucketCreated;

    public OBSConsumer(OBSEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.destinationBucketCreated = false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.obsClient = this.endpoint.initClient();

        if (ObjectHelper.isEmpty(endpoint.getBucketName())) {
            throw new IllegalArgumentException("Bucket name is mandatory to download objects");
        }

        if (endpoint.isMoveAfterRead()) {
            // check if destination bucket is set in the endpoint, which is mandatory when moveAfterRead = true
            if (ObjectHelper.isEmpty(endpoint.getDestinationBucket())) {
                throw new IllegalArgumentException("Destination bucket is mandatory when moveAfterRead is true");
            }

            // get the location of the source bucket
            BucketMetadataInfoRequest request = new BucketMetadataInfoRequest(endpoint.getBucketName());
            BucketMetadataInfoResult metadata = obsClient.getBucketMetadata(request);
            String bucketLocation = metadata.getLocation();

            try {
                BucketMetadataInfoRequest destinationRequest = new BucketMetadataInfoRequest(endpoint.getDestinationBucket());
                BucketMetadataInfoResult destinationMetadata = obsClient.getBucketMetadata(destinationRequest);
                String destinationLocation = destinationMetadata.getLocation();

                // destination bucket is already created, so check if its location is the same as the source bucket location
                if (!bucketLocation.equals(destinationLocation)) {
                    throw new IllegalArgumentException(
                            "Destination bucket location must have the same location as the source bucket");
                }

                // destination bucket is already created with the right location, so return
                this.destinationBucketCreated = true;
                return;
            } catch (ObsException e) {
                // 404 means that bucket doesn't exist
                if (e.getResponseCode() != 404) {
                    throw e;
                }
            }

            // if destination bucket doesn't already exist, create it with the same location as the source bucket
            obsClient.createBucket(endpoint.getDestinationBucket(), bucketLocation);
            this.destinationBucketCreated = true;
        }
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String fileName = endpoint.getFileName();
        String bucketName = endpoint.getBucketName();
        Queue<Exchange> exchanges;

        if (endpoint.isMoveAfterRead() && !destinationBucketCreated) {
            // when destinationBucket is not ready, create empty list of exchanges
            exchanges = new LinkedList<>();
        } else if (ObjectHelper.isNotEmpty(fileName)) {
            // when file name is given, just download that object
            ObsObject object = obsClient.getObject(bucketName, fileName);
            List<ObsObject> list = new ArrayList<>();
            list.add(object);
            exchanges = createExchanges(list);
        } else {
            // if file name is not given, get a list of all objects
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            request.setPrefix(endpoint.getPrefix());
            request.setDelimiter(endpoint.getDelimiter());

            if (maxMessagesPerPoll > 0) {
                request.setMaxKeys(maxMessagesPerPoll);
            }

            // if there was a marker set in previous poll, then use it to continue from where last poll finished
            if (marker != null) {
                LOG.trace("Resuming from marker: {}", marker);
                request.setMarker(marker);
            }

            ObjectListing objectListing = obsClient.listObjects(request);

            // okay we have some response from huawei so lets mark the consumer as ready
            forceConsumerAsReady();

            // if the list is truncated, set marker for next poll. Otherwise, set marker to null
            if (objectListing.isTruncated()) {
                marker = objectListing.getNextMarker();
            } else {
                marker = null;
            }

            exchanges = createExchanges(objectListing.getObjects());
        }
        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // set exchange properties
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update number of pending exchanges
            pendingExchanges = total - index - 1;

            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    processComplete(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    processFailure(exchange);
                }
            });

            AsyncCallback callback = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, callback);
        }

        return total;
    }

    /**
     * Create exchanges for each OBS object in obsObjects
     */
    private Queue<Exchange> createExchanges(List<ObsObject> obsObjects) {
        Queue<Exchange> answer = new LinkedList<>();
        for (ObsObject objectSummary : obsObjects) {
            ObsObject obsObject;

            if (objectSummary.getMetadata().getContentType() == null) {
                // object was from list objects. Since not all object data is included when listing objects, we must retrieve all the data by calling getObject
                obsObject = obsClient.getObject(endpoint.getBucketName(), objectSummary.getObjectKey());
            } else {
                // object was already retrieved using getObjects
                obsObject = objectSummary;
            }

            // check if object should be included
            if (includeObsObject(obsObject)) {
                Exchange exchange = createExchange(obsObject);
                answer.add(exchange);
            }
        }
        return answer;
    }

    /**
     * Determine of obsObject should be included as an exchange based on the includeFolders user option
     */
    private boolean includeObsObject(ObsObject obsObject) {
        return endpoint.isIncludeFolders() || !obsObject.getObjectKey().endsWith("/");
    }

    /**
     * Create a new exchange from obsObject
     */
    public Exchange createExchange(ObsObject obsObject) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(endpoint.getExchangePattern());

        OBSUtils.mapObsObject(exchange, obsObject);

        return exchange;
    }

    /**
     * To handle the exchange after it has been processed
     */
    private void processComplete(Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(OBSHeaders.BUCKET_NAME, String.class);
        String objectKey = exchange.getIn().getHeader(OBSHeaders.OBJECT_KEY, String.class);

        // copy object to destination bucket
        if (endpoint.isMoveAfterRead()) {
            obsClient.copyObject(bucketName, objectKey, endpoint.getDestinationBucket(), objectKey);
        }

        // delete object from source bucket
        if (endpoint.isDeleteAfterRead()) {
            obsClient.deleteObject(bucketName, objectKey);
        }
    }

    /**
     * To handle when the exchange failed
     */
    private void processFailure(Exchange exchange) {
        Exception exception = exchange.getException();
        if (exception != null) {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange, exception);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }
}
