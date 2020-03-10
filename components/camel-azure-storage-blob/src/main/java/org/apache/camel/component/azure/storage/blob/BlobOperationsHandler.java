package org.apache.camel.component.azure.storage.blob;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlobClientBase;
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

    public List<BlobContainerItem> handleListBlobContainers(final BlobServiceClient client) {
        return client.listBlobContainers().stream()
                .collect(Collectors.toList());
    }

    public void handleDownloadBlob(final Exchange exchange, final BlobClient client) throws IOException {
       checkIfContainerOrBlobIsEmpty(configuration);

       OutputStream outputStream = exchange.getIn().getBody(OutputStream.class);
       if (outputStream == null && !ObjectHelper.isEmpty(configuration.getFileDir())) {
           final File outputFile = new File(configuration.getFileDir(), configuration.getBlobName());
           getMessageForResponse(exchange).setBody(outputFile);
           outputStream = new FileOutputStream(outputFile);
       }
       try {
           if (outputStream == null) {
               final BlobInputStream blobInputStream = client.openInputStream();
               exchange.getIn().setBody(blobInputStream);
           } else {
               client.download(outputStream);
           }
       } finally {
           if (outputStream != null) {
               outputStream.close();
           }
       }
    }

    public List<BlobItem> handleListBlobs(final BlobContainerClient client) {
        // we need to have a container to list blobs
        if (ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("No container name was specified while on ListBlobs operations.");
        }
        return client.listBlobs().stream()
                .collect(Collectors.toList());
    }

    private void checkIfContainerOrBlobIsEmpty(final BlobConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getContainerName()) || ObjectHelper.isEmpty(configuration.getBlobName())) {
            throw new IllegalArgumentException("No blob or container name was specified.");
        }
    }

    private static Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getMessage();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }
}
