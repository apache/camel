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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.BlockEntry;
import com.microsoft.azure.storage.blob.BlockListingFilter;
import com.microsoft.azure.storage.blob.CloudAppendBlob;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.blob.PageRange;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.azure.common.ExchangeUtil;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Azure Storage Blob Service
 */
public class BlobServiceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BlobServiceProducer.class);

    public BlobServiceProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        BlobServiceOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            operation = BlobServiceOperations.listBlobs;
        } else {
            switch (operation) {
            case getBlob:
                getBlob(exchange);
                break;
            case deleteBlob:
                deleteBlob(exchange);
                break;    
            case listBlobs:
                listBlobs(exchange);
                break;
            case updateBlockBlob:
                updateBlockBlob(exchange);
                break;
            case uploadBlobBlocks:
                uploadBlobBlocks(exchange);
                break;
            case commitBlobBlockList:
                commitBlobBlockList(exchange);
                break;
            case getBlobBlockList:
                getBlobBlockList(exchange);
                break;    
            case createAppendBlob:
                createAppendBlob(exchange);
                break;    
            case updateAppendBlob:
                updateAppendBlob(exchange);
                break;
            case createPageBlob:
                createPageBlob(exchange);
                break;
            case updatePageBlob:
                uploadPageBlob(exchange);
                break;
            case resizePageBlob:
                resizePageBlob(exchange);
                break;
            case clearPageBlob:
                clearPageBlob(exchange);
                break;
            case getPageBlobRanges:
                getPageBlobRanges(exchange);
                break;    
            default:
                throw new IllegalArgumentException("Unsupported operation");
            }
        }     
    }
    
    private void listBlobs(Exchange exchange) throws Exception {
        CloudBlobContainer client = BlobServiceUtil.createBlobContainerClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        LOG.trace("Getting the blob list from the container [{}] from exchange [{}]...", 
                  getConfiguration().getContainerName(), exchange);
        BlobServiceConfiguration cfg = getConfiguration();
        EnumSet<BlobListingDetails> details = null;
        Object detailsObject = exchange.getIn().getHeader(BlobServiceConstants.BLOB_LISTING_DETAILS);
        if (detailsObject instanceof EnumSet) {
            @SuppressWarnings("unchecked")
            EnumSet<BlobListingDetails> theDetails = (EnumSet<BlobListingDetails>)detailsObject;
            details = theDetails;
        } else if (detailsObject instanceof BlobListingDetails) {
            details = EnumSet.of((BlobListingDetails)detailsObject);
        }
        Iterable<ListBlobItem> items = 
            client.listBlobs(cfg.getBlobPrefix(), cfg.isUseFlatListing(), 
                             details, opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange).setBody(items);
    }
    
    private void updateBlockBlob(Exchange exchange) throws Exception {
        CloudBlockBlob client = BlobServiceUtil.createBlockBlobClient(getConfiguration());
        configureCloudBlobForWrite(client);
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        
        InputStream inputStream = getInputStreamFromExchange(exchange);
        
        LOG.trace("Putting a block blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        try {
            client.upload(inputStream, -1,
                          opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        } finally {
            closeInputStreamIfNeeded(inputStream);
        }
    }
    
    private void uploadBlobBlocks(Exchange exchange) throws Exception {
        Object object = exchange.getIn().getMandatoryBody();
        
        List<BlobBlock> blobBlocks = null;
        if (object instanceof List) {
            blobBlocks = (List<BlobBlock>)blobBlocks;
        } else if (object instanceof BlobBlock) {
            blobBlocks = Collections.singletonList((BlobBlock)object);
        } 
        if (blobBlocks == null || blobBlocks.isEmpty()) {
            throw new IllegalArgumentException("Illegal storageBlocks payload");
        }
        
        CloudBlockBlob client = BlobServiceUtil.createBlockBlobClient(getConfiguration());
        configureCloudBlobForWrite(client);
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        
        LOG.trace("Putting a blob [{}] from blocks from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        List<BlockEntry> blockEntries = new LinkedList<BlockEntry>();
        for (BlobBlock blobBlock : blobBlocks) {
            blockEntries.add(blobBlock.getBlockEntry());
            client.uploadBlock(blobBlock.getBlockEntry().getId(), blobBlock.getBlockStream(), -1, 
                               opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        }
        Boolean commitBlockListLater = exchange.getIn().getHeader(BlobServiceConstants.COMMIT_BLOCK_LIST_LATER, 
                                                                  Boolean.class);
        if (Boolean.TRUE != commitBlockListLater) {
            client.commitBlockList(blockEntries, 
                                   opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        }
    }
    
    private void commitBlobBlockList(Exchange exchange) throws Exception {
        Object object = exchange.getIn().getMandatoryBody();
        
        List<BlockEntry> blockEntries = null;
        if (object instanceof List) {
            blockEntries = (List<BlockEntry>)blockEntries;
        } else if (object instanceof BlockEntry) {
            blockEntries = Collections.singletonList((BlockEntry)object);
        } 
        if (blockEntries == null || blockEntries.isEmpty()) {
            throw new IllegalArgumentException("Illegal commit block list payload");
        }
        
        CloudBlockBlob client = BlobServiceUtil.createBlockBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        
        LOG.trace("Putting a blob [{}] block list from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        client.commitBlockList(blockEntries, 
                               opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
    }
    
    private void getBlob(Exchange exchange) throws Exception {
        BlobServiceUtil.getBlob(exchange, getConfiguration());
    }
    
    private void deleteBlob(Exchange exchange) throws Exception {
        switch (getConfiguration().getBlobType()) {
        case blockblob:
            deleteBlockBlob(exchange);
            break;
        case appendblob:
            deleteAppendBlob(exchange);
            break;
        case pageblob:
            deletePageBlob(exchange);
            break;    
        default:
            throw new IllegalArgumentException("Unsupported blob type");
        }
    }
    
    private void getBlobBlockList(Exchange exchange) throws Exception {
        CloudBlockBlob client = BlobServiceUtil.createBlockBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        LOG.trace("Getting the blob block list [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        BlockListingFilter filter = exchange.getIn().getBody(BlockListingFilter.class);
        if (filter == null) {
            filter = BlockListingFilter.COMMITTED;
        }
        List<BlockEntry> blockEntries =
            client.downloadBlockList(filter, opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange).setBody(blockEntries);
    }
    
    private void deleteBlockBlob(Exchange exchange) throws Exception {
        CloudBlockBlob client = BlobServiceUtil.createBlockBlobClient(getConfiguration());
        doDeleteBlock(client, exchange);
    }
    
    private void createAppendBlob(Exchange exchange) throws Exception {
        CloudAppendBlob client = BlobServiceUtil.createAppendBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        if (opts.getAccessCond() == null) {
            // Default: do not reset the blob content if the blob already exists
            opts.setAccessCond(AccessCondition.generateIfNotExistsCondition());
        }
        doCreateAppendBlob(client, opts, exchange);
    }
    
    private void doCreateAppendBlob(CloudAppendBlob client, BlobServiceRequestOptions opts, Exchange exchange) 
        throws Exception {
        LOG.trace("Creating an append blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        try {
            client.createOrReplace(opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        } catch (StorageException ex) {
            if (ex.getHttpStatusCode() != 409) {
                throw ex;
            }
        }
        ExchangeUtil.getMessageForResponse(exchange)
            .setHeader(BlobServiceConstants.APPEND_BLOCK_CREATED, Boolean.TRUE);
    }

    private void updateAppendBlob(Exchange exchange) throws Exception {
        CloudAppendBlob client = BlobServiceUtil.createAppendBlobClient(getConfiguration());
        configureCloudBlobForWrite(client);
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        if (opts.getAccessCond() == null) {
            // Default: do not reset the blob content if the blob already exists
            opts.setAccessCond(AccessCondition.generateIfNotExistsCondition());
        }
        
        Boolean appendBlobCreated = exchange.getIn().getHeader(BlobServiceConstants.APPEND_BLOCK_CREATED, 
                                                                  Boolean.class);
        if (Boolean.TRUE != appendBlobCreated) {
            doCreateAppendBlob(client, opts, exchange);
        }
        
        InputStream inputStream = getInputStreamFromExchange(exchange);
        try {
            client.appendBlock(inputStream, -1,
                               opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        } finally {
            closeInputStreamIfNeeded(inputStream);
        }
    }    
    
    private void deleteAppendBlob(Exchange exchange) throws Exception {
        CloudAppendBlob client = BlobServiceUtil.createAppendBlobClient(getConfiguration());
        doDeleteBlock(client, exchange);
    }
    
    
    private void createPageBlob(Exchange exchange) throws Exception {
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        if (opts.getAccessCond() == null) {
            // Default: do not reset the blob content if the blob already exists
            opts.setAccessCond(AccessCondition.generateIfNotExistsCondition());
        }
        doCreatePageBlob(client, opts, exchange);
    }
    
    private void doCreatePageBlob(CloudPageBlob client, BlobServiceRequestOptions opts, Exchange exchange) 
        throws Exception {
        LOG.trace("Creating a page blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        Long pageSize = getPageBlobSize(exchange);
        try {
            client.create(pageSize,
                          opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        } catch (StorageException ex) {
            if (ex.getHttpStatusCode() != 409) {
                throw ex;
            }
        }
        ExchangeUtil.getMessageForResponse(exchange)
            .setHeader(BlobServiceConstants.PAGE_BLOCK_CREATED, Boolean.TRUE);
        
    }
    
    private void uploadPageBlob(Exchange exchange) throws Exception {
        LOG.trace("Updating a page blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        configureCloudBlobForWrite(client);
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        if (opts.getAccessCond() == null) {
            // Default: do not reset the blob content if the blob already exists
            opts.setAccessCond(AccessCondition.generateIfNotExistsCondition());
        }
        
        Boolean pageBlobCreated = exchange.getIn().getHeader(BlobServiceConstants.PAGE_BLOCK_CREATED, 
                                                                  Boolean.class);
        if (Boolean.TRUE != pageBlobCreated) {
            doCreatePageBlob(client, opts, exchange);
        }
        InputStream inputStream = getInputStreamFromExchange(exchange);
        doUpdatePageBlob(client, inputStream, opts, exchange);
        
    }
    
    private void resizePageBlob(Exchange exchange) throws Exception {
        LOG.trace("Resizing a page blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        Long pageSize = getPageBlobSize(exchange);
        client.resize(pageSize, opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
    }
    
    private void clearPageBlob(Exchange exchange) throws Exception {
        LOG.trace("Clearing a page blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
                
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        
        Long blobOffset = getConfiguration().getBlobOffset();
        Long blobDataLength = getConfiguration().getDataLength();
        PageRange range = exchange.getIn().getHeader(BlobServiceConstants.PAGE_BLOB_RANGE, PageRange.class);
        if (range != null) {
            blobOffset = range.getStartOffset();
            blobDataLength = range.getEndOffset() - range.getStartOffset();
        }
        if (blobDataLength == null) {        
            blobDataLength = blobOffset == 0 ? getPageBlobSize(exchange) : 512L; 
        }
        client.clearPages(blobOffset, blobDataLength,
                          opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
    }
    
    private void doUpdatePageBlob(CloudPageBlob client, InputStream is, BlobServiceRequestOptions opts, Exchange exchange) 
        throws Exception {
        
        Long blobOffset = getConfiguration().getBlobOffset();
        Long blobDataLength = getConfiguration().getDataLength();
        PageRange range = exchange.getIn().getHeader(BlobServiceConstants.PAGE_BLOB_RANGE, PageRange.class);
        if (range != null) {
            blobOffset = range.getStartOffset();
            blobDataLength = range.getEndOffset() - range.getStartOffset();
        }
        if (blobDataLength == null) {        
            blobDataLength = (long)is.available(); 
        }
        try {
            client.uploadPages(is, blobOffset, blobDataLength,
                           opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        } finally {
            closeInputStreamIfNeeded(is);    
        }
        
    }
    
    private void getPageBlobRanges(Exchange exchange) throws Exception {
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        BlobServiceUtil.configureCloudBlobForRead(client, getConfiguration());
        BlobServiceRequestOptions opts = BlobServiceUtil.getRequestOptions(exchange);
        LOG.trace("Getting the page blob ranges [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        List<PageRange> ranges = 
            client.downloadPageRanges(opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
        ExchangeUtil.getMessageForResponse(exchange).setBody(ranges);
    }
    
    private void deletePageBlob(Exchange exchange) throws Exception {
        CloudPageBlob client = BlobServiceUtil.createPageBlobClient(getConfiguration());
        doDeleteBlock(client, exchange);
    }
    
    private Long getPageBlobSize(Exchange exchange) {
        Long pageSize = exchange.getIn().getHeader(BlobServiceConstants.PAGE_BLOB_SIZE, Long.class);
        if (pageSize == null) {
            pageSize = 512L;
        }
        return pageSize;
    }

    
    private void doDeleteBlock(CloudBlob client, Exchange exchange) throws Exception {
        LOG.trace("Deleting a blob [{}] from exchange [{}]...", getConfiguration().getBlobName(), exchange);
        client.delete();
    }

    private String getCharsetName(Exchange exchange) {
        String charset = exchange.getIn().getHeader(Exchange.CHARSET_NAME, String.class);
        return charset == null ? "UTF-8" : charset;
    }
    
    private void configureCloudBlobForWrite(CloudBlob client) {
        if (getConfiguration().getStreamWriteSize() > 0) {
            client.setStreamWriteSizeInBytes(getConfiguration().getStreamWriteSize());
        }
        if (getConfiguration().getBlobMetadata() != null) {
            client.setMetadata(new HashMap<String, String>(getConfiguration().getBlobMetadata()));
        }
    }

    private BlobServiceOperations determineOperation(Exchange exchange) {
        BlobServiceOperations operation = exchange.getIn().getHeader(BlobServiceConstants.OPERATION, BlobServiceOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected BlobServiceConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "StorageBlobProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public BlobServiceEndpoint getEndpoint() {
        return (BlobServiceEndpoint) super.getEndpoint();
    }
    
    private InputStream getInputStreamFromExchange(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();

        if (body instanceof WrappedFile) {
            // unwrap file
            body = ((WrappedFile) body).getFile();
        }

        InputStream is;
        if (body instanceof InputStream) {
            is = (InputStream) body;
        } else if (body instanceof File) {
            is = new FileInputStream((File)body);
        } else if (body instanceof byte[]) {
            is = new ByteArrayInputStream((byte[]) body);
        } else {
            // try as input stream
            is = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);
        }

        if (is == null) {
            // fallback to string based
            throw new IllegalArgumentException("Unsupported blob type:" + body.getClass().getName());
        }

        return is;
    }
    
    private void closeInputStreamIfNeeded(InputStream inputStream) throws IOException {
        if (getConfiguration().isCloseStreamAfterWrite()) {
            inputStream.close();
        }
    }

}
