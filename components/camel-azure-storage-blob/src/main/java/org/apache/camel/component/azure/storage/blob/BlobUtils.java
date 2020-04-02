package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.io.IOUtils;

public final class BlobUtils {

    public static Message getInMessage(final Exchange exchange) {
        return exchange.getIn();
    }

    public static Long getInputStreamLength(final InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("Reset inputStream is not supported, provide an inputstream with supported mark/reset.");
        }
        final long length = IOUtils.toByteArray(inputStream).length;
        inputStream.reset();

        return length;
    }

    private BlobUtils() {
    }

}
