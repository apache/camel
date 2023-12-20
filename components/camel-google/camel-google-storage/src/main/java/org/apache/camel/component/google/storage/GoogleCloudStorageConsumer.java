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
package org.apache.camel.component.google.storage;

import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.CopyRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudStorageConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageConsumer.class);

    private final Language language;

    public GoogleCloudStorageConsumer(GoogleCloudStorageEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.language = getEndpoint().getCamelContext().resolveLanguage("file");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().isMoveAfterRead()) {
            Bucket bucket = getStorageClient().get(getConfiguration().getDestinationBucket());
            if (bucket != null) {
                LOG.trace("Bucket [{}] already exists", bucket.getName());
                return;
            } else {
                LOG.trace("Destination Bucket [{}] doesn't exist yet", getConfiguration().getDestinationBucket());
                if (getConfiguration().isAutoCreateBucket()) {
                    // creates the new bucket because it doesn't exist yet
                    GoogleCloudStorageEndpoint.createNewBucket(getConfiguration().getDestinationBucket(), getConfiguration(),
                            getStorageClient());
                }
            }
        }
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        String fileName = getConfiguration().getObjectName();
        String bucketName = getConfiguration().getBucketName();
        Queue<Exchange> exchanges;
        String filter = getConfiguration().getFilter();

        if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            Blob blob = getStorageClient().get(bucketName, fileName);

            exchanges = createExchanges(blob, fileName);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            Page<Blob> page = getStorageClient().list(bucketName);

            // okay we have some response from Google so lets mark the consumer as ready
            forceConsumerAsReady();

            List<Blob> bloblist = new LinkedList<>();
            for (Blob blob : page.iterateAll()) {
                if (filter != null && !filter.isEmpty()) {
                    if (blob.getBlobId().getName().matches(filter)) {
                        bloblist.add(blob);
                    }
                } else {
                    bloblist.add(blob);
                }
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", bloblist.size(), bucketName);
            }

            exchanges = createExchanges(bloblist);
        }

        return processBatch(CastUtils.cast(exchanges));
    }

    protected Queue<Exchange> createExchanges(Blob blob, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = createExchange(blob, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<Blob> blobList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", blobList.size());
        }

        Queue<Exchange> answer = new LinkedList<>();
        try {
            for (Blob blob : blobList) {
                if (includeObject(blob)) {
                    Exchange exchange = createExchange(blob, blob.getBlobId().getName());
                    answer.add(exchange);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error getting object due: {}", e.getMessage(), e);
            throw e;
        }

        return answer;
    }

    /**
     * Decide whether to include the Objects in the results
     *
     * @param  blob the blob
     * @return      true to include, false to exclude
     */
    protected boolean includeObject(Blob blob) {
        // is the blog a folder
        boolean folder = blob.getName().endsWith("/");

        if (folder && !getConfiguration().isIncludeFolders()) {
            // we should not include folders
            return false;
        }

        return true;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "ConsumerOnCompletion";
                }
            });

            // use default consumer callback
            defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
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
            if (getConfiguration().isMoveAfterRead()) {
                String bucketName = exchange.getIn().getHeader(GoogleCloudStorageConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class);

                LOG.trace("Moving object from bucket {} with key {} to bucket {}...", bucketName, key,
                        getConfiguration().getDestinationBucket());

                BlobId sourceBlobId = BlobId.of(bucketName, key);
                BlobId targetBlobId = BlobId.of(getConfiguration().getDestinationBucket(), key);
                CopyRequest request = CopyRequest.of(sourceBlobId, targetBlobId);
                CopyWriter copyWriter = getStorageClient().copy(request);

                LOG.trace("Moved object from bucket {} with key {} to bucketName {} -> {}", bucketName, key,
                        getConfiguration().getDestinationBucket(), copyWriter.getResult());
            }
            if (getConfiguration().isDeleteAfterRead()) {
                String bucketName = exchange.getIn().getHeader(GoogleCloudStorageConstants.BUCKET_NAME, String.class);
                String key = exchange.getIn().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class);

                LOG.trace("Deleting object from bucket {} with key {}...", bucketName, key);

                boolean b = getStorageClient().delete(bucketName, key);

                LOG.trace("Deleted object from bucket {} with key {}, result={}", bucketName, key, b);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred during moving or deleting object. This exception is ignored.",
                    exchange, e);
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
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }

    protected GoogleCloudStorageConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Storage getStorageClient() {
        return getEndpoint().getStorageClient();
    }

    @Override
    public GoogleCloudStorageEndpoint getEndpoint() {
        return (GoogleCloudStorageEndpoint) super.getEndpoint();
    }

    public Exchange createExchange(Blob blob, String key) {
        return createExchange(getEndpoint().getExchangePattern(), blob, key);
    }

    public Exchange createExchange(ExchangePattern pattern, Blob blob, String key) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting object with key [{}] from bucket [{}]...", key, getConfiguration().getBucketName());
            LOG.trace("Got object [{}]", blob);
        }

        Exchange exchange = createExchange(true);
        exchange.setPattern(pattern);
        Message message = exchange.getIn();

        if (getConfiguration().isIncludeBody()) {
            // download as file
            if (getConfiguration().getDownloadFileName() != null) {
                // create a dummy exchange as Exchange is needed for expression evaluation
                String result = evaluateFileExpression(exchange, getConfiguration().getDownloadFileName(), blob.getName());
                if (result != null) {
                    File file = new File(result);
                    blob.downloadTo(file.toPath());
                    message.setBody(file);
                }
            } else {
                // store blob data in the message body
                try {
                    // if stream caching is enabled then use that so we can stream accordingly
                    // for example to overflow to disk for big streams
                    OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);
                    blob.downloadTo(osb);
                    message.setBody(osb.build());
                } catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        } else {
            // store raw blob
            message.setBody(blob);
        }

        message.setHeader(GoogleCloudStorageConstants.OBJECT_NAME, key);
        message.setHeader(GoogleCloudStorageConstants.BUCKET_NAME, getConfiguration().getBucketName());
        //OTHER METADATA
        message.setHeader(GoogleCloudStorageConstants.CACHE_CONTROL, blob.getCacheControl());
        message.setHeader(GoogleCloudStorageConstants.METADATA_COMPONENT_COUNT, blob.getComponentCount());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_DISPOSITION, blob.getContentDisposition());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, blob.getContentEncoding());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CONTENT_LANGUAGE, blob.getContentLanguage());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_TYPE, blob.getContentType());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CUSTOM_TIME, blob.getCustomTime());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CRC32C_HEX, blob.getCrc32cToHexString());
        message.setHeader(GoogleCloudStorageConstants.METADATA_ETAG, blob.getEtag());
        message.setHeader(GoogleCloudStorageConstants.METADATA_GENERATION, blob.getGeneration());
        message.setHeader(GoogleCloudStorageConstants.METADATA_BLOB_ID, blob.getBlobId());
        message.setHeader(GoogleCloudStorageConstants.METADATA_KMS_KEY_NAME, blob.getKmsKeyName());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_MD5, blob.getMd5ToHexString());
        message.setHeader(GoogleCloudStorageConstants.METADATA_MEDIA_LINK, blob.getMediaLink());
        message.setHeader(GoogleCloudStorageConstants.METADATA_METAGENERATION, blob.getMetageneration());
        message.setHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, blob.getSize());
        message.setHeader(GoogleCloudStorageConstants.METADATA_STORAGE_CLASS, blob.getStorageClass());
        message.setHeader(GoogleCloudStorageConstants.METADATA_CREATE_TIME, blob.getCreateTime());
        message.setHeader(GoogleCloudStorageConstants.METADATA_LAST_UPDATE, new Date(blob.getUpdateTime()));

        return exchange;
    }

    protected String evaluateFileExpression(Exchange exchange, String downloadFileName, String blogName) {
        CamelContext camelContext = exchange.getContext();
        // use blob as file name
        exchange.getMessage().setHeader(GoogleCloudStorageConstants.FILE_NAME, blogName);

        String eval = downloadFileName;
        if (!downloadFileName.contains("$")) {
            eval = downloadFileName + "/${file:name}";
        }
        Expression exp = language.createExpression(eval);
        exp.init(camelContext);

        String result = exp.evaluate(exchange, String.class);
        if (exchange.getException() != null) {
            throw RuntimeCamelException.wrapRuntimeCamelException(exchange.getException());
        }
        return result;
    }

}
