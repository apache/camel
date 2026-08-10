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
package org.apache.camel.component.alibaba.oss;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import com.aliyun.sdk.service.oss2.utils.IOUtils;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSSConsumer extends ScheduledBatchPollingConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(OSSConsumer.class);

    private final OSSEndpoint endpoint;
    private OSSClient ossClient;
    private String continuationToken;

    public OSSConsumer(OSSEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.ossClient = this.endpoint.initClient();

        if (ObjectHelper.isEmpty(endpoint.getBucketName())) {
            throw new IllegalArgumentException("Bucket name is mandatory to consume objects");
        }
    }

    @Override
    protected int poll() throws Exception {
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String bucketName = endpoint.getBucketName();
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.newBuilder()
                .bucket(bucketName);

        if (ObjectHelper.isNotEmpty(endpoint.getPrefix())) {
            requestBuilder.prefix(endpoint.getPrefix());
        }

        int maxKeys = maxMessagesPerPoll > 0 ? maxMessagesPerPoll : 10;
        requestBuilder.maxKeys((long) maxKeys);

        if (continuationToken != null) {
            LOG.trace("Resuming from continuation token: {}", continuationToken);
            requestBuilder.continuationToken(continuationToken);
        }

        ListObjectsV2Result listing = ossClient.listObjectsV2(requestBuilder.build());

        forceConsumerAsReady();

        if (Boolean.TRUE.equals(listing.isTruncated()) && listing.nextContinuationToken() != null) {
            continuationToken = listing.nextContinuationToken();
        } else {
            continuationToken = null;
        }

        Queue<Exchange> exchanges = createExchanges(bucketName, listing.contents());
        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

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

    private Queue<Exchange> createExchanges(String bucketName, List<ObjectSummary> summaries) throws Exception {
        Queue<Exchange> answer = new LinkedList<>();
        if (summaries == null) {
            return answer;
        }

        for (ObjectSummary summary : summaries) {
            if (summary.key() == null || summary.key().endsWith("/")) {
                continue;
            }

            GetObjectResult result = ossClient.getObject(GetObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .key(summary.key())
                    .build());

            byte[] body;
            try (var stream = result.body()) {
                body = IOUtils.toByteArray(stream);
            }

            Exchange exchange = createExchange(true);
            exchange.setPattern(endpoint.getExchangePattern());
            OSSUtils.mapOssObject(exchange, bucketName, summary.key(), result, body);
            answer.add(exchange);
        }
        return answer;
    }

    private void processComplete(Exchange exchange) {
        if (endpoint.isDeleteAfterRead()) {
            String bucketName = exchange.getIn().getHeader(OSSHeaders.BUCKET_NAME, String.class);
            String objectKey = exchange.getIn().getHeader(OSSHeaders.OBJECT_KEY, String.class);
            if (ObjectHelper.isNotEmpty(bucketName) && ObjectHelper.isNotEmpty(objectKey)) {
                ossClient.deleteObject(DeleteObjectRequest.newBuilder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build());
            }
        }
    }

    private void processFailure(Exchange exchange) {
        Exception exception = exchange.getException();
        if (exception != null) {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange, exception);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }
}
