package org.apache.camel.component.azure.storage.blob;

import java.util.Locale;

import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The azure-storage-blob component is used for storing and retrieving blobs from Azure Storage Blob Service using SDK v12.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "azure-storage-blob", title = "Azure Storage Blob Service", syntax = "azure-blob:containerName", label = "cloud,file")
public class BlobEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(BlobEndpoint.class);

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient blobContainerClient;


    @UriParam
    private BlobConfiguration configuration;

    public BlobEndpoint(final String uri, final Component component, final BlobConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BlobProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    public BlobServiceClient getBlobServiceClient() {
        if (blobServiceClient == null) {
            blobServiceClient = BlobClientFactory.createBlobServiceClient(configuration);
        }
        return blobServiceClient;
    }

    public BlobContainerClient getBlobContainerClient() {
        if (blobContainerClient == null) {
            blobContainerClient = BlobClientFactory.createBlobContainerClient(configuration);
        }
        return blobContainerClient;
    }

    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobConfiguration configuration) {
        this.configuration = configuration;
    }
}
