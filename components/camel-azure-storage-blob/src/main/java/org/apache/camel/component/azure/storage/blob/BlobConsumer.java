package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.implementation.models.StorageErrorException;
import com.azure.storage.blob.models.BlobStorageException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;

public class BlobConsumer extends ScheduledPollConsumer {

    public BlobConsumer(final BlobEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String containerName = getEndpoint().getConfiguration().getContainerName();
        final String blobName = getEndpoint().getConfiguration().getBlobName();
        final BlobServiceClient serviceClient = getEndpoint().getBlobServiceClient();
        final BlobClientWrapper clientWrapper = new BlobClientWrapper(serviceClient.getBlobContainerClient(containerName).getBlobClient(blobName));
        final BlobOperations operations = new BlobOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = getEndpoint().createExchange();

        try {
            BlobOperationResponse response;
            if (!ObjectHelper.isEmpty(getEndpoint().getConfiguration().getFileDir())) {
                // if we have a fileDir set, we download our content
                response = operations.downloadBlobToFile(exchange);
            } else {
                // otherwise, we rely on the outputstream/inputstream
                response = operations.getBlob(exchange);
            }

            getEndpoint().setResponseOnExchange(response, exchange);

            getAsyncProcessor().process(exchange);
            return 1;
        } catch (BlobStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    @Override
    public BlobEndpoint getEndpoint() {
        return (BlobEndpoint) super.getEndpoint();
    }
}
