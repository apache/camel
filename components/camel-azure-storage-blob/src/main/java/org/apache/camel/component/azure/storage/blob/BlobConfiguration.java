package org.apache.camel.component.azure.storage.blob;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class BlobConfiguration implements Cloneable {

    @UriPath
    private String accountName;
    @UriPath
    private String containerName;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "producer", enums = "listContainers")
    private BlobOperationsDefinition operation;
    @UriParam(label = "common")
    private String blobName;
    @UriParam(label = "common", enums = "blockblob,appendblob,pageblob", defaultValue = "blockblob")
    private BlobType blobType = BlobType.blockblob;
    @UriParam(label = "common")
    private String fileDir;
    @UriParam(label = "common", defaultValue = "0")
    private long blobOffset = 0;
    @UriParam(label = "common")
    private Long dataCount;
    @UriParam(label = "common", defaultValue = "false")
    private boolean getRangeContentMd5;
    @UriParam(label = "common", defaultValue = "0")
    private int maxRetryRequests = 0;


    /**
     * dd
     * @return
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * dd
     * @return
     */
    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    /**
     * dd
     * @return
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * dd
     * @return
     */
    public BlobOperationsDefinition getOperation() {
        return operation;
    }

    public void setOperation(BlobOperationsDefinition operation) {
        this.operation = operation;
    }

    /**
     * dd
     * @return
     */
    public String getBlobName() {
        return blobName;
    }

    public void setBlobName(String blobName) {
        this.blobName = blobName;
    }

    /**
     * dd
     * @return
     */
    public BlobType getBlobType() {
        return blobType;
    }

    public void setBlobType(BlobType blobType) {
        this.blobType = blobType;
    }

    /**
     * dd
     * @return
     */
    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    /**
     * Set the blob offset for the upload or download operations, default is 0
     */
    public long getBlobOffset() {
        return blobOffset;
    }

    public void setBlobOffset(long blobOffset) {
        this.blobOffset = blobOffset;
    }

    /**
     * How many bytes to include in the range. Must be greater than or equal to 0 if specified.
     */
    public Long getDataCount() {
        return dataCount;
    }

    public void setDataCount(Long dataCount) {
        this.dataCount = dataCount;
    }

    /**
     * Whether the contentMD5 for the specified blob range should be returned.
     */
    public boolean isGetRangeContentMd5() {
        return getRangeContentMd5;
    }

    public void setGetRangeContentMd5(boolean getRangeContentMd5) {
        this.getRangeContentMd5 = getRangeContentMd5;
    }

    /**
     * Specifies the maximum number of additional HTTP Get requests that will be made while reading the data from a response body.
     */
    public int getMaxRetryRequests() {
        return maxRetryRequests;
    }

    public void setMaxRetryRequests(int maxRetryRequests) {
        this.maxRetryRequests = maxRetryRequests;
    }

    public BlobConfiguration copy() {
        try {
            return (BlobConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
