package org.apache.camel.component.azure.storage.blob.operations;

import java.io.File;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related {@link BlobClient}
 */
public class BlobOperations {

    private final BlobConfiguration configuration;
    private final BlobClient client;

    public BlobOperations(final BlobConfiguration configuration, final BlobClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse downloadBlob() {
       checkIfContainerOrBlobIsEmpty(configuration);

       final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();

       if (isFileDownloadPathIsSet(configuration)) {
           // then we download the file directly
           final File outputFile = new File(configuration.getFileDir(), configuration.getBlobName());
           final BlobProperties properties = client.downloadToFile(outputFile.getAbsolutePath());
           blobOperationResponse.setBody(outputFile);
           blobOperationResponse.setHeaders(properties);
       } else {
           // then we return inputStream
           final BlobInputStream inputStream = client.openInputStream();
           blobOperationResponse.setBody(inputStream);
           blobOperationResponse.setHeaders(inputStream.getProperties());
       }

       return blobOperationResponse;
    }

    private boolean isFileDownloadPathIsSet(final BlobConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getFileDir());
    }

    public BlobOperationResponse deleteBlob() {
        checkIfContainerOrBlobIsEmpty(configuration);

        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        final Response<Void> azureResponse = client.deleteWithResponse(null, null, null, Context.NONE);

        blobOperationResponse.setHeaders(azureResponse.getHeaders());

        return blobOperationResponse;
    }

    private void checkIfContainerOrBlobIsEmpty(final BlobConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getContainerName()) || ObjectHelper.isEmpty(configuration.getBlobName())) {
            throw new IllegalArgumentException("No blob or container name was specified.");
        }
    }
}
