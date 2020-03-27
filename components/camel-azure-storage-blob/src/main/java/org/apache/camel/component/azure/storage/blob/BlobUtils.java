package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class BlobUtils {

    public static Message getInMessage(final Exchange exchange) {
        return exchange.getIn();
    }

    private BlobUtils() {
    }
}
