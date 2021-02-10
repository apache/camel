package org.apache.camel.component.google.storage;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.CopyRequest;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudStorageConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageConsumer.class);

    //private String marker;
    //private transient String consumerToString;

    public GoogleCloudStorageConsumer(GoogleCloudStorageEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
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
                    BucketInfo bucketInfo = BucketInfo.newBuilder(getConfiguration().getDestinationBucket()).build();
                    bucket = getStorageClient().create(bucketInfo);
                    LOG.trace("Destination Bucket created", bucket.getName());
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
        //String doneFileName = getConfiguration().getDoneFileName();
        Queue<Exchange> exchanges = new LinkedList<>();

        //TODO ripristinare
        //if (!doneFileCheckPasses(bucketName, doneFileName)) {
        //    exchanges = new LinkedList<>();
        //} else 
        if (fileName != null) {
            LOG.trace("Getting object in bucket [{}] with file name [{}]...", bucketName, fileName);

            Blob blob = getStorageClient().get(bucketName, fileName);

            exchanges = createExchanges(blob, fileName);
        } else {
            LOG.trace("Queueing objects in bucket [{}]...", bucketName);

            List<Blob> bloblist = new LinkedList<>();
            for (Blob blob : getStorageClient().list(bucketName).iterateAll()) {
                bloblist.add(blob);
            }

            /*
            ListObjectsRequest.Builder listObjectsRequest = ListObjectsRequest.builder();
            listObjectsRequest.bucket(bucketName);
            listObjectsRequest.prefix(getConfiguration().getPrefix());
            listObjectsRequest.delimiter(getConfiguration().getDelimiter());
            
            if (maxMessagesPerPoll > 0) {
                listObjectsRequest.maxKeys(maxMessagesPerPoll);
            }
            // if there was a marker from previous poll then use that to
            // continue from where we left last time
            if (marker != null) {
                LOG.trace("Resuming from marker: {}", marker);
                listObjectsRequest.marker(marker);
            }
            
            ListObjectsResponse listObjects = getAmazonS3Client().listObjects(listObjectsRequest.build());
            
            if (listObjects.isTruncated()) {
                marker = listObjects.nextMarker();
                LOG.trace("Returned list is truncated, so setting next marker: {}", marker);
            } else {
                // no more data so clear marker
                marker = null;
            }
            */
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found {} objects in bucket [{}]...", bloblist.size(), bucketName);
            }

            exchanges = createExchanges(bloblist);
        }

        return processBatch(CastUtils.cast(exchanges));
    }

    /*
    private boolean doneFileCheckPasses(String bucketName, String doneFileName) {
        if (doneFileName == null) {
            return true;
        } else {
            return checkFileExists(bucketName, doneFileName);
        }
    }
    */

    /*
    private boolean checkFileExists(String bucketName, String doneFileName) {
        HeadObjectRequest.Builder headObjectsRequest = HeadObjectRequest.builder();
        headObjectsRequest.bucket(bucketName);
        headObjectsRequest.key(doneFileName);
        try {
            getAmazonS3Client().headObject(headObjectsRequest.build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
    */

    protected Queue<Exchange> createExchanges(Blob blob, String key) {
        Queue<Exchange> answer = new LinkedList<>();
        Exchange exchange = getEndpoint().createExchange(blob, key);
        answer.add(exchange);
        return answer;
    }

    protected Queue<Exchange> createExchanges(List<Blob> blobList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", blobList.size());
        }

        //Collection<Blob> blobs = new ArrayList<>();
        Queue<Exchange> answer = new LinkedList<>();
        try {
            for (Blob blob : blobList) {
                /*
                Builder getRequest
                        = GetObjectRequest.builder().bucket(getConfiguration().getBucketName()).key(s3ObjectSummary.key());
                if (getConfiguration().isUseCustomerKey()) {
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyId())) {
                        getRequest.sseCustomerKey(getConfiguration().getCustomerKeyId());
                    }
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyMD5())) {
                        getRequest.sseCustomerKeyMD5(getConfiguration().getCustomerKeyMD5());
                    }
                    if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerAlgorithm())) {
                        getRequest.sseCustomerAlgorithm(getConfiguration().getCustomerAlgorithm());
                    }
                }*/
                //ResponseInputStream<GetObjectResponse> s3Object = getAmazonS3Client().getObject(getRequest.build(), ResponseTransformer.toInputStream());

                //if (includeS3Object(s3Object)) {
                //    blobs.add(s3Object);
                Exchange exchange = getEndpoint().createExchange(blob, blob.getBlobId().getName());
                answer.add(exchange);
                //} else {
                // If includeFolders != true and the object is not included, it is safe to close the object here.
                // If includeFolders == true, the exchange will close the object.
                //    IOHelper.close(s3Object);
                //}
            }
        } catch (Exception e) {
            LOG.warn("Error getting object due: {}", e.getMessage(), e);
            // ensure all previous gathered s3 objects are closed
            // if there was an exception creating the exchanges in this batch
            //s3Objects.forEach(IOHelper::close);
            throw e;
        }

        return answer;
    }

    /**
     * Decide whether to include the Objects in the results
     *
     * @param  Object
     * @return        true to include, false to exclude
     */
    protected boolean includeObject(Blob blob) {

        if (getConfiguration().isIncludeFolders()) {
            return true;
        } else {
            //TODO understand if the object is a directory

            // Config says to ignore folders/directories
            //return !Optional.of(((GetObjectResponse) s3Object.response()).contentType()).orElse("")
            //        .toLowerCase().startsWith("application/x-directory");
            return true;
        }
    }

    @Override
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
            exchange.adapt(ExtendedExchange.class).addOnCompletion(new Synchronization() {
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
        //LOG.info("processCommit");
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

    protected GoogleCloudStorageComponentConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Storage getStorageClient() {
        return getEndpoint().getStorageClient();
    }

    @Override
    public GoogleCloudStorageEndpoint getEndpoint() {
        return (GoogleCloudStorageEndpoint) super.getEndpoint();
    }
}
