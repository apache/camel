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
package org.apache.camel.component.azure.blob;

import java.util.Map;

import com.microsoft.azure.storage.blob.CloudBlob;
import org.apache.camel.component.azure.common.AbstractConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class BlobServiceConfiguration extends AbstractConfiguration {

    private String containerName;
    
    private String blobName;
    
    @UriParam
    private CloudBlob azureBlobClient;
    
    @UriParam(defaultValue = "blockblob")
    private BlobType blobType = BlobType.blockblob;
    
    @UriParam(label = "producer", defaultValue = "listBlobs")
    private BlobServiceOperations operation = BlobServiceOperations.listBlobs;
    
    @UriParam(label = "producer")
    private int streamWriteSize;
    
    @UriParam
    private int streamReadSize;
    
    @UriParam(label = "producer")
    private Map<String, String> blobMetadata;
    
    @UriParam(defaultValue = "true")
    private boolean closeStreamAfterRead = true;
    
    @UriParam(label = "producer", defaultValue = "true")
    private boolean closeStreamAfterWrite = true;
    
    @UriParam
    private String fileDir;
    
    @UriParam(defaultValue = "0")
    private Long blobOffset = 0L;
    
    @UriParam
    private Long dataLength;
    
    @UriParam(label = "producer")
    private String blobPrefix;
    
    @UriParam
    private boolean publicForRead;
    
    @UriParam(label = "producer", defaultValue = "true")
    private boolean useFlatListing = true;
    
    public BlobServiceOperations getOperation() {
        return operation;
    }

    /**
     * Blob service operation hint to the producer 
     */
    public void setOperation(BlobServiceOperations operation) {
        this.operation = operation;
    }

    public String getContainerName() {
        return containerName;
    }

    /**
     * Set the blob service container name 
     */
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }
    
    public String getBlobName() {
        return blobName;
    }

    /**
     * Blob name, required for most operations
     */
    public void setBlobName(String blobName) {
        this.blobName = blobName;
    }

    public BlobType getBlobType() {
        return blobType;
    }

    /**
     * Set a blob type, 'blockblob' is default
     */
    public void setBlobType(BlobType blobType) {
        this.blobType = blobType;
    }
    
    public int getStreamWriteSize() {
        return streamWriteSize;
    }

    /**
     * Set the size of the buffer for writing block and page blocks 
     */
    public void setStreamWriteSize(int streamWriteSize) {
        this.streamWriteSize = streamWriteSize;
    }

    public int getStreamReadSize() {
        return streamReadSize;
    }

    /**
     * Set the minimum read size in bytes when reading the blob content 
     */
    public void setStreamReadSize(int streamReadSize) {
        this.streamReadSize = streamReadSize;
    }

    public Map<String, String> getBlobMetadata() {
        return blobMetadata;
    }

    /**
     * Set the blob meta-data 
     */
    public void setBlobMetadata(Map<String, String> blobMetadata) {
        this.blobMetadata = blobMetadata;
    }
    
    public CloudBlob getAzureBlobClient() {
        return azureBlobClient;
    }

    /**
     * The blob service client 
     */
    public void setAzureBlobClient(CloudBlob azureBlobClient) {
        this.azureBlobClient = azureBlobClient;
    }

    public boolean isCloseStreamAfterWrite() {
        return closeStreamAfterWrite;
    }

    /**
     * Close the stream after write or keep it open, default is true
     */
    public void setCloseStreamAfterWrite(boolean closeStreamAfterWrite) {
        this.closeStreamAfterWrite = closeStreamAfterWrite;
    }

    public boolean isCloseStreamAfterRead() {
        return closeStreamAfterRead;
    }

    /**
     * Close the stream after read or keep it open, default is true
     */
    public void setCloseStreamAfterRead(boolean closeStreamAfterRead) {
        this.closeStreamAfterRead = closeStreamAfterRead;
    }

    public String getFileDir() {
        return fileDir;
    }

    /**
     * Set the file directory where the downloaded blobs will be saved to 
     */
    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }

    public Long getBlobOffset() {
        return blobOffset;
    }

    /**
     *  Set the blob offset for the upload or download operations, default is 0 
     */
    public void setBlobOffset(Long dataOffset) {
        this.blobOffset = dataOffset;
    }
    
    public Long getDataLength() {
        return dataLength;
    }

    /**
     * Set the data length for the download or page blob upload operations 
     */
    public void setDataLength(Long dataLength) {
        this.dataLength = dataLength;
    }

    public String getBlobPrefix() {
        return blobPrefix;
    }
    
    /**
     * Set a prefix which can be used for listing the blobs 
     */
    public void setBlobPrefix(String blobPrefix) {
        this.blobPrefix = blobPrefix;
    }
    
    public boolean isPublicForRead() {
        return publicForRead;
    }

    /**
     * Storage resources can be public for reading their content, if this property is enabled
     * then the credentials do not have to be set
     */
    public void setPublicForRead(boolean publicForRead) {
        this.publicForRead = publicForRead;
    }

    public boolean isUseFlatListing() {
        return useFlatListing;
    }

    /**
     * Specify if the flat or hierarchical blob listing should be used 
     */
    public void setUseFlatListing(boolean useFlatListing) {
        this.useFlatListing = useFlatListing;
    }
}