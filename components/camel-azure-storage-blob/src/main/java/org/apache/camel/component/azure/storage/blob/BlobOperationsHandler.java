package org.apache.camel.component.azure.storage.blob;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeader;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

/**
 * Handler responsible executing blob operations
 */
public class BlobOperationsHandler {

    private final BlobConfiguration configuration;

    public BlobOperationsHandler(final BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    public BlobExchangeResponse handleListBlobContainers(final BlobServiceClient client) {
        final BlobExchangeResponse blobExchangeResponse = new BlobExchangeResponse();
        blobExchangeResponse.setBody(client.listBlobContainers().stream()
                .collect(Collectors.toList()));

        return blobExchangeResponse;
    }

    public BlobExchangeResponse handleDownloadBlob(final BlobClient client) {
       checkIfContainerOrBlobIsEmpty(configuration);

       final BlobExchangeResponse blobExchangeResponse = new BlobExchangeResponse();

       if (isFileDownloadPathIsSet(configuration)) {
           // then we download the file directly
           final File outputFile = new File(configuration.getFileDir(), configuration.getBlobName());
           final BlobProperties properties = client.downloadToFile(outputFile.getAbsolutePath());
           blobExchangeResponse.setBody(outputFile);
           blobExchangeResponse.setHeaders(BlobUtils.createHeadersFromBlobProperties(properties));
       } else {
           // then we return inputStream
           final BlobInputStream inputStream = client.openInputStream();
           blobExchangeResponse.setBody(inputStream);
           blobExchangeResponse.setHeaders(BlobUtils.createHeadersFromBlobProperties(inputStream.getProperties()));
       }

       return blobExchangeResponse;
    }

    private boolean isFileDownloadPathIsSet(final BlobConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getFileDir());
    }

    public BlobExchangeResponse handleListBlobs(final BlobContainerClient client) {
        final BlobExchangeResponse blobExchangeResponse = new BlobExchangeResponse();

        // we need to have a container to list blobs
        if (ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("No container name was specified while on ListBlobs operations.");
        }

        blobExchangeResponse.setBody(client.listBlobs().stream()
                .collect(Collectors.toList()));

        return blobExchangeResponse;
    }

    public BlobExchangeResponse handleDeleteBlob(final BlobClient client) {
        checkIfContainerOrBlobIsEmpty(configuration);

        final BlobExchangeResponse blobExchangeResponse = new BlobExchangeResponse();

        final Response<Void> azureResponse = client.deleteWithResponse(null, null, null, Context.NONE);

        final Map<String, Object> headers = azureResponse.getHeaders().stream()
                .collect(Collectors.toMap(HttpHeader::getName, HttpHeader::getValue));
        blobExchangeResponse.setHeaders(headers);

        return blobExchangeResponse;
    }

    private void checkIfContainerOrBlobIsEmpty(final BlobConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getContainerName()) || ObjectHelper.isEmpty(configuration.getBlobName())) {
            throw new IllegalArgumentException("No blob or container name was specified.");
        }
    }
}
