package org.apache.camel.component.google.storage;

import com.google.cloud.storage.Storage;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudStorageComponentConfiguration implements Cloneable {

    @UriPath(label = "common", description = "Bucket name")
    @Metadata(required = true)
    private String bucketName;

    @UriParam(label = "common", description = "Service account key")
    private String serviceAccountKey;

    //@UriParam(label = "common", description = "ProjectId")
    //private String projectId;

    @UriParam(label = "producer",
              enums = "copyObject,listObjects,deleteObject,deleteBucket,listBuckets,getObject,createDownloadLink")
    private GoogleCloudStorageComponentOperations operation;

    @UriParam
    private String objectName;

    @UriParam(label = "common", defaultValue = "true")
    private boolean autoCreateBucket = true;

    @UriParam(label = "consumer")
    private boolean moveAfterRead;
    @UriParam(label = "consumer")
    private String destinationBucket;
    //    @UriParam(label = "consumer")
    //    private String destinationBucketPrefix;
    //    @UriParam(label = "consumer")
    //    private String destinationBucketSuffix;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    /*
    @UriParam(label = "consumer")
    private String fileName;
    @UriParam(label = "consumer")
    private String prefix;
    @UriParam(label = "consumer")
    private String delimiter;
    @UriParam(label = "consumer")
    private String doneFileName;
    */
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean includeBody = true;
    //@UriParam(label = "consumer", defaultValue = "true")
    //private boolean includeFolders = true;

    @UriParam
    private Storage storageClient;

    public String getBucketName() {
        return this.bucketName;
    }

    /**
     * Bucket name
     */
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * The Service account key that can be used as credentials for the Storage client.
     * 
     * @param serviceAccountKey
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getObjectName() {
        return objectName;
    }

    /**
     * objectName
     */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public Storage getStorageClient() {
        return storageClient;
    }

    /**
     * Set strage client
     * 
     * @param storageClient
     */
    public void setStorageClient(Storage storageClient) {
        this.storageClient = storageClient;
    }

    public GoogleCloudStorageComponentOperations getOperation() {
        return operation;
    }

    /**
     * set the operation for the producer
     * 
     * @param operation
     */
    public void setOperation(GoogleCloudStorageComponentOperations operation) {
        this.operation = operation;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    /**
     * Setting the autocreation of the bucket bucketName.
     */
    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public boolean isMoveAfterRead() {
        return moveAfterRead;
    }

    /**
     * Move objects from S3 bucket to a different bucket after they have been retrieved. To accomplish the operation the
     * destinationBucket option must be set. The copy bucket operation is only performed if the Exchange is committed.
     * If a rollback occurs, the object is not moved.
     */
    public void setMoveAfterRead(boolean moveAfterRead) {
        this.moveAfterRead = moveAfterRead;
    }

    public String getDestinationBucket() {
        return destinationBucket;
    }

    /**
     * Define the destination bucket where an object must be moved when moveAfterRead is set to true.
     */
    public void setDestinationBucket(String destinationBucket) {
        this.destinationBucket = destinationBucket;
    }

    /*
    public String getDestinationBucketPrefix() {
        return destinationBucketPrefix;
    }
    
    /**
     * Define the destination bucket prefix to use when an object must be moved and moveAfterRead is set to true.
     *
    public void setDestinationBucketPrefix(String destinationBucketPrefix) {
        this.destinationBucketPrefix = destinationBucketPrefix;
    }
    
    public String getDestinationBucketSuffix() {
        return destinationBucketSuffix;
    }
    
    /**
     * Define the destination bucket suffix to use when an object must be moved and moveAfterRead is set to true.
     *
    public void setDestinationBucketSuffix(String destinationBucketSuffix) {
        this.destinationBucketSuffix = destinationBucketSuffix;
    }
    
    /**
     * If it is true, the folders/directories will be consumed. If it is false, they will be ignored, and Exchanges will
     * not be created for those
     *
    public void setIncludeFolders(boolean includeFolders) {
        this.includeFolders = includeFolders;
    }
    
    public boolean isIncludeFolders() {
        return includeFolders;
    }
    */
    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete objects from S3 after they have been retrieved. The delete is only performed if the Exchange is committed.
     * If a rollback occurs, the object is not deleted.
     * <p/>
     * If this option is false, then the same objects will be retrieve over and over again on the polls. Therefore you
     * need to use the Idempotent Consumer EIP in the route to filter out duplicates. You can filter using the
     * {@link AWS2S3Constants#BUCKET_NAME} and {@link AWS2S3Constants#KEY} headers, or only the
     * {@link AWS2S3Constants#KEY} header.
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    /**
     * If it is true, the S3Object exchange will be consumed and put into the body and closed. If false the S3Object
     * stream will be put raw into the body and the headers will be set with the S3 object metadata. This option is
     * strongly related to autocloseBody option. In case of setting includeBody to true because the S3Object stream will
     * be consumed then it will also be closed in case of includeBody false then it will be up to the caller to close
     * the S3Object stream. However setting autocloseBody to true when includeBody is false it will schedule to close
     * the S3Object stream automatically on exchange completion.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    public GoogleCloudStorageComponentConfiguration copy() {
        try {
            return (GoogleCloudStorageComponentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
