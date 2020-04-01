package org.apache.camel.component.azure.storage.blob;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

public final class BlobUtils {

    public static Message getInMessage(final Exchange exchange) {
        return exchange.getIn();
    }

    private BlobUtils() {
    }

}
