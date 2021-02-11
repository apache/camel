package org.apache.camel.component.google.storage;

import java.io.ByteArrayOutputStream;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.storage.client.StorageInternalClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google Cloud Storage is an Object Storage to store any amount of data and retrieve it as often you like.
 * 
 * Google Storage Endpoint definition represents a bucket within the storage and contains configuration 
 * to customize the behavior of Consumer and Producer.
 */
@UriEndpoint(firstVersion = "3.7.0", scheme = "google-storage", title = "Google Storage",
             syntax = "google-storage:bucketName",
             category = { Category.CLOUD })
public class GoogleCloudStorageEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageEndpoint.class);

    @UriParam
    private GoogleCloudStorageComponentConfiguration configuration;

    private Storage storageClient;

    public GoogleCloudStorageEndpoint(String uri, GoogleCloudStorageComponent component,
                                      GoogleCloudStorageComponentConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GoogleCloudStorageProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new GoogleCloudStorageConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.storageClient = configuration.getStorageClient() != null
                ? configuration.getStorageClient()
                : StorageInternalClientFactory.getStorageClient(this.configuration).getGoogleCloudStorage();

        if (configuration.isAutoCreateBucket()) {
            Bucket bucket = storageClient.get(configuration.getBucketName());
            if (bucket != null) {
                LOG.trace("Bucket [{}] already exists", bucket.getName());
                return;
            } else {
                // creates the new bucket because it doesn't exist yet
                BucketInfo bucketInfo = BucketInfo.newBuilder(configuration.getBucketName()).build();
                bucket = storageClient.create(bucketInfo);
                LOG.trace("Bucket [{}] has been created", bucket.getName());
            }
        }
    }

    public GoogleCloudStorageComponentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     * 
     * @param configuration
     */
    public void setConfiguration(GoogleCloudStorageComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    public Exchange createExchange(Blob blob, String key) {
        return createExchange(getExchangePattern(), blob, key);
    }

    public Exchange createExchange(ExchangePattern pattern, Blob blob, String key) {
        LOG.trace("Getting object with key [{}] from bucket [{}]...", key, getConfiguration().getBucketName());

        LOG.trace("Got object [{}]", blob);

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();

        if (configuration.isIncludeBody()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                blob.downloadTo(baos);
                message.setBody(baos.toByteArray());

            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            message.setBody(blob);
        }

        message.setHeader(GoogleCloudStorageConstants.OBJECT_NAME, key);
        message.setHeader(GoogleCloudStorageConstants.BUCKET_NAME, getConfiguration().getBucketName());
        /*
        TODO
        message.setHeader(AWS2S3Constants.E_TAG, s3Object.response().eTag());
        message.setHeader(AWS2S3Constants.LAST_MODIFIED, s3Object.response().lastModified());
        message.setHeader(AWS2S3Constants.VERSION_ID, s3Object.response().versionId());
        message.setHeader(AWS2S3Constants.CONTENT_TYPE, s3Object.response().contentType());
        message.setHeader(AWS2S3Constants.CONTENT_LENGTH, s3Object.response().contentLength());
        message.setHeader(AWS2S3Constants.CONTENT_ENCODING, s3Object.response().contentEncoding());
        message.setHeader(AWS2S3Constants.CONTENT_DISPOSITION, s3Object.response().contentDisposition());
        message.setHeader(AWS2S3Constants.CACHE_CONTROL, s3Object.response().cacheControl());
        message.setHeader(AWS2S3Constants.SERVER_SIDE_ENCRYPTION, s3Object.response().serverSideEncryption());
        message.setHeader(AWS2S3Constants.EXPIRATION_TIME, s3Object.response().expiration());
        message.setHeader(AWS2S3Constants.REPLICATION_STATUS, s3Object.response().replicationStatus());
        message.setHeader(AWS2S3Constants.STORAGE_CLASS, s3Object.response().storageClass());
        message.setHeader(AWS2S3Constants.METADATA, s3Object.response().metadata());
        */
        /*
         * If includeBody == true, it is safe to close the object here because the S3Object
         * was consumed already. If includeBody != true, the caller is responsible for
         * closing the stream once the body has been fully consumed or use the autoCloseBody
         * configuration to automatically schedule the body closing at the end of exchange.
         */
        /*
        if (configuration.isIncludeBody()) {
            IOHelper.close(s3Object);
        } else {
            if (configuration.isAutocloseBody()) {
                exchange.adapt(ExtendedExchange.class).addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        IOHelper.close(s3Object);
                    }
                });
            }
        }
        */

        return exchange;
    }

}
