package org.apache.camel.component.azure.storage.blob.operations;

import java.time.Duration;
import java.util.Map;

import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;

import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.blob.BlobContainerClient}
 */
public class BlobContainerOperations {

    private final BlobConfiguration configuration;
    private final BlobContainerClientWrapper client;

    public BlobContainerOperations(final BlobConfiguration configuration, final BlobContainerClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse listBlobs(final Exchange exchange) {
        if (exchange == null) {
            return new BlobOperationResponse(client.listBlobs(new ListBlobsOptions(), null));
        }

        final ListBlobsOptions listBlobOptions = getListBlobOptions(exchange);
        final Duration timeout = getTimeoutFromHeaders(exchange);

        return new BlobOperationResponse(client.listBlobs(listBlobOptions, timeout));
    }

    public BlobOperationResponse createContainer(final Exchange exchange) {
        if (exchange == null) {
            return new BlobOperationResponse(true, client.createContainer(null, null, null));
        }

        @SuppressWarnings("unchecked")
        final Map<String, String> metadata = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.METADATA, Map.class);
        final PublicAccessType publicAccessType = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.PUBLIC_ACCESS_TYPE, PublicAccessType.class);
        final Duration timeout = getTimeoutFromHeaders(exchange);

        return new BlobOperationResponse(true, client.createContainer(metadata, publicAccessType, timeout));
    }

    public BlobOperationResponse deleteContainer(final Exchange exchange) {
        if (exchange == null) {
            return new BlobOperationResponse(true, client.deleteContainer(null, null));
        }

        final BlobRequestConditions blobRequestConditions = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.BLOB_REQUEST_CONDITION, BlobRequestConditions.class);
        final Duration timeout = getTimeoutFromHeaders(exchange);

        return new BlobOperationResponse(true, client.deleteContainer(blobRequestConditions, timeout));
    }

    private ListBlobsOptions getListBlobOptions(final Exchange exchange) {
        ListBlobsOptions blobsOptions = BlobUtils.getInMessage(exchange).getHeader(
                BlobConstants.LIST_BLOB_OPTIONS, ListBlobsOptions.class);

        if (!ObjectHelper.isEmpty(blobsOptions)) {
            return blobsOptions;
        } else {
            blobsOptions = new ListBlobsOptions();
        }

        final BlobListDetails blobListDetails = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.BLOB_LIST_DETAILS, BlobListDetails.class);
        final String prefix = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.PREFIX, String.class);
        final Integer maxResultsPerPage = BlobUtils.getInMessage(exchange).getHeader(BlobConstants.MAX_RESULTS_PER_PAGE, Integer.class);

        blobsOptions.setDetails(blobListDetails);
        blobsOptions.setMaxResultsPerPage(maxResultsPerPage);
        blobsOptions.setPrefix(prefix);

        return blobsOptions;
    }

    private Duration getTimeoutFromHeaders (final Exchange exchange) {
        return BlobUtils.getInMessage(exchange).getHeader(BlobConstants.TIMEOUT, Duration.class);
    }
}
