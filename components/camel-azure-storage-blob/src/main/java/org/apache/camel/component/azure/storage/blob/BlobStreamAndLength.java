package org.apache.camel.component.azure.storage.blob;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;

public class BlobStreamAndLength {

    private final InputStream inputStream;

    private final long streamLength;

    public static BlobStreamAndLength createBlobStreamAndLengthFromExchangeBody(final Exchange exchange) throws IOException {
        Object body = exchange.getIn().getBody();

        if (body instanceof WrappedFile) {
            // unwrap file
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof InputStream) {
            return new BlobStreamAndLength((InputStream) body, BlobUtils.getInputStreamLength((InputStream) body));
        }
        if (body instanceof File) {
            return new BlobStreamAndLength(new FileInputStream((File) body), ((File) body).length());
        }
        if (body instanceof byte[]) {
            return new BlobStreamAndLength(new ByteArrayInputStream((byte[]) body), ((byte[]) body).length);
        }

        // try as input stream
        final InputStream inputStream = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);

        if (inputStream == null) {
            // fallback to string based
            throw new IllegalArgumentException("Unsupported blob type:" + body.getClass().getName());
        }

        return new BlobStreamAndLength(inputStream, BlobUtils.getInputStreamLength(inputStream));
    }

    private BlobStreamAndLength(InputStream inputStream, long streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getStreamLength() {
        return streamLength;
    }
}
