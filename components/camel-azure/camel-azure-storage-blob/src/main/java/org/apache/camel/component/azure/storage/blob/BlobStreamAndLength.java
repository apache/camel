/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.storage.blob;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;

public final class BlobStreamAndLength {

    private final InputStream inputStream;

    private final long streamLength;

    private BlobStreamAndLength(InputStream inputStream, long streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    @SuppressWarnings("rawtypes")
    public static BlobStreamAndLength createBlobStreamAndLengthFromExchangeBody(final Exchange exchange) throws IOException {
        Object body = exchange.getIn().getBody();
        Long blobSize = exchange.getIn().getHeader(BlobConstants.BLOB_UPLOAD_SIZE, () -> null, Long.class);
        exchange.getIn().removeHeader(BlobConstants.BLOB_UPLOAD_SIZE); // remove to avoid issues for further uploads

        if (body instanceof WrappedFile) {
            // unwrap file
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof InputStream) {
            return new BlobStreamAndLength(
                    (InputStream) body, blobSize != null ? blobSize : BlobUtils.getInputStreamLength((InputStream) body));
        }
        if (body instanceof File) {
            return new BlobStreamAndLength(new BufferedInputStream(new FileInputStream((File) body)), ((File) body).length());
        }
        if (body instanceof byte[]) {
            return new BlobStreamAndLength(new ByteArrayInputStream((byte[]) body), ((byte[]) body).length);
        }

        // try as input stream
        final InputStream inputStream
                = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);

        if (inputStream == null) {
            // fallback to string based
            throw new IllegalArgumentException("Unsupported blob type:" + body.getClass().getName());
        }

        return new BlobStreamAndLength(inputStream, blobSize != null ? blobSize : BlobUtils.getInputStreamLength(inputStream));
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getStreamLength() {
        return streamLength;
    }
}
