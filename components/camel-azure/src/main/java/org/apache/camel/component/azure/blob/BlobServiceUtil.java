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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudAppendBlob;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.azure.storage.blob.PageRange;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.common.ExchangeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlobServiceUtil {

    private static final Logger LOG = LoggerFactory.getLogger(BlobServiceUtil.class);

    private BlobServiceUtil() {
    }

    public static void getBlob(Exchange exchange, BlobServiceConfiguration cfg)
        throws Exception {
        switch (cfg.getBlobType()) {
        case blockblob:
            getBlockBlob(exchange, cfg);
            break;
        case appendblob:
            getAppendBlob(exchange, cfg);
            break;
        case pageblob:
            getPageBlob(exchange, cfg);
            break;
        default:
            throw new IllegalArgumentException("Unsupported blob type");
        }
    }

    private static void getBlockBlob(Exchange exchange, BlobServiceConfiguration cfg)
        throws Exception {
        CloudBlockBlob client = createBlockBlobClient(cfg);
        doGetBlob(client, exchange, cfg);
    }

    private static void getAppendBlob(Exchange exchange, BlobServiceConfiguration cfg) throws Exception {
        CloudAppendBlob client = createAppendBlobClient(cfg);
        doGetBlob(client, exchange, cfg);
    }

    private static void getPageBlob(Exchange exchange, BlobServiceConfiguration cfg) throws Exception {
        CloudPageBlob client = createPageBlobClient(cfg);
        doGetBlob(client, exchange, cfg);
    }

    private static void doGetBlob(CloudBlob client, Exchange exchange, BlobServiceConfiguration cfg)
        throws Exception {
        BlobServiceUtil.configureCloudBlobForRead(client, cfg);
        BlobServiceRequestOptions opts = getRequestOptions(exchange);
        LOG.trace("Getting a blob [{}] from exchange [{}]...", cfg.getBlobName(), exchange);
        OutputStream os = exchange.getIn().getBody(OutputStream.class);
        if (os == null) {
            String fileDir = cfg.getFileDir();
            if (fileDir != null) {
                File file = new File(fileDir, getBlobFileName(cfg));
                ExchangeUtil.getMessageForResponse(exchange).setBody(file);
                os = new FileOutputStream(file);  
            }
        }
        try {
            if (os == null) {
                // Let the producers like file: deal with it
                InputStream blobStream = client.openInputStream(
                    opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
                exchange.getIn().setBody(blobStream);
                exchange.getIn().setHeader(Exchange.FILE_NAME, getBlobFileName(cfg));
            } else {
                Long blobOffset = cfg.getBlobOffset();
                Long blobDataLength = cfg.getDataLength();
                if (client instanceof CloudPageBlob) {
                    PageRange range = exchange.getIn().getHeader(BlobServiceConstants.PAGE_BLOB_RANGE, PageRange.class);
                    if (range != null) {
                        blobOffset = range.getStartOffset();
                        blobDataLength = range.getEndOffset() - range.getStartOffset();
                    }
                }
                client.downloadRange(blobOffset, blobDataLength, os,
                                     opts.getAccessCond(), opts.getRequestOpts(), opts.getOpContext());
            }
        } finally {
            if (os != null && cfg.isCloseStreamAfterRead()) {
                os.close();
            }
        }
    }
    private static String getBlobFileName(BlobServiceConfiguration cfg) {
        return cfg.getBlobName()  + ".blob";
    }

    public static CloudBlobContainer createBlobContainerClient(BlobServiceConfiguration cfg)
        throws Exception {
        URI uri = prepareStorageBlobUri(cfg, false);
        StorageCredentials creds = getAccountCredentials(cfg);
        return new CloudBlobContainer(uri, creds);
    }

    public static CloudBlockBlob createBlockBlobClient(BlobServiceConfiguration cfg)
        throws Exception {
        CloudBlockBlob client = (CloudBlockBlob) getConfiguredClient(cfg);
        if (client == null) {
            URI uri = prepareStorageBlobUri(cfg);
            StorageCredentials creds = getAccountCredentials(cfg);
            client = new CloudBlockBlob(uri, creds);
        }
        return client;
    }

    public static CloudAppendBlob createAppendBlobClient(BlobServiceConfiguration cfg)
        throws Exception {
        CloudAppendBlob client = (CloudAppendBlob) getConfiguredClient(cfg);
        if (client == null) {
            URI uri = prepareStorageBlobUri(cfg);
            StorageCredentials creds = getAccountCredentials(cfg);
            client = new CloudAppendBlob(uri, creds);
        }
        return client;
    }

    public static CloudPageBlob createPageBlobClient(BlobServiceConfiguration cfg)
        throws Exception {
        CloudPageBlob client = (CloudPageBlob) getConfiguredClient(cfg);
        if (client == null) {
            URI uri = prepareStorageBlobUri(cfg);
            StorageCredentials creds = getAccountCredentials(cfg);
            client = new CloudPageBlob(uri, creds);
        }
        return client;
    }

    public static CloudBlob getConfiguredClient(BlobServiceConfiguration cfg) {
        CloudBlob client = cfg.getAzureBlobClient();
        if (client != null) {
            Class<?> expectedCls = null;
            if (cfg.getBlobType() == BlobType.blockblob) {
                expectedCls = CloudBlockBlob.class;
            } else if (cfg.getBlobType() == BlobType.appendblob) {
                expectedCls = CloudAppendBlob.class;
            } else if (cfg.getBlobType() == BlobType.pageblob) {
                expectedCls = CloudPageBlob.class;
            }
            if (client.getClass() != expectedCls) {
                throw new IllegalArgumentException("Invalid Client Type");
            }
            if (!client.getUri().equals(prepareStorageBlobUri(cfg))) {
                throw new IllegalArgumentException("Invalid Client URI");
            }
        }
        return client;
    }

    public static StorageCredentials getAccountCredentials(BlobServiceConfiguration cfg) {
        return cfg.getCredentials();
    }

    public static void configureCloudBlobForRead(CloudBlob client, BlobServiceConfiguration cfg) {
        if (cfg.getStreamReadSize() > 0) {
            client.setStreamMinimumReadSizeInBytes(cfg.getStreamReadSize());
        }
    }

    public static URI prepareStorageBlobUri(BlobServiceConfiguration cfg) {
        return prepareStorageBlobUri(cfg, true);
    }

    public static URI prepareStorageBlobUri(BlobServiceConfiguration cfg, boolean blobNameRequired) {
        if (blobNameRequired && cfg.getBlobName() == null) {
            throw new IllegalArgumentException("Blob name must be specified");
        }

        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append("https://")
            .append(cfg.getAccountName())
            .append(BlobServiceConstants.SERVICE_URI_SEGMENT)
            .append("/")
            .append(cfg.getContainerName());
        if (cfg.getBlobName() != null) {
            uriBuilder.append("/")
                .append(cfg.getBlobName());
        }
        return URI.create(uriBuilder.toString());
    }


    public static BlobServiceRequestOptions getRequestOptions(Exchange exchange) {
        BlobServiceRequestOptions opts = exchange.getIn().getHeader(
            BlobServiceConstants.BLOB_SERVICE_REQUEST_OPTIONS, BlobServiceRequestOptions.class);
        if (opts != null) {
            return opts;
        } else {
            opts = new BlobServiceRequestOptions();
        }
        AccessCondition accessCond =
            exchange.getIn().getHeader(BlobServiceConstants.ACCESS_CONDITION, AccessCondition.class);
        BlobRequestOptions requestOpts =
            exchange.getIn().getHeader(BlobServiceConstants.BLOB_REQUEST_OPTIONS, BlobRequestOptions.class);
        OperationContext opContext =
            exchange.getIn().getHeader(BlobServiceConstants.OPERATION_CONTEXT, OperationContext.class);
        opts.setAccessCond(accessCond);
        opts.setOpContext(opContext);
        opts.setRequestOpts(requestOpts);
        return opts;
    }
}
